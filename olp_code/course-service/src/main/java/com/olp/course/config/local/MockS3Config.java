package com.olp.course.config.local;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.Collections;

/**
 * Provides a mock S3Presigner for local development.
 * Returns a fake presigned URL instead of making real AWS calls.
 * Active only on the local profile.
 */
@Configuration
@Profile("local")
@Slf4j
public class MockS3Config {

    @Bean
    @Primary
    public S3Presigner s3Presigner() {
        log.info("[LOCAL MOCK] Using mock S3Presigner — no real AWS S3 calls");

        return new S3Presigner() {

            @Override
            public PresignedPutObjectRequest presignPutObject(
                    PutObjectPresignRequest presignRequest) {

                String bucket = presignRequest.putObjectRequest().bucket();
                String key    = presignRequest.putObjectRequest().key();

                log.info("[LOCAL MOCK] S3 presignPutObject: bucket={}, key={}", bucket, key);

                try {
                    // Return a fake URL that looks real but goes nowhere
                    URL fakeUrl = new URL(
                            "http://localhost:4566/" + bucket + "/" + key
                            + "?X-Amz-Algorithm=AWS4-HMAC-SHA256"
                            + "&X-Amz-Expires=900"
                            + "&X-Amz-SignedHeaders=host"
                            + "&X-Amz-Signature=fakesignature"
                    );

                    return PresignedPutObjectRequest.builder()
                            .expiration(Instant.now().plusSeconds(900))
                            .isBrowserExecutable(true)
                            .signedHeaders(Collections.emptyMap())
                            .url(fakeUrl)
                            .build();

                } catch (MalformedURLException e) {
                    throw new RuntimeException("Mock S3 URL generation failed", e);
                }
            }

            @Override
            public void close() {
                // nothing to close
            }
        };
    }
}
