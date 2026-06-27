package com.olp.progress.service;

import com.olp.common.exception.ResourceNotFoundException;
import com.olp.progress.dto.ProgressEvent;
import com.olp.progress.dto.ProgressResponse;
import com.olp.progress.dto.UpdateProgressRequest;
import com.olp.progress.entity.VideoProgress;
import com.olp.progress.repository.VideoProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProgressService Unit Tests")
class ProgressServiceTest {

    @Mock private VideoProgressRepository progressRepository;
    @Mock private RedisProgressService redisProgressService;
    @Mock private SqsPort sqsPort;

    @InjectMocks
    private ProgressService progressService;

    private static final UUID   USER_ID   = UUID.randomUUID();
    private static final UUID   COURSE_ID = UUID.randomUUID();
    private static final String USER_STR  = USER_ID.toString();

    private VideoProgress testProgress;

    @BeforeEach
    void setUp() {
        testProgress = VideoProgress.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .courseId(COURSE_ID)
                .currentTimeSecs(300)
                .durationSecs(600)
                .percentComplete(50)
                .lastUpdatedAt(LocalDateTime.now())
                .build();
    }

    // Helper to build request
    private UpdateProgressRequest request(int current, int duration) {
        UpdateProgressRequest r = new UpdateProgressRequest();
        r.setCurrentTimeSecs(current);
        r.setDurationSecs(duration);
        return r;
    }

    @Nested
    @DisplayName("updateProgress()")
    class UpdateProgressTests {

        @Test
        @DisplayName("should write to Redis and publish to SQS")
        void updateProgress_writesRedisAndPublishesSQS() {
            ProgressResponse response = progressService.updateProgress(
                    COURSE_ID, USER_STR, request(300, 600));

            verify(redisProgressService).writeProgress(USER_ID, COURSE_ID, 50);
            verify(sqsPort).publish(any(ProgressEvent.class));
            assertThat(response.getPercentComplete()).isEqualTo(50);
        }

        @Test
        @DisplayName("should calculate 75% correctly")
        void updateProgress_calculatesPercentageCorrectly() {
            ProgressResponse response = progressService.updateProgress(
                    COURSE_ID, USER_STR, request(450, 600));

            verify(redisProgressService).writeProgress(USER_ID, COURSE_ID, 75);
            assertThat(response.getPercentComplete()).isEqualTo(75);
        }

        @Test
        @DisplayName("should cap percentage at 100 when currentTime exceeds duration")
        void updateProgress_exceedsDuration_capsAt100() {
            ProgressResponse response = progressService.updateProgress(
                    COURSE_ID, USER_STR, request(700, 600));

            verify(redisProgressService).writeProgress(USER_ID, COURSE_ID, 100);
            assertThat(response.getPercentComplete()).isEqualTo(100);
        }

        @Test
        @DisplayName("should return zero percent when currentTime is zero")
        void updateProgress_zeroProgress_returnsZero() {
            ProgressResponse response = progressService.updateProgress(
                    COURSE_ID, USER_STR, request(0, 600));

            verify(redisProgressService).writeProgress(USER_ID, COURSE_ID, 0);
            assertThat(response.getPercentComplete()).isEqualTo(0);
        }

