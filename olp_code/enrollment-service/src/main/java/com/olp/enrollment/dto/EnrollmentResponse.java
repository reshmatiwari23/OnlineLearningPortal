package com.olp.enrollment.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response body for enrollment operations.
 * Returned on enrol, get enrollment, and list enrollments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentResponse {

    private UUID id;
    private UUID courseId;
    private UUID userId;
    private LocalDateTime enrolledAt;
    private LocalDateTime completedAt;   // null until course is completed
    private boolean completed;           // convenience flag: completedAt != null
}
