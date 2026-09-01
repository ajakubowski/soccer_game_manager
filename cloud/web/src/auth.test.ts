import { describe, expect, it } from "vitest";
import { constantTimeEqual, derivePasswordHash } from "../../worker/auth";

describe("password authentication helpers", () => {
  it("derives stable salted hashes and compares them safely", async () => {
    const salt = "00112233445566778899aabbccddeeff";
    const first = await derivePasswordHash("correct horse battery staple", salt, 100_000);
    const second = await derivePasswordHash("correct horse battery staple", salt, 100_000);
    const other = await derivePasswordHash("different password", salt, 100_000);

    expect(first).toBe(second);
    expect(constantTimeEqual(first, second)).toBe(true);
    expect(constantTimeEqual(first, other)).toBe(false);
  });
});
