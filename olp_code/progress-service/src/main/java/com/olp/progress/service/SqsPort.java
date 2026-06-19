package com.olp.progress.service;

import com.olp.progress.dto.ProgressEvent;

/**
 * Interface for SQS publishing.
 * SqsProgressPublisher implements this with real AWS SDK (aws profile).
 * MockSqsPublisher implements this with immediate local processing (local profile).
 */
public interface SqsPort {
    void publish(ProgressEvent event);
}
