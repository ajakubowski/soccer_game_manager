import type { AuthSessionResponse, AuthUser } from "../shared/contracts";
import type { D1Database } from "@cloudflare/workers-types";

const SESSION_COOKIE = "soccer_session";
const SESSION_DURATION_MS = 30 * 24 * 60 * 60 * 1000;
// Cloudflare Workers currently caps PBKDF2 at 100,000 iterations.
const PASSWORD_ITERATIONS = 100_000;
const LOGIN_WINDOW_MS = 15 * 60 * 1000;
const MAX_LOGIN_FAILURES = 8;

interface UserRow {
  email: string;
  username: string | null;
  display_name: string;
  password_hash: string | null;
  password_salt: string | null;
  password_iterations: number | null;
}

export interface BrowserIdentity {
  email: string;
  username: string;
  displayName: string;
}

export async function getAuthSession(request: Request, db: D1Database): Promise<AuthSessionResponse> {
  const identity = await authenticateBrowser(request, db);
  const registered = await db.prepare(
    "SELECT COUNT(*) AS count FROM users WHERE password_hash IS NOT NULL",
  ).first<{ count: number }>();
  return {
    user: identity ? toAuthUser(identity) : null,
    registrationOpen: Number(registered?.count ?? 0) === 0,
  };
}

