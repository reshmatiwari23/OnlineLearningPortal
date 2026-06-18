-- ============================================================
-- V3__create_enrollments.sql  (H2-compatible version)
-- TIMESTAMPTZ → TIMESTAMP, RANDOM_UUID() for default UUID
-- ============================================================

CREATE TABLE IF NOT EXISTS enrollments (
    id            UUID        PRIMARY KEY DEFAULT RANDOM_UUID(),
    course_id     UUID        NOT NULL,
    user_id       UUID        NOT NULL,
    enrolled_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    completed_at  TIMESTAMP,

    CONSTRAINT uq_enrollment UNIQUE (course_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_enrollments_user_course
    ON enrollments (user_id, course_id);

CREATE INDEX IF NOT EXISTS idx_enrollments_user_id
    ON enrollments (user_id);

CREATE INDEX IF NOT EXISTS idx_enrollments_course_id
    ON enrollments (course_id);
