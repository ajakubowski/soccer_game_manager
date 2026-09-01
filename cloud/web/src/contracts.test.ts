import { describe, expect, it } from "vitest";
import { lineupCellKey } from "../../shared/contracts";

describe("shared collaboration contracts", () => {
  it("creates a stable lineup cell identity", () => {
    expect(lineupCellKey({ gameId: "g1", halfNumber: 2, roundIndex: 3, slotKey: "CENTER_DEFENSE" }))
      .toBe("g1:2:3:CENTER_DEFENSE");
  });
});
