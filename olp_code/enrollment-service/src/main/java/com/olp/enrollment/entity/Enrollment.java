package com.olp.enrollment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity for the enrollments table.
 *
 * Intentionally simple — no relationship mappings to User or Course
 * because enrollment-service does not own those tables.
 * It stores the UUIDs and looks up course details from course-service
 * only when needed (or caches them locally in a future iteration).
 *
 * The UNIQUE(course_id, user_id) constraint is on the table itself.
 * Spring Data will throw a DataIntegrityViolationException if violated,
 * which we catch in EnrollmentService and convert to DuplicateResourceException.
 */
@Entity
@Table(name = "enrollments",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_enrollment",
           columnNames = {"course_id", "user_id"}
       ))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // UUID of the course — from courses table (owned by course-service)
    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    // UUID of the learner — from users table (owned by auth-service)
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private LocalDateTime enrolledAt;

    // Set by progress-service when learner reaches 100%
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        enrolledAt = LocalDateTime.now();
    }
}
