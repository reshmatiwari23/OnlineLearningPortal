-- ============================================================
-- V1__init.sql  (H2-compatible version)
-- Used only for local development with H2 in-memory database.
-- The production version is in db/migration/ (PostgreSQL syntax).
--
-- Differences from the PostgreSQL version:
--   1. No CREATE EXTENSION (H2 does not need pgcrypto — UUID is built-in)
--   2. TIMESTAMPTZ replaced with TIMESTAMP (H2 does not support TIMESTAMPTZ)
--   3. No trigger (H2 supports triggers but we handle updated_at in Java @PreUpdate)
--   4. No COMMENT ON TABLE (H2 does not support it)
--   5. UUID default uses RANDOM_UUID() instead of gen_random_uuid()
-- ============================================================

-- ── Users table ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id            UUID          PRIMARY KEY DEFAULT RANDOM_UUID(),
    email         VARCHAR(255)  NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    name          VARCHAR(255)  NOT NULL,
    role          VARCHAR(50)   NOT NULL DEFAULT 'user',
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('user', 'instructor', 'admin'))
);

-- ── Indexes ───────────────────────────────────────────────────────
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users (email);
CREATE INDEX        IF NOT EXISTS idx_users_role  ON users (role);
