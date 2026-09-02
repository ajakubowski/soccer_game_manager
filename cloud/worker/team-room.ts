import { DurableObject } from "cloudflare:workers";
import type {
  CloudEntity,
  CollaborationEvent,
  ControllerLease,
  MutationCommand,
  MutationResult,
  PresenceMember,
  PublishedLineupSnapshot,
  TeamSnapshot,
} from "../shared/contracts";
import { lineupCellKey, LIVE_ENTITY_TYPES } from "../shared/contracts";

interface Actor {
  id: string;
  displayName: string;
  role: "OWNER" | "COACH" | "VIEWER";
  teamId?: string;
  deviceId?: string;
  userId?: string;
  deviceName?: string;
}

interface StoredEntityRow extends Record<string, SqlStorageValue> {
  entity_type: string;
  entity_id: string;
  version: number;
  payload: string | null;
  deleted_at: number | null;
  updated_at: number;
  updated_by: string;
}

interface PublishedRow extends Record<string, SqlStorageValue> {
  game_id: string;
  published_version: number;
  team_revision: number;
  payload: string;
  lineup_name: string | null;
  published_by: string;
  published_by_user: string | null;
  published_from_device_id: string | null;
  published_from_device_name: string | null;
  published_at: number;
}

interface ControllerRow extends Record<string, SqlStorageValue> {
  game_id: string;
  device_id: string;
  holder_name: string;
  token_hash: string;
  expires_at: number;
  claimed_at: number;
}

interface ChangeRow extends StoredEntityRow {
  revision: number;
}

