package com.olp.course.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for PUT /api/courses/{id} (update course).
 * All fields are optional — only provided fields are updated.
 */
@Data
public class UpdateCourseRequest {

    @Size(min = 3, max = 200, message = "Title must be between 3 and 200 characters")
    private String title;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    // Instructor can manually publish/unpublish
    private Boolean isPublished;
}
