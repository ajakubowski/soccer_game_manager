import { describe, expect, it } from "vitest";
import type { TeamSnapshot } from "../../shared/contracts";
import { buildPrintReportModel } from "./print-report";

describe("buildPrintReportModel", () => {
  it("builds both lineup halves and recorded score details", () => {
    const snapshot: TeamSnapshot = {
      teamId: "team-1",
      teamRevision: 8,
      controllerLeases: [],
      publishedLineups: [],
      entities: [
        entity("game", "game-1", { gameId: "game-1", opponent: "Rockets", location: "Field 1", scheduledAt: 1_800_000_000_000, status: "FINAL", templateJson: JSON.stringify({ formationType: "CLASSIC_U9", plannedRoundsPerHalf: 2, substitutionWindowMinutes: 5 }), extraLineupSlotsJson: "[]" }),
        entity("player", "player-1", { name: "Alex" }),
        entity("player", "player-2", { name: "Jordan" }),
        entity("assignment", "a1", { gameId: "game-1", playerId: "player-1", halfNumber: 1, roundIndex: 1, position: "LEFT_DEFENSE", positionGroup: "DEFENSE" }),
        entity("assignment", "a2", { gameId: "game-1", playerId: "player-2", halfNumber: 2, roundIndex: 2, position: "GOALIE", positionGroup: "GOALIE" }),
        entity("goal", "goal-1", { gameId: "game-1", scoredBy: "TEAM", scorerPlayerId: "player-1", halfNumber: 1, elapsedSecondsInHalf: 125, notes: "Fast break" }),
      ],
    };

    const model = buildPrintReportModel(snapshot, "U9 Lightning", "game-1");

    expect(model?.halves).toHaveLength(2);
    expect(model?.halves[0].rows).toHaveLength(2);
    expect(model?.halves[0].rows[0].playersByPosition.LEFT_DEFENSE).toBe("Alex");
    expect(model?.teamGoals).toBe(1);
    expect(model?.goals[0]).toMatchObject({ time: "02:05", description: "Alex" });
  });
});

function entity(entityType: string, entityId: string, payload: Record<string, unknown>) {
  return { entityType, entityId, payload, version: 1, deletedAt: null, updatedAt: 1, updatedBy: "test" };
}
