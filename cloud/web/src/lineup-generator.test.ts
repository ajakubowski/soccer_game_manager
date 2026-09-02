import { describe, expect, it } from "vitest";
import { FORMATIONS, generateWebLineup, type WebLineupPlayer } from "../../shared/lineup-generator";

const players = (count: number): WebLineupPlayer[] => Array.from({ length: count }, (_, index) => ({
  playerId: `p${index + 1}`,
  name: `Player ${index + 1}`,
  keeperEligible: true,
  availableFirstHalf: true,
  availableSecondHalf: true,
}));

describe("web lineup generator", () => {
  it("generates both classic halves with fixed keepers and cross-half group rotation", () => {
    const result = generateWebLineup({
      players: players(13),
      formationType: "CLASSIC_U9",
      halfDurationMinutes: 25,
      substitutionWindowMinutes: 5,
    });
    expect(result.roundsPerHalf).toBe(6);
    expect(result.assignments).toHaveLength(2 * 6 * 7);
    for (const half of [1, 2]) {
      const keeperAssignments = result.assignments.filter(item => item.halfNumber === half && item.position === "GOALIE");
      const keepers = new Set(keeperAssignments.map(item => item.playerId));
      expect(keepers.size).toBe(1);
      expect(keeperAssignments).toHaveLength(result.roundsPerHalf);
      expect(new Set(keeperAssignments.map(item => item.roundIndex)).size).toBe(result.roundsPerHalf);
      const fieldGroupsByPlayer = new Map<string, Set<string>>();
      const positionsByPlayer = new Map<string, Set<string>>();
      result.assignments.filter(item => item.halfNumber === half && item.position !== "GOALIE").forEach(item => {
        const groups = fieldGroupsByPlayer.get(item.playerId) ?? new Set<string>();
        groups.add(item.positionGroup);
        fieldGroupsByPlayer.set(item.playerId, groups);
        const positions = positionsByPlayer.get(item.playerId) ?? new Set<string>();
        positions.add(item.position);
        positionsByPlayer.set(item.playerId, positions);
      });
      for (const groups of fieldGroupsByPlayer.values()) expect(groups.size).toBe(1);
      for (const positions of positionsByPlayer.values()) expect(positions.size).toBe(1);
    }
    const firstGroups = new Map(result.assignments.filter(item => item.halfNumber === 1 && item.position !== "GOALIE").map(item => [item.playerId, item.positionGroup]));
    for (const assignment of result.assignments.filter(item => item.halfNumber === 2 && item.position !== "GOALIE")) {
      if (firstGroups.has(assignment.playerId)) expect(assignment.positionGroup).not.toBe(firstGroups.get(assignment.playerId));
    }
    for (const half of [1, 2]) {
      for (let round = 1; round <= result.roundsPerHalf; round += 1) {
        const row = result.assignments.filter(item => item.halfNumber === half && item.roundIndex === round);
        expect(new Set(row.map(item => item.position))).toEqual(new Set(FORMATIONS.CLASSIC_U9.positions));
        expect(new Set(row.map(item => item.playerId)).size).toBe(7);
        if (round === 1) continue;
        const previous = new Map(result.assignments.filter(item => item.halfNumber === half && item.roundIndex === round - 1).map(item => [item.playerId, item.position]));
        row.forEach(item => {
          if (previous.has(item.playerId)) expect(item.position).toBe(previous.get(item.playerId));
        });
        const previousFieldPlayers = new Set(result.assignments
          .filter(item => item.halfNumber === half && item.roundIndex === round - 1 && item.position !== "GOALIE")
          .map(item => item.playerId));
        const currentFieldPlayers = new Set(row.filter(item => item.position !== "GOALIE").map(item => item.playerId));
        expect([...previousFieldPlayers].filter(playerId => currentFieldPlayers.has(playerId))).toEqual([]);
      }
    }
  });

  it("keeps in-half playing time balanced when group sizes cannot form perfect cohorts", () => {
    const result = generateWebLineup({
      players: players(11),
      formationType: "CLASSIC_U9",
      halfDurationMinutes: 25,
      substitutionWindowMinutes: 5,
    });
    for (const half of [1, 2]) {
      for (const group of ["DEFENSE", "LR_MID", "CM_STRIKER"]) {
        const counts = new Map<string, number>();
        result.assignments
          .filter(item => item.halfNumber === half && item.positionGroup === group)
          .forEach(item => counts.set(item.playerId, (counts.get(item.playerId) ?? 0) + 1));
        const values = [...counts.values()];
        expect(Math.max(...values) - Math.min(...values)).toBeLessThanOrEqual(1);
      }
    }
  });

  it("rotates the maximum possible players in every position group each round", () => {
    const result = generateWebLineup({
      players: players(10),
      formationType: "ATTACK_BACK_THREE",
      halfDurationMinutes: 25,
      substitutionWindowMinutes: 4,
    });
    for (const half of [1, 2]) {
      for (const definition of FORMATIONS.ATTACK_BACK_THREE.groups) {
        const groupAssignments = result.assignments.filter(item => item.halfNumber === half && item.positionGroup === definition.key);
        const groupPlayerCount = new Set(groupAssignments.map(item => item.playerId)).size;
        const unavoidableRepeats = Math.max(0, definition.positions.length * 2 - groupPlayerCount);
        for (let round = 2; round <= result.roundsPerHalf; round += 1) {
          const previousAssignments = new Map(groupAssignments.filter(item => item.roundIndex === round - 1).map(item => [item.playerId, item.position]));
          const currentAssignments = new Map(groupAssignments.filter(item => item.roundIndex === round).map(item => [item.playerId, item.position]));
          const stayingPlayerIds = [...previousAssignments.keys()].filter(playerId => currentAssignments.has(playerId));
          expect(stayingPlayerIds).toHaveLength(unavoidableRepeats);
          for (const playerId of stayingPlayerIds) expect(currentAssignments.get(playerId)).toBe(previousAssignments.get(playerId));
        }
      }
    }
  });

  it("keeps the unavoidable holdover in place for three-player classic groups", () => {
    const result = generateWebLineup({
      players: players(10),
      formationType: "CLASSIC_U9",
      halfDurationMinutes: 25,
      substitutionWindowMinutes: 4,
    });
    for (const half of [1, 2]) {
      for (const definition of FORMATIONS.CLASSIC_U9.groups) {
        const groupAssignments = result.assignments.filter(item => item.halfNumber === half && item.positionGroup === definition.key);
        for (let round = 2; round <= result.roundsPerHalf; round += 1) {
          const previousAssignments = new Map(groupAssignments.filter(item => item.roundIndex === round - 1).map(item => [item.playerId, item.position]));
          const currentAssignments = new Map(groupAssignments.filter(item => item.roundIndex === round).map(item => [item.playerId, item.position]));
          const stayingPlayerIds = [...previousAssignments.keys()].filter(playerId => currentAssignments.has(playerId));
          expect(stayingPlayerIds).toHaveLength(1);
          expect(currentAssignments.get(stayingPlayerIds[0])).toBe(previousAssignments.get(stayingPlayerIds[0]));
        }
      }
    }
  });

  it("keeps a manually selected keeper in goal for every rotation of that half", () => {
    const result = generateWebLineup({
      players: players(11),
      formationType: "CLASSIC_U9",
      halfDurationMinutes: 25,
      substitutionWindowMinutes: 4,
      plannedRoundsPerHalf: 8,
      manualLocks: [{ halfNumber: 1, positionGroup: "GOALIE", playerIds: ["p7"] }],
    });
    const halfOneGoalies = result.assignments.filter(item => item.halfNumber === 1 && item.position === "GOALIE");
    expect(halfOneGoalies).toHaveLength(8);
    expect(halfOneGoalies.every(item => item.playerId === "p7")).toBe(true);
    expect(result.assignments.filter(item => item.halfNumber === 1 && item.playerId === "p7").every(item => item.position === "GOALIE")).toBe(true);
  });

  it("generates Attack and Back Three positions and honors half availability", () => {
    const roster = players(9);
    roster[8].availableFirstHalf = false;
    const result = generateWebLineup({
      players: roster,
      formationType: "ATTACK_BACK_THREE",
      halfDurationMinutes: 25,
      substitutionWindowMinutes: 4,
    });
    const firstHalfPlayers = new Set(result.assignments.filter(item => item.halfNumber === 1).map(item => item.playerId));
    expect(firstHalfPlayers.has(roster[8].playerId)).toBe(false);
    expect(new Set(result.assignments.map(item => item.position))).toEqual(new Set(FORMATIONS.ATTACK_BACK_THREE.positions));
    expect(new Set(result.assignments.filter(item => item.position !== "GOALIE").map(item => item.positionGroup))).toEqual(new Set(["ATTACK", "DEFENSE"]));
  });

  it("uses the requested rotation count and honors goalie, group, and center-defense locks", () => {
    const roster = players(12);
    const result = generateWebLineup({
      players: roster,
      formationType: "ATTACK_BACK_THREE",
      halfDurationMinutes: 25,
      substitutionWindowMinutes: 5,
      plannedRoundsPerHalf: 7,
      manualLocks: [
        { halfNumber: 1, positionGroup: "GOALIE", playerIds: ["p1"] },
        { halfNumber: 1, positionGroup: "ATTACK", playerIds: ["p2", "p3"] },
        { halfNumber: 1, positionGroup: "DEFENSE", lockedPosition: "CENTER_DEFENSE", playerIds: ["p4", "p5"] },
      ],
    });

    expect(result.roundsPerHalf).toBe(7);
    expect(new Set(result.assignments.filter(item => item.halfNumber === 1 && item.position === "GOALIE").map(item => item.playerId))).toEqual(new Set(["p1"]));
    expect(new Set(result.assignments.filter(item => item.halfNumber === 1 && ["p2", "p3"].includes(item.playerId)).map(item => item.positionGroup))).toEqual(new Set(["ATTACK"]));
    expect(new Set(result.assignments.filter(item => item.halfNumber === 1 && item.position === "CENTER_DEFENSE").map(item => item.playerId))).toEqual(new Set(["p4", "p5"]));
  });
});
