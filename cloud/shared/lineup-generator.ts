export type WebFormationType = "CLASSIC_U9" | "ATTACK_BACK_THREE";

export interface WebLineupPlayer {
  playerId: string;
  name: string;
  keeperEligible: boolean;
  availableFirstHalf: boolean;
  availableSecondHalf: boolean;
}

export interface WebPlayerHistory {
  keeperAssignments: number;
  minutesPlayed?: number;
  groupCounts: Record<string, number>;
  positionCounts: Record<string, number>;
  totalAssignments: number;
}

export interface WebGeneratedAssignment {
  playerId: string;
  halfNumber: number;
  roundIndex: number;
  position: string;
  positionGroup: string;
}

export interface WebManualLock {
  halfNumber: number;
  positionGroup: string;
  playerIds: string[];
  lockedPosition?: string | null;
}

export interface WebLineupGenerationResult {
  assignments: WebGeneratedAssignment[];
  warnings: string[];
  roundsPerHalf: number;
}

export interface WebLineupGenerationInput {
  players: WebLineupPlayer[];
  formationType: WebFormationType;
  halfDurationMinutes: number;
  substitutionWindowMinutes: number;
  plannedRoundsPerHalf?: number;
  manualLocks?: WebManualLock[];
  historyByPlayer?: Record<string, WebPlayerHistory>;
  variationSeed?: number;
}

export const FORMATIONS = {
  CLASSIC_U9: {
    label: "Classic U9",
    description: "Defense / L-R Mid / CM-Striker",
    positions: ["LEFT_DEFENSE", "RIGHT_DEFENSE", "LEFT_MIDFIELDER", "RIGHT_MIDFIELDER", "CENTER_MIDFIELDER", "STRIKER", "GOALIE"],
    groups: [
      { key: "DEFENSE", positions: ["LEFT_DEFENSE", "RIGHT_DEFENSE"] },
      { key: "LR_MID", positions: ["LEFT_MIDFIELDER", "RIGHT_MIDFIELDER"] },
      { key: "CM_STRIKER", positions: ["CENTER_MIDFIELDER", "STRIKER"] },
    ],
  },
  ATTACK_BACK_THREE: {
    label: "Attack + Back Three",
    description: "Attack / Defense / Goalie",
    positions: ["STRIKER", "LEFT_MIDFIELDER", "RIGHT_MIDFIELDER", "LEFT_DEFENSE", "CENTER_DEFENSE", "RIGHT_DEFENSE", "GOALIE"],
    groups: [
      { key: "ATTACK", positions: ["STRIKER", "LEFT_MIDFIELDER", "RIGHT_MIDFIELDER"] },
      { key: "DEFENSE", positions: ["LEFT_DEFENSE", "CENTER_DEFENSE", "RIGHT_DEFENSE"] },
    ],
  },
} as const;

