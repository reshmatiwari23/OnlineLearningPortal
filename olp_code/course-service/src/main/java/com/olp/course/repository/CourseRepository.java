package com.olp.course.repository;

import com.olp.course.entity.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for courses table.
 */
@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {

    // All published courses — used for the learner catalogue
    Page<Course> findAllByIsPublishedTrue(Pageable pageable);

    // All courses by a specific instructor (published + drafts)
    Page<Course> findAllByInstructorId(UUID instructorId, Pageable pageable);

    // Get a specific course owned by a specific instructor
    // Used to verify ownership before update/delete
    Optional<Course> findByIdAndInstructorId(UUID id, UUID instructorId);

    // Check if a title is already used by the same instructor
    boolean existsByTitleAndInstructorId(String title, UUID instructorId);

    // Search by title (case-insensitive, partial match)
    @Query("SELECT c FROM Course c WHERE LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) AND c.isPublished = true")
    Page<Course> searchByTitle(@Param("keyword") String keyword, Pageable pageable);
}
