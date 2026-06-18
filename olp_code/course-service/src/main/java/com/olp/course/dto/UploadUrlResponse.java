package com.olp.course.dto;

import lombok.*;

/**
 * Response for POST /api/courses/{id}/upload-url
 *
 * The frontend uses uploadUrl to PUT the video file directly to S3.
 * The video never passes through the API server — S3 handles it directly.
 * The URL expires after 15 minutes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadUrlResponse {

    // S3 presigned PUT URL — frontend calls this directly with the video file
    private String uploadUrl;

    // The S3 object key — stored in courses.video_url after upload
    private String s3Key;

    // How many seconds until the presigned URL expires
    private long expiresInSeconds;
}
