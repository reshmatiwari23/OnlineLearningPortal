-- ============================================================
-- V3__create_enrollments.sql
-- Creates the enrollments table.
--
-- Key design: UNIQUE(course_id, user_id) enforced at DB level.
-- The application also checks before inserting (returns 409),
-- but the DB constraint is the final guarantee against
-- race conditions under concurrent requests.
-- ============================================================

CREATE TABLE IF NOT EXISTS enrollments (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id     UUID        NOT NULL,
    user_id       UUID        NOT NULL,
    enrolled_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at  TIMESTAMPTZ,                         -- NULL until course is completed

    -- The most important constraint: a user can only enrol once per course
    CONSTRAINT uq_enrollment UNIQUE (course_id, user_id)
);

-- ── Indexes ──────────────────────────────────────────────────────
-- Most common queries:
-- 1. "Is this user enrolled in this course?" — exact lookup
CREATE INDEX IF NOT EXISTS idx_enrollments_user_course
    ON enrollments (user_id, course_id);

-- 2. "All courses this user is enrolled in"
CREATE INDEX IF NOT EXISTS idx_enrollments_user_id
    ON enrollments (user_id);

-- 3. "How many learners in this course?" (instructor dashboard)
CREATE INDEX IF NOT EXISTS idx_enrollments_course_id
    ON enrollments (course_id);

-- ── Trigger ───────────────────────────────────────────────────────
-- Note: enrollments has no updated_at column (enrollments are immutable)
-- so no trigger is needed here.

COMMENT ON TABLE enrollments IS
    'Tracks which users are enrolled in which courses. '
    'UNIQUE(course_id, user_id) prevents duplicate enrollments. '
    'completed_at is set when a learner reaches 100% progress.';
