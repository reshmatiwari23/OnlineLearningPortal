package com.olp.enrollment;

import com.olp.common.exception.DuplicateResourceException;
import com.olp.common.exception.ResourceNotFoundException;
import com.olp.enrollment.controller.EnrollmentController;
import com.olp.enrollment.dto.EnrollmentResponse;
import com.olp.enrollment.service.EnrollmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EnrollmentController.class)
@WithMockUser
class EnrollmentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  EnrollmentService enrollmentService;

    private static final String USER_ID   = "550e8400-e29b-41d4-a716-446655440000";
    private static final UUID   COURSE_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");

    private EnrollmentResponse sampleResponse() {
        return EnrollmentResponse.builder()
                .id(UUID.randomUUID())
                .courseId(COURSE_ID)
                .userId(UUID.fromString(USER_ID))
                .enrolledAt(LocalDateTime.now())
                .completed(false)
                .build();
    }

    @Test
    @DisplayName("POST /enrollment/{courseId} → 201 when learner enrols")
    void enrol_success() throws Exception {
        when(enrollmentService.enrol(eq(COURSE_ID), eq(USER_ID)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/enrollment/" + COURSE_ID)
                .with(csrf())
                .header("x-user-id",   USER_ID)
                .header("x-user-role", "user"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /enrollment/{courseId} → 409 when already enrolled")
    void enrol_duplicate() throws Exception {
        when(enrollmentService.enrol(any(), any()))
                .thenThrow(new DuplicateResourceException("You are already enrolled in this course"));

        mockMvc.perform(post("/api/enrollment/" + COURSE_ID)
                .with(csrf())
                .header("x-user-id",   USER_ID)
                .header("x-user-role", "user"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You are already enrolled in this course"));
    }

    @Test
    @DisplayName("POST /enrollment/{courseId} → 403 when instructor tries to enrol")
    void enrol_instructor_forbidden() throws Exception {
        mockMvc.perform(post("/api/enrollment/" + COURSE_ID)
                .with(csrf())
                .header("x-user-id",   USER_ID)
                .header("x-user-role", "instructor"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /enrollment/{courseId} → 200 when unenrolled")
    void unenrol_success() throws Exception {
        doNothing().when(enrollmentService).unenrol(any(), any());

        mockMvc.perform(delete("/api/enrollment/" + COURSE_ID)
                .with(csrf())
                .header("x-user-id",   USER_ID)
                .header("x-user-role", "user"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /enrollment/{courseId} → 404 when not enrolled")
    void unenrol_not_enrolled() throws Exception {
        doThrow(new ResourceNotFoundException("Enrollment", "courseId", COURSE_ID))
                .when(enrollmentService).unenrol(any(), any());

        mockMvc.perform(delete("/api/enrollment/" + COURSE_ID)
                .with(csrf())
                .header("x-user-id",   USER_ID)
                .header("x-user-role", "user"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /enrollment/my → 200 with list of enrollments")
    void getMyEnrollments_success() throws Exception {
        when(enrollmentService.getMyEnrollments(USER_ID))
                .thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/enrollment/my")
                .header("x-user-id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
