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
 
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Slf4j
public class CourseController {
 
    private final CourseService courseService;
 
    // ── Public endpoints ─────────────────────────────────────────
 
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CourseResponse>>> getAllCourses(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.getAllPublishedCourses(page, size)));
    }
 
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> getCourse(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.getCourseById(id)));
    }
 
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<CourseResponse>>> searchCourses(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.searchCourses(keyword, page, size)));
    }
 
    // ── Instructor endpoints ──────────────────────────────────────
 
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<CourseResponse>>> getMyCourses(
            @RequestHeader(value = "x-user-id",   required = false) String userId,
            @RequestHeader(value = "x-user-role", required = false) String userRole,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "12") int size) {
 
        if (userId == null || !"instructor".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only instructors can view their courses"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                courseService.getCoursesByInstructor(userId, page, size)));
    }
 
    @PostMapping
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(
            @Valid @RequestBody CreateCourseRequest request,
            @RequestHeader(value = "x-user-id",   required = false) String userId,
            @RequestHeader(value = "x-user-role", required = false) String userRole,
            @RequestHeader(value = "x-user-name", required = false) String userName) {
 
        log.info("Create course: userId={}, role={}, title={}",
                userId, userRole, request.getTitle());
 
        if (userId == null || !"instructor".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only instructors can create courses"));
        }
 
        CourseResponse course = courseService.createCourse(
                request,
                userId,
                userName != null ? userName : "Instructor");
 
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(course, "Course created successfully"));
    }
 
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> updateCourse(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseRequest request,
            @RequestHeader(value = "x-user-id",   required = false) String userId,
            @RequestHeader(value = "x-user-role", required = false) String userRole) {
 
        if (userId == null || !"instructor".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only instructors can update courses"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                courseService.updateCourse(id, request, userId)));
    }
 
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(
            @PathVariable UUID id,
            @RequestHeader(value = "x-user-id",   required = false) String userId,
            @RequestHeader(value = "x-user-role", required = false) String userRole) {
 
        if (userId == null || !"instructor".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only instructors can delete courses"));
        }
        courseService.deleteCourse(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Course deleted"));
    }
 
    @PostMapping("/{id}/upload-url")
    public ResponseEntity<ApiResponse<UploadUrlResponse>> getUploadUrl(
            @PathVariable UUID id,
            @RequestParam String fileName,
            @RequestHeader(value = "x-user-id",   required = false) String userId,
            @RequestHeader(value = "x-user-role", required = false) String userRole) {
 
        if (userId == null || !"instructor".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only instructors can upload videos"));
        }
 
        // Correct parameter order matching CourseService.generateUploadUrl(
        //   UUID courseId, String fileName, String instructorId)
        return ResponseEntity.ok(ApiResponse.success(
                courseService.generateUploadUrl(id, fileName, userId)));
    }
 
    @PatchMapping("/{id}/upload-status")
    public ResponseEntity<ApiResponse<CourseResponse>> updateUploadStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            @RequestParam(required = false, defaultValue = "0") Integer durationSecs) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.updateUploadStatus(id, status, durationSecs)));
    }
}
 