package com.olp.progress.service;

import com.olp.progress.dto.ProgressEvent;
import com.olp.progress.dto.ProgressResponse;
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
    @Mock private SqsProgressPublisher sqsProgressPublisher;

    @InjectMocks
    private ProgressService progressService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();

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
                .build();
    }

    @Nested
    @DisplayName("updateProgress()")
    class UpdateProgressTests {

        @Test
        @DisplayName("should write to Redis and publish to SQS")
        void updateProgress_writesRedisAndPublishesSQS() {
            progressService.updateProgress(USER_ID, COURSE_ID, 300, 600);

            verify(redisProgressService).writeProgress(USER_ID, COURSE_ID, 50);
            verify(sqsProgressPublisher).publish(any(ProgressEvent.class));
        }

        @Test
        @DisplayName("should calculate percentage correctly")
        void updateProgress_calculatesPercentageCorrectly() {
            progressService.updateProgress(USER_ID, COURSE_ID, 450, 600);

            verify(redisProgressService).writeProgress(USER_ID, COURSE_ID, 75);
        }

        @Test
        @DisplayName("should cap percentage at 100 when currentTime exceeds duration")
        void updateProgress_exceedsDuration_capsAt100() {
            progressService.updateProgress(USER_ID, COURSE_ID, 700, 600);

            verify(redisProgressService).writeProgress(USER_ID, COURSE_ID, 100);
        }

        @Test
        @DisplayName("should return zero percent when currentTime is zero")
        void updateProgress_zeroProgress_returnsZero() {
            progressService.updateProgress(USER_ID, COURSE_ID, 0, 600);

            verify(redisProgressService).writeProgress(USER_ID, COURSE_ID, 0);
        }

        @Test
        @DisplayName("should still return response when SQS publish fails")
        void updateProgress_sqsFails_doesNotThrow() {
            doThrow(new RuntimeException("SQS unavailable"))
                    .when(sqsProgressPublisher).publish(any());

            assertThatCode(() ->
                    progressService.updateProgress(USER_ID, COURSE_ID, 300, 600))
                    .doesNotThrowAnyException();

            verify(redisProgressService).writeProgress(any(), any(), anyInt());
        }

        @Test
        @DisplayName("should publish correct ProgressEvent to SQS")
        void updateProgress_publishesCorrectEvent() {
            progressService.updateProgress(USER_ID, COURSE_ID, 300, 600);

            ArgumentCaptor<ProgressEvent> captor = ArgumentCaptor.forClass(ProgressEvent.class);
            verify(sqsProgressPublisher).publish(captor.capture());

            ProgressEvent event = captor.getValue();
            assertThat(event.getUserId()).isEqualTo(USER_ID);
            assertThat(event.getCourseId()).isEqualTo(COURSE_ID);
            assertThat(event.getCurrentTimeSecs()).isEqualTo(300);
            assertThat(event.getDurationSecs()).isEqualTo(600);
            assertThat(event.getPercentComplete()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("getProgress()")
    class GetProgressTests {

        @Test
        @DisplayName("should return progress from Redis when available")
        void getProgress_redisHit_returnsRedisData() {
            when(redisProgressService.readProgress(USER_ID, COURSE_ID)).thenReturn(Optional.of(75));

            ProgressResponse response = progressService.getProgress(USER_ID, COURSE_ID);

            assertThat(response.getPercentComplete()).isEqualTo(75);
            assertThat(response.getSource()).isEqualTo("redis");
            verify(progressRepository, never()).findByUserIdAndCourseId(any(), any());
        }

        @Test
        @DisplayName("should fall back to database when Redis cache miss")
        void getProgress_redisMiss_fallsBackToDatabase() {
            when(redisProgressService.readProgress(USER_ID, COURSE_ID)).thenReturn(Optional.empty());
            when(progressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                    .thenReturn(Optional.of(testProgress));

            ProgressResponse response = progressService.getProgress(USER_ID, COURSE_ID);

            assertThat(response.getPercentComplete()).isEqualTo(50);
            assertThat(response.getSource()).isEqualTo("database");
        }

        @Test
        @DisplayName("should return zero progress when not found in Redis or database")
        void getProgress_notFound_returnsZero() {
            when(redisProgressService.readProgress(USER_ID, COURSE_ID)).thenReturn(Optional.empty());
            when(progressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                    .thenReturn(Optional.empty());

            ProgressResponse response = progressService.getProgress(USER_ID, COURSE_ID);

            assertThat(response.getPercentComplete()).isZero();
        }
    }

    @Nested
    @DisplayName("getAllProgressForUser()")
    class GetAllProgressForUserTests {

        @Test
        @DisplayName("should return all progress records for user")
        void getAllProgressForUser_returnsAllRecords() {
            VideoProgress second = VideoProgress.builder()
                    .id(UUID.randomUUID())
                    .userId(USER_ID)
                    .courseId(UUID.randomUUID())
                    .percentComplete(100)
                    .build();

            when(progressRepository.findAllByUserId(USER_ID))
                    .thenReturn(List.of(testProgress, second));

            List<ProgressResponse> results = progressService.getAllProgressForUser(USER_ID);

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(r -> r.getUserId().equals(USER_ID));
        }

        @Test
        @DisplayName("should return empty list when user has no progress")
        void getAllProgressForUser_noProgress_returnsEmpty() {
            when(progressRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

            List<ProgressResponse> results = progressService.getAllProgressForUser(USER_ID);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("persistProgressEvent()")
    class PersistProgressEventTests {

        @Test
        @DisplayName("should upsert progress when record does not exist")
        void persistProgressEvent_newRecord_insertsNew() {
            ProgressEvent event = ProgressEvent.builder()
                    .userId(USER_ID).courseId(COURSE_ID)
                    .currentTimeSecs(300).durationSecs(600)
                    .percentComplete(50).build();

            when(progressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                    .thenReturn(Optional.empty());
            when(progressRepository.save(any())).thenReturn(testProgress);

            progressService.persistProgressEvent(event);

            verify(progressRepository).save(argThat(p ->
                p.getPercentComplete() == 50 &&
                p.getCurrentTimeSecs() == 300
            ));
        }

        @Test
        @DisplayName("should update existing record on upsert")
        void persistProgressEvent_existingRecord_updatesExisting() {
            ProgressEvent event = ProgressEvent.builder()
                    .userId(USER_ID).courseId(COURSE_ID)
                    .currentTimeSecs(480).durationSecs(600)
                    .percentComplete(80).build();

            when(progressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                    .thenReturn(Optional.of(testProgress));
            when(progressRepository.save(any())).thenReturn(testProgress);

            progressService.persistProgressEvent(event);

            verify(progressRepository).save(argThat(p ->
                p.getPercentComplete() == 80 &&
                p.getCurrentTimeSecs() == 480
            ));
        }

        @Test
        @DisplayName("should set completedAt when percent reaches 100")
        void persistProgressEvent_completed_setsCompletedAt() {
            ProgressEvent event = ProgressEvent.builder()
                    .userId(USER_ID).courseId(COURSE_ID)
                    .currentTimeSecs(600).durationSecs(600)
                    .percentComplete(100).build();

            when(progressRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID))
                    .thenReturn(Optional.of(testProgress));
            when(progressRepository.save(any())).thenReturn(testProgress);

            progressService.persistProgressEvent(event);

            verify(progressRepository).save(argThat(p ->
                p.getCompletedAt() != null
            ));
        }
    }
}
