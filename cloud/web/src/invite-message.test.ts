import { describe, expect, it } from "vitest";
import { buildManualInviteMessage } from "./invite-message";

describe("manual invitation message", () => {
  it("includes the team, inviter, role, exact email, and clean app link", () => {
    const message = buildManualInviteMessage({
      appUrl: "https://manager.soccergrowthhub.com/?source=access#invite",
      invitedEmail: "coach@example.com",
      inviterName: "Andy",
      role: "COACH",
      teamName: "McFarland U9 Lightning",
    });

    expect(message).toContain("Andy invited you");
    expect(message).toContain("McFarland U9 Lightning");
    expect(message).toContain("as a Coach");
    expect(message).toContain("coach@example.com");
    expect(message).toContain("https://manager.soccergrowthhub.com/");
    expect(message).not.toContain("source=access");
  });
});
