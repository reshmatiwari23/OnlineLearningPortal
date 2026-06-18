package com.olp.course.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity for the courses table.
 *
 * Key design points:
 * - instructor_id is stored (not a FK join) so course-service
 *   never needs to call auth-service to get instructor details.
 * - ai_summary is JSONB in PostgreSQL — stored as JsonNode in Java.
 *   Claude generates this via ai-service after video upload.
 * - video_url stores the S3 object key (e.g. "videos/uuid.mp4")
 *   The full CloudFront URL is assembled at runtime in CourseService.
 */
@Entity
@Table(name = "courses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // UUID of the instructor who owns this course (from users table)
    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;

    // Denormalised — avoids joining to users table on every list query
    @Column(name = "instructor_name", nullable = false, length = 255)
    private String instructorName;

    // S3 object key — e.g. "videos/course-uuid/lecture.mp4"
    // Full URL: https://cloudfront-domain/videos/course-uuid/lecture.mp4
    @Column(name = "video_url", length = 1000)
    private String videoUrl;

    // Video duration in seconds — set by Lambda after upload
    @Column(name = "video_duration")
    @Builder.Default
    private Integer videoDuration = 0;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    // JSONB column — Claude-generated summary stored as JSON
    // Structure: {title, objectives[], summary, difficulty, keyTakeaway}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_summary", columnDefinition = "jsonb")
    private JsonNode aiSummary;

    // Tracks video upload lifecycle — set by course-service and video-processor Lambda
    // Stored as lowercase string in DB to match the CHECK constraint values
    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 50)
    @Builder.Default
    private UploadStatus uploadStatus = UploadStatus.NONE;

    // True once the video has been indexed in Bedrock Knowledge Base
    @Column(name = "kb_ingested", nullable = false)
    @Builder.Default
    private Boolean kbIngested = false;

    // Instructors publish courses manually after reviewing the AI summary
    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private Boolean isPublished = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
