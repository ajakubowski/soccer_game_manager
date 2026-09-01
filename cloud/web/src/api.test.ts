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
});
