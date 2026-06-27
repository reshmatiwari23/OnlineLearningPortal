package com.olp.enrollment.service;

import com.olp.common.exception.DuplicateResourceException;
import com.olp.common.exception.ResourceNotFoundException;
import com.olp.enrollment.dto.EnrollmentResponse;
import com.olp.enrollment.entity.Enrollment;
import com.olp.enrollment.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnrollmentService Unit Tests")
class EnrollmentServiceTest {

    @Mock private EnrollmentRepository enrollmentRepository;

    @InjectMocks
    private EnrollmentService enrollmentService;

    private static final UUID   USER_ID    = UUID.randomUUID();
    private static final UUID   COURSE_ID  = UUID.randomUUID();
    private static final String USER_STR   = USER_ID.toString();

    private Enrollment testEnrollment;

    @BeforeEach
    void setUp() {
        testEnrollment = Enrollment.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .courseId(COURSE_ID)
                .enrolledAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("enrol()")
    class EnrolTests {

        @Test
        @DisplayName("should enrol learner successfully when not already enrolled")
        void enrol_success() {
            when(enrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, USER_ID))
                    .thenReturn(false);
            when(enrollmentRepository.save(any(Enrollment.class)))
                    .thenReturn(testEnrollment);

            EnrollmentResponse response = enrollmentService.enrol(COURSE_ID, USER_STR);

            assertThat(response).isNotNull();
            assertThat(response.getCourseId()).isEqualTo(COURSE_ID);
            assertThat(response.getUserId()).isEqualTo(USER_ID);
            verify(enrollmentRepository).save(any(Enrollment.class));
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when already enrolled")
        void enrol_alreadyEnrolled_throwsException() {
            when(enrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, USER_ID))
                    .thenReturn(true);

            assertThatThrownBy(() -> enrollmentService.enrol(COURSE_ID, USER_STR))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already enrolled");

            verify(enrollmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DuplicateResourceException on race condition DB constraint")
        void enrol_raceCondition_throwsException() {
            when(enrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, USER_ID))
                    .thenReturn(false);
            when(enrollmentRepository.save(any(Enrollment.class)))
                    .thenThrow(new DataIntegrityViolationException("unique constraint"));

            assertThatThrownBy(() -> enrollmentService.enrol(COURSE_ID, USER_STR))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("should set enrolledAt timestamp on enrolment")
        void enrol_setsEnrolledAt() {
            when(enrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, USER_ID))
                    .thenReturn(false);
            when(enrollmentRepository.save(any(Enrollment.class)))
                    .thenReturn(testEnrollment);

            EnrollmentResponse response = enrollmentService.enrol(COURSE_ID, USER_STR);

            assertThat(response.getEnrolledAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("unenrol()")
    class UnenrolTests {

        @Test
        @DisplayName("should unenrol learner successfully when enrolled")
        void unenrol_success() {
            when(enrollmentRepository.findByCourseIdAndUserId(COURSE_ID, USER_ID))
                    .thenReturn(Optional.of(testEnrollment));

            enrollmentService.unenrol(COURSE_ID, USER_STR);

            verify(enrollmentRepository).delete(testEnrollment);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not enrolled")
        void unenrol_notEnrolled_throwsException() {
            when(enrollmentRepository.findByCourseIdAndUserId(COURSE_ID, USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.unenrol(COURSE_ID, USER_STR))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(enrollmentRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("isEnrolled()")
    class IsEnrolledTests {

        @Test
        @DisplayName("should return true when learner is enrolled")
        void isEnrolled_enrolled_returnsTrue() {
            when(enrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, USER_ID))
                    .thenReturn(true);

            assertThat(enrollmentService.isEnrolled(COURSE_ID, USER_STR)).isTrue();
        }

        @Test
        @DisplayName("should return false when learner is not enrolled")
        void isEnrolled_notEnrolled_returnsFalse() {
            when(enrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, USER_ID))
                    .thenReturn(false);

            assertThat(enrollmentService.isEnrolled(COURSE_ID, USER_STR)).isFalse();
        }
    }

    @Nested
    @DisplayName("getMyEnrollments()")
    class GetMyEnrollmentsTests {

        @Test
        @DisplayName("should return all enrollments for user")
        void getMyEnrollments_returnsAllEnrollments() {
            Enrollment second = Enrollment.builder()
                    .id(UUID.randomUUID()).userId(USER_ID)
                    .courseId(UUID.randomUUID())
                    .enrolledAt(LocalDateTime.now()).build();

            when(enrollmentRepository.findAllByUserId(USER_ID))
                    .thenReturn(List.of(testEnrollment, second));

            List<EnrollmentResponse> results = enrollmentService.getMyEnrollments(USER_STR);

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(r -> r.getUserId().equals(USER_ID));
        }

        @Test
        @DisplayName("should return empty list when no enrollments")
        void getMyEnrollments_noEnrollments_returnsEmpty() {
            when(enrollmentRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

            assertThat(enrollmentService.getMyEnrollments(USER_STR)).isEmpty();
        }
    }

    @Nested
    @DisplayName("getEnrollmentCount()")
    class GetEnrollmentCountTests {

        @Test
        @DisplayName("should return correct learner count for course")
        void getEnrollmentCount_returnsCount() {
            when(enrollmentRepository.countByCourseId(COURSE_ID)).thenReturn(42L);

            assertThat(enrollmentService.getEnrollmentCount(COURSE_ID)).isEqualTo(42L);
        }

        @Test
        @DisplayName("should return zero when no learners enrolled")
        void getEnrollmentCount_noLearners_returnsZero() {
            when(enrollmentRepository.countByCourseId(COURSE_ID)).thenReturn(0L);

            assertThat(enrollmentService.getEnrollmentCount(COURSE_ID)).isZero();
        }
    }
}