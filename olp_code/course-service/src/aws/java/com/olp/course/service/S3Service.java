package com.olp.course.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

/**
 * Real S3 implementation — active on aws profile only.
 * Implements S3Port so CourseService can inject it without AWS SDK dependency.
 */
@Service
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class S3Service implements S3Port {

    private final S3Presigner s3Presigner;

    @Value("${aws.s3.videos-bucket}")
    private String videosBucket;

    @Value("${aws.s3.presigned-url-expiry-minutes:15}")
    private int expiryMinutes;

    @Override
    public PresignedUploadResult generateUploadUrl(UUID courseId, String fileName) {
        String safeFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String s3Key = String.format("videos/%s/%s", courseId, safeFileName);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(videosBucket)
                .key(s3Key)
                .contentType("video/mp4")
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expiryMinutes))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        String uploadUrl = presigned.url().toString();

        log.info("Generated presigned URL for course: {}, key: {}", courseId, s3Key);
        return new PresignedUploadResult(uploadUrl, s3Key, (long) expiryMinutes * 60);
    }
}
