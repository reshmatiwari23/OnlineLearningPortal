package com.olp.progress.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The message payload sent to SQS for async RDS writes.
 *
 * After writing to Redis, ProgressService serialises this to JSON
 * and sends it to the olp-progress-writes-queue.
 *
 * The SQS consumer (ProgressConsumer) reads this message
 * and upserts into the video_progress table in RDS.
 *
 * This decouples the fast Redis write (synchronous, returns 200ms)
 * from the slower RDS write (asynchronous, done in batches).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressEvent {

    private UUID userId;
    private UUID courseId;
    private Integer currentTimeSecs;
    private Integer durationSecs;
    private Integer percentComplete;
    private LocalDateTime eventTime;    // when the Video.js event fired
}
