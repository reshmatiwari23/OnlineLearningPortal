package com.olp.progress.controller;

import com.olp.common.dto.ApiResponse;
import com.olp.progress.dto.ProgressResponse;
import com.olp.progress.dto.UpdateProgressRequest;
import com.olp.progress.service.ProgressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for video progress endpoints.
 *
 * Header contract (injected by API Gateway):
 *   x-user-id    — UUID of the authenticated user
 *   x-user-role  — "user" or "instructor"
 *
 * Endpoints:
 *   POST /api/progress/{courseId}          — update progress (every 5s from Video.js)
 *   GET  /api/progress/{courseId}          — get progress for current user
 *   GET  /api/progress/my                  — all progress for current user
 *   GET  /api/progress/{courseId}/all      — all learner progress (instructor only)
 */
@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
@Slf4j
public class ProgressController {

    private final ProgressService progressService;

    /**
     * Update progress for the current user in a specific course.
     * Called by Video.js every 5 seconds during playback.
     *
     * This endpoint must return in <200ms.
     * It writes to Redis synchronously and SQS asynchronously.
     */
    @PostMapping("/{courseId}")
    public ResponseEntity<ApiResponse<ProgressResponse>> updateProgress(
            @PathVariable                UUID courseId,
            @Valid @RequestBody          UpdateProgressRequest request,
            @RequestHeader("x-user-id")   String userId,
            @RequestHeader("x-user-role") String userRole) {

        ProgressResponse response = progressService.updateProgress(courseId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get progress for the current user in a specific course.
     * Checks Redis first, falls back to RDS.
     */
    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<ProgressResponse>> getProgress(
            @PathVariable               UUID courseId,
            @RequestHeader("x-user-id") String userId) {

        ProgressResponse response = progressService.getProgress(courseId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get all progress records for the current user.
     * Used by the learner dashboard to show all in-progress courses.
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<ProgressResponse>>> getMyProgress(
            @RequestHeader("x-user-id") String userId) {

        List<ProgressResponse> progress = progressService.getAllProgressForUser(userId);
        return ResponseEntity.ok(ApiResponse.success(progress));
    }

    /**
     * Get all learners' progress in a specific course.
     * Used by instructor dashboard to see learner engagement.
     */
    @GetMapping("/{courseId}/all")
    public ResponseEntity<ApiResponse<List<ProgressResponse>>> getCourseProgress(
            @PathVariable                UUID courseId,
            @RequestHeader("x-user-role") String userRole) {

        requireInstructor(userRole);
        List<ProgressResponse> progress = progressService.getAllProgressForCourse(courseId);
        return ResponseEntity.ok(ApiResponse.success(progress));
    }

    // ── Helper ─────────────────────────────────────────────────

    private void requireInstructor(String userRole) {
        if (!"instructor".equals(userRole)) {
            throw new com.olp.common.exception.UnauthorisedException(
                    "Only instructors can view all learner progress"
            );
        }
    }
}
