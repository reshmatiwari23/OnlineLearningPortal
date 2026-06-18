-- ============================================================
-- V2__create_courses.sql
-- Creates the courses table.
-- Flyway runs this after V1 (users table from auth-service).
-- Note: both auth-service and course-service share the same
-- database (olp_db) — each service owns its own tables.
-- ============================================================

CREATE TABLE IF NOT EXISTS courses (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(500)    NOT NULL,
    description     TEXT,
    instructor_id   UUID            NOT NULL,       -- FK to users.id
    instructor_name VARCHAR(255)    NOT NULL,       -- denormalised for fast reads
    video_url       VARCHAR(1000),                  -- S3 object key (not full URL)
    video_duration  INTEGER         DEFAULT 0,      -- seconds, set by Lambda
    thumbnail_url   VARCHAR(1000),
    ai_summary      JSONB,                          -- Claude-generated summary
    kb_ingested     BOOLEAN         NOT NULL DEFAULT FALSE,
    is_published    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_courses_title_instructor UNIQUE (title, instructor_id)
);

-- ── Indexes ──────────────────────────────────────────────────────
-- Most common queries: list all courses, filter by instructor
CREATE INDEX IF NOT EXISTS idx_courses_instructor_id ON courses (instructor_id);
CREATE INDEX IF NOT EXISTS idx_courses_is_published  ON courses (is_published);
CREATE INDEX IF NOT EXISTS idx_courses_created_at    ON courses (created_at DESC);

-- GIN index on JSONB ai_summary for fast JSON field searches
CREATE INDEX IF NOT EXISTS idx_courses_ai_summary ON courses USING GIN (ai_summary);

-- ── Auto-update trigger ───────────────────────────────────────────
-- Reuse the function created in V1 (it is already in the DB)
CREATE TRIGGER trg_courses_updated_at
    BEFORE UPDATE ON courses
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE courses IS
    'Course catalogue. instructor_id links to users table. '
    'ai_summary stores Claude-generated JSON: {title, objectives, summary, difficulty, keyTakeaway}. '
    'video_url stores the S3 object key — full URL assembled at runtime via CloudFront.';

COMMENT ON COLUMN courses.ai_summary IS
    'JSON structure: {"title": "...", "objectives": ["..."], "summary": "...", "difficulty": "beginner|intermediate|advanced", "keyTakeaway": "..."}';