export class TeamRoom extends DurableObject<Env> {
  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    ctx.blockConcurrencyWhile(async () => {
      this.ctx.storage.sql.exec(`
        CREATE TABLE IF NOT EXISTS metadata (
          key TEXT PRIMARY KEY,
          value TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS entities (
          entity_type TEXT NOT NULL,
          entity_id TEXT NOT NULL,
          version INTEGER NOT NULL,
          payload TEXT,
          deleted_at INTEGER,
          updated_at INTEGER NOT NULL,
          updated_by TEXT NOT NULL,
          PRIMARY KEY (entity_type, entity_id)
        );
        CREATE TABLE IF NOT EXISTS changes (
          revision INTEGER PRIMARY KEY,
          mutation_id TEXT NOT NULL UNIQUE,
          entity_type TEXT NOT NULL,
          entity_id TEXT NOT NULL,
          version INTEGER NOT NULL,
          payload TEXT,
          deleted_at INTEGER,
          updated_at INTEGER NOT NULL,
          updated_by TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS mutation_results (
          mutation_id TEXT PRIMARY KEY,
          result_json TEXT NOT NULL,
          created_at INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS lineup_cell_versions (
          cell_key TEXT PRIMARY KEY,
          version INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS published_lineups (
          game_id TEXT NOT NULL,
          published_version INTEGER NOT NULL,
          team_revision INTEGER NOT NULL,
          payload TEXT NOT NULL,
          lineup_name TEXT,
          published_by TEXT NOT NULL,
          published_by_user TEXT,
          published_from_device_id TEXT,
          published_from_device_name TEXT,
          published_at INTEGER NOT NULL,
          PRIMARY KEY (game_id, published_version)
        );
        CREATE TABLE IF NOT EXISTS controller_leases (
          game_id TEXT PRIMARY KEY,
          device_id TEXT NOT NULL,
          holder_name TEXT NOT NULL,
          token_hash TEXT NOT NULL,
          expires_at INTEGER NOT NULL,
          claimed_at INTEGER NOT NULL
        );
      `);
      this.ctx.storage.sql.exec(
        "INSERT OR IGNORE INTO metadata (key, value) VALUES ('team_revision', '0')",
      );
      const publishedColumns = new Set(
        this.ctx.storage.sql.exec<{ name: string }>("PRAGMA table_info(published_lineups)")
          .toArray()
          .map(column => column.name),
      );
      const missingPublishedColumns = [
        ["lineup_name", "TEXT"],
        ["published_by_user", "TEXT"],
        ["published_from_device_id", "TEXT"],
        ["published_from_device_name", "TEXT"],
      ] as const;
      missingPublishedColumns.forEach(([name, type]) => {
        if (!publishedColumns.has(name)) {
          this.ctx.storage.sql.exec(`ALTER TABLE published_lineups ADD COLUMN ${name} ${type}`);
        }
      });
    });
  }

  async initializeTeam(
    teamId: string,
    profile: Record<string, unknown>,
    season: Record<string, unknown>,
    actor: Actor,
  ): Promise<TeamSnapshot> {
    if (!this.entity("team_profile", teamId)) {
      this.applyMutationInternal({
        mutationId: crypto.randomUUID(),
        deviceId: "web",
        teamId,
        entityType: "team_profile",
        entityId: teamId,
        operation: "UPSERT_ENTITY",
        expectedVersion: 0,
        payload: profile,
        createdAt: Date.now(),
      }, actor);
    }
    if (!this.entity("season", teamId)) {
      this.applyMutationInternal({
        mutationId: crypto.randomUUID(),
        deviceId: "web",
        teamId,
        entityType: "season",
        entityId: teamId,
        operation: "UPSERT_ENTITY",
        expectedVersion: 0,
        payload: season,
        createdAt: Date.now(),
      }, actor);
    }
    return this.getSnapshot(teamId);
  }

  async getSnapshot(teamId: string): Promise<TeamSnapshot> {
    const entities = this.ctx.storage.sql.exec<StoredEntityRow>(
      "SELECT * FROM entities ORDER BY entity_type, entity_id",
    ).toArray().map(mapEntity);
    const publishedLineups = this.ctx.storage.sql.exec<PublishedRow>(
      "SELECT * FROM published_lineups ORDER BY game_id, published_version",
    ).toArray().map(mapPublished);
    const controllerLeases = this.activeControllerLeases();
    return {
      teamId,
      teamRevision: this.currentRevision(),
      entities,
      publishedLineups,
      controllerLeases,
    };
  }

  async getChanges(afterRevision: number): Promise<{ teamRevision: number; changes: CloudEntity[] }> {
    const changes = this.ctx.storage.sql.exec<ChangeRow>(
      "SELECT * FROM changes WHERE revision > ? ORDER BY revision",
      afterRevision,
    ).toArray().map(mapEntity);
    return { teamRevision: this.currentRevision(), changes };
  }

  async applyMutations(commands: MutationCommand[], actor: Actor): Promise<MutationResult> {
    if (actor.role === "VIEWER") throw new Error("Viewer access is read-only");
    const acceptedMutationIds: string[] = [];
    const conflicts: MutationResult["conflicts"] = [];
    const changes: CloudEntity[] = [];

    for (const command of commands) {
      const cached = this.ctx.storage.sql.exec<{ result_json: string }>(
        "SELECT result_json FROM mutation_results WHERE mutation_id = ?",
        command.mutationId,
      ).toArray()[0];
      if (cached) {
        const previous = JSON.parse(cached.result_json) as MutationResult;
        acceptedMutationIds.push(...previous.acceptedMutationIds);
        conflicts.push(...previous.conflicts);
        changes.push(...previous.changes);
        continue;
      }

      const result = await this.validateMutation(command, actor);
      if (result) {
        conflicts.push(result);
        this.storeMutationResult(command.mutationId, {
          teamRevision: this.currentRevision(),
          acceptedMutationIds: [],
          conflicts: [result],
          changes: [],
        });
        continue;
      }

      const entity = this.applyMutationInternal(command, actor);
      acceptedMutationIds.push(command.mutationId);
      changes.push(entity);
      this.storeMutationResult(command.mutationId, {
        teamRevision: this.currentRevision(),
        acceptedMutationIds: [command.mutationId],
        conflicts: [],
        changes: [entity],
      });
    }

    const response = { teamRevision: this.currentRevision(), acceptedMutationIds, conflicts, changes };
    if (changes.length > 0) this.broadcast({ type: "changes", teamRevision: response.teamRevision, changes });
    return response;
  }

  async replaceLineup(
    gameId: string,
    expectedGameVersion: number,
    gamePayload: Record<string, unknown>,
    assignmentPayloads: Record<string, unknown>[],
    mutationId: string,
    actor: Actor,
  ): Promise<MutationResult> {
    if (actor.role === "VIEWER") throw new Error("Viewer access is read-only");
    const cached = this.ctx.storage.sql.exec<{ result_json: string }>(
      "SELECT result_json FROM mutation_results WHERE mutation_id = ?",
      mutationId,
    ).toArray()[0];
    if (cached) return JSON.parse(cached.result_json) as MutationResult;

    const game = this.entity("game", gameId);
    const actualVersion = game?.version ?? 0;
    if (actualVersion !== expectedGameVersion) {
      const conflict: MutationResult["conflicts"][number] = {
        mutationId,
        entityType: "game",
        entityId: gameId,
        reason: "VERSION_MISMATCH",
        expectedVersion: expectedGameVersion,
        actualVersion,
        serverEntity: game,
      };
      const response = { teamRevision: this.currentRevision(), acceptedMutationIds: [], conflicts: [conflict], changes: [] };
      this.storeMutationResult(mutationId, response);
      return response;
    }
    const status = String(game?.payload?.status ?? "");
    if (status === "LIVE" || status === "FINAL") throw new Error("FORBIDDEN:Live or final lineups cannot be regenerated");
    if (assignmentPayloads.length > 500) throw new Error("FORBIDDEN:Lineup is too large");

    const requestedCells = new Set<string>();
    for (const payload of assignmentPayloads) {
      const cell = assignmentCell("assignment", payload);
      if (!cell || cell.gameId !== gameId || typeof payload.assignmentId !== "string" || typeof payload.playerId !== "string") {
        throw new Error("FORBIDDEN:Invalid lineup assignment");
      }
      const key = lineupCellKey(cell);
      if (requestedCells.has(key)) throw new Error("FORBIDDEN:Duplicate lineup cell");
      requestedCells.add(key);
    }

    let response!: MutationResult;
    this.ctx.storage.transactionSync(() => {
      const changes: CloudEntity[] = [];
      const currentAssignments = this.ctx.storage.sql.exec<StoredEntityRow>(
        "SELECT * FROM entities WHERE entity_type = 'assignment' AND deleted_at IS NULL",
      ).toArray().map(mapEntity).filter(entity => entity.payload?.gameId === gameId);

      for (const existing of currentAssignments) {
        const cell = assignmentCell("assignment", existing.payload);
        changes.push(this.applyMutationInternal({
          mutationId: `${mutationId}:delete:${existing.entityId}`,
          deviceId: actor.deviceId ?? "web",
          teamId: actor.teamId ?? "",
          entityType: "assignment",
          entityId: existing.entityId,
          operation: "DELETE_ENTITY",
          expectedVersion: existing.version,
          payload: null,
          createdAt: Date.now(),
          cell,
        }, actor));
      }

      for (const payload of assignmentPayloads) {
        const assignmentId = String(payload.assignmentId);
        changes.push(this.applyMutationInternal({
          mutationId: `${mutationId}:create:${assignmentId}`,
          deviceId: actor.deviceId ?? "web",
          teamId: actor.teamId ?? "",
          entityType: "assignment",
          entityId: assignmentId,
          operation: "SET_LINEUP_CELL",
          expectedVersion: 0,
          payload,
          createdAt: Date.now(),
          cell: assignmentCell("assignment", payload),
        }, actor));
      }

      changes.push(this.applyMutationInternal({
        mutationId: `${mutationId}:game`,
        deviceId: actor.deviceId ?? "web",
        teamId: actor.teamId ?? "",
        entityType: "game",
        entityId: gameId,
        operation: "UPSERT_ENTITY",
        expectedVersion: expectedGameVersion,
        payload: { ...gamePayload, gameId },
        createdAt: Date.now(),
      }, actor));

      response = {
        teamRevision: this.currentRevision(),
        acceptedMutationIds: [mutationId],
        conflicts: [],
        changes,
      };
      this.storeMutationResult(mutationId, response);
    });
    this.broadcast({ type: "changes", teamRevision: response.teamRevision, changes: response.changes });
    return response;
  }

  async publishLineup(
    gameId: string,
    expectedTeamRevision: number,
    payload: Record<string, unknown>,
    lineupName: string | undefined,
    actor: Actor,
  ): Promise<PublishedLineupSnapshot> {
    if (actor.role === "VIEWER") throw new Error("Viewer access is read-only");
    const currentRevision = this.currentRevision();
    if (expectedTeamRevision !== currentRevision) {
      throw new Error(`STALE_DRAFT:${currentRevision}`);
    }
    const previous = this.ctx.storage.sql.exec<{ version: number }>(
      "SELECT COALESCE(MAX(published_version), 0) AS version FROM published_lineups WHERE game_id = ?",
      gameId,
    ).one();
    const published: PublishedLineupSnapshot = {
      gameId,
      publishedVersion: previous.version + 1,
      teamRevision: currentRevision,
      payload,
      lineupName: lineupName?.trim() || null,
      publishedBy: actor.displayName,
      publishedByUser: actor.userId ?? actor.id,
      publishedFromDeviceId: actor.deviceId ?? "web",
      publishedFromDeviceName: actor.deviceName ?? "Web app",
      publishedAt: Date.now(),
    };
    this.ctx.storage.sql.exec(
      `INSERT INTO published_lineups
       (game_id, published_version, team_revision, payload, lineup_name, published_by,
        published_by_user, published_from_device_id, published_from_device_name, published_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      gameId,
      published.publishedVersion,
      published.teamRevision,
      JSON.stringify(payload),
      published.lineupName,
      published.publishedBy,
      published.publishedByUser,
      published.publishedFromDeviceId,
      published.publishedFromDeviceName,
      published.publishedAt,
    );
    this.broadcast({ type: "published", teamRevision: currentRevision, published });
    return published;
  }

  async claimController(
    gameId: string,
    deviceId: string,
    holderName: string,
    durationHours: number,
    actor: Actor,
  ): Promise<ControllerLease> {
    if (actor.role === "VIEWER") throw new Error("Viewer access is read-only");
    const current = this.controllerRow(gameId);
    const now = Date.now();
    if (current && current.expires_at > now && current.device_id !== deviceId) {
      throw new Error(`CONTROLLER_HELD:${current.holder_name}`);
    }
    const leaseToken = secureToken();
    const tokenHash = await sha256(leaseToken);
    const lease: ControllerLease = {
      gameId,
      deviceId,
      holderName,
      claimedAt: now,
      expiresAt: now + Math.min(Math.max(durationHours, 1), 48) * 60 * 60 * 1000,
      leaseToken,
    };
    this.ctx.storage.sql.exec(
      `INSERT OR REPLACE INTO controller_leases
       (game_id, device_id, holder_name, token_hash, expires_at, claimed_at)
       VALUES (?, ?, ?, ?, ?, ?)`,
      gameId,
      deviceId,
      holderName,
      tokenHash,
      lease.expiresAt,
      lease.claimedAt,
    );
    this.broadcast({ type: "controller", teamRevision: this.currentRevision(), controllerLease: lease });
    return lease;
  }

  async fetch(request: Request): Promise<Response> {
    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("Expected websocket", { status: 426 });
    }
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    const connection: PresenceMember = {
      connectionId: crypto.randomUUID(),
      email: request.headers.get("X-Soccer-Actor") ?? "unknown",
      displayName: request.headers.get("X-Soccer-Display-Name") ?? "Coach",
      location: new URL(request.url).searchParams.get("location") ?? "Team",
    };
    server.serializeAttachment(connection);
    this.ctx.acceptWebSocket(server);
    this.broadcastPresence();
    return new Response(null, { status: 101, webSocket: client });
  }

  webSocketMessage(socket: WebSocket, message: string | ArrayBuffer): void {
    if (typeof message !== "string") return;
    const attachment = socket.deserializeAttachment() as PresenceMember | null;
    const parsed = JSON.parse(message) as { type?: string; location?: string };
    if (attachment && parsed.type === "presence" && parsed.location) {
      const updated = { ...attachment, location: parsed.location.slice(0, 80) };
      socket.serializeAttachment(updated);
      this.broadcastPresence();
    }
  }

  webSocketClose(socket: WebSocket): void {
    this.broadcastPresence(socket);
  }

  webSocketError(socket: WebSocket): void {
    this.broadcastPresence(socket);
  }

  private async validateMutation(command: MutationCommand, actor: Actor): Promise<MutationResult["conflicts"][number] | null> {
    const entity = this.entity(command.entityType, command.entityId);
    const actualVersion = command.cell
      ? this.cellVersionOrNull(lineupCellKey(command.cell)) ?? entity?.version ?? 0
      : entity?.version ?? 0;
    if (actualVersion !== command.expectedVersion) {
      return {
        mutationId: command.mutationId,
        entityType: command.entityType,
        entityId: command.entityId,
        reason: "VERSION_MISMATCH",
        expectedVersion: command.expectedVersion,
        actualVersion,
        serverEntity: entity,
      };
    }
    if (LIVE_ENTITY_TYPES.has(command.entityType)) {
      const previousPayload = entity?.payload as Record<string, unknown> | null;
      const gameId = String(command.payload?.gameId ?? previousPayload?.gameId ?? command.entityId);
      const lease = this.controllerRow(gameId);
      if (lease && lease.expires_at > Date.now() &&
          (lease.device_id !== actor.deviceId || !command.controllerLeaseToken ||
           !constantTimeEqual(lease.token_hash, await sha256(command.controllerLeaseToken)))) {
        return {
          mutationId: command.mutationId,
          entityType: command.entityType,
          entityId: command.entityId,
          reason: "CONTROLLER_REQUIRED",
          expectedVersion: command.expectedVersion,
          actualVersion,
          serverEntity: entity,
        };
      }
    }
    return null;
  }

  private applyMutationInternal(command: MutationCommand, actor: Actor): CloudEntity {
    const previous = this.entity(command.entityType, command.entityId);
    const version = (command.cell ? this.cellVersion(lineupCellKey(command.cell)) : previous?.version ?? 0) + 1;
    const revision = this.currentRevision() + 1;
    const updatedAt = Date.now();
    const deletedAt = command.operation === "DELETE_ENTITY" ? updatedAt : null;
    const payloadJson = deletedAt ? null : JSON.stringify(command.payload ?? {});
    this.ctx.storage.sql.exec(
      `INSERT OR REPLACE INTO entities
       (entity_type, entity_id, version, payload, deleted_at, updated_at, updated_by)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
      command.entityType,
      command.entityId,
      version,
      payloadJson,
      deletedAt,
      updatedAt,
      actor.id,
    );
    this.ctx.storage.sql.exec(
      `INSERT INTO changes
       (revision, mutation_id, entity_type, entity_id, version, payload, deleted_at, updated_at, updated_by)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      revision,
      command.mutationId,
      command.entityType,
      command.entityId,
      version,
      payloadJson,
      deletedAt,
      updatedAt,
      actor.id,
    );
    const cell = command.cell ?? assignmentCell(command.entityType, command.payload);
    if (cell) {
      this.ctx.storage.sql.exec(
        "INSERT OR REPLACE INTO lineup_cell_versions (cell_key, version) VALUES (?, ?)",
        lineupCellKey(cell),
        version,
      );
    }
    this.ctx.storage.sql.exec("UPDATE metadata SET value = ? WHERE key = 'team_revision'", String(revision));
    return {
      entityType: command.entityType,
      entityId: command.entityId,
      version,
      payload: deletedAt ? null : command.payload,
      deletedAt,
      updatedAt,
      updatedBy: actor.id,
    };
  }

  private entity(entityType: string, entityId: string): CloudEntity | null {
    const row = this.ctx.storage.sql.exec<StoredEntityRow>(
      "SELECT * FROM entities WHERE entity_type = ? AND entity_id = ?",
      entityType,
      entityId,
    ).toArray()[0];
    return row ? mapEntity(row) : null;
  }

  private controllerRow(gameId: string): ControllerRow | null {
    return this.ctx.storage.sql.exec<ControllerRow>(
      "SELECT * FROM controller_leases WHERE game_id = ?",
      gameId,
    ).toArray()[0] ?? null;
  }

  private activeControllerLeases(): ControllerLease[] {
    const now = Date.now();
    this.ctx.storage.sql.exec("DELETE FROM controller_leases WHERE expires_at <= ?", now);
    return this.ctx.storage.sql.exec<ControllerRow>(
      "SELECT * FROM controller_leases ORDER BY claimed_at DESC",
    ).toArray().map(row => ({
      gameId: row.game_id,
      deviceId: row.device_id,
      holderName: row.holder_name,
      expiresAt: row.expires_at,
      claimedAt: row.claimed_at,
    }));
  }

  private currentRevision(): number {
    const row = this.ctx.storage.sql.exec<{ value: string }>(
      "SELECT value FROM metadata WHERE key = 'team_revision'",
    ).one();
    return Number(row.value);
  }

  private cellVersion(key: string): number {
    const row = this.ctx.storage.sql.exec<{ version: number }>(
      "SELECT version FROM lineup_cell_versions WHERE cell_key = ?",
      key,
    ).toArray()[0];
    return row?.version ?? 0;
  }

  private cellVersionOrNull(key: string): number | null {
    const row = this.ctx.storage.sql.exec<{ version: number }>(
      "SELECT version FROM lineup_cell_versions WHERE cell_key = ?",
      key,
    ).toArray()[0];
    return row?.version ?? null;
  }

  private storeMutationResult(mutationId: string, result: MutationResult): void {
    this.ctx.storage.sql.exec(
      "INSERT OR REPLACE INTO mutation_results (mutation_id, result_json, created_at) VALUES (?, ?, ?)",
      mutationId,
      JSON.stringify(result),
      Date.now(),
    );
  }

  private broadcast(event: CollaborationEvent, excludedSocket?: WebSocket): void {
    const data = JSON.stringify(event);
    for (const socket of this.ctx.getWebSockets()) {
      if (socket === excludedSocket) continue;
      try {
        socket.send(data);
      } catch {
        // Hibernating WebSocket cleanup can briefly retain a closed socket.
      }
    }
  }

  private broadcastPresence(excludedSocket?: WebSocket): void {
    const presence = this.ctx.getWebSockets()
      .filter(socket => socket !== excludedSocket)
      .map(socket => socket.deserializeAttachment() as PresenceMember | null)
      .filter((member): member is PresenceMember => member !== null);
    this.broadcast({ type: "presence", teamRevision: this.currentRevision(), presence }, excludedSocket);
  }
}

function mapEntity(row: StoredEntityRow): CloudEntity {
  return {
    entityType: row.entity_type,
    entityId: row.entity_id,
    version: row.version,
    payload: row.payload ? JSON.parse(row.payload) as Record<string, unknown> : null,
    deletedAt: row.deleted_at,
    updatedAt: row.updated_at,
    updatedBy: row.updated_by,
  };
}

function mapPublished(row: PublishedRow): PublishedLineupSnapshot {
  return {
    gameId: row.game_id,
    publishedVersion: row.published_version,
    teamRevision: row.team_revision,
    payload: JSON.parse(row.payload) as Record<string, unknown>,
    lineupName: row.lineup_name ?? null,
    publishedBy: row.published_by,
    publishedByUser: row.published_by_user ?? row.published_by,
    publishedFromDeviceId: row.published_from_device_id ?? "legacy",
    publishedFromDeviceName: row.published_from_device_name ?? "Unknown device",
    publishedAt: row.published_at,
  };
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

function constantTimeEqual(left: string, right: string): boolean {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index += 1) {
    difference |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return difference === 0;
}

function assignmentCell(entityType: string, payload: Record<string, unknown> | null): MutationCommand["cell"] {
  if (entityType !== "assignment" || !payload) return undefined;
  const gameId = payload.gameId;
  const halfNumber = payload.halfNumber;
  const roundIndex = payload.roundIndex;
  const position = payload.position;
  if (typeof gameId !== "string" || typeof halfNumber !== "number" || typeof roundIndex !== "number" || typeof position !== "string") {
    return undefined;
  }
  return { gameId, halfNumber, roundIndex, slotKey: position };
}
