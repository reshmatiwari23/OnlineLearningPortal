-- ============================================================
-- V4__add_upload_status.sql  (H2-compatible version)
-- H2 supports ALTER TABLE ADD COLUMN and CHECK constraints.
-- ============================================================

ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS upload_status VARCHAR(50) NOT NULL DEFAULT 'none';

ALTER TABLE courses
    ADD CONSTRAINT chk_upload_status
    CHECK (upload_status IN ('none', 'pending', 'processing', 'ready', 'failed'));

CREATE INDEX IF NOT EXISTS idx_courses_upload_status
    ON courses (upload_status);
