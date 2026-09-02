import type {
  AuthSessionResponse,
  AuthUser,
  CollaborationEvent,
  MemberRole,
  MutationCommand,
  MutationResult,
  PublishedLineupSnapshot,
  TeamSnapshot,
  TeamSummary,
} from "../../shared/contracts";

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  if (init?.body) headers.set("Content-Type", "application/json");
  const response = await fetch(path, { ...init, headers, credentials: "same-origin" });
  if (!response.ok) {
    const body = await response.json().catch(() => ({ error: response.statusText })) as { error?: string };
    throw new Error(body.error ?? `Request failed (${response.status})`);
  }
  return response.json() as Promise<T>;
}

export const cloudApi = {
  authSession: () => api<AuthSessionResponse>("/api/auth/session"),
  register: (username: string, email: string, password: string, bootstrapCode?: string) => api<{ user: AuthUser }>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify({ username, email, password, bootstrapCode }),
  }),
  login: (identifier: string, password: string) => api<{ user: AuthUser }>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ identifier, password }),
  }),
  logout: () => api<{ ok: boolean }>("/api/auth/logout", { method: "POST", body: JSON.stringify({}) }),
  teams: () => api<{ teams: TeamSummary[] }>("/api/me/teams"),
  createTeam: (name: string, year: number) => api<{ teamId: string }>("/api/teams", {
    method: "POST",
    body: JSON.stringify({ name, year }),
  }),
  snapshot: (teamId: string) => api<TeamSnapshot>(`/api/teams/${teamId}/snapshot`),
  mutate: (teamId: string, commands: MutationCommand[]) => api<MutationResult>(`/api/teams/${teamId}/mutations`, {
    method: "POST",
    body: JSON.stringify({ commands }),
  }),
  publish: (teamId: string, gameId: string, expectedTeamRevision: number, payload: Record<string, unknown>, lineupName?: string) =>
    api<PublishedLineupSnapshot>(`/api/teams/${teamId}/games/${gameId}/lineup/publish`, {
      method: "POST",
      body: JSON.stringify({ expectedTeamRevision, payload, lineupName: lineupName?.trim() || undefined }),
    }),
  replaceLineup: (
    teamId: string,
    gameId: string,
    expectedGameVersion: number,
    game: Record<string, unknown>,
    assignments: Record<string, unknown>[],
    mutationId: string,
  ) => api<MutationResult>(`/api/teams/${teamId}/games/${gameId}/lineup/replace`, {
    method: "POST",
    body: JSON.stringify({ expectedGameVersion, game, assignments, mutationId }),
  }),
  deleteGame: (teamId: string, gameId: string, expectedGameVersion: number, mutationId: string) =>
    api<MutationResult>(`/api/teams/${teamId}/games/${gameId}`, {
      method: "DELETE",
      body: JSON.stringify({ expectedGameVersion, mutationId }),
    }),
  pairing: (teamId: string) => api<{ code: string; expiresAt: number }>(`/api/teams/${teamId}/pairings`, {
    method: "POST",
    body: JSON.stringify({}),
  }),
  members: (teamId: string) => api<{ members: Array<{ email: string; role: MemberRole }>; actorRole: MemberRole }>(`/api/teams/${teamId}/members`),
  invite: (teamId: string, email: string, role: MemberRole, sendEmail = true) => api<{
    ok: boolean;
    emailSent: boolean;
    manualShare?: boolean;
    deliveryMessage?: string;
    messageId?: string;
  }>(`/api/teams/${teamId}/members`, {
    method: "POST",
    body: JSON.stringify({ email, role, sendEmail }),
  }),
};

export function collaborationSocket(teamId: string, onEvent: (event: CollaborationEvent) => void): WebSocket {
  const protocol = location.protocol === "https:" ? "wss:" : "ws:";
  const socket = new WebSocket(`${protocol}//${location.host}/api/teams/${teamId}/collaboration?location=Game%20Hub`);
  socket.addEventListener("message", event => onEvent(JSON.parse(event.data) as CollaborationEvent));
  return socket;
}
