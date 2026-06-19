package com.olp.course.dto;
 
import com.olp.course.entity.UploadStatus;
import lombok.*;
 
import java.time.LocalDateTime;
import java.util.UUID;
 
/**
 * DTO returned for all course API responses.
 * aiSummary is a JSON string — parsed by the frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseResponse {
 
    private UUID id;
    private String title;
    private String description;
    private UUID instructorId;
    private String instructorName;
    private String videoUrl;
    private Integer videoDuration;
    private String thumbnailUrl;
    private UploadStatus uploadStatus;
    private String aiSummary;     // JSON string — null until AI processes the course
    private Boolean kbIngested;
    private Boolean isPublished;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
 
 