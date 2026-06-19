package com.olp.course.config.local;

import com.olp.course.service.S3Port;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Mock S3 — active on local profile only.
 * Implements S3Port directly (no AWS SDK needed).
 * Returns a fake presigned URL for local testing.
 */
@Service
@Primary
@Profile("local")
@Slf4j
public class MockS3Service implements S3Port {

    @Override
    public PresignedUploadResult generateUploadUrl(UUID courseId, String fileName) {
        String safeFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String s3Key = String.format("videos/%s/%s", courseId, safeFileName);
        String fakeUrl = "http://localhost:4566/olp-videos-local/" + s3Key
                + "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=900&X-Amz-Signature=fakesig";

        log.info("[LOCAL MOCK] S3 presigned URL generated for: {}", s3Key);
        return new PresignedUploadResult(fakeUrl, s3Key, 900L);
    }
}
