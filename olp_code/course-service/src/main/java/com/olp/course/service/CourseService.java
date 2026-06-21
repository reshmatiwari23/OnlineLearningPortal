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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseService {

    private final CourseRepository courseRepository;
    private final S3Port s3Service;

    @Value("${aws.cloudfront.domain:}")
    private String cloudfrontDomain;

    @Value("${local.demo.video-url:https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4}")
    private String demoVideoUrl;

    @Transactional
    public CourseResponse createCourse(CreateCourseRequest request,
                                       String instructorId,
                                       String instructorName) {
        log.info("Creating course '{}' for instructor: {}", request.getTitle(), instructorId);
        UUID instructorUuid = UUID.fromString(instructorId);

        if (courseRepository.existsByTitleAndInstructorId(request.getTitle(), instructorUuid)) {
            throw new DuplicateResourceException(
                    "You already have a course titled '" + request.getTitle() + "'");
        }

        Course course = Course.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .instructorId(instructorUuid)
                .instructorName(instructorName)
                .uploadStatus(UploadStatus.NONE)
                .isPublished(false)
                .kbIngested(false)
                .build();

        course = courseRepository.save(course);
        log.info("Course created: {}", course.getId());
        return toResponse(course);
    }

    @Transactional(readOnly = true)
    public Page<CourseResponse> getAllPublishedCourses(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return courseRepository.findAllByIsPublishedTrue(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CourseResponse> getCoursesByInstructor(String instructorId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return courseRepository.findAllByInstructorId(
                UUID.fromString(instructorId), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CourseResponse getCourseById(UUID courseId) {
        return toResponse(findCourseOrThrow(courseId));
    }

    @Transactional(readOnly = true)
    public Page<CourseResponse> searchCourses(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return courseRepository.searchByTitle(keyword, pageable).map(this::toResponse);
    }

    @Transactional
    public CourseResponse updateCourse(UUID courseId,
                                       UpdateCourseRequest request,
                                       String instructorId) {
        Course course = findCourseOwnedByInstructor(courseId, instructorId);

        if (request.getTitle() != null && !request.getTitle().equals(course.getTitle())) {
            if (courseRepository.existsByTitleAndInstructorId(
                    request.getTitle(), UUID.fromString(instructorId))) {
                throw new DuplicateResourceException(
                        "You already have a course titled '" + request.getTitle() + "'");
            }
            course.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) course.setDescription(request.getDescription());
        if (request.getIsPublished() != null) course.setIsPublished(request.getIsPublished());

        course = courseRepository.save(course);
        log.info("Course updated: {}", courseId);
        return toResponse(course);
    }

    @Transactional
    public void deleteCourse(UUID courseId, String instructorId) {
        Course course = findCourseOwnedByInstructor(courseId, instructorId);
        courseRepository.delete(course);
        log.info("Course deleted: {}", courseId);
    }

    @Transactional
    public UploadUrlResponse generateUploadUrl(UUID courseId,
                                               String fileName,
                                               String instructorId) {
        Course course = findCourseOwnedByInstructor(courseId, instructorId);
        S3Port.PresignedUploadResult result = s3Service.generateUploadUrl(courseId, fileName);

        course.setVideoUrl(result.s3Key());
        course.setUploadStatus(UploadStatus.PENDING);
        courseRepository.save(course);

        return UploadUrlResponse.builder()
                .uploadUrl(result.uploadUrl())
                .s3Key(result.s3Key())
                .expiresInSeconds(result.expiresInSeconds())
                .build();
    }

    @Transactional
    public CourseResponse updateUploadStatus(UUID courseId,
                                             String status,
                                             Integer durationSecs) {
        Course course = findCourseOrThrow(courseId);

        UploadStatus newStatus;
        try {
            newStatus = UploadStatus.valueOf(status.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid status: '" + status + "'. Valid: NONE, PENDING, PROCESSING, READY, FAILED");
        }

        course.setUploadStatus(newStatus);

        if (newStatus == UploadStatus.READY) {
            if (durationSecs != null && durationSecs > 0) {
                course.setVideoDuration(durationSecs);
            }

            String currentUrl = course.getVideoUrl();

            if (currentUrl == null || currentUrl.isEmpty()) {
                // No URL at all — use demo video
                course.setVideoUrl(demoVideoUrl);
                log.info("No video URL — using demo for course: {}", courseId);
            } else if (!currentUrl.startsWith("http")) {
                // It is an S3 key — convert to CloudFront URL
                if (cloudfrontDomain != null && !cloudfrontDomain.isEmpty()) {
                    course.setVideoUrl("https://" + cloudfrontDomain + "/" + currentUrl);
                    log.info("Set CloudFront URL for course: {}", courseId);
                } else {
                    // No CloudFront configured — use demo video
                    course.setVideoUrl(demoVideoUrl);
                    log.info("No CloudFront domain configured — using demo for course: {}", courseId);
                }
            }
            // else: already starts with http — keep existing URL as is
        }

        if (newStatus == UploadStatus.FAILED) {
            course.setVideoUrl(null);
        }

        course = courseRepository.save(course);
        log.info("Upload status updated: courseId={}, status={}", courseId, newStatus);
        return toResponse(course);
    }

    private Course findCourseOrThrow(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
    }

    private Course findCourseOwnedByInstructor(UUID courseId, String instructorId) {
        Course course = findCourseOrThrow(courseId);
        if (!course.getInstructorId().equals(UUID.fromString(instructorId))) {
            throw new UnauthorisedException("You do not have permission to modify this course");
        }
        return course;
    }

    /**
     * Converts a Course entity to a CourseResponse DTO.
     * Resolves the video URL:
     *   - If it starts with http → already a full URL (local demo or CloudFront)
     *   - If it is an S3 key → prepend CloudFront domain
     *   - If CloudFront domain not configured → return S3 key as-is
     */
    private CourseResponse toResponse(Course course) {
        String videoUrl = resolveVideoUrl(course.getVideoUrl());

        return CourseResponse.builder()
                .id(course.getId())
                .title(course.getTitle())
                .description(course.getDescription())
                .instructorId(course.getInstructorId())
                .instructorName(course.getInstructorName())
                .videoUrl(videoUrl)
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

    private String resolveVideoUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return null;
        // Already a full URL (http/https)
        if (rawUrl.startsWith("http")) return rawUrl;
        // S3 key — prepend CloudFront domain if configured
        if (cloudfrontDomain != null && !cloudfrontDomain.isEmpty()) {
            return "https://" + cloudfrontDomain + "/" + rawUrl;
        }
        // No CloudFront — return as-is (S3 key)
        return rawUrl;
    }
}
