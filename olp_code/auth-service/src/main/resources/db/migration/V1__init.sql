-- ============================================================
-- V1__init.sql
-- Online Learning Portal — Initial schema
-- Flyway runs this automatically when auth-service starts.
-- Never edit a migration that has already run. Create V2 instead.
-- ============================================================

-- Enable pgcrypto for gen_random_uuid() — needed on PostgreSQL 15
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ── Users table ─────────────────────────────────────────────────
-- Stores all platform users (learners and instructors).
-- Passwords are stored as BCrypt(12) hashes — never plain text.

CREATE TABLE IF NOT EXISTS users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255)    NOT NULL,
    password_hash VARCHAR(255)    NOT NULL,
    name          VARCHAR(255)    NOT NULL,
    role          VARCHAR(50)     NOT NULL DEFAULT 'user',
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT uq_users_email  UNIQUE (email),
    CONSTRAINT chk_users_role  CHECK  (role IN ('user', 'instructor', 'admin'))
);

-- ── Indexes ──────────────────────────────────────────────────────
-- idx_users_email: used on every login and signup check
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users (email);

-- idx_users_role: used for admin queries filtering by role
CREATE INDEX IF NOT EXISTS idx_users_role ON users (role);

-- ── Auto-update updated_at trigger ───────────────────────────────
-- Automatically sets updated_at = NOW() whenever a row is updated.

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ── Table comment ─────────────────────────────────────────────────
COMMENT ON TABLE users IS
    'Platform users — both learners (role=user) and instructors (role=instructor). '
    'Passwords stored as BCrypt(12) hashes. Auth tokens issued via Amazon Cognito.';
