package com.olp.enrollment.controller;

import com.olp.common.dto.ApiResponse;
import com.olp.enrollment.dto.EnrollmentResponse;
import com.olp.enrollment.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for enrollment endpoints.
 *
 * Header contract (injected by API Gateway):
 *   x-user-id    — UUID of the authenticated user
 *   x-user-role  — "user" (learner) or "instructor"
 *
 * Endpoints:
 *   POST   /api/enrollment/{courseId}          — enrol in a course (learner only)
 *   DELETE /api/enrollment/{courseId}          — unenrol from a course
 *   GET    /api/enrollment/my                  — my enrolled courses
 *   GET    /api/enrollment/{courseId}/status   — am I enrolled in this course?
 *   GET    /api/enrollment/{courseId}/count    — how many learners enrolled? (instructor)
 */
@RestController
@RequestMapping("/api/enrollment")
@RequiredArgsConstructor
@Slf4j
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    /**
     * Enrol the authenticated learner in a course.
     * Returns 201 on success, 409 if already enrolled.
     */
    @PostMapping("/{courseId}")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> enrol(
            @PathVariable                UUID courseId,
            @RequestHeader("x-user-id")   String userId,
            @RequestHeader("x-user-role") String userRole) {

        requireLearner(userRole);
        EnrollmentResponse response = enrollmentService.enrol(courseId, userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Enrolled successfully"));
    }

    /**
     * Unenrol the authenticated learner from a course.
     * Returns 200 on success, 404 if not enrolled.
     */
    @DeleteMapping("/{courseId}")
    public ResponseEntity<ApiResponse<Void>> unenrol(
            @PathVariable                UUID courseId,
            @RequestHeader("x-user-id")   String userId,
            @RequestHeader("x-user-role") String userRole) {

        requireLearner(userRole);
        enrollmentService.unenrol(courseId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Unenrolled successfully"));
    }

    /**
     * Get all courses the authenticated user is enrolled in.
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<EnrollmentResponse>>> getMyEnrollments(
            @RequestHeader("x-user-id") String userId) {

        List<EnrollmentResponse> enrollments = enrollmentService.getMyEnrollments(userId);
        return ResponseEntity.ok(ApiResponse.success(enrollments));
    }

    /**
     * Check if the authenticated user is enrolled in a specific course.
     * Used by the frontend to show enrol/unenrol button state.
     */
    @GetMapping("/{courseId}/status")
    public ResponseEntity<ApiResponse<Boolean>> checkEnrollment(
            @PathVariable               UUID courseId,
            @RequestHeader("x-user-id") String userId) {

        boolean enrolled = enrollmentService.isEnrolled(courseId, userId);
        return ResponseEntity.ok(ApiResponse.success(enrolled));
    }

    /**
     * Get the number of learners enrolled in a course.
     * Used by instructor dashboard.
     */
    @GetMapping("/{courseId}/count")
    public ResponseEntity<ApiResponse<Long>> getEnrollmentCount(
            @PathVariable UUID courseId) {

        long count = enrollmentService.getEnrollmentCount(courseId);
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    // ── Helper ─────────────────────────────────────────────────

    private void requireLearner(String userRole) {
        if (!"user".equals(userRole)) {
            throw new com.olp.common.exception.UnauthorisedException(
                    "Only learners can enrol in courses"
            );
        }
    }
}
