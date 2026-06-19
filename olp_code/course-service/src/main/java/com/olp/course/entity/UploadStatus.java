package com.olp.course.entity;
 
/**
 * Upload status lifecycle for course videos.
 *
 * Stored as UPPERCASE string in DB to match Java enum names.
 * The PATCH endpoint accepts both cases via toUpperCase() conversion.
 */
public enum UploadStatus {
    NONE,
    PENDING,
    PROCESSING,
    READY,
    FAILED
}
 
 