package com.olp.progress;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Online Learning Portal — Progress Service
 *
 * Responsibilities:
 *   POST /api/progress/{courseId}       — update progress (Redis write + SQS async)
 *   GET  /api/progress/{courseId}       — get progress (Redis → RDS fallback)
 *   GET  /api/progress/my               — all progress for current user
 *   GET  /api/progress/{courseId}/all   — all learner progress (instructor only)
 *
 * Port: 8084
 * Write strategy: Redis-first (synchronous) → SQS → RDS (asynchronous)
 * Handles: 2,000+ writes/second at 10K concurrent viewers
 */
@SpringBootApplication
@EnableScheduling
public class ProgressServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProgressServiceApplication.class, args);
    }
}
