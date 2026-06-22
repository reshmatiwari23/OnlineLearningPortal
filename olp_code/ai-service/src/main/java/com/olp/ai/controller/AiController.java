package com.olp.ai.controller;

import com.olp.ai.dto.*;
import com.olp.ai.service.BedrockPort;
import com.olp.ai.service.NudgeService;
import com.olp.ai.service.RecommendationService;
import com.olp.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final BedrockPort bedrockService;
    private final RecommendationService recommendationService;
    private final NudgeService nudgeService;

    @Value("${course.service.url:http://olp-alb-1897172403.ap-south-1.elb.amazonaws.com}")
    private String courseServiceUrl;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @Valid @RequestBody          ChatRequest request,
            @RequestHeader("x-user-id")   String userId,
            @RequestHeader("x-user-role") String userRole) {

        log.info("Chat request: userId={}, courseId={}, sessionId={}",
                userId, request.getCourseId(), request.getSessionId());

        SseEmitter emitter = new SseEmitter(300_000L);

        bedrockService.invokeWithRAG(
                request.getQuestion(),
                request.getSessionId(),
                emitter
        );

        return emitter;
    }

    @GetMapping("/recommend")
    public ResponseEntity<ApiResponse<List<RecommendationResponse>>> getRecommendations(
            @RequestHeader("x-user-id")   String userId,
            @RequestParam List<String>     enrolledTopics,
            @RequestParam String           candidates) {

        log.info("Recommendation request for userId: {}", userId);
        List<RecommendationResponse> recommendations =
                recommendationService.getRecommendations(userId, enrolledTopics, candidates);

        return ResponseEntity.ok(ApiResponse.success(recommendations));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Double>>> semanticSearch(
            @RequestParam String query,
            @RequestHeader("x-user-id") String userId) {

        log.info("Semantic search: query='{}', userId={}", query, userId);
        List<Double> embedding = bedrockService.generateEmbedding(query);
        return ResponseEntity.ok(ApiResponse.success(embedding));
    }

    @PostMapping("/summary")
    public ResponseEntity<ApiResponse<String>> generateSummary(
            @Valid @RequestBody SummaryRequest request) {

        log.info("Summary generation for courseId: {}", request.getCourseId());
        String summary = bedrockService.generateCourseSummary(
                request.getCourseTitle(),
                request.getTranscriptText()
        );

        // Save summary back to course-service
        try {
            HttpClient client = HttpClient.newHttpClient();

            // Escape the summary JSON for embedding in another JSON object
            String escapedSummary = summary
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

            String body = "{\"aiSummary\": \"" + escapedSummary + "\", \"kbIngested\": true}";

            HttpRequest saveRequest = HttpRequest.newBuilder()
                .uri(URI.create(courseServiceUrl + "/api/courses/" + request.getCourseId() + "/ai-summary"))
                .header("Content-Type", "application/json")
                .header("x-internal-service", "ai-service")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = client.send(saveRequest,
                HttpResponse.BodyHandlers.ofString());
            log.info("Summary saved to course: {}, status: {}",
                request.getCourseId(), response.statusCode());

        } catch (Exception e) {
            log.warn("Could not save summary to course-service: {}", e.getMessage());
        }

        return ResponseEntity.ok(ApiResponse.success(summary, "Summary generated"));
    }

    @PostMapping("/nudge")
    public ResponseEntity<ApiResponse<Boolean>> sendNudge(
            @Valid @RequestBody NudgeRequest request) {

        log.info("Nudge request for learner: {}, course: {}",
                request.getLearnerEmail(), request.getCourseTitle());

        boolean sent = nudgeService.sendNudge(request);
        return ResponseEntity.ok(ApiResponse.success(sent,
                sent ? "Nudge sent successfully" : "Nudge failed — check logs"));
    }
}
