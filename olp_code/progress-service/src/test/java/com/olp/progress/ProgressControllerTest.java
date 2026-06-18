package com.olp.progress;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.common.exception.ResourceNotFoundException;
import com.olp.progress.controller.ProgressController;
import com.olp.progress.dto.ProgressResponse;
import com.olp.progress.dto.UpdateProgressRequest;
import com.olp.progress.service.ProgressService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProgressController.class)
@WithMockUser
class ProgressControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  ProgressService progressService;

    private static final String USER_ID   = "550e8400-e29b-41d4-a716-446655440000";
    private static final UUID   COURSE_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");

    private ProgressResponse sampleProgress(int percent, String source) {
        return ProgressResponse.builder()
                .userId(UUID.fromString(USER_ID))
                .courseId(COURSE_ID)
                .currentTimeSecs(1800)
                .durationSecs(3600)
                .percentComplete(percent)
                .completed(percent >= 100)
                .source(source)
                .build();
    }

    @Test
    @DisplayName("POST /progress/{courseId} → 200 with percent calculated")
    void updateProgress_success() throws Exception {
        UpdateProgressRequest request = new UpdateProgressRequest();
        request.setCurrentTimeSecs(1800);
        request.setDurationSecs(3600);

        when(progressService.updateProgress(eq(COURSE_ID), eq(USER_ID), any()))
                .thenReturn(sampleProgress(50, "redis"));

        mockMvc.perform(post("/api/progress/" + COURSE_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("x-user-id",   USER_ID)
                .header("x-user-role", "user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.percentComplete").value(50))
                .andExpect(jsonPath("$.data.source").value("redis"));
    }

    @Test
    @DisplayName("POST /progress/{courseId} → 400 when currentTimeSecs missing")
    void updateProgress_validation_fails() throws Exception {
        UpdateProgressRequest request = new UpdateProgressRequest();
        request.setDurationSecs(3600);

        mockMvc.perform(post("/api/progress/" + COURSE_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("x-user-id",   USER_ID)
                .header("x-user-role", "user"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /progress/{courseId} → 200 with completed=true at 100%")
    void updateProgress_completion() throws Exception {
        UpdateProgressRequest request = new UpdateProgressRequest();
        request.setCurrentTimeSecs(3600);
        request.setDurationSecs(3600);

        when(progressService.updateProgress(eq(COURSE_ID), eq(USER_ID), any()))
                .thenReturn(sampleProgress(100, "redis"));

        mockMvc.perform(post("/api/progress/" + COURSE_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("x-user-id",   USER_ID)
                .header("x-user-role", "user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.percentComplete").value(100))
                .andExpect(jsonPath("$.data.completed").value(true));
    }

    @Test
    @DisplayName("GET /progress/{courseId} → 200 from Redis cache")
    void getProgress_redis_hit() throws Exception {
        when(progressService.getProgress(COURSE_ID, USER_ID))
                .thenReturn(sampleProgress(43, "redis"));

        mockMvc.perform(get("/api/progress/" + COURSE_ID)
                .header("x-user-id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("redis"));
    }

    @Test
    @DisplayName("GET /progress/{courseId} → 404 when no progress exists")
    void getProgress_not_found() throws Exception {
        when(progressService.getProgress(COURSE_ID, USER_ID))
                .thenThrow(new ResourceNotFoundException("Progress", "userId", USER_ID));

        mockMvc.perform(get("/api/progress/" + COURSE_ID)
                .header("x-user-id", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /progress/my → 200 with list of all user progress")
    void getMyProgress_success() throws Exception {
        when(progressService.getAllProgressForUser(USER_ID))
                .thenReturn(List.of(sampleProgress(43, "database")));

        mockMvc.perform(get("/api/progress/my")
                .header("x-user-id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /progress/{courseId}/all → 403 for learner")
    void getCourseProgress_learner_forbidden() throws Exception {
        mockMvc.perform(get("/api/progress/" + COURSE_ID + "/all")
                .header("x-user-id",   USER_ID)
                .header("x-user-role", "user"))
                .andExpect(status().isForbidden());
    }
}
