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
      result.assignments.filter(item => item.halfNumber === half && item.position !== "GOALIE").forEach(item => {
        const groups = fieldGroupsByPlayer.get(item.playerId) ?? new Set<string>();
        groups.add(item.positionGroup);
        fieldGroupsByPlayer.set(item.playerId, groups);
      });
      for (const groups of fieldGroupsByPlayer.values()) expect(groups.size).toBe(1);
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
