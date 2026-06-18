package com.olp.course.entity;

/**
 * Represents the lifecycle of a course video upload.
 *
 * State machine:
 *
 *   NONE
 *    │
 *    ▼ (instructor calls POST /api/courses/{id}/upload-url)
 *   PENDING
 *    │
 *    ▼ (browser uploads to S3 → S3:ObjectCreated fires → Lambda starts)
 *   PROCESSING
 *    │
 *    ├──► READY    (Lambda validates OK → Transcribe job started → AI pipeline runs)
 *    │
 *    └──► FAILED   (wrong file type, too large, corrupt file, ffprobe error)
 *
 * The AI pipeline (Transcribe → embed → summarise) ONLY runs when status = READY.
 * The frontend shows a progress indicator based on this status.
 * If FAILED the instructor can try uploading again (status resets to PENDING).
 */
public enum UploadStatus {

    /** No video has been uploaded yet — initial state of every new course */
    NONE,

    /** Presigned URL has been issued — browser is uploading to S3 */
    PENDING,

    /** S3:ObjectCreated received — video-processor Lambda is validating the file */
    PROCESSING,

    /** Video passed validation — Transcribe job started, AI pipeline running */
    READY,

    /** Video failed validation — wrong format, too large, or corrupt */
    FAILED
}
