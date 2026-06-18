package com.olp.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.common.exception.DuplicateResourceException;
import com.olp.common.exception.ResourceNotFoundException;
import com.olp.common.exception.UnauthorisedException;
import com.olp.course.controller.CourseController;
import com.olp.course.dto.*;
import com.olp.course.service.CourseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CourseController.class)
@WithMockUser
class CourseControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  CourseService courseService;

    private static final String INSTRUCTOR_ID   = "550e8400-e29b-41d4-a716-446655440000";
    private static final String INSTRUCTOR_NAME = "Test Instructor";
    private static final String LEARNER_ID      = "660e8400-e29b-41d4-a716-446655440001";

    private CourseResponse sampleCourse() {
        return CourseResponse.builder()
                .id(UUID.randomUUID())
                .title("Spring Boot for Beginners")
                .description("Learn Spring Boot from scratch")
                .instructorId(UUID.fromString(INSTRUCTOR_ID))
                .instructorName(INSTRUCTOR_NAME)
                .isPublished(false)
                .kbIngested(false)
                .build();
    }

    @Test
    @DisplayName("GET /api/courses → 200 with page of courses")
    void getAllCourses_success() throws Exception {
        var page = new PageImpl<>(List.of(sampleCourse()), PageRequest.of(0, 12), 1);
        when(courseService.getAllPublishedCourses(0, 12)).thenReturn(page);

        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("POST /api/courses → 201 when instructor creates course")
    void createCourse_success() throws Exception {
        CreateCourseRequest request = new CreateCourseRequest();
        request.setTitle("Spring Boot for Beginners");
        request.setDescription("Learn Spring Boot");

        when(courseService.createCourse(any(), eq(INSTRUCTOR_ID), eq(INSTRUCTOR_NAME)))
                .thenReturn(sampleCourse());

        mockMvc.perform(post("/api/courses")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("x-user-id",   INSTRUCTOR_ID)
                .header("x-user-role", "instructor")
                .header("x-user-name", INSTRUCTOR_NAME))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/courses → 403 when learner tries to create course")
    void createCourse_forbidden_for_learner() throws Exception {
        CreateCourseRequest request = new CreateCourseRequest();
        request.setTitle("Test Course");

        mockMvc.perform(post("/api/courses")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("x-user-id",   LEARNER_ID)
                .header("x-user-role", "user"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/courses → 409 when title already exists")
    void createCourse_duplicate_title() throws Exception {
        CreateCourseRequest request = new CreateCourseRequest();
        request.setTitle("Existing Course");

        when(courseService.createCourse(any(), any(), any()))
                .thenThrow(new DuplicateResourceException("You already have a course titled 'Existing Course'"));

        mockMvc.perform(post("/api/courses")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("x-user-id",   INSTRUCTOR_ID)
                .header("x-user-role", "instructor")
                .header("x-user-name", INSTRUCTOR_NAME))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE /api/courses/{id} → 200 when owner deletes")
    void deleteCourse_success() throws Exception {
        UUID courseId = UUID.randomUUID();

        mockMvc.perform(delete("/api/courses/" + courseId)
                .with(csrf())
                .header("x-user-id",   INSTRUCTOR_ID)
                .header("x-user-role", "instructor"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/courses/{id} → 403 when non-owner tries to delete")
    void deleteCourse_not_owner() throws Exception {
        UUID courseId = UUID.randomUUID();

        doThrow(new UnauthorisedException("You do not have permission"))
                .when(courseService).deleteCourse(any(), any());

        mockMvc.perform(delete("/api/courses/" + courseId)
                .with(csrf())
                .header("x-user-id",   INSTRUCTOR_ID)
                .header("x-user-role", "instructor"))
                .andExpect(status().isForbidden());
    }
}
