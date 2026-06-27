package com.olp.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.common.exception.DuplicateResourceException;
import com.olp.common.exception.UnauthorisedException;
import com.olp.course.controller.CourseController;
import com.olp.course.dto.CourseResponse;
import com.olp.course.dto.CreateCourseRequest;
import com.olp.course.entity.UploadStatus;
import com.olp.course.service.CourseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private CourseService courseService;

    private static final UUID INSTRUCTOR_ID = UUID.randomUUID();
    private static final UUID COURSE_ID     = UUID.randomUUID();

    private CourseResponse sampleCourse() {
        return CourseResponse.builder()
                .id(COURSE_ID)
                .title("AWS Cloud Course")
                .description("Learn AWS")
                .instructorId(INSTRUCTOR_ID)
                .instructorName("Test Instructor")
                .uploadStatus(UploadStatus.NONE)
                .isPublished(false)
                .kbIngested(false)
                .build();
    }

    @Test
    @DisplayName("GET /api/courses → 200 with published courses")
    void getAllCourses_success() throws Exception {
        Page<CourseResponse> page = new PageImpl<>(List.of(sampleCourse()));
        when(courseService.getAllPublishedCourses(0, 12)).thenReturn(page);

        mockMvc.perform(get("/api/courses").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/courses/{id} → 200 when course exists")
    void getCourse_success() throws Exception {
        when(courseService.getCourseById(COURSE_ID)).thenReturn(sampleCourse());

        mockMvc.perform(get("/api/courses/{id}", COURSE_ID).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("AWS Cloud Course"));
    }

    @Test
    @DisplayName("POST /api/courses → 201 when instructor creates course")
    void createCourse_success() throws Exception {
        CreateCourseRequest request = new CreateCourseRequest();
        request.setTitle("New Course");
        request.setDescription("Description");

        when(courseService.createCourse(any(), anyString(), anyString()))
                .thenReturn(sampleCourse());

        mockMvc.perform(post("/api/courses")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("x-user-id", INSTRUCTOR_ID.toString())
                .header("x-user-role", "instructor")
                .header("x-user-name", "Test Instructor"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/courses → 403 when not instructor")
    void createCourse_notInstructor_forbidden() throws Exception {
        CreateCourseRequest request = new CreateCourseRequest();
        request.setTitle("New Course");

        mockMvc.perform(post("/api/courses")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("x-user-id", UUID.randomUUID().toString())
                .header("x-user-role", "user"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/courses → 409 when title already exists")
    void createCourse_duplicateTitle_conflict() throws Exception {
        CreateCourseRequest request = new CreateCourseRequest();
        request.setTitle("Duplicate Course");

        when(courseService.createCourse(any(), anyString(), anyString()))
                .thenThrow(new DuplicateResourceException("Title already exists"));

        mockMvc.perform(post("/api/courses")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("x-user-id", INSTRUCTOR_ID.toString())
                .header("x-user-role", "instructor"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE /api/courses/{id} → 200 when owner deletes")
    void deleteCourse_owner_success() throws Exception {
        doNothing().when(courseService).deleteCourse(COURSE_ID, INSTRUCTOR_ID.toString());

        mockMvc.perform(delete("/api/courses/{id}", COURSE_ID)
                .with(csrf())
                .header("x-user-id", INSTRUCTOR_ID.toString())
                .header("x-user-role", "instructor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/courses/{id} → 403 when not instructor")
    void deleteCourse_notInstructor_forbidden() throws Exception {
        mockMvc.perform(delete("/api/courses/{id}", COURSE_ID)
                .with(csrf())
                .header("x-user-id", UUID.randomUUID().toString())
                .header("x-user-role", "user"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/courses/{id} → 4xx when service throws UnauthorisedException")
    void deleteCourse_notOwner_unauthorized() throws Exception {
        doThrow(new UnauthorisedException("Not your course"))
                .when(courseService).deleteCourse(any(), anyString());

        mockMvc.perform(delete("/api/courses/{id}", COURSE_ID)
                .with(csrf())
                .header("x-user-id", UUID.randomUUID().toString())
                .header("x-user-role", "instructor"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("GET /api/courses/my → 403 when not instructor")
    void getMyCourses_notInstructor_forbidden() throws Exception {
        mockMvc.perform(get("/api/courses/my")
                .with(csrf())
                .header("x-user-id", UUID.randomUUID().toString())
                .header("x-user-role", "user"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/courses/my → 200 when instructor")
    void getMyCourses_instructor_success() throws Exception {
        Page<CourseResponse> page = new PageImpl<>(List.of(sampleCourse()));
        when(courseService.getCoursesByInstructor(anyString(), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/courses/my")
                .with(csrf())
                .header("x-user-id", INSTRUCTOR_ID.toString())
                .header("x-user-role", "instructor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PATCH /api/courses/{id}/upload-status → 200")
    void updateUploadStatus_success() throws Exception {
        when(courseService.updateUploadStatus(COURSE_ID, "READY", 3600))
                .thenReturn(sampleCourse());

        mockMvc.perform(patch("/api/courses/{id}/upload-status", COURSE_ID)
                .with(csrf())
                .param("status", "READY")
                .param("durationSecs", "3600"))
                .andExpect(status().isOk());
    }
}