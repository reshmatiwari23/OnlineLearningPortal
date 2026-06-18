-- ============================================================
-- V5__create_progress.sql
-- Creates the video_progress table.
--
-- Write strategy:
--   HOT PATH  → Redis (every 5 seconds per viewer, synchronous)
--   COLD PATH → This table (batched via SQS, asynchronous)
--
-- This table is the source of truth for progress.
-- Redis is just a fast write buffer with a 60-second TTL.
-- If Redis misses, we read from here.
-- ============================================================

CREATE TABLE IF NOT EXISTS video_progress (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL,
    course_id         UUID        NOT NULL,

    -- Current playback position in seconds
    -- Updated by Video.js timeupdate event every 5 seconds
    current_time_secs INTEGER     NOT NULL DEFAULT 0,

    -- Total video duration in seconds
    -- Set once from courses.video_duration — avoids a join on every read
    duration_secs     INTEGER     NOT NULL DEFAULT 0,

    -- Calculated: ROUND((current_time_secs / duration_secs) * 100)
    -- Stored as integer 0-100 for fast reads and sorting
    percent_complete  INTEGER     NOT NULL DEFAULT 0
                      CHECK (percent_complete BETWEEN 0 AND 100),

    -- Timestamp of last progress update (from the SQS message, not DB insert time)
    last_updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Set to NOW() when percent_complete reaches 100
    completed_at      TIMESTAMPTZ,

    -- Each user has exactly one progress record per course
    CONSTRAINT uq_progress UNIQUE (user_id, course_id)
);

-- ── Indexes ──────────────────────────────────────────────────────

-- Primary lookup: "what is user X's progress in course Y?"
CREATE UNIQUE INDEX IF NOT EXISTS idx_progress_user_course
    ON video_progress (user_id, course_id);

-- "All progress records for user X" — learner dashboard
CREATE INDEX IF NOT EXISTS idx_progress_user_id
    ON video_progress (user_id);

-- "All learners' progress in course X" — instructor dashboard
CREATE INDEX IF NOT EXISTS idx_progress_course_id
    ON video_progress (course_id);

-- "Find stalled learners" — used by progress nudge scheduler
-- Stalled = last_updated more than 3 days ago AND percent_complete < 50
CREATE INDEX IF NOT EXISTS idx_progress_last_updated
    ON video_progress (last_updated_at);

-- ── Trigger ───────────────────────────────────────────────────────
-- Reuse the update_updated_at_column function from V1.
-- We use last_updated_at instead of updated_at here — same purpose.
CREATE OR REPLACE FUNCTION update_last_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_progress_last_updated
    BEFORE UPDATE ON video_progress
    FOR EACH ROW
    EXECUTE FUNCTION update_last_updated_at();

COMMENT ON TABLE video_progress IS
    'Stores video watch progress per user per course. '
    'Written to asynchronously via SQS — Redis is the synchronous write target. '
    'percent_complete is pre-calculated to avoid runtime division on every read.';
