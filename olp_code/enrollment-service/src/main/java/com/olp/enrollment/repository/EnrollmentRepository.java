package com.olp.enrollment.repository;

import com.olp.enrollment.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for enrollments table.
 */
@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    // Check if a specific user is already enrolled in a specific course
    // Used before inserting to give a friendly 409 before hitting the DB constraint
    boolean existsByCourseIdAndUserId(UUID courseId, UUID userId);

    // Get a specific enrollment — used for unenrol and status check
    Optional<Enrollment> findByCourseIdAndUserId(UUID courseId, UUID userId);

    // All enrollments for a user — used for "my courses" list
    List<Enrollment> findAllByUserId(UUID userId);

    // All enrollments for a course — used for instructor dashboard (learner count)
    List<Enrollment> findAllByCourseId(UUID courseId);

    // Count of learners enrolled in a course
    long countByCourseId(UUID courseId);
}
