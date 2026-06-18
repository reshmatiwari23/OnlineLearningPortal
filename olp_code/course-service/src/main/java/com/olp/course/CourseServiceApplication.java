package com.olp.course;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Online Learning Portal — Course Service
 *
 * Responsibilities:
 *   GET    /api/courses              — list published courses (paginated)
 *   GET    /api/courses/my           — instructor's own courses
 *   GET    /api/courses/search       — search by title
 *   GET    /api/courses/{id}         — get single course
 *   POST   /api/courses              — create course (instructor only)
 *   PUT    /api/courses/{id}         — update course (owner only)
 *   DELETE /api/courses/{id}         — delete course (owner only)
 *   POST   /api/courses/{id}/upload-url — S3 presigned URL for video upload
 *
 * Port: 8082
 * Database: RDS PostgreSQL (olp_db, courses table)
 * Storage: Amazon S3 (olp-videos bucket)
 */
@SpringBootApplication
public class CourseServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourseServiceApplication.class, args);
    }
}
