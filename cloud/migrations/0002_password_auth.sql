ALTER TABLE users ADD COLUMN username TEXT COLLATE NOCASE;
ALTER TABLE users ADD COLUMN password_hash TEXT;
ALTER TABLE users ADD COLUMN password_salt TEXT;
ALTER TABLE users ADD COLUMN password_iterations INTEGER;

CREATE UNIQUE INDEX IF NOT EXISTS users_username_idx
ON users(username COLLATE NOCASE)
WHERE username IS NOT NULL;

CREATE TABLE IF NOT EXISTS sessions (
    token_hash TEXT PRIMARY KEY,
    email TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL,
    FOREIGN KEY (email) REFERENCES users(email) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS sessions_email_idx ON sessions(email);
CREATE INDEX IF NOT EXISTS sessions_expiry_idx ON sessions(expires_at);

CREATE TABLE IF NOT EXISTS auth_attempts (
    identifier_hash TEXT PRIMARY KEY,
    window_started_at INTEGER NOT NULL,
    failure_count INTEGER NOT NULL
);

