package com.olp.enrollment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Online Learning Portal — Enrollment Service
 *
 * Responsibilities:
 *   POST   /api/enrollment/{courseId}        — enrol learner in course (201)
 *   DELETE /api/enrollment/{courseId}        — unenrol learner (200)
 *   GET    /api/enrollment/my               — learner's enrolled courses
 *   GET    /api/enrollment/{courseId}/status — is learner enrolled? (boolean)
 *   GET    /api/enrollment/{courseId}/count  — learner count per course
 *
 * Port: 8083
 * Database: RDS PostgreSQL (olp_db, enrollments table)
 * Key constraint: UNIQUE(course_id, user_id) prevents duplicate enrollments
 */
@SpringBootApplication
public class EnrollmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnrollmentServiceApplication.class, args);
    }
}
