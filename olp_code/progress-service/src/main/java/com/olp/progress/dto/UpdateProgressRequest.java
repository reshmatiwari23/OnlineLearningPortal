package com.olp.progress.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Request body for POST /api/progress/{courseId}.
 * Sent by Video.js every 5 seconds during video playback.
 */
@Data
public class UpdateProgressRequest {

    // Current playback position in seconds from Video.js currentTime property
    @NotNull(message = "currentTimeSecs is required")
    @Min(value = 0, message = "currentTimeSecs cannot be negative")
    private Integer currentTimeSecs;

    // Total video duration in seconds from Video.js duration property
    @NotNull(message = "durationSecs is required")
    @Min(value = 1, message = "durationSecs must be at least 1 second")
    private Integer durationSecs;
}