export async function registerAccount(request: Request, db: D1Database, bootstrapCodeHash: string): Promise<Response> {
  const body = await readAuthJson<{ username: string; email: string; password: string; bootstrapCode?: string }>(request);
  const email = normalizeEmail(body.email);
  const username = normalizeUsername(body.username);
  validateEmail(email);
  validateUsername(username);
  validatePassword(body.password);

  const existing = await db.prepare(
    `SELECT email, username, display_name, password_hash, password_salt, password_iterations
     FROM users WHERE email = ?`,
  ).bind(email).first<UserRow>();
  const registered = await db.prepare(
    "SELECT COUNT(*) AS count FROM users WHERE password_hash IS NOT NULL",
  ).first<{ count: number }>();
  if (Number(registered?.count ?? 0) === 0) {
    const candidateHash = await sha256(body.bootstrapCode?.trim() ?? "");
    if (!constantTimeEqual(candidateHash, bootstrapCodeHash)) {
      throw new Error("UNAUTHORIZED:Invalid owner setup code");
    }
  }
  if (existing?.password_hash) throw new Error("FORBIDDEN:That account is already active");
  if (Number(registered?.count ?? 0) > 0 && !existing) {
    throw new Error("FORBIDDEN:Ask a team owner to invite this email before registering");
  }

  const duplicateUsername = await db.prepare(
    "SELECT email FROM users WHERE username = ? COLLATE NOCASE AND email <> ?",
  ).bind(username, email).first<{ email: string }>();
  if (duplicateUsername) throw new Error("FORBIDDEN:That username is already in use");

  const salt = randomHex(16);
  const passwordHash = await derivePasswordHash(body.password, salt, PASSWORD_ITERATIONS);
  const now = Date.now();
  if (existing) {
    await db.prepare(
      `UPDATE users SET username = ?, display_name = ?, password_hash = ?, password_salt = ?, password_iterations = ?
       WHERE email = ?`,
    ).bind(username, username, passwordHash, salt, PASSWORD_ITERATIONS, email).run();
  } else {
    await db.prepare(
      `INSERT INTO users
       (email, username, display_name, password_hash, password_salt, password_iterations, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
    ).bind(email, username, username, passwordHash, salt, PASSWORD_ITERATIONS, now).run();
  }
  return createSessionResponse(db, { email, username, displayName: username }, 201);
}

export async function loginAccount(request: Request, db: D1Database): Promise<Response> {
  const body = await readAuthJson<{ identifier: string; password: string }>(request);
  const identifier = body.identifier.trim().toLowerCase();
  if (!identifier || body.password.length > 256) throw new Error("UNAUTHORIZED:Invalid username/email or password");
  const attemptKey = await sha256(identifier);
  await enforceLoginLimit(db, attemptKey);

  const user = await db.prepare(
    `SELECT email, username, display_name, password_hash, password_salt, password_iterations
     FROM users WHERE email = ? OR username = ? COLLATE NOCASE LIMIT 1`,
  ).bind(identifier, identifier).first<UserRow>();
  const salt = user?.password_salt ?? "00000000000000000000000000000000";
  const iterations = user?.password_iterations ?? PASSWORD_ITERATIONS;
  const candidate = await derivePasswordHash(body.password, salt, iterations);
  if (!user?.password_hash || !constantTimeEqual(candidate, user.password_hash)) {
    await recordLoginFailure(db, attemptKey);
    throw new Error("UNAUTHORIZED:Invalid username/email or password");
  }

  await db.batch([
    db.prepare("DELETE FROM auth_attempts WHERE identifier_hash = ?").bind(attemptKey),
    db.prepare("DELETE FROM sessions WHERE expires_at <= ?").bind(Date.now()),
  ]);
  return createSessionResponse(db, {
    email: user.email,
    username: user.username ?? user.display_name,
    displayName: user.display_name,
  });
}

export async function logoutAccount(request: Request, db: D1Database): Promise<Response> {
  const token = cookieValue(request.headers.get("Cookie"), SESSION_COOKIE);
  if (token) {
    await db.prepare("DELETE FROM sessions WHERE token_hash = ?")
      .bind(await sha256(token)).run();
  }
  return Response.json({ ok: true }, {
    headers: { "Set-Cookie": expiredSessionCookie(), "Cache-Control": "no-store" },
  });
}

export async function authenticateBrowser(request: Request, db: D1Database): Promise<BrowserIdentity | null> {
  const token = cookieValue(request.headers.get("Cookie"), SESSION_COOKIE);
  if (!token) return null;
  const row = await db.prepare(
    `SELECT users.email, users.username, users.display_name
     FROM sessions JOIN users ON users.email = sessions.email
     WHERE sessions.token_hash = ? AND sessions.expires_at > ?`,
  ).bind(await sha256(token), Date.now()).first<Pick<UserRow, "email" | "username" | "display_name">>();
  if (!row) return null;
  return {
    email: row.email,
    username: row.username ?? row.display_name,
    displayName: row.display_name,
  };
}

export async function derivePasswordHash(password: string, saltHex: string, iterations: number): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(password),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const bits = await crypto.subtle.deriveBits({
    name: "PBKDF2",
    hash: "SHA-256",
    salt: hexBytes(saltHex),
    iterations,
  }, key, 256);
  return bytesHex(new Uint8Array(bits));
}

export function constantTimeEqual(left: string, right: string): boolean {
  const length = Math.max(left.length, right.length);
  let difference = left.length ^ right.length;
  for (let index = 0; index < length; index += 1) {
    difference |= (left.charCodeAt(index) || 0) ^ (right.charCodeAt(index) || 0);
  }
  return difference === 0;
}

async function createSessionResponse(db: D1Database, identity: BrowserIdentity, status = 200): Promise<Response> {
  const token = randomHex(32);
  const now = Date.now();
  await db.prepare(
    "INSERT INTO sessions (token_hash, email, created_at, expires_at, last_seen_at) VALUES (?, ?, ?, ?, ?)",
  ).bind(await sha256(token), identity.email, now, now + SESSION_DURATION_MS, now).run();
  return Response.json({ user: toAuthUser(identity) }, {
    status,
    headers: { "Set-Cookie": sessionCookie(token), "Cache-Control": "no-store" },
  });
}

async function enforceLoginLimit(db: D1Database, key: string): Promise<void> {
  const attempt = await db.prepare(
    "SELECT window_started_at, failure_count FROM auth_attempts WHERE identifier_hash = ?",
  ).bind(key).first<{ window_started_at: number; failure_count: number }>();
  if (attempt && attempt.window_started_at > Date.now() - LOGIN_WINDOW_MS && attempt.failure_count >= MAX_LOGIN_FAILURES) {
    throw new Error("RATE_LIMITED:Too many failed sign-in attempts. Try again in 15 minutes");
  }
}

async function recordLoginFailure(db: D1Database, key: string): Promise<void> {
  const now = Date.now();
  await db.prepare(
    `INSERT INTO auth_attempts (identifier_hash, window_started_at, failure_count) VALUES (?, ?, 1)
     ON CONFLICT(identifier_hash) DO UPDATE SET
       window_started_at = CASE WHEN window_started_at <= ? THEN excluded.window_started_at ELSE window_started_at END,
       failure_count = CASE WHEN window_started_at <= ? THEN 1 ELSE failure_count + 1 END`,
  ).bind(key, now, now - LOGIN_WINDOW_MS, now - LOGIN_WINDOW_MS).run();
}

function validatePassword(password: string): void {
  if (password.length < 10 || password.length > 128) {
    throw new Error("FORBIDDEN:Password must be between 10 and 128 characters");
  }
}

function validateUsername(username: string): void {
  if (!/^[a-z0-9._-]{3,30}$/i.test(username)) {
    throw new Error("FORBIDDEN:Username must be 3-30 letters, numbers, dots, dashes, or underscores");
  }
}

function validateEmail(email: string): void {
  if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    throw new Error("FORBIDDEN:Enter a valid email address");
  }
}

function normalizeEmail(value: string): string {
  return value.trim().toLowerCase();
}

function normalizeUsername(value: string): string {
  return value.trim();
}

function toAuthUser(identity: BrowserIdentity): AuthUser {
  return { email: identity.email, username: identity.username, displayName: identity.displayName };
}

function sessionCookie(token: string): string {
  return `${SESSION_COOKIE}=${token}; Path=/; Max-Age=${SESSION_DURATION_MS / 1000}; HttpOnly; Secure; SameSite=Strict`;
}

function expiredSessionCookie(): string {
  return `${SESSION_COOKIE}=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Strict`;
}

function cookieValue(header: string | null, name: string): string | null {
  if (!header) return null;
  for (const item of header.split(";")) {
    const [key, ...rest] = item.trim().split("=");
    if (key === name) return rest.join("=");
  }
  return null;
}

function randomHex(length: number): string {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return bytesHex(bytes);
}

function bytesHex(bytes: Uint8Array): string {
  return Array.from(bytes, byte => byte.toString(16).padStart(2, "0")).join("");
}

function hexBytes(value: string): ArrayBuffer {
  if (value.length % 2 !== 0 || !/^[0-9a-f]+$/i.test(value)) throw new Error("Invalid password salt");
  const bytes = Uint8Array.from(value.match(/.{2}/g) ?? [], byte => Number.parseInt(byte, 16));
  const buffer = new ArrayBuffer(bytes.length);
  new Uint8Array(buffer).set(bytes);
  return buffer;
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return bytesHex(new Uint8Array(digest));
}

async function readAuthJson<T>(request: Request): Promise<T> {
  if (!request.headers.get("content-type")?.includes("application/json")) {
    throw new Error("FORBIDDEN:JSON request required");
  }
  return request.json() as Promise<T>;
}
