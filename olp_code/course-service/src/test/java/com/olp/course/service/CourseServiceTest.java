package com.olp.course.service;

import com.olp.common.exception.DuplicateResourceException;
import com.olp.common.exception.ResourceNotFoundException;
import com.olp.common.exception.UnauthorisedException;
import com.olp.course.dto.CreateCourseRequest;
import com.olp.course.dto.CourseResponse;
import com.olp.course.dto.UpdateCourseRequest;
import com.olp.course.entity.Course;
import com.olp.course.entity.UploadStatus;
import com.olp.course.repository.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CourseService Unit Tests")
class CourseServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private S3Port s3Port;

    @InjectMocks
    private CourseService courseService;

    private static final UUID INSTRUCTOR_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final String CLOUDFRONT_DOMAIN = "d1ka6o9mjvg4i9.cloudfront.net";

    private Course testCourse;
    private CreateCourseRequest createRequest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(courseService, "cloudfrontDomain", CLOUDFRONT_DOMAIN);
        ReflectionTestUtils.setField(courseService, "videosBucket", "olp-videos-418272762620");

        testCourse = Course.builder()
                .id(COURSE_ID)
                .title("Introduction to AWS Cloud")
                .description("Learn AWS fundamentals")
                .instructorId(INSTRUCTOR_ID)
                .instructorName("Test Instructor")
                .isPublished(false)
                .uploadStatus(UploadStatus.NONE)
                .build();

        createRequest = new CreateCourseRequest();
        createRequest.setTitle("Introduction to AWS Cloud");
        createRequest.setDescription("Learn AWS fundamentals");
    }

    @Nested
    @DisplayName("createCourse()")
    class CreateCourseTests {

        @Test
        @DisplayName("should create course successfully with unique title")
        void createCourse_success() {
            when(courseRepository.existsByTitleAndInstructorId(
                    createRequest.getTitle(), INSTRUCTOR_ID)).thenReturn(false);
            when(courseRepository.save(any(Course.class))).thenReturn(testCourse);

            CourseResponse response = courseService.createCourse(
                    createRequest, INSTRUCTOR_ID, "Test Instructor");

            assertThat(response).isNotNull();
            assertThat(response.getTitle()).isEqualTo("Introduction to AWS Cloud");
            assertThat(response.getInstructorId()).isEqualTo(INSTRUCTOR_ID);
            verify(courseRepository).save(any(Course.class));
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when title already exists for instructor")
        void createCourse_duplicateTitle_throwsException() {
            when(courseRepository.existsByTitleAndInstructorId(
                    createRequest.getTitle(), INSTRUCTOR_ID)).thenReturn(true);

            assertThatThrownBy(() -> courseService.createCourse(
                    createRequest, INSTRUCTOR_ID, "Test Instructor"))
                    .isInstanceOf(DuplicateResourceException.class);

            verify(courseRepository, never()).save(any());
        }

        @Test
        @DisplayName("should allow same title for different instructors")
        void createCourse_sameTitleDifferentInstructor_success() {
            UUID otherInstructor = UUID.randomUUID();
            when(courseRepository.existsByTitleAndInstructorId(
                    createRequest.getTitle(), otherInstructor)).thenReturn(false);
            when(courseRepository.save(any(Course.class))).thenReturn(testCourse);

            assertThatCode(() -> courseService.createCourse(
                    createRequest, otherInstructor, "Other Instructor"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("getCourseById()")
    class GetCourseByIdTests {

        @Test
        @DisplayName("should return course when it exists")
        void getCourseById_exists_returnsCourse() {
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(testCourse));

            CourseResponse response = courseService.getCourseById(COURSE_ID);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(COURSE_ID);
            assertThat(response.getTitle()).isEqualTo("Introduction to AWS Cloud");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when course does not exist")
        void getCourseById_notFound_throwsException() {
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> courseService.getCourseById(COURSE_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(COURSE_ID.toString());
        }
    }

    @Nested
    @DisplayName("updateCourse()")
    class UpdateCourseTests {

        @Test
        @DisplayName("should update course when instructor is owner")
        void updateCourse_ownerUpdates_success() {
            UpdateCourseRequest updateRequest = new UpdateCourseRequest();
            updateRequest.setTitle("Updated AWS Course");
            updateRequest.setIsPublished(true);

            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(testCourse));
            when(courseRepository.save(any(Course.class))).thenReturn(testCourse);

            CourseResponse response = courseService.updateCourse(
                    COURSE_ID, updateRequest, INSTRUCTOR_ID);

            assertThat(response).isNotNull();
            verify(courseRepository).save(any(Course.class));
        }

        @Test
        @DisplayName("should throw UnauthorisedException when non-owner tries to update")
        void updateCourse_nonOwner_throwsException() {
            UUID otherUser = UUID.randomUUID();
            UpdateCourseRequest updateRequest = new UpdateCourseRequest();
            updateRequest.setTitle("Hacked Title");

            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(testCourse));

            assertThatThrownBy(() -> courseService.updateCourse(
                    COURSE_ID, updateRequest, otherUser))
                    .isInstanceOf(UnauthorisedException.class);

            verify(courseRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteCourse()")
    class DeleteCourseTests {

        @Test
        @DisplayName("should delete course when instructor is owner")
        void deleteCourse_ownerDeletes_success() {
            when(courseRepository.findByIdAndInstructorId(COURSE_ID, INSTRUCTOR_ID))
                    .thenReturn(Optional.of(testCourse));

            courseService.deleteCourse(COURSE_ID, INSTRUCTOR_ID);

            verify(courseRepository).delete(testCourse);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when course not found for instructor")
        void deleteCourse_notFound_throwsException() {
            when(courseRepository.findByIdAndInstructorId(COURSE_ID, INSTRUCTOR_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> courseService.deleteCourse(COURSE_ID, INSTRUCTOR_ID))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(courseRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("updateUploadStatus()")
    class UploadStatusTests {

        @Test
        @DisplayName("should set videoUrl to CloudFront URL when status is READY")
        void updateUploadStatus_ready_setsCloudFrontUrl() {
            String s3Key = "videos/" + COURSE_ID + "/lecture.mp4";
            testCourse.setVideoUrl(s3Key);

            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(testCourse));
            when(courseRepository.save(any(Course.class))).thenReturn(testCourse);

            courseService.updateUploadStatus(COURSE_ID, UploadStatus.READY, 3600);

            verify(courseRepository).save(argThat(course ->
                course.getVideoUrl().startsWith("https://" + CLOUDFRONT_DOMAIN) &&
                course.getUploadStatus() == UploadStatus.READY &&
                course.getVideoDuration() == 3600
            ));
        }

        @Test
        @DisplayName("should clear videoUrl when status is FAILED")
        void updateUploadStatus_failed_clearsVideoUrl() {
            testCourse.setVideoUrl("videos/" + COURSE_ID + "/lecture.mp4");

            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(testCourse));
            when(courseRepository.save(any(Course.class))).thenReturn(testCourse);

            courseService.updateUploadStatus(COURSE_ID, UploadStatus.FAILED, 0);

            verify(courseRepository).save(argThat(course ->
                course.getVideoUrl() == null &&
                course.getUploadStatus() == UploadStatus.FAILED
            ));
        }

        @Test
        @DisplayName("should not modify existing CloudFront URL when already set")
        void updateUploadStatus_alreadyCloudFront_noChange() {
            String existingUrl = "https://" + CLOUDFRONT_DOMAIN + "/videos/" + COURSE_ID + "/lecture.mp4";
            testCourse.setVideoUrl(existingUrl);

            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(testCourse));
            when(courseRepository.save(any(Course.class))).thenReturn(testCourse);

            courseService.updateUploadStatus(COURSE_ID, UploadStatus.READY, 3600);

            verify(courseRepository).save(argThat(course ->
                course.getVideoUrl().equals(existingUrl)
            ));
        }
    }

    @Nested
    @DisplayName("getPublishedCourses()")
    class GetPublishedCoursesTests {

        @Test
        @DisplayName("should return paginated published courses")
        void getPublishedCourses_returnsPaginatedResults() {
            testCourse.setIsPublished(true);
            Page<Course> page = new PageImpl<>(List.of(testCourse));
            when(courseRepository.findAllByIsPublishedTrue(any(PageRequest.class)))
                    .thenReturn(page);

            var result = courseService.getPublishedCourses(0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getTitle())
                    .isEqualTo("Introduction to AWS Cloud");
        }

        @Test
        @DisplayName("should return empty page when no published courses")
        void getPublishedCourses_noCourses_returnsEmptyPage() {
            when(courseRepository.findAllByIsPublishedTrue(any(PageRequest.class)))
                    .thenReturn(Page.empty());

            var result = courseService.getPublishedCourses(0, 10);

            assertThat(result.getContent()).isEmpty();
        }
    }
}
