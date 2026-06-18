package com.olp.course.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

/**
 * Generates S3 presigned PUT URLs for video upload.
 *
 * How the presigned URL pattern works:
 * 1. Frontend calls POST /api/courses/{id}/upload-url
 * 2. course-service generates a presigned URL (valid 15 min)
 * 3. Frontend PUTs the video file DIRECTLY to S3 using that URL
 * 4. The video never passes through the API server
 * 5. S3 fires ObjectCreated event → SQS → Lambda processes the video
 *
 * Benefits:
 * - No file size limit (API Gateway has a 10MB limit)
 * - No memory pressure on ECS tasks
 * - S3 handles multipart upload for large files automatically
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final S3Presigner s3Presigner;

    @Value("${aws.s3.videos-bucket}")
    private String videosBucket;

    @Value("${aws.s3.presigned-url-expiry-minutes:15}")
    private int expiryMinutes;

    /**
     * Generates a presigned PUT URL for uploading a video to S3.
     *
     * @param courseId  UUID of the course — used to organise videos in S3
     * @param fileName  original filename from the browser (e.g. "lecture.mp4")
     * @return PresignedUploadResult containing the URL and S3 object key
     */
    public PresignedUploadResult generateUploadUrl(UUID courseId, String fileName) {
        // Sanitise the filename — remove spaces and special characters
        String safeFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");

        // S3 object key — organises videos by courseId
        // Example: videos/550e8400-e29b-41d4-a716/lecture.mp4
        String s3Key = String.format("videos/%s/%s", courseId, safeFileName);

        log.debug("Generating presigned URL for bucket: {}, key: {}", videosBucket, s3Key);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(videosBucket)
                .key(s3Key)
                .contentType("video/mp4")
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expiryMinutes))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        String uploadUrl = presignedRequest.url().toString();

        log.info("Generated presigned URL for course: {}, key: {}, expires in: {} min",
                courseId, s3Key, expiryMinutes);

        return new PresignedUploadResult(uploadUrl, s3Key, (long) expiryMinutes * 60);
    }

    /**
     * Simple value object returned by generateUploadUrl.
     */
    public record PresignedUploadResult(String uploadUrl, String s3Key, long expiresInSeconds) {}
}
