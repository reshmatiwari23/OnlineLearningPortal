package com.olp.progress.controller;
 
import com.olp.common.dto.ApiResponse;
import com.olp.progress.dto.ProgressResponse;
import com.olp.progress.dto.UpdateProgressRequest;
import com.olp.progress.service.ProgressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
 
import java.util.List;
import java.util.UUID;
 
@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
@Slf4j
public class ProgressController {
 
    private final ProgressService progressService;
 
    @PostMapping("/{courseId}")
    public ResponseEntity<ApiResponse<ProgressResponse>> updateProgress(
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateProgressRequest request,
            @RequestHeader(value = "x-user-id", required = false) String userId) {
 
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User not authenticated"));
        }
 
        log.debug("Progress update: courseId={}, userId={}, secs={}",
                courseId, userId, request.getCurrentTimeSecs());
 
        ProgressResponse response = progressService.updateProgress(
                courseId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
 
    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<ProgressResponse>> getProgress(
            @PathVariable UUID courseId,
            @RequestHeader(value = "x-user-id", required = false) String userId) {
 
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User not authenticated"));
        }
 
        ProgressResponse response = progressService.getProgress(courseId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
 
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<ProgressResponse>>> getMyProgress(
            @RequestHeader(value = "x-user-id", required = false) String userId) {
 
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
 
        return ResponseEntity.ok(ApiResponse.success(
                progressService.getAllProgressForUser(userId)));
    }
 
    @GetMapping("/{courseId}/all")
    public ResponseEntity<ApiResponse<List<ProgressResponse>>> getCourseProgress(
            @PathVariable UUID courseId,
            @RequestHeader(value = "x-user-role", required = false) String userRole) {
 
        if (!"instructor".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only instructors can view all progress"));
        }
 
        return ResponseEntity.ok(ApiResponse.success(
                progressService.getAllProgressForCourse(courseId)));
    }
}
 
 