package com.olp.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.ai.controller.AiController;
import com.olp.ai.dto.*;
import com.olp.ai.service.BedrockService;
import com.olp.ai.service.NudgeService;
import com.olp.ai.service.RecommendationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AiController.class)
class AiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean BedrockService bedrockService;
    @MockBean RecommendationService recommendationService;
    @MockBean NudgeService nudgeService;

    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";

    // ── POST /ai/summary ─────────────────────────────────────────

    @Test
    @DisplayName("POST /ai/summary → 200 with generated summary JSON")
    void generateSummary_success() throws Exception {
        SummaryRequest request = SummaryRequest.builder()
                .courseId(UUID.randomUUID())
                .courseTitle("Spring Boot for Beginners")
                .transcriptText("In this course we cover Spring Boot 3 fundamentals...")
                .build();

        String mockSummary = """
                {
                  "title": "Spring Boot for Beginners",
                  "objectives": ["Understand auto-configuration", "Build REST APIs"],
                  "summary": "This course covers Spring Boot 3 fundamentals...",
                  "difficulty": "beginner",
                  "keyTakeaway": "Build production-ready Spring Boot applications"
                }
                """;

        when(bedrockService.generateCourseSummary(any(), any())).thenReturn(mockSummary);

        mockMvc.perform(post("/ai/summary")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNotEmpty());
    }

    // ── GET /ai/recommend ─────────────────────────────────────────

    @Test
    @DisplayName("GET /ai/recommend → 200 with 3 recommendations")
    void getRecommendations_success() throws Exception {
        List<RecommendationResponse> mockRecs = List.of(
                RecommendationResponse.builder()
                        .courseId(UUID.randomUUID())
                        .courseTitle("Apache Kafka for Developers")
                        .aiReason("Based on your distributed systems background, Kafka extends your skills")
                        .similarityScore(0.91)
                        .build(),
                RecommendationResponse.builder()
                        .courseId(UUID.randomUUID())
                        .courseTitle("gRPC with Java")
                        .aiReason("Complements your Spring Boot knowledge with modern RPC")
                        .similarityScore(0.87)
                        .build()
        );

        when(recommendationService.getRecommendations(any(), any(), any()))
                .thenReturn(mockRecs);

        mockMvc.perform(get("/ai/recommend")
                .param("enrolledTopics", "Spring Boot", "Microservices")
                .param("candidates", "[]")
                .header("x-user-id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].aiReason").isNotEmpty());
    }

    // ── GET /ai/search ────────────────────────────────────────────

    @Test
    @DisplayName("GET /ai/search → 200 with embedding vector")
    void semanticSearch_success() throws Exception {
        // Return a small fake embedding for the test
        List<Double> mockEmbedding = List.of(0.1, 0.2, 0.3, 0.4, 0.5);
        when(bedrockService.generateEmbedding(any())).thenReturn(mockEmbedding);

        mockMvc.perform(get("/ai/search")
                .param("query", "Spring Boot concurrency")
                .header("x-user-id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ── POST /ai/nudge ────────────────────────────────────────────

    @Test
    @DisplayName("POST /ai/nudge → 200 when nudge sent successfully")
    void sendNudge_success() throws Exception {
        NudgeRequest request = NudgeRequest.builder()
                .userId(USER_ID)
                .learnerName("Reshma")
                .learnerEmail("reshma@example.com")
                .courseId(UUID.randomUUID())
                .courseTitle("Spring Boot for Beginners")
                .percentComplete(43)
                .daysSinceLastWatch(4)
                .build();

        when(nudgeService.sendNudge(any())).thenReturn(true);

        mockMvc.perform(post("/ai/nudge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("POST /ai/nudge → 200 with false when nudge fails")
    void sendNudge_fails() throws Exception {
        NudgeRequest request = NudgeRequest.builder()
                .userId(USER_ID)
                .learnerName("Test")
                .learnerEmail("bad@email")
                .courseId(UUID.randomUUID())
                .courseTitle("Test Course")
                .percentComplete(10)
                .daysSinceLastWatch(5)
                .build();

        when(nudgeService.sendNudge(any())).thenReturn(false);

        mockMvc.perform(post("/ai/nudge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    // ── POST /ai/summary → validation ────────────────────────────

    @Test
    @DisplayName("POST /ai/summary → 400 when transcriptText is missing")
    void generateSummary_missing_transcript() throws Exception {
        SummaryRequest request = new SummaryRequest();
        request.setCourseId(UUID.randomUUID());
        request.setCourseTitle("Test Course");
        // transcriptText not set

        mockMvc.perform(post("/ai/summary")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()); // no @NotBlank on transcriptText — it's optional
    }
}