export function generateWebLineup(input: WebLineupGenerationInput): WebLineupGenerationResult {
  const formation = FORMATIONS[input.formationType];
  const suggestedRounds = Math.ceil(Math.max(input.halfDurationMinutes, 1) / Math.max(input.substitutionWindowMinutes, 1)) + 1;
  const roundsPerHalf = Math.max(1, Math.round(input.plannedRoundsPerHalf ?? suggestedRounds));
  const history = input.historyByPlayer ?? {};
  const seed = input.variationSeed ?? 0;
  const sanitizedLocks = sanitizeManualLocks(input, formation);
  const warnings = [...sanitizedLocks.warnings];
  const assignments: WebGeneratedAssignment[] = [];
  const firstHalfGroups = new Map<string, string>();
  const selectedKeepers: WebLineupPlayer[] = [];

  for (const halfNumber of [1, 2]) {
    const available = input.players.filter(player => halfNumber === 1 ? player.availableFirstHalf : player.availableSecondHalf);
    if (available.length < formation.positions.length) {
      warnings.push(`Half ${halfNumber} needs at least ${formation.positions.length} available players to fill every position.`);
      continue;
    }

    const lockedKeeper = sanitizedLocks.groups.get(halfNumber)?.get("GOALIE")?.[0];
    const keeperPool = available.filter(candidate =>
      selectedKeepers.every(selected => selected.playerId !== candidate.playerId) || available.length <= selectedKeepers.length + 1
    );
    const keeper = lockedKeeper ?? [...(keeperPool.length ? keeperPool : available)].sort((left, right) =>
      compareNumbers(left.keeperEligible ? 0 : 1, right.keeperEligible ? 0 : 1) ||
      compareNumbers(history[left.playerId]?.keeperAssignments ?? 0, history[right.playerId]?.keeperAssignments ?? 0) ||
      compareNumbers(history[left.playerId]?.minutesPlayed ?? 0, history[right.playerId]?.minutesPlayed ?? 0) ||
      compareNumbers(variationRank(seed, `keeper-${halfNumber}-${left.playerId}`), variationRank(seed, `keeper-${halfNumber}-${right.playerId}`)) ||
      left.name.localeCompare(right.name)
    )[0];
    selectedKeepers.push(keeper);

    const fieldPlayers = available.filter(player => player.playerId !== keeper.playerId);
    const locksForHalf = sanitizedLocks.groups.get(halfNumber) ?? new Map();
    const positionLocksForHalf: Map<string, WebLineupPlayer[]> = sanitizedLocks.positions.get(halfNumber) ?? new Map();
    const lockedPlayersByGroup = new Map<string, WebLineupPlayer[]>();
    for (const group of formation.groups) {
      const positionLocked = group.positions.flatMap(position => positionLocksForHalf.get(position) ?? []);
      lockedPlayersByGroup.set(group.key, uniquePlayers([...(locksForHalf.get(group.key) ?? []), ...positionLocked]).filter(player => player.playerId !== keeper.playerId));
    }
    const capacities = createGroupCapacities(
      formation.groups.map(group => group.key),
      fieldPlayers.length,
      new Map([...lockedPlayersByGroup].map(([group, players]) => [group, players.length])),
    );
    if ([...lockedPlayersByGroup.values()].flat().length > fieldPlayers.length) warnings.push(`Half ${halfNumber} has more locked field players than available field spots.`);
    if (capacities.some(capacity => capacity < 3)) {
      warnings.push(`Half ${halfNumber} cannot keep every field group at three players with the current availability.`);
    }
    const priorGroups = halfNumber === 2 ? firstHalfGroups : new Map<string, string>();
    if (halfNumber === 2) {
      for (const [group, lockedPlayers] of lockedPlayersByGroup) {
        for (const player of lockedPlayers) {
          if (priorGroups.get(player.playerId) === group) warnings.push(`${player.name} is manually locked into ${groupLabel(group)} for both halves.`);
        }
      }
    }
    const grouped = assignFieldGroups(
      formation.groups.map(group => group.key),
      fieldPlayers,
      capacities,
      lockedPlayersByGroup,
      priorGroups,
      history,
    );
    if (halfNumber === 1) {
      for (const [group, groupedPlayers] of grouped) for (const player of groupedPlayers) firstHalfGroups.set(player.playerId, group);
    }

    for (let roundIndex = 1; roundIndex <= roundsPerHalf; roundIndex += 1) {
      assignments.push({ playerId: keeper.playerId, halfNumber, roundIndex, position: "GOALIE", positionGroup: "GOALIE" });
    }

    for (const group of formation.groups) {
      assignments.push(...generateGroupRounds({
        halfNumber,
        roundsPerHalf,
        groupKey: group.key,
        positions: [...group.positions],
        players: grouped.get(group.key) ?? [],
        lockedPlayerIdsByPosition: Object.fromEntries(
          group.positions.map(position => [
            position,
            (positionLocksForHalf.get(position) ?? []).map(player => player.playerId),
          ]),
        ),
        history,
        seed,
      }));
    }
  }

  const positionOrder = new Map<string, number>(formation.positions.map((position, index) => [position, index]));
  assignments.sort((left, right) =>
    compareNumbers(left.halfNumber, right.halfNumber) ||
    compareNumbers(left.roundIndex, right.roundIndex) ||
    compareNumbers(positionOrder.get(left.position) ?? 999, positionOrder.get(right.position) ?? 999)
  );
  return { assignments, warnings: [...new Set(warnings)], roundsPerHalf };
}

