-- ============================================================
-- V4__add_upload_status.sql
-- Adds upload_status column to courses table.
--
-- This column is set by:
--   1. course-service sets it to 'pending' when a presigned URL is generated
--   2. video-processor Lambda sets it to 'processing' when it starts
--   3. video-processor Lambda sets it to 'ready' when validation passes
--   4. video-processor Lambda sets it to 'failed' when validation fails
--
-- The frontend uses this to show the instructor the upload progress.
-- AI pipeline (Transcribe → embed → summarise) only runs when status = 'ready'.
-- ============================================================

ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS upload_status VARCHAR(50) NOT NULL DEFAULT 'none';

-- Valid values:
--   none       — no video uploaded yet (initial state)
--   pending    — presigned URL generated, waiting for browser to upload
--   processing — S3:ObjectCreated fired, Lambda is validating
--   ready      — video validated, Transcribe job started
--   failed     — video failed validation (wrong format, too large, corrupt)

ALTER TABLE courses
    ADD CONSTRAINT chk_upload_status
    CHECK (upload_status IN ('none', 'pending', 'processing', 'ready', 'failed'));

-- Index for Lambda queries: "find all courses stuck in processing state"
CREATE INDEX IF NOT EXISTS idx_courses_upload_status
    ON courses (upload_status);

COMMENT ON COLUMN courses.upload_status IS
    'Tracks video upload lifecycle. '
    'none → pending (presigned URL issued) → processing (Lambda running) '
    '→ ready (validated, AI pipeline started) or failed (invalid file).';
