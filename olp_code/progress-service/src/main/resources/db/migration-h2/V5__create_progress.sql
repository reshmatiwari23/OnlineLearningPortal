-- ============================================================
-- V5__create_progress.sql  (H2-compatible version)
-- ============================================================

CREATE TABLE IF NOT EXISTS video_progress (
    id                UUID        PRIMARY KEY DEFAULT RANDOM_UUID(),
    user_id           UUID        NOT NULL,
    course_id         UUID        NOT NULL,
    current_time_secs INTEGER     NOT NULL DEFAULT 0,
    duration_secs     INTEGER     NOT NULL DEFAULT 0,
    percent_complete  INTEGER     NOT NULL DEFAULT 0
                      CHECK (percent_complete BETWEEN 0 AND 100),
    last_updated_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMP,

    CONSTRAINT uq_progress UNIQUE (user_id, course_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_progress_user_course
    ON video_progress (user_id, course_id);

CREATE INDEX IF NOT EXISTS idx_progress_user_id
    ON video_progress (user_id);

CREATE INDEX IF NOT EXISTS idx_progress_course_id
    ON video_progress (course_id);

CREATE INDEX IF NOT EXISTS idx_progress_last_updated
    ON video_progress (last_updated_at);
