package com.olp.course.service;

import java.util.UUID;

/**
 * Interface for S3 presigned URL generation.
 * S3Service implements this with real AWS SDK (aws profile).
 * MockS3Service implements this with fake responses (local profile).
 *
 * Lives in src/main so it compiles without the AWS SDK.
 */
public interface S3Port {

    /**
     * Generate a presigned PUT URL for video upload.
     * @param courseId  UUID of the course
     * @param fileName  original filename from the browser
     * @return result containing uploadUrl, s3Key, expiresInSeconds
     */
    PresignedUploadResult generateUploadUrl(UUID courseId, String fileName);

    /**
     * Simple value object returned by generateUploadUrl.
     */
    record PresignedUploadResult(String uploadUrl, String s3Key, long expiresInSeconds) {}
}
