package com.olp.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.ai.controller.AiController;
import com.olp.ai.dto.*;
import com.olp.ai.service.BedrockPort;
import com.olp.ai.service.NudgeService;
import com.olp.ai.service.RecommendationService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AiController.class)
@WithMockUser
class AiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // Mock the interfaces — not the concrete AWS classes
    @MockBean BedrockPort bedrockPort;
    @MockBean RecommendationService recommendationService;
    @MockBean NudgeService nudgeService;

    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";

    @Test
    @DisplayName("POST /ai/summary → 200 with generated summary")
    void generateSummary_success() throws Exception {
        SummaryRequest request = SummaryRequest.builder()
                .courseId(UUID.randomUUID())
                .courseTitle("Spring Boot for Beginners")
                .transcriptText("In this course we cover Spring Boot 3...")
                .build();

        when(bedrockPort.generateCourseSummary(any(), any()))
                .thenReturn("{\"title\":\"Spring Boot\",\"difficulty\":\"beginner\"}");

        mockMvc.perform(post("/ai/summary")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /ai/recommend → 200 with recommendations")
    void getRecommendations_success() throws Exception {
        when(recommendationService.getRecommendations(any(), any(), any()))
                .thenReturn(List.of(
                        RecommendationResponse.builder()
                                .courseId(UUID.randomUUID())
                                .aiReason("Great match for your skills")
                                .build()
                ));

        mockMvc.perform(get("/ai/recommend")
                .param("enrolledTopics", "Spring Boot")
                .param("candidates", "[]")
                .header("x-user-id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /ai/search → 200 with embedding vector")
    void semanticSearch_success() throws Exception {
        when(bedrockPort.generateEmbedding(any()))
                .thenReturn(List.of(0.1, 0.2, 0.3));

        mockMvc.perform(get("/ai/search")
                .param("query", "Spring Boot concurrency")
                .header("x-user-id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("POST /ai/nudge → 200 when nudge sent")
    void sendNudge_success() throws Exception {
        NudgeRequest request = NudgeRequest.builder()
                .userId(USER_ID)
                .learnerName("Test Learner")
                .learnerEmail("test@example.com")
                .courseId(UUID.randomUUID())
                .courseTitle("Spring Boot")
                .percentComplete(25)
                .daysSinceLastWatch(4)
                .build();

        when(nudgeService.sendNudge(any())).thenReturn(true);

        mockMvc.perform(post("/ai/nudge")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("POST /ai/nudge → 200 with false when nudge fails")
    void sendNudge_fails() throws Exception {
        NudgeRequest request = NudgeRequest.builder()
                .userId(USER_ID)
                .learnerName("Test")
                .learnerEmail("test@example.com")
                .courseId(UUID.randomUUID())
                .courseTitle("Test Course")
                .percentComplete(10)
                .daysSinceLastWatch(5)
                .build();

        when(nudgeService.sendNudge(any())).thenReturn(false);

        mockMvc.perform(post("/ai/nudge")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }
}