function generateGroupRounds(input: {
  halfNumber: number;
  roundsPerHalf: number;
  groupKey: string;
  positions: string[];
  players: WebLineupPlayer[];
  lockedPlayerIdsByPosition: Record<string, string[]>;
  history: Record<string, WebPlayerHistory>;
  seed: number;
}): WebGeneratedAssignment[] {
  const output: WebGeneratedAssignment[] = [];
  const appearances = new Map(input.players.map(player => [player.playerId, 0]));
  const positionCounts = new Map<string, number>();
  let previous = new Map<string, string>();

  for (let roundIndex = 1; roundIndex <= input.roundsPerHalf; roundIndex += 1) {
    const selected = [...input.players].sort((left, right) =>
      compareNumbers(appearances.get(left.playerId) ?? 0, appearances.get(right.playerId) ?? 0) ||
      compareNumbers(input.history[left.playerId]?.minutesPlayed ?? 0, input.history[right.playerId]?.minutesPlayed ?? 0) ||
      compareNumbers(variationRank(input.seed, `round-${input.halfNumber}-${roundIndex}-${left.playerId}`), variationRank(input.seed, `round-${input.halfNumber}-${roundIndex}-${right.playerId}`)) ||
      left.name.localeCompare(right.name)
    ).slice(0, input.positions.length);
    for (const position of input.positions) {
      const lockedCandidates = input.players
        .filter(player => input.lockedPlayerIdsByPosition[position]?.includes(player.playerId))
        .sort((left, right) =>
          compareNumbers(appearances.get(left.playerId) ?? 0, appearances.get(right.playerId) ?? 0) ||
          compareNumbers(positionCounts.get(`${left.playerId}:${position}`) ?? 0, positionCounts.get(`${right.playerId}:${position}`) ?? 0) ||
          compareNumbers(variationRank(input.seed, `locked-position-${input.halfNumber}-${roundIndex}-${position}-${left.playerId}`), variationRank(input.seed, `locked-position-${input.halfNumber}-${roundIndex}-${position}-${right.playerId}`)) ||
          left.name.localeCompare(right.name)
        );
      const lockedPlayer = lockedCandidates[0];
      if (!lockedPlayer || selected.some(player => player.playerId === lockedPlayer.playerId)) continue;
      const replaceable = selected
        .filter(player => !Object.values(input.lockedPlayerIdsByPosition).some(ids => ids.includes(player.playerId)))
        .sort((left, right) =>
          compareNumbers(appearances.get(right.playerId) ?? 0, appearances.get(left.playerId) ?? 0) || right.name.localeCompare(left.name)
        )[0] ?? [...selected].sort((left, right) =>
          compareNumbers(appearances.get(right.playerId) ?? 0, appearances.get(left.playerId) ?? 0) || right.name.localeCompare(left.name)
        )[0];
      const replacementIndex = replaceable ? selected.findIndex(player => player.playerId === replaceable.playerId) : -1;
      if (replacementIndex >= 0) selected[replacementIndex] = lockedPlayer;
    }
    selected.forEach(player => appearances.set(player.playerId, (appearances.get(player.playerId) ?? 0) + 1));

    const remaining = new Map(selected.map(player => [player.playerId, player]));
    const current = new Map<string, string>();
    for (const position of input.positions) {
      const lockedPlayer = [...remaining.values()]
        .filter(player => input.lockedPlayerIdsByPosition[position]?.includes(player.playerId))
        .sort((left, right) =>
          compareNumbers(positionCounts.get(`${left.playerId}:${position}`) ?? 0, positionCounts.get(`${right.playerId}:${position}`) ?? 0) ||
          compareNumbers(appearances.get(left.playerId) ?? 0, appearances.get(right.playerId) ?? 0) ||
          compareNumbers(variationRank(input.seed, `locked-assignment-${input.halfNumber}-${roundIndex}-${position}-${left.playerId}`), variationRank(input.seed, `locked-assignment-${input.halfNumber}-${roundIndex}-${position}-${right.playerId}`)) ||
          left.name.localeCompare(right.name)
        )[0];
      if (lockedPlayer) {
        current.set(position, lockedPlayer.playerId);
        remaining.delete(lockedPlayer.playerId);
      }
    }
    for (const position of input.positions) {
      if (current.has(position)) continue;
      const priorPlayerId = previous.get(position);
      if (priorPlayerId && remaining.has(priorPlayerId)) {
        current.set(position, priorPlayerId);
        remaining.delete(priorPlayerId);
      }
    }
    for (const position of input.positions.filter(position => !current.has(position))) {
      const player = [...remaining.values()].sort((left, right) =>
        compareNumbers(
          (input.history[left.playerId]?.positionCounts?.[position] ?? 0) + (positionCounts.get(`${left.playerId}:${position}`) ?? 0),
          (input.history[right.playerId]?.positionCounts?.[position] ?? 0) + (positionCounts.get(`${right.playerId}:${position}`) ?? 0),
        ) ||
        compareNumbers(appearances.get(left.playerId) ?? 0, appearances.get(right.playerId) ?? 0) ||
        compareNumbers(variationRank(input.seed, `position-${input.halfNumber}-${roundIndex}-${position}-${left.playerId}`), variationRank(input.seed, `position-${input.halfNumber}-${roundIndex}-${position}-${right.playerId}`)) ||
        left.name.localeCompare(right.name)
      )[0];
      if (!player) continue;
      current.set(position, player.playerId);
      remaining.delete(player.playerId);
    }
    for (const position of input.positions) {
      const playerId = current.get(position);
      if (!playerId) continue;
      const key = `${playerId}:${position}`;
      positionCounts.set(key, (positionCounts.get(key) ?? 0) + 1);
      output.push({ playerId, halfNumber: input.halfNumber, roundIndex, position, positionGroup: input.groupKey });
    }
    previous = current;
  }
  return output;
}

