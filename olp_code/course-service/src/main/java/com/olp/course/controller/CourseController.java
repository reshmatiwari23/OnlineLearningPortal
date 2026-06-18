package com.olp.course.controller;

import com.olp.common.dto.ApiResponse;
import com.olp.course.dto.*;
import com.olp.course.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for course management.
 *
 * Header contract (injected by API Gateway Lambda Authoriser):
 *   x-user-id    — UUID of the authenticated user
 *   x-user-role  — "user" or "instructor"
 *
 * course-service reads these headers directly.
 * It NEVER validates the JWT — that is done at API Gateway.
 *
 * Endpoints:
 *   GET    /api/courses                      — list published courses (paginated)
 *   GET    /api/courses/my                   — instructor's own courses
 *   GET    /api/courses/search?keyword=java  — search by title
 *   GET    /api/courses/{id}                 — get single course
 *   POST   /api/courses                      — create course (instructor only)
 *   PUT    /api/courses/{id}                 — update course (owner only)
 *   DELETE /api/courses/{id}                 — delete course (owner only)
 *   POST   /api/courses/{id}/upload-url      — get S3 presigned URL (owner only)
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Slf4j
public class CourseController {

    private final CourseService courseService;

    // ── Public endpoints (any authenticated user) ──────────────

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CourseResponse>>> getAllCourses(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "12") int size) {

        Page<CourseResponse> courses = courseService.getAllPublishedCourses(page, size);
        return ResponseEntity.ok(ApiResponse.success(courses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> getCourseById(
            @PathVariable UUID id) {

        CourseResponse course = courseService.getCourseById(id);
        return ResponseEntity.ok(ApiResponse.success(course));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<CourseResponse>>> searchCourses(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "12") int size) {

        Page<CourseResponse> courses = courseService.searchCourses(keyword, page, size);
        return ResponseEntity.ok(ApiResponse.success(courses));
    }

    // ── Instructor endpoints ───────────────────────────────────

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<CourseResponse>>> getMyCourses(
            @RequestHeader("x-user-id")   String userId,
            @RequestHeader("x-user-role") String userRole,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "12") int size) {

        requireInstructor(userRole);
        Page<CourseResponse> courses = courseService.getCoursesByInstructor(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(courses));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(
            @Valid @RequestBody          CreateCourseRequest request,
            @RequestHeader("x-user-id")   String userId,
            @RequestHeader("x-user-role") String userRole,
            @RequestHeader(value = "x-user-name", defaultValue = "Instructor") String userName) {

        requireInstructor(userRole);
        CourseResponse course = courseService.createCourse(request, userId, userName);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(course, "Course created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> updateCourse(
            @PathVariable                UUID id,
            @Valid @RequestBody          UpdateCourseRequest request,
            @RequestHeader("x-user-id")   String userId,
            @RequestHeader("x-user-role") String userRole) {

        requireInstructor(userRole);
        CourseResponse course = courseService.updateCourse(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success(course, "Course updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(
            @PathVariable                UUID id,
            @RequestHeader("x-user-id")   String userId,
            @RequestHeader("x-user-role") String userRole) {

        requireInstructor(userRole);
        courseService.deleteCourse(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Course deleted successfully"));
    }

    @PostMapping("/{id}/upload-url")
    public ResponseEntity<ApiResponse<UploadUrlResponse>> getUploadUrl(
            @PathVariable                UUID id,
            @RequestParam                String fileName,
            @RequestHeader("x-user-id")   String userId,
            @RequestHeader("x-user-role") String userRole) {

        requireInstructor(userRole);
        UploadUrlResponse response = courseService.generateUploadUrl(id, fileName, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Called by the video-processor Lambda after validating the uploaded video.
     * This is an internal endpoint — not exposed to the public internet via API Gateway.
     * The Lambda calls it directly via the ALB using the internal VPC DNS.
     *
     * Request params:
     *   status       — "processing" | "ready" | "failed"
     *   durationSecs — video duration in seconds (only when status = ready)
     *
     * Example Lambda call (status = ready):
     *   PATCH /api/courses/{id}/upload-status?status=ready&durationSecs=3600
     */
    @PatchMapping("/{id}/upload-status")
    public ResponseEntity<ApiResponse<CourseResponse>> updateUploadStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            @RequestParam(required = false) Integer durationSecs) {

        CourseResponse course = courseService.updateUploadStatus(id, status, durationSecs);
        return ResponseEntity.ok(ApiResponse.success(course,
                "Upload status updated to: " + status));
    }

    // ── Helper ─────────────────────────────────────────────────

    /**
     * Verifies the user has the instructor role.
     * Called at the start of every instructor-only endpoint.
     */
    private void requireInstructor(String userRole) {
        if (!"instructor".equals(userRole)) {
            throw new com.olp.common.exception.UnauthorisedException(
                    "Only instructors can perform this action"
            );
        }
    }
}
