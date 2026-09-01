PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
    email TEXT PRIMARY KEY,
    display_name TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS teams (
    team_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    year INTEGER NOT NULL,
    created_by TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (created_by) REFERENCES users(email)
);

CREATE TABLE IF NOT EXISTS memberships (
    team_id TEXT NOT NULL,
    email TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('OWNER', 'COACH', 'VIEWER')),
    created_at INTEGER NOT NULL,
    PRIMARY KEY (team_id, email),
    FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE,
    FOREIGN KEY (email) REFERENCES users(email) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS devices (
    device_id TEXT PRIMARY KEY,
    team_id TEXT NOT NULL,
    name TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    created_by TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL,
    revoked_at INTEGER,
    FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS pairing_codes (
    code_hash TEXT PRIMARY KEY,
    team_id TEXT NOT NULL,
    created_by TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS memberships_email_idx ON memberships(email);
CREATE INDEX IF NOT EXISTS devices_team_idx ON devices(team_id);
CREATE INDEX IF NOT EXISTS pairing_codes_expiry_idx ON pairing_codes(expires_at);
