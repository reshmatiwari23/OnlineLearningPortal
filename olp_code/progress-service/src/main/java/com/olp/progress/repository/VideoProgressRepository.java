package com.olp.progress.repository;

import com.olp.progress.entity.VideoProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for video_progress table.
 *
 * NOTE: In the hot path (progress updates), this repository is only
 * used by the SQS consumer — NOT by the progress update endpoint.
 * The update endpoint writes to Redis only and returns immediately.
 */
@Repository
public interface VideoProgressRepository extends JpaRepository<VideoProgress, UUID> {

    // Primary lookup — used when Redis misses on a progress read
    Optional<VideoProgress> findByUserIdAndCourseId(UUID userId, UUID courseId);

    // All progress records for a user — learner dashboard
    List<VideoProgress> findAllByUserId(UUID userId);

    // All progress records for a course — instructor dashboard
    List<VideoProgress> findAllByCourseId(UUID courseId);

    // Find stalled learners for the AI nudge scheduler:
    // learners who haven't watched anything in 3+ days and are under 50% complete
    @Query("SELECT vp FROM VideoProgress vp " +
           "WHERE vp.lastUpdatedAt < :cutoff " +
           "AND vp.percentComplete < :threshold " +
           "AND vp.completedAt IS NULL")
    List<VideoProgress> findStalledLearners(
            @Param("cutoff")    LocalDateTime cutoff,
            @Param("threshold") int threshold);

    // Does any progress record exist for this user+course combination?
    boolean existsByUserIdAndCourseId(UUID userId, UUID courseId);
}
