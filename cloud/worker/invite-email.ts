import type { MemberRole } from "../shared/contracts";

export interface TeamInviteEmailInput {
  appUrl: string;
  invitedEmail: string;
  inviterEmail: string;
  inviterName: string;
  role: MemberRole;
  teamName: string;
}

export interface TeamInviteEmailContent {
  html: string;
  subject: string;
  text: string;
}

export interface InviteEmailDelivery {
  messageId: string;
}

const ROLE_DETAILS: Record<MemberRole, { label: string; description: string }> = {
  OWNER: {
    label: "Team Owner",
    description: "manage the roster, schedule, lineups, reports, devices, and team access",
  },
  COACH: {
    label: "Coach",
    description: "view and edit the roster, schedule, and lineups, and publish shared lineup versions",
  },
  VIEWER: {
    label: "Viewer",
    description: "view the team, shared lineups, game status, history, and reports without making changes",
  },
};

export function buildTeamInviteEmail(input: TeamInviteEmailInput): TeamInviteEmailContent {
  const role = ROLE_DETAILS[input.role];
  const appUrl = normalizeAppUrl(input.appUrl);
  const safeAppUrl = escapeHtml(appUrl);
  const safeEmail = escapeHtml(input.invitedEmail);
  const safeInviter = escapeHtml(input.inviterName);
  const safeTeam = escapeHtml(input.teamName);

  const subject = `${input.inviterName} invited you to ${input.teamName}`;
  const text = [
    `You've been invited to Soccer Game Manager`,
    "",
    `${input.inviterName} invited you to join ${input.teamName} as a ${role.label}.`,
    `This role lets you ${role.description}.`,
    "",
    `Activate your access: ${appUrl}`,
    `Choose \"Activate invite\" and register using ${input.invitedEmail}. Your registration email must match this invitation.`,
    "",
    "If you were not expecting this invitation, you can safely ignore this email.",
    "This mailbox is not monitored. Contact the person who invited you if you need help.",
  ].join("\n");

  const html = `<!doctype html>
<html lang="en">
  <body style="margin:0;background:#edf4fc;color:#101319;font-family:Arial,sans-serif;">
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#edf4fc;padding:28px 12px;">
      <tr><td align="center">
        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#ffffff;border:1px solid #c7d9ee;border-radius:18px;overflow:hidden;">
          <tr><td style="background:#12365f;padding:24px 30px;color:#ffffff;">
            <div style="font-size:13px;font-weight:700;letter-spacing:1.2px;text-transform:uppercase;color:#cce0fa;">Soccer Growth Hub</div>
            <h1 style="margin:8px 0 0;font-size:27px;line-height:1.2;">Join ${safeTeam}</h1>
          </td></tr>
          <tr><td style="padding:30px;">
            <p style="margin:0 0 18px;font-size:17px;line-height:1.55;"><strong>${safeInviter}</strong> invited you to collaborate in Soccer Game Manager.</p>
            <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="margin:0 0 24px;background:#edf4fc;border-radius:12px;">
              <tr><td style="padding:18px 20px;">
                <div style="margin-bottom:8px;font-size:13px;color:#52667d;">TEAM</div>
                <div style="font-size:18px;font-weight:700;">${safeTeam}</div>
                <div style="margin:16px 0 8px;font-size:13px;color:#52667d;">ROLE</div>
                <div style="font-size:18px;font-weight:700;">${role.label}</div>
                <div style="margin-top:5px;font-size:14px;line-height:1.5;color:#40546a;">You can ${role.description}.</div>
              </td></tr>
            </table>
            <p style="margin:0 0 20px;font-size:15px;line-height:1.55;">Select <strong>Activate invite</strong>, then register with <strong>${safeEmail}</strong>. The email must match this invitation.</p>
            <table role="presentation" cellspacing="0" cellpadding="0"><tr><td style="border-radius:999px;background:#2165b5;">
              <a href="${safeAppUrl}" style="display:inline-block;padding:13px 24px;color:#ffffff;font-size:16px;font-weight:700;text-decoration:none;">Activate invitation</a>
            </td></tr></table>
            <p style="margin:24px 0 0;font-size:13px;line-height:1.5;color:#65788d;">If you were not expecting this invitation, you can safely ignore it. This mailbox is not monitored; contact ${safeInviter} if you need help.</p>
          </td></tr>
        </table>
      </td></tr>
    </table>
  </body>
</html>`;

  return { subject, text, html };
}

export async function sendTeamInviteEmail(
  apiKey: string | undefined,
  fromAddress: string,
  input: TeamInviteEmailInput,
): Promise<InviteEmailDelivery> {
  if (!apiKey) throw new Error("Resend API key is not configured");
  const content = buildTeamInviteEmail(input);
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      to: [input.invitedEmail],
      from: `Soccer Growth Hub <${fromAddress}>`,
      reply_to: input.inviterEmail,
      subject: content.subject,
      text: content.text,
      html: content.html,
    }),
  });
  const result = await response.json() as { id?: string; message?: string; name?: string };
  if (!response.ok || !result.id) {
    throw new Error(`Resend rejected the invitation (${response.status}): ${result.message ?? result.name ?? "Unknown error"}`);
  }
  return { messageId: result.id };
}

function normalizeAppUrl(value: string): string {
  const url = new URL(value);
  if (url.protocol !== "https:" && url.hostname !== "localhost") {
    throw new Error("Invitation app URL must use HTTPS");
  }
  return url.toString();
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, character => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "\"": "&quot;",
    "'": "&#39;",
  })[character] ?? character);
}
