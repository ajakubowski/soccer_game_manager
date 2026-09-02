import type { MemberRole, MutationCommand } from "../shared/contracts";
import {
  authenticateBrowser,
  getAuthSession,
  loginAccount,
  logoutAccount,
  registerAccount,
} from "./auth";
import { sendTeamInviteEmail } from "./invite-email";
export { TeamRoom } from "./team-room";

interface Actor {
  id: string;
  displayName: string;
  role: MemberRole;
  teamId?: string;
  deviceId?: string;
  userId?: string;
  deviceName?: string;
}

interface MembershipRow {
  team_id: string;
  name: string;
  year: number;
  role: MemberRole;
}

interface DeviceRow {
  device_id: string;
  team_id: string;
  name: string;
  token_hash: string;
  created_by: string;
  revoked_at: number | null;
}

interface TeamRow {
  team_id: string;
  name: string;
  year: number;
  created_at: number;
}

const DEFAULT_U9_TEMPLATE = JSON.stringify({
  name: "U9 Match",
  halfCount: 2,
  halfDurationMinutes: 25,
  substitutionWindowMinutes: 4,
  substitutionEventsPerHalf: 3,
  nextSubAlertSeconds: 60,
  formationType: "CLASSIC_U9",
  positions: [
    "LEFT_DEFENSE",
    "RIGHT_DEFENSE",
    "LEFT_MIDFIELDER",
    "RIGHT_MIDFIELDER",
    "CENTER_MIDFIELDER",
    "STRIKER",
    "GOALIE",
  ],
});

export default {
  async fetch(request, env): Promise<Response> {
    const url = new URL(request.url);
    if (!url.pathname.startsWith("/api/")) return env.ASSETS.fetch(request);
    try {
      if (request.method === "OPTIONS") return apiHeaders(new Response(null, { status: 204 }), request);
      enforceSameOrigin(request, url);
      const response = await route(request, env, url);
      return apiHeaders(response, request);
    } catch (error) {
      const errorId = crypto.randomUUID();
      console.error(JSON.stringify({ level: "error", errorId, path: url.pathname, error: String(error) }));
      const message = error instanceof Error ? error.message : "Unexpected error";
      const status = message.startsWith("UNAUTHORIZED") ? 401
        : message.startsWith("RATE_LIMITED") ? 429
        : message.startsWith("FORBIDDEN") ? 403
          : message.startsWith("NOT_FOUND") ? 404
            : message.startsWith("STALE_DRAFT") || message.startsWith("CONTROLLER_HELD") ? 409
              : 500;
      return apiHeaders(json({ error: message, errorId }, status), request);
    }
  },
} satisfies ExportedHandler<Env>;

