import { afterEach, describe, expect, it, vi } from "vitest";
import { cloudApi } from "./api";

describe("lineup regeneration API", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("sends regeneration as one version-checked lineup replacement", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(JSON.stringify({
      teamRevision: 12,
      acceptedMutationIds: ["mutation-1"],
      conflicts: [],
      changes: [],
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);

    await cloudApi.replaceLineup(
      "team-1",
      "game-1",
      4,
      { gameId: "game-1", status: "PREGAME" },
      [{ assignmentId: "a1", gameId: "game-1", halfNumber: 1, roundIndex: 1, position: "GOALIE", playerId: "p1" }],
      "mutation-1",
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [path, init] = fetchMock.mock.calls[0];
    expect(path).toBe("/api/teams/team-1/games/game-1/lineup/replace");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(String(init?.body))).toMatchObject({
      expectedGameVersion: 4,
      mutationId: "mutation-1",
      assignments: [{ assignmentId: "a1" }],
    });
  });

  it("includes an optional lineup name when publishing", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(JSON.stringify({
      gameId: "game-1",
      publishedVersion: 3,
      teamRevision: 12,
      payload: {},
      lineupName: "Game-day final",
      publishedBy: "Coach",
      publishedByUser: "coach@example.com",
      publishedFromDeviceId: "web",
      publishedFromDeviceName: "Web app",
      publishedAt: 123,
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);

    await cloudApi.publish("team-1", "game-1", 12, { assignments: [] }, " Game-day final ");

    const [path, init] = fetchMock.mock.calls[0];
    expect(path).toBe("/api/teams/team-1/games/game-1/lineup/publish");
    expect(JSON.parse(String(init?.body))).toMatchObject({
      expectedTeamRevision: 12,
      lineupName: "Game-day final",
    });
  });
});
