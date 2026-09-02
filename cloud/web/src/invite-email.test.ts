import { afterEach, describe, expect, it, vi } from "vitest";
import { buildTeamInviteEmail, sendTeamInviteEmail } from "../../worker/invite-email";

afterEach(() => vi.unstubAllGlobals());

describe("team invitation email", () => {
  it("names the inviter, team, role, activation address, and invited email", () => {
    const email = buildTeamInviteEmail({
      appUrl: "https://manager.soccergrowthhub.com/",
      invitedEmail: "coach@example.com",
      inviterEmail: "andrew@example.com",
      inviterName: "Andrew",
      role: "COACH",
      teamName: "McFarland U9 Lightning",
    });

    expect(email.subject).toBe("Andrew invited you to McFarland U9 Lightning");
    expect(email.text).toContain("join McFarland U9 Lightning as a Coach");
    expect(email.text).toContain("coach@example.com");
    expect(email.html).toContain("https://manager.soccergrowthhub.com/");
  });

  it("escapes user-controlled values in the HTML message", () => {
    const email = buildTeamInviteEmail({
      appUrl: "https://manager.soccergrowthhub.com/",
      invitedEmail: "coach+u9@example.com",
      inviterEmail: "andrew@example.com",
      inviterName: "Coach <Admin>",
      role: "VIEWER",
      teamName: "U9 & Friends",
    });

    expect(email.html).toContain("Coach &lt;Admin&gt;");
    expect(email.html).toContain("U9 &amp; Friends");
    expect(email.html).not.toContain("Coach <Admin>");
    expect(email.text).toContain("as a Viewer");
  });

  it("sends through Resend using the API key as an authorization secret", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: "email_123" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);

    const result = await sendTeamInviteEmail("re_secret", "team@soccergrowthhub.com", {
      appUrl: "https://manager.soccergrowthhub.com/",
      invitedEmail: "coach@example.com",
      inviterEmail: "andrew@example.com",
      inviterName: "Andrew",
      role: "COACH",
      teamName: "McFarland U9 Lightning",
    });

    expect(result.messageId).toBe("email_123");
    expect(fetchMock).toHaveBeenCalledOnce();
    const [url, request] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("https://api.resend.com/emails");
    expect(request.headers).toMatchObject({ Authorization: "Bearer re_secret" });
    expect(request.body).toContain("team@soccergrowthhub.com");
    expect(request.body).toContain('"reply_to":"andrew@example.com"');
    expect(request.body).not.toContain("re_secret");
  });

  it("fails before making a request when the API key is not configured", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    await expect(sendTeamInviteEmail(undefined, "team@soccergrowthhub.com", {
      appUrl: "https://manager.soccergrowthhub.com/",
      invitedEmail: "coach@example.com",
      inviterEmail: "andrew@example.com",
      inviterName: "Andrew",
      role: "VIEWER",
      teamName: "McFarland U9 Lightning",
    })).rejects.toThrow("Resend API key is not configured");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
