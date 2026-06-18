package com.olp.progress.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * AWS SDK beans for progress-service.
 * Active on all profiles EXCEPT local.
 *
 * SqsClient publishes progress events to olp-progress-writes-queue.
 * The queue URL is injected via SQS_PROGRESS_QUEUE_URL env variable.
 *
 * Publishing is fire-and-forget — SqsProgressPublisher swallows errors
 * so a SQS outage never fails the progress update endpoint.
 */
@Configuration
@Profile("!local")
public class AwsConfig {

    @Value("${aws.region:ap-south-1}")
    private String awsRegion;

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
