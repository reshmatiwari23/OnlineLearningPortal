-- ============================================================
-- V2__create_courses.sql  (H2-compatible version)
-- H2 differences:
--   1. No JSONB — use VARCHAR(5000) instead
--   2. No GIN index — skip it
--   3. TIMESTAMPTZ → TIMESTAMP
--   4. No trigger — @PreUpdate in Java handles updated_at
--   5. UUID default: RANDOM_UUID()
-- ============================================================

CREATE TABLE IF NOT EXISTS courses (
    id              UUID            PRIMARY KEY DEFAULT RANDOM_UUID(),
    title           VARCHAR(500)    NOT NULL,
    description     VARCHAR(5000),
    instructor_id   UUID            NOT NULL,
    instructor_name VARCHAR(255)    NOT NULL,
    video_url       VARCHAR(1000),
    video_duration  INTEGER         DEFAULT 0,
    thumbnail_url   VARCHAR(1000),
    ai_summary      VARCHAR(5000),                  -- JSONB stored as VARCHAR in H2
    kb_ingested     BOOLEAN         NOT NULL DEFAULT FALSE,
    is_published    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_courses_title_instructor UNIQUE (title, instructor_id)
);

CREATE INDEX IF NOT EXISTS idx_courses_instructor_id ON courses (instructor_id);
CREATE INDEX IF NOT EXISTS idx_courses_is_published  ON courses (is_published);
CREATE INDEX IF NOT EXISTS idx_courses_created_at    ON courses (created_at DESC);
