package com.olp.course.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * AWS SDK beans for course-service.
 * Active on all profiles EXCEPT local.
 *
 * S3Client     — used for any direct S3 operations (future use)
 * S3Presigner  — generates presigned PUT URLs for video uploads
 *
 * The presigned URL pattern means video bytes NEVER pass through
 * this service — the browser uploads directly to S3.
 */
@Configuration
@Profile("!local")
public class AwsConfig {

    @Value("${aws.region:ap-south-1}")
    private String awsRegion;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