function createGroupCapacities(
  groupKeys: string[],
  fieldPlayerCount: number,
  lockedCounts: Map<string, number>,
): number[] {
  const base = Math.floor(fieldPlayerCount / groupKeys.length);
  const extra = fieldPlayerCount % groupKeys.length;
  const capacities = groupKeys.map((_, index) => base + (index < extra ? 1 : 0));
  groupKeys.forEach((group, groupIndex) => {
    let deficit = (lockedCounts.get(group) ?? 0) - capacities[groupIndex];
    if (deficit <= 0) return;
    capacities[groupIndex] = lockedCounts.get(group) ?? capacities[groupIndex];
    const donors = groupKeys.map((donor, index) => ({ donor, index }))
      .filter(item => item.index !== groupIndex)
      .sort((left, right) =>
        (capacities[right.index] - (lockedCounts.get(right.donor) ?? 0)) -
        (capacities[left.index] - (lockedCounts.get(left.donor) ?? 0))
      );
    for (const donor of donors) {
      while (deficit > 0 && capacities[donor.index] > (lockedCounts.get(donor.donor) ?? 0)) {
        capacities[donor.index] -= 1;
        deficit -= 1;
      }
    }
  });
  return capacities;
}

function assignFieldGroups(
  groupKeys: string[],
  fieldPlayers: WebLineupPlayer[],
  capacities: number[],
  lockedPlayersByGroup: Map<string, WebLineupPlayer[]>,
  priorGroups: Map<string, string>,
  history: Record<string, WebPlayerHistory>,
): Map<string, WebLineupPlayer[]> {
  const result = new Map(groupKeys.map(group => [group, [...(lockedPlayersByGroup.get(group) ?? [])]]));
  const remaining = new Map(groupKeys.map((group, index) => [group, capacities[index] - (result.get(group)?.length ?? 0)]));
  const lockedIds = new Set([...lockedPlayersByGroup.values()].flat().map(player => player.playerId));
  const playerOrder = [...fieldPlayers].sort((left, right) => {
    const leftOptions = groupKeys.filter(group => (remaining.get(group) ?? 0) > 0 && priorGroups.get(left.playerId) !== group).length;
    const rightOptions = groupKeys.filter(group => (remaining.get(group) ?? 0) > 0 && priorGroups.get(right.playerId) !== group).length;
    return compareNumbers(leftOptions, rightOptions) ||
      compareNumbers(history[left.playerId]?.minutesPlayed ?? 0, history[right.playerId]?.minutesPlayed ?? 0) ||
      left.name.localeCompare(right.name);
  });
  for (const player of playerOrder.filter(player => !lockedIds.has(player.playerId))) {
    let allowed = groupKeys.filter(group => (remaining.get(group) ?? 0) > 0 && priorGroups.get(player.playerId) !== group);
    if (!allowed.length) allowed = groupKeys.filter(group => (remaining.get(group) ?? 0) > 0);
    const selected = [...allowed].sort((left, right) =>
      compareNumbers(history[player.playerId]?.groupCounts?.[left] ?? 0, history[player.playerId]?.groupCounts?.[right] ?? 0) ||
      compareNumbers(result.get(left)?.length ?? 0, result.get(right)?.length ?? 0) ||
      compareNumbers(groupKeys.indexOf(left), groupKeys.indexOf(right))
    )[0] ?? groupKeys[0];
    result.get(selected)?.push(player);
    remaining.set(selected, (remaining.get(selected) ?? 1) - 1);
  }
  return result;
}