        @Test
        @DisplayName("should persist directly when SQS publish fails")
        void updateProgress_sqsFails_persistsDirectly() {
            doThrow(new RuntimeException("SQS unavailable"))
                    .when(sqsPort).publish(any());
            when(progressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                    .thenReturn(Optional.of(testProgress));

            assertThatCode(() ->
                    progressService.updateProgress(COURSE_ID, USER_STR, request(300, 600)))
                    .doesNotThrowAnyException();

            verify(progressRepository).save(any());
        }

        @Test
        @DisplayName("should publish correct ProgressEvent to SQS")
        void updateProgress_publishesCorrectEvent() {
            progressService.updateProgress(COURSE_ID, USER_STR, request(300, 600));

            ArgumentCaptor<ProgressEvent> captor = ArgumentCaptor.forClass(ProgressEvent.class);
            verify(sqsPort).publish(captor.capture());

            ProgressEvent event = captor.getValue();
            assertThat(event.getUserId()).isEqualTo(USER_ID);
            assertThat(event.getCourseId()).isEqualTo(COURSE_ID);
            assertThat(event.getCurrentTimeSecs()).isEqualTo(300);
            assertThat(event.getDurationSecs()).isEqualTo(600);
            assertThat(event.getPercentComplete()).isEqualTo(50);
        }

        @Test
        @DisplayName("should return zero when duration is zero")
        void updateProgress_zeroDuration_returnsZero() {
            ProgressResponse response = progressService.updateProgress(
                    COURSE_ID, USER_STR, request(100, 0));

            assertThat(response.getPercentComplete()).isEqualTo(0);
        }

        @Test
        @DisplayName("should mark completed when percent reaches 100")
        void updateProgress_complete_marksCompleted() {
            ProgressResponse response = progressService.updateProgress(
                    COURSE_ID, USER_STR, request(600, 600));

            assertThat(response.isCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("getProgress()")
    class GetProgressTests {

        @Test
        @DisplayName("should return progress from Redis when available")
        void getProgress_redisHit_returnsRedisData() {
            when(redisProgressService.readProgress(USER_ID, COURSE_ID)).thenReturn(75);
            when(progressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                    .thenReturn(Optional.of(testProgress));

            ProgressResponse response = progressService.getProgress(COURSE_ID, USER_STR);

            assertThat(response.getPercentComplete()).isEqualTo(75);
            assertThat(response.getSource()).isEqualTo("redis");
        }

        @Test
        @DisplayName("should fall back to database when Redis returns null")
        void getProgress_redisMiss_fallsBackToDatabase() {
            when(redisProgressService.readProgress(USER_ID, COURSE_ID)).thenReturn(null);
            when(progressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                    .thenReturn(Optional.of(testProgress));

            ProgressResponse response = progressService.getProgress(COURSE_ID, USER_STR);

            assertThat(response.getPercentComplete()).isEqualTo(50);
            assertThat(response.getSource()).isEqualTo("database");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found in DB")
        void getProgress_notFound_throwsException() {
            when(redisProgressService.readProgress(USER_ID, COURSE_ID)).thenReturn(null);
            when(progressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> progressService.getProgress(COURSE_ID, USER_STR))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should fall back to DB when Redis throws exception")
        void getProgress_redisException_fallsBackToDatabase() {
            when(redisProgressService.readProgress(USER_ID, COURSE_ID))
                    .thenThrow(new RuntimeException("Redis unavailable"));
            when(progressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                    .thenReturn(Optional.of(testProgress));

            ProgressResponse response = progressService.getProgress(COURSE_ID, USER_STR);

            assertThat(response.getSource()).isEqualTo("database");
        }
    }

    @Nested
    @DisplayName("getAllProgressForUser()")
    class GetAllProgressForUserTests {

        @Test
        @DisplayName("should return all progress records for user")
        void getAllProgressForUser_returnsAllRecords() {
            VideoProgress second = VideoProgress.builder()
                    .id(UUID.randomUUID()).userId(USER_ID)
                    .courseId(UUID.randomUUID()).percentComplete(100)
                    .lastUpdatedAt(LocalDateTime.now()).build();

            when(progressRepository.findAllByUserId(USER_ID))
                    .thenReturn(List.of(testProgress, second));

            List<ProgressResponse> results = progressService.getAllProgressForUser(USER_STR);

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(r -> r.getUserId().equals(USER_ID));
        }

        @Test
        @DisplayName("should return empty list when user has no progress")
        void getAllProgressForUser_noProgress_returnsEmpty() {
            when(progressRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

            assertThat(progressService.getAllProgressForUser(USER_STR)).isEmpty();
        }
    }

    @Nested
    @DisplayName("persistProgressEvent()")
    class PersistProgressEventTests {

        @Test
        @DisplayName("should insert new record when not exists")
        void persistProgressEvent_newRecord_inserts() {
            ProgressEvent event = ProgressEvent.builder()
                    .userId(USER_ID).courseId(COURSE_ID)
                    .currentTimeSecs(300).durationSecs(600)
                    .percentComplete(50).eventTime(LocalDateTime.now()).build();

            when(progressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                    .thenReturn(Optional.empty());

            progressService.persistProgressEvent(event);

            verify(progressRepository).save(argThat(p ->
                p.getPercentComplete() == 50 && p.getCurrentTimeSecs() == 300
            ));
        }

        @Test
        @DisplayName("should update existing record on upsert")
        void persistProgressEvent_existingRecord_updates() {
            ProgressEvent event = ProgressEvent.builder()
                    .userId(USER_ID).courseId(COURSE_ID)
                    .currentTimeSecs(480).durationSecs(600)
                    .percentComplete(80).eventTime(LocalDateTime.now()).build();

            when(progressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                    .thenReturn(Optional.of(testProgress));

            progressService.persistProgressEvent(event);

            verify(progressRepository).save(argThat(p ->
                p.getPercentComplete() == 80 && p.getCurrentTimeSecs() == 480
            ));
        }

        @Test
        @DisplayName("should set completedAt when percent reaches 100")
        void persistProgressEvent_completed_setsCompletedAt() {
            ProgressEvent event = ProgressEvent.builder()
                    .userId(USER_ID).courseId(COURSE_ID)
                    .currentTimeSecs(600).durationSecs(600)
                    .percentComplete(100).eventTime(LocalDateTime.now()).build();

            when(progressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                    .thenReturn(Optional.of(testProgress));

            progressService.persistProgressEvent(event);

            verify(progressRepository).save(argThat(p ->
                p.getCompletedAt() != null
            ));
        }
    }
}
