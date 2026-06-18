package com.olp.enrollment.service;

import com.olp.common.exception.DuplicateResourceException;
import com.olp.common.exception.ResourceNotFoundException;
import com.olp.enrollment.dto.EnrollmentResponse;
import com.olp.enrollment.entity.Enrollment;
import com.olp.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Business logic for course enrollment.
 *
 * Enrol flow:
 *   1. Check if already enrolled (application-level check for friendly 409)
 *   2. Insert enrollment record
 *   3. If DB UNIQUE constraint fires anyway (race condition), catch and return 409
 *
 * The double check (app level + DB constraint) handles:
 *   - Normal case: app check returns 409 immediately
 *   - Race condition: two simultaneous requests both pass app check,
 *     but only one succeeds at DB level — the other gets 409 from DB constraint
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;

    // ── Enrol ──────────────────────────────────────────────────

    @Transactional
    public EnrollmentResponse enrol(UUID courseId, String userId) {

        UUID userUuid = UUID.fromString(userId);

        log.info("Enrol request: userId={}, courseId={}", userId, courseId);

        // Application-level duplicate check — gives a friendly 409
        if (enrollmentRepository.existsByCourseIdAndUserId(courseId, userUuid)) {
            throw new DuplicateResourceException(
                    "You are already enrolled in this course"
            );
        }

        Enrollment enrollment = Enrollment.builder()
                .courseId(courseId)
                .userId(userUuid)
                .build();

        try {
            enrollment = enrollmentRepository.save(enrollment);
            log.info("Enrollment created: id={}", enrollment.getId());
            return toResponse(enrollment);

        } catch (DataIntegrityViolationException ex) {
            // DB UNIQUE constraint fired — concurrent duplicate request
            log.warn("Duplicate enrollment caught at DB level: userId={}, courseId={}",
                    userId, courseId);
            throw new DuplicateResourceException("You are already enrolled in this course");
        }
    }

    // ── Unenrol ────────────────────────────────────────────────

    @Transactional
    public void unenrol(UUID courseId, String userId) {

        UUID userUuid = UUID.fromString(userId);

        Enrollment enrollment = enrollmentRepository
                .findByCourseIdAndUserId(courseId, userUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Enrollment", "courseId+userId", courseId + "+" + userId));

        enrollmentRepository.delete(enrollment);
        log.info("Enrollment deleted: userId={}, courseId={}", userId, courseId);
    }

    // ── Read ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> getMyEnrollments(String userId) {
        return enrollmentRepository
                .findAllByUserId(UUID.fromString(userId))
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse getEnrollment(UUID courseId, String userId) {
        return enrollmentRepository
                .findByCourseIdAndUserId(courseId, UUID.fromString(userId))
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Enrollment", "courseId", courseId));
    }

    @Transactional(readOnly = true)
    public boolean isEnrolled(UUID courseId, String userId) {
        return enrollmentRepository
                .existsByCourseIdAndUserId(courseId, UUID.fromString(userId));
    }

    @Transactional(readOnly = true)
    public long getEnrollmentCount(UUID courseId) {
        return enrollmentRepository.countByCourseId(courseId);
    }

    // ── Helper ─────────────────────────────────────────────────

    private EnrollmentResponse toResponse(Enrollment e) {
        return EnrollmentResponse.builder()
                .id(e.getId())
                .courseId(e.getCourseId())
                .userId(e.getUserId())
                .enrolledAt(e.getEnrolledAt())
                .completedAt(e.getCompletedAt())
                .completed(e.getCompletedAt() != null)
                .build();
    }
}
