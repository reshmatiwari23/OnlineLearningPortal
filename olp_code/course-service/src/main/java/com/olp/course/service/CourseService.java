package com.olp.course.service;

import com.olp.common.exception.DuplicateResourceException;
import com.olp.common.exception.ResourceNotFoundException;
import com.olp.common.exception.UnauthorisedException;
import com.olp.course.dto.*;
import com.olp.course.entity.Course;
import com.olp.course.entity.UploadStatus;
import com.olp.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Business logic for course management.
 *
 * Security model:
 * - instructorId comes from the x-user-id header (injected by API Gateway)
 * - course-service NEVER validates the JWT itself
 * - All instructor-only operations verify that the requesting user
 *   owns the course before proceeding
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourseService {

    private final CourseRepository courseRepository;
    private final S3Service s3Service;

    // ── Create ─────────────────────────────────────────────────

    @Transactional
    public CourseResponse createCourse(CreateCourseRequest request,
                                       String instructorId,
                                       String instructorName) {

        log.info("Creating course '{}' for instructor: {}", request.getTitle(), instructorId);

        UUID instructorUuid = UUID.fromString(instructorId);

        // Check for duplicate title by the same instructor
        if (courseRepository.existsByTitleAndInstructorId(request.getTitle(), instructorUuid)) {
            throw new DuplicateResourceException(
                    "You already have a course titled '" + request.getTitle() + "'"
            );
        }

        Course course = Course.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .instructorId(instructorUuid)
                .instructorName(instructorName)
                .isPublished(false)
                .kbIngested(false)
                .build();

        course = courseRepository.save(course);
        log.info("Course created with id: {}", course.getId());

        return toResponse(course);
    }

    // ── Read ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CourseResponse> getAllPublishedCourses(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return courseRepository.findAllByIsPublishedTrue(pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CourseResponse> getCoursesByInstructor(String instructorId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return courseRepository.findAllByInstructorId(UUID.fromString(instructorId), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CourseResponse getCourseById(UUID courseId) {
        Course course = findCourseOrThrow(courseId);
        return toResponse(course);
    }

    @Transactional(readOnly = true)
    public Page<CourseResponse> searchCourses(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return courseRepository.searchByTitle(keyword, pageable)
                .map(this::toResponse);
    }

    // ── Update ─────────────────────────────────────────────────

    @Transactional
    public CourseResponse updateCourse(UUID courseId,
                                       UpdateCourseRequest request,
                                       String instructorId) {

        Course course = findCourseOwnedByInstructor(courseId, instructorId);

        // Only update fields that were actually provided
        if (request.getTitle() != null && !request.getTitle().equals(course.getTitle())) {
            // Check new title is not already used by this instructor
            if (courseRepository.existsByTitleAndInstructorId(
                    request.getTitle(), UUID.fromString(instructorId))) {
                throw new DuplicateResourceException(
                        "You already have a course titled '" + request.getTitle() + "'"
                );
            }
            course.setTitle(request.getTitle());
        }

        if (request.getDescription() != null) {
            course.setDescription(request.getDescription());
        }

        if (request.getIsPublished() != null) {
            course.setIsPublished(request.getIsPublished());
        }

        course = courseRepository.save(course);
        log.info("Course updated: {}", courseId);

        return toResponse(course);
    }

    // ── Delete ─────────────────────────────────────────────────

    @Transactional
    public void deleteCourse(UUID courseId, String instructorId) {
        Course course = findCourseOwnedByInstructor(courseId, instructorId);
        courseRepository.delete(course);
        log.info("Course deleted: {}", courseId);
    }

    // ── S3 Upload URL ──────────────────────────────────────────

    @Transactional
    public UploadUrlResponse generateUploadUrl(UUID courseId,
                                               String fileName,
                                               String instructorId) {

        // Verify the instructor owns this course before generating upload URL
        Course course = findCourseOwnedByInstructor(courseId, instructorId);

        S3Service.PresignedUploadResult result =
                s3Service.generateUploadUrl(courseId, fileName);

        // Store the S3 key and mark status as PENDING
        // (browser has the URL and is about to upload)
        course.setVideoUrl(result.s3Key());
        course.setUploadStatus(UploadStatus.PENDING);
        courseRepository.save(course);

        log.info("Generated upload URL for course: {}, s3Key: {}, status: PENDING",
                courseId, result.s3Key());

        return UploadUrlResponse.builder()
                .uploadUrl(result.uploadUrl())
                .s3Key(result.s3Key())
                .expiresInSeconds(result.expiresInSeconds())
                .build();
    }

    /**
     * Called by the video-processor Lambda (via an internal API or SQS)
     * to update the upload status after validation.
     *
     * Status transitions:
     *   PENDING → PROCESSING  (Lambda started, file received)
     *   PROCESSING → READY    (validation passed)
     *   PROCESSING → FAILED   (validation failed)
     *
     * @param courseId     the course whose video was uploaded
     * @param status       the new status string (processing/ready/failed)
     * @param durationSecs video duration in seconds (set when status = ready)
     */
    @Transactional
    public CourseResponse updateUploadStatus(UUID courseId,
                                             String status,
                                             Integer durationSecs) {

        Course course = findCourseOrThrow(courseId);

        UploadStatus newStatus = UploadStatus.valueOf(status.toUpperCase());
        course.setUploadStatus(newStatus);

        // Set duration when Lambda confirms the video is valid
        if (newStatus == UploadStatus.READY && durationSecs != null) {
            course.setVideoDuration(durationSecs);
        }

        // If upload failed, reset the video URL so instructor can try again
        if (newStatus == UploadStatus.FAILED) {
            course.setVideoUrl(null);
            log.warn("Upload failed for course: {} — video URL cleared", courseId);
        }

        course = courseRepository.save(course);
        log.info("Upload status updated: courseId={}, status={}", courseId, newStatus);

        return toResponse(course);
    }

    // ── Private helpers ────────────────────────────────────────

    private Course findCourseOrThrow(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
    }

    /**
     * Finds a course AND verifies the requesting user is the instructor.
     * Throws ResourceNotFoundException if course not found (404).
     * Throws UnauthorisedException if course belongs to a different instructor (403).
     *
     * We return 403 instead of 404 here intentionally — returning 404 for
     * "you don't own this" would leak whether the course exists at all.
     */
    private Course findCourseOwnedByInstructor(UUID courseId, String instructorId) {
        Course course = findCourseOrThrow(courseId);

        if (!course.getInstructorId().equals(UUID.fromString(instructorId))) {
            throw new UnauthorisedException(
                    "You do not have permission to modify this course"
            );
        }
        return course;
    }

    /**
     * Converts a Course entity to a CourseResponse DTO.
     */
    private CourseResponse toResponse(Course course) {
        return CourseResponse.builder()
                .id(course.getId())
                .title(course.getTitle())
                .description(course.getDescription())
                .instructorId(course.getInstructorId())
                .instructorName(course.getInstructorName())
                .videoUrl(course.getVideoUrl())
                .videoDuration(course.getVideoDuration())
                .thumbnailUrl(course.getThumbnailUrl())
                .uploadStatus(course.getUploadStatus())
                .aiSummary(course.getAiSummary())
                .kbIngested(course.getKbIngested())
                .isPublished(course.getIsPublished())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .build();
    }
}