async function route(request: Request, env: Env, url: URL): Promise<Response> {
  if (url.pathname === "/api/health") return json({ ok: true, environment: env.ENVIRONMENT });
  if (url.pathname === "/api/device/pair" && request.method === "POST") return pairDevice(request, env);
  if (url.pathname === "/api/auth/session" && request.method === "GET") return json(await getAuthSession(request, env.DIRECTORY_DB));
  if (url.pathname === "/api/auth/register" && request.method === "POST") {
    return registerAccount(request, env.DIRECTORY_DB, env.BOOTSTRAP_CODE_HASH);
  }
  if (url.pathname === "/api/auth/login" && request.method === "POST") return loginAccount(request, env.DIRECTORY_DB);
  if (url.pathname === "/api/auth/logout" && request.method === "POST") return logoutAccount(request, env.DIRECTORY_DB);

  const actor = await authenticate(request, env);
  if (url.pathname === "/api/me/teams" && request.method === "GET") return listTeams(env, actor);
  if (url.pathname === "/api/teams" && request.method === "POST") return createTeam(request, env, actor);

  const teamMatch = url.pathname.match(/^\/api\/(?:device\/)?teams\/([^/]+)(?:\/(.*))?$/);
  if (!teamMatch) throw new Error("NOT_FOUND:Route");
  const teamId = decodeURIComponent(teamMatch[1]);
  const action = teamMatch[2] ?? "";
  const teamActor = await authorizeTeam(env, actor, teamId);
  const room = env.TEAM_ROOMS.getByName(teamId);

  if (action === "snapshot" && request.method === "GET") {
    const team = await env.DIRECTORY_DB.prepare(
      "SELECT team_id, name, year, created_at FROM teams WHERE team_id = ?",
    ).bind(teamId).first<TeamRow>();
    if (!team) throw new Error("NOT_FOUND:Team");
    return json(await room.initializeTeam(
      teamId,
      teamProfile(team),
      seasonProfile(team),
      teamActor,
    ));
  }
  if (action === "changes" && request.method === "GET") {
    const after = Number(url.searchParams.get("after") ?? 0);
    return json(await room.getChanges(Number.isFinite(after) ? after : 0));
  }
  if (action === "mutations" && request.method === "POST") {
    const body = await readJson<{ commands: MutationCommand[] }>(request);
    return json(await room.applyMutations(body.commands, teamActor));
  }
  if (action === "pairings" && request.method === "POST") return createPairing(request, env, teamActor, teamId);
  if (action === "members" && request.method === "GET") return listMembers(env, teamActor, teamId);
  if (action === "members" && request.method === "POST") return upsertMember(request, env, teamActor, teamId);
  if (action.startsWith("members/") && request.method === "DELETE") {
    return removeMember(env, teamActor, teamId, decodeURIComponent(action.slice("members/".length)));
  }
  if (action === "devices" && request.method === "GET") return listDevices(env, teamActor, teamId);
  if (action.startsWith("devices/") && request.method === "DELETE") {
    return revokeDevice(env, teamActor, teamId, decodeURIComponent(action.slice("devices/".length)));
  }
  if (action === "collaboration" && request.headers.get("Upgrade") === "websocket") {
    const headers = new Headers(request.headers);
    headers.set("X-Soccer-Actor", teamActor.id);
    headers.set("X-Soccer-Display-Name", teamActor.displayName);
    return room.fetch(new Request(request, { headers }));
  }

  const publishMatch = action.match(/^games\/([^/]+)\/lineup\/publish$/);
  if (publishMatch && request.method === "POST") {
    const body = await readJson<{ expectedTeamRevision: number; payload: Record<string, unknown>; lineupName?: string }>(request);
    return json(await room.publishLineup(
      decodeURIComponent(publishMatch[1]),
      body.expectedTeamRevision,
      body.payload,
      body.lineupName,
      teamActor,
    ));
  }
  const replaceLineupMatch = action.match(/^games\/([^/]+)\/lineup\/replace$/);
  if (replaceLineupMatch && request.method === "POST") {
    const body = await readJson<{
      expectedGameVersion: number;
      game: Record<string, unknown>;
      assignments: Record<string, unknown>[];
      mutationId: string;
    }>(request);
    return json(await room.replaceLineup(
      decodeURIComponent(replaceLineupMatch[1]),
      body.expectedGameVersion,
      body.game,
      body.assignments,
      body.mutationId,
      teamActor,
    ));
  }
  const deleteGameMatch = action.match(/^games\/([^/]+)$/);
  if (deleteGameMatch && request.method === "DELETE") {
    const body = await readJson<{ expectedGameVersion: number; mutationId: string }>(request);
    return json(await room.deleteGame(
      decodeURIComponent(deleteGameMatch[1]),
      body.expectedGameVersion,
      body.mutationId,
      teamActor,
    ));
  }
  const controllerMatch = action.match(/^games\/([^/]+)\/controller\/claim$/);
  if (controllerMatch && request.method === "POST") {
    const body = await readJson<{ deviceId?: string; holderName?: string; durationHours?: number }>(request);
    const deviceId = teamActor.deviceId ?? body.deviceId;
    if (!deviceId) throw new Error("FORBIDDEN:A paired device is required");
    return json(await room.claimController(
      decodeURIComponent(controllerMatch[1]),
      deviceId,
      body.holderName ?? teamActor.displayName,
      body.durationHours ?? 24,
      teamActor,
    ));
  }
  throw new Error("NOT_FOUND:Route");
}

async function authenticate(request: Request, env: Env): Promise<Actor> {
  const authorization = request.headers.get("Authorization");
  if (authorization?.startsWith("Bearer ")) {
    const tokenHash = await sha256(authorization.slice(7));
    const device = await env.DIRECTORY_DB.prepare(
      "SELECT device_id, team_id, name, token_hash, created_by, revoked_at FROM devices WHERE token_hash = ?",
    ).bind(tokenHash).first<DeviceRow>();
    if (!device || device.revoked_at) throw new Error("UNAUTHORIZED:Device token");
    await env.DIRECTORY_DB.prepare("UPDATE devices SET last_seen_at = ? WHERE device_id = ?")
      .bind(Date.now(), device.device_id).run();
    return {
      id: `device:${device.device_id}`,
      displayName: device.name,
      role: "COACH",
      teamId: device.team_id,
      deviceId: device.device_id,
      userId: device.created_by,
      deviceName: device.name,
    };
  }
  const identity = await authenticateBrowser(request, env.DIRECTORY_DB);
  if (!identity) throw new Error("UNAUTHORIZED:Sign in required");
  return { id: identity.email, displayName: identity.displayName, role: "VIEWER" };
}

async function authorizeTeam(env: Env, actor: Actor, teamId: string): Promise<Actor> {
  if (actor.deviceId) {
    if (actor.teamId !== teamId) throw new Error("FORBIDDEN:Device team mismatch");
    return actor;
  }
  const membership = await env.DIRECTORY_DB.prepare(
    "SELECT role FROM memberships WHERE team_id = ? AND email = ?",
  ).bind(teamId, actor.id).first<{ role: MemberRole }>();
  if (!membership) throw new Error("FORBIDDEN:Team membership required");
  return { ...actor, role: membership.role, teamId };
}

async function listTeams(env: Env, actor: Actor): Promise<Response> {
  if (actor.deviceId) {
    const team = await env.DIRECTORY_DB.prepare(
      "SELECT teams.team_id, teams.name, teams.year, 'COACH' AS role FROM teams WHERE team_id = ?",
    ).bind(actor.teamId).first<MembershipRow>();
    return json({ teams: team ? [mapTeam(team)] : [] });
  }
  const teams = await env.DIRECTORY_DB.prepare(
    `SELECT teams.team_id, teams.name, teams.year, memberships.role
     FROM teams JOIN memberships ON teams.team_id = memberships.team_id
     WHERE memberships.email = ? ORDER BY teams.year DESC, teams.name`,
  ).bind(actor.id).all<MembershipRow>();
  return json({ teams: teams.results.map(mapTeam) });
}

async function createTeam(request: Request, env: Env, actor: Actor): Promise<Response> {
  if (actor.deviceId) throw new Error("FORBIDDEN:Create teams from the web app");
  const body = await readJson<{ teamId?: string; name: string; year: number }>(request);
  const name = body.name?.trim();
  if (!name) throw new Error("FORBIDDEN:Team name is required");
  if (!Number.isInteger(body.year) || body.year < 2000 || body.year > 2100) {
    throw new Error("FORBIDDEN:Enter a valid team year");
  }
  const teamId = body.teamId ?? crypto.randomUUID();
  const now = Date.now();
  await env.DIRECTORY_DB.batch([
    env.DIRECTORY_DB.prepare("INSERT INTO teams (team_id, name, year, created_by, created_at) VALUES (?, ?, ?, ?, ?)")
      .bind(teamId, name, body.year, actor.id, now),
    env.DIRECTORY_DB.prepare("INSERT INTO memberships (team_id, email, role, created_at) VALUES (?, ?, 'OWNER', ?)")
      .bind(teamId, actor.id, now),
  ]);
  const room = env.TEAM_ROOMS.getByName(teamId);
  const team: TeamRow = { team_id: teamId, name, year: body.year, created_at: now };
  await room.initializeTeam(
    teamId,
    teamProfile(team),
    seasonProfile(team),
    { ...actor, role: "OWNER", teamId },
  );
  return json({ teamId }, 201);
}

async function createPairing(request: Request, env: Env, actor: Actor, teamId: string): Promise<Response> {
  if (actor.role === "VIEWER") throw new Error("FORBIDDEN:Coach access required");
  const code = pairingCode();
  const now = Date.now();
  await env.DIRECTORY_DB.prepare(
    "INSERT INTO pairing_codes (code_hash, team_id, created_by, expires_at, created_at) VALUES (?, ?, ?, ?, ?)",
  ).bind(await sha256(code), teamId, actor.id, now + 10 * 60 * 1000, now).run();
  return json({ code, expiresAt: now + 10 * 60 * 1000 });
}

async function pairDevice(request: Request, env: Env): Promise<Response> {
  const body = await readJson<{ code: string; deviceName: string }>(request);
  const codeHash = await sha256(body.code.replace(/\s/g, "").toUpperCase());
  const pairing = await env.DIRECTORY_DB.prepare(
    "SELECT team_id, created_by, expires_at FROM pairing_codes WHERE code_hash = ?",
  ).bind(codeHash).first<{ team_id: string; created_by: string; expires_at: number }>();
  if (!pairing || pairing.expires_at <= Date.now()) throw new Error("UNAUTHORIZED:Pairing code expired or invalid");
  const deviceId = crypto.randomUUID();
  const token = secureToken();
  const now = Date.now();
  await env.DIRECTORY_DB.batch([
    env.DIRECTORY_DB.prepare(
      `INSERT INTO devices
       (device_id, team_id, name, token_hash, created_by, created_at, last_seen_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
    ).bind(deviceId, pairing.team_id, body.deviceName.trim(), await sha256(token), pairing.created_by, now, now),
    env.DIRECTORY_DB.prepare("DELETE FROM pairing_codes WHERE code_hash = ?").bind(codeHash),
  ]);
  return json({ deviceId, teamId: pairing.team_id, token });
}

async function listMembers(env: Env, actor: Actor, teamId: string): Promise<Response> {
  const result = await env.DIRECTORY_DB.prepare(
    "SELECT email, role, created_at FROM memberships WHERE team_id = ? ORDER BY role, email",
  ).bind(teamId).all();
  return json({ members: result.results, actorRole: actor.role });
}

async function upsertMember(request: Request, env: Env, actor: Actor, teamId: string): Promise<Response> {
  if (actor.role !== "OWNER") throw new Error("FORBIDDEN:Owner access required");
  const body = await readJson<{ email: string; role: MemberRole; sendEmail?: boolean }>(request);
  const email = body.email.trim().toLowerCase();
  if (!email.includes("@") || !["OWNER", "COACH", "VIEWER"].includes(body.role)) {
    throw new Error("FORBIDDEN:Invalid member");
  }
  const now = Date.now();
  await env.DIRECTORY_DB.batch([
    env.DIRECTORY_DB.prepare("INSERT OR IGNORE INTO users (email, display_name, created_at) VALUES (?, ?, ?)")
      .bind(email, email.split("@")[0], now),
    env.DIRECTORY_DB.prepare(
      "INSERT OR REPLACE INTO memberships (team_id, email, role, created_at) VALUES (?, ?, ?, ?)",
    ).bind(teamId, email, body.role, now),
  ]);
  const team = await env.DIRECTORY_DB.prepare("SELECT name FROM teams WHERE team_id = ?")
    .bind(teamId).first<{ name: string }>();
  if (!team) throw new Error("NOT_FOUND:Team");

  if (body.sendEmail === false) {
    return json({ ok: true, emailSent: false, manualShare: true });
  }

  try {
    const delivery = await sendTeamInviteEmail(env.RESEND_API_KEY, env.INVITE_FROM_ADDRESS, {
      appUrl: env.APP_BASE_URL,
      invitedEmail: email,
      inviterEmail: actor.id,
      inviterName: actor.displayName,
      role: body.role,
      teamName: team.name,
    });
    return json({ ok: true, emailSent: true, messageId: delivery.messageId });
  } catch (error) {
    console.error(JSON.stringify({
      level: "error",
      event: "team_invite_email_failed",
      teamId,
      recipientDomain: email.split("@")[1],
      error: String(error),
    }));
    return json({
      ok: true,
      emailSent: false,
      deliveryMessage: "Access was added, but the invitation email could not be sent. Share the app link directly or try inviting again.",
    });
  }
}

async function removeMember(env: Env, actor: Actor, teamId: string, email: string): Promise<Response> {
  if (actor.role !== "OWNER" || email === actor.id) throw new Error("FORBIDDEN:Owner access required");
  await env.DIRECTORY_DB.prepare("DELETE FROM memberships WHERE team_id = ? AND email = ?")
    .bind(teamId, email.toLowerCase()).run();
  return json({ ok: true });
}

async function listDevices(env: Env, actor: Actor, teamId: string): Promise<Response> {
  const result = await env.DIRECTORY_DB.prepare(
    "SELECT device_id, name, created_at, last_seen_at, revoked_at FROM devices WHERE team_id = ? ORDER BY created_at DESC",
  ).bind(teamId).all();
  return json({ devices: result.results, actorRole: actor.role });
}

async function revokeDevice(env: Env, actor: Actor, teamId: string, deviceId: string): Promise<Response> {
  if (actor.role !== "OWNER") throw new Error("FORBIDDEN:Owner access required");
  await env.DIRECTORY_DB.prepare("UPDATE devices SET revoked_at = ? WHERE team_id = ? AND device_id = ?")
    .bind(Date.now(), teamId, deviceId).run();
  return json({ ok: true });
}

function mapTeam(row: MembershipRow) {
  return { teamId: row.team_id, name: row.name, year: row.year, role: row.role };
}

function teamProfile(team: TeamRow): Record<string, unknown> {
  return {
    seasonId: team.team_id,
    name: team.name,
    year: team.year,
    defaultTemplateJson: DEFAULT_U9_TEMPLATE,
    createdAt: team.created_at,
  };
}

function seasonProfile(team: TeamRow): Record<string, unknown> {
  return {
    seasonId: team.team_id,
    name: team.name,
    year: team.year,
    defaultTemplateJson: DEFAULT_U9_TEMPLATE,
    createdAt: team.created_at,
  };
}

async function readJson<T>(request: Request): Promise<T> {
  if (!request.headers.get("content-type")?.includes("application/json")) {
    throw new Error("FORBIDDEN:JSON request required");
  }
  return request.json<T>();
}

function json(value: unknown, status = 200): Response {
  return Response.json(value, { status, headers: { "Cache-Control": "no-store" } });
}

function apiHeaders(response: Response, request: Request): Response {
  // WebSocket upgrade responses carry a runtime-owned socket that cannot be
  // reconstructed as a normal Response without breaking the 101 handshake.
  if (response.status === 101) return response;
  const headers = new Headers(response.headers);
  const origin = request.headers.get("Origin");
  if (origin === new URL(request.url).origin) headers.set("Access-Control-Allow-Origin", origin);
  headers.set("Access-Control-Allow-Credentials", "true");
  headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
  headers.set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
  headers.set("X-Content-Type-Options", "nosniff");
  headers.set("Referrer-Policy", "no-referrer");
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
}

function enforceSameOrigin(request: Request, url: URL): void {
  if (!["POST", "PUT", "PATCH", "DELETE"].includes(request.method)) return;
  if (request.headers.get("Authorization")?.startsWith("Bearer ")) return;
  if (url.pathname === "/api/device/pair") return;
  if (request.headers.get("Origin") !== url.origin) throw new Error("FORBIDDEN:Invalid request origin");
}

function pairingCode(): string {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = new Uint8Array(8);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, value => alphabet[value % alphabet.length]).join("");
}

function secureToken(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, byte => byte.toString(16).padStart(2, "0")).join("");
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
}