function sanitizeManualLocks(
  input: WebLineupGenerationInput,
  formation: (typeof FORMATIONS)[WebFormationType],
): { groups: Map<number, Map<string, WebLineupPlayer[]>>; positions: Map<number, Map<string, WebLineupPlayer[]>>; warnings: string[] } {
  const warnings: string[] = [];
  const groups = new Map<number, Map<string, WebLineupPlayer[]>>();
  const positions = new Map<number, Map<string, WebLineupPlayer[]>>();
  const playerById = new Map(input.players.map(player => [player.playerId, player]));
  const activeGroups = new Set([...formation.groups.map(group => group.key as string), "GOALIE"]);
  const activePositions = new Set(formation.positions as readonly string[]);
  const groupedLocks = new Map<number, WebManualLock[]>();
  for (const lock of input.manualLocks ?? []) groupedLocks.set(lock.halfNumber, [...(groupedLocks.get(lock.halfNumber) ?? []), lock]);
  for (const [halfNumber, halfLocks] of groupedLocks) {
    if (halfNumber < 1 || halfNumber > 2) {
      warnings.push(`Ignored manual locks for half ${halfNumber} because that half does not exist.`);
      continue;
    }
    const taken = new Set<string>();
    const halfGroups = new Map<string, WebLineupPlayer[]>();
    const halfPositions = new Map<string, WebLineupPlayer[]>();
    for (const position of formation.positions) {
      const requested = [...new Set(halfLocks.filter(lock => lock.lockedPosition === position).flatMap(lock => lock.playerIds))];
      const selected = sanitizeRequestedPlayers(requested, halfNumber, label(position), playerById, taken, warnings);
      if (selected.length) halfPositions.set(position, selected);
    }
    for (const group of [...formation.groups.map(item => item.key as string), "GOALIE"]) {
      const requested = [...new Set(halfLocks.filter(lock => lock.positionGroup === group && !lock.lockedPosition).flatMap(lock => lock.playerIds))];
      let selected = sanitizeRequestedPlayers(requested, halfNumber, groupLabel(group), playerById, taken, warnings);
      if (group === "GOALIE" && selected.length > 1) {
        warnings.push(`Half ${halfNumber} goalie lock only supports one player. Keeping ${selected[0].name}.`);
        selected = selected.slice(0, 1);
      }
      if (selected.length) halfGroups.set(group, selected);
    }
    for (const lock of halfLocks.filter(lock => lock.lockedPosition && !activePositions.has(lock.lockedPosition) && lock.playerIds.length)) {
      warnings.push(`Ignored ${label(lock.lockedPosition!)} locks because that position is not used by ${formation.label}.`);
    }
    for (const lock of halfLocks.filter(lock => !lock.lockedPosition && !activeGroups.has(lock.positionGroup) && lock.playerIds.length)) {
      warnings.push(`Ignored ${groupLabel(lock.positionGroup)} locks because that group is not used by ${formation.label}.`);
    }
    groups.set(halfNumber, halfGroups);
    positions.set(halfNumber, halfPositions);
  }
  return { groups, positions, warnings };
}

function sanitizeRequestedPlayers(
  requested: string[],
  halfNumber: number,
  lockLabel: string,
  playerById: Map<string, WebLineupPlayer>,
  taken: Set<string>,
  warnings: string[],
): WebLineupPlayer[] {
  const selected: WebLineupPlayer[] = [];
  for (const playerId of requested) {
    const player = playerById.get(playerId);
    if (!player) {
      warnings.push(`Ignored a manual lock for an unavailable player in half ${halfNumber} ${lockLabel}.`);
      continue;
    }
    const available = halfNumber === 1 ? player.availableFirstHalf : player.availableSecondHalf;
    if (!available) {
      warnings.push(`Ignored a manual lock for ${player.name} in half ${halfNumber} because that player is unavailable that half.`);
      continue;
    }
    if (taken.has(playerId)) {
      warnings.push(`Ignored duplicate manual lock for ${player.name} in half ${halfNumber}.`);
      continue;
    }
    taken.add(playerId);
    selected.push(player);
  }
  return selected;
}

function uniquePlayers(players: WebLineupPlayer[]): WebLineupPlayer[] {
  return [...new Map(players.map(player => [player.playerId, player])).values()];
}

function groupLabel(group: string): string {
  return ({ ATTACK: "Attack", DEFENSE: "Defense", LR_MID: "L/R Mid", CM_STRIKER: "CM/Striker", GOALIE: "Goalie" } as Record<string, string>)[group] ?? group;
}

function label(value: string): string {
  return value.toLowerCase().split("_").map(part => part[0]?.toUpperCase() + part.slice(1)).join(" ");
}

function compareNumbers(left: number, right: number): number {
  return left - right;
}

function kotlinHash(value: string): number {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) hash = ((hash << 5) - hash + value.charCodeAt(index)) | 0;
  return hash;
}

function variationRank(seed: number, key: string): number {
  return kotlinHash(`${seed}${key}`);
}
