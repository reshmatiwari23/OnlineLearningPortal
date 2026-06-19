package com.olp.course.entity;
 
import jakarta.persistence.*;
import lombok.*;
 
import java.time.LocalDateTime;
import java.util.UUID;
 
/**
 * JPA entity for the courses table.
 *
 * H2 compatibility notes:
 * - ai_summary stored as TEXT (not JSONB) — H2 does not support JSONB
 * - On AWS with PostgreSQL, JSONB is used via the PostgreSQL migration
 * - JsonNode serialisation handled in CourseService/DTO layer
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
 
    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;
 
    @Column(name = "instructor_name", nullable = false, length = 255)
    private String instructorName;
 
    @Column(name = "video_url", length = 1000)
    private String videoUrl;
 
    @Column(name = "video_duration")
    @Builder.Default
    private Integer videoDuration = 0;
 
    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;
 
    /**
     * AI summary stored as JSON string.
     * PostgreSQL: JSONB column
     * H2: TEXT column (same data, different storage)
     * Parsed to/from JSON string in CourseService.
     */
    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 50)
    @Builder.Default
    private UploadStatus uploadStatus = UploadStatus.NONE;
 
    @Column(name = "kb_ingested", nullable = false)
    @Builder.Default
    private Boolean kbIngested = false;
 
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
 
 