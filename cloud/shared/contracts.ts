export type MemberRole = "OWNER" | "COACH" | "VIEWER";
export type SyncStatus = "OFFLINE" | "SYNCING" | "SYNCED" | "PENDING" | "CONFLICT";
export type MutationOperation = "UPSERT_ENTITY" | "DELETE_ENTITY" | "SET_LINEUP_CELL";

export interface AuthUser {
  email: string;
  username: string;
  displayName: string;
}

export interface AuthSessionResponse {
  user: AuthUser | null;
  registrationOpen: boolean;
}

export interface CloudEntity<T = Record<string, unknown>> {
  entityType: string;
  entityId: string;
  version: number;
  payload: T | null;
  deletedAt: number | null;
  updatedAt: number;
  updatedBy: string;
}

export interface MutationCommand<T = Record<string, unknown>> {
  mutationId: string;
  deviceId: string;
  teamId: string;
  entityType: string;
  entityId: string;
  operation: MutationOperation;
  expectedVersion: number;
  payload: T | null;
  createdAt: number;
  cell?: {
    gameId: string;
    halfNumber: number;
    roundIndex: number;
    slotKey: string;
  };
  controllerLeaseToken?: string;
}

export interface SyncConflict {
  mutationId: string;
  entityType: string;
  entityId: string;
  reason: "VERSION_MISMATCH" | "STALE_DRAFT" | "CONTROLLER_REQUIRED";
  expectedVersion: number;
  actualVersion: number;
  serverEntity: CloudEntity | null;
}

export interface MutationResult {
  teamRevision: number;
  acceptedMutationIds: string[];
  conflicts: SyncConflict[];
  changes: CloudEntity[];
}

export interface PublishedLineupSnapshot {
  gameId: string;
  publishedVersion: number;
  teamRevision: number;
  payload: Record<string, unknown>;
  publishedBy: string;
  publishedAt: number;
}

export interface ControllerLease {
  gameId: string;
  deviceId: string;
  holderName: string;
  expiresAt: number;
  claimedAt: number;
  leaseToken?: string;
}

export interface TeamSnapshot {
  teamId: string;
  teamRevision: number;
  entities: CloudEntity[];
  publishedLineups: PublishedLineupSnapshot[];
  controllerLeases: ControllerLease[];
}

export interface TeamSummary {
  teamId: string;
  name: string;
  year: number;
  role: MemberRole;
}

export interface PresenceMember {
  connectionId: string;
  email: string;
  displayName: string;
  location: string;
}

export interface CollaborationEvent {
  type: "presence" | "changes" | "published" | "controller";
  teamRevision: number;
  presence?: PresenceMember[];
  changes?: CloudEntity[];
  published?: PublishedLineupSnapshot;
  controllerLease?: ControllerLease;
}

export const LIVE_ENTITY_TYPES = new Set(["game", "assignment", "goal", "availability"]);

export function lineupCellKey(cell: NonNullable<MutationCommand["cell"]>): string {
  return `${cell.gameId}:${cell.halfNumber}:${cell.roundIndex}:${cell.slotKey}`;
}
