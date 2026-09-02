import type { MemberRole } from "../../shared/contracts";

const ROLE_LABELS: Record<MemberRole, string> = {
  OWNER: "Team Owner",
  COACH: "Coach",
  VIEWER: "Viewer",
};

export interface ManualInviteMessageInput {
  appUrl: string;
  invitedEmail: string;
  inviterName: string;
  role: MemberRole;
  teamName: string;
}

export function buildManualInviteMessage(input: ManualInviteMessageInput): string {
  const appUrl = new URL(input.appUrl);
  appUrl.hash = "";
  appUrl.search = "";

  return [
    `You're invited to help manage ${input.teamName}`,
    "",
    `Hi, ${input.inviterName} invited you to join ${input.teamName} as a ${ROLE_LABELS[input.role]} in Soccer Game Manager.`,
    "",
    `Open the app: ${appUrl.toString()}`,
    `Choose "Activate invite" and create your account using ${input.invitedEmail}. The email must match the invitation.`,
    "",
    "Once signed in, the shared roster, schedule, and published lineups will be available to you.",
  ].join("\n");
}
