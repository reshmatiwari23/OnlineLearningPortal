package com.olp.ai.controller;

import com.olp.ai.dto.*;
import com.olp.ai.service.BedrockPort;
import com.olp.ai.service.NudgeService;
import com.olp.ai.service.RecommendationService;
import com.olp.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * REST controller for all AI/Bedrock features.
 *
 * All endpoints are prefixed with /ai.
 * API Gateway routes /ai/* to this service (port 8085).
 *
 * Endpoints:
 *   POST /ai/chat              — RAG course assistant (SSE streaming)
 *   GET  /ai/recommend         — personalised course recommendations
 *   GET  /ai/search            — semantic course search
 *   POST /ai/summary           — auto course summary (internal — Lambda calls this)
 *   POST /ai/nudge             — send progress nudge (internal — scheduler calls this)
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final BedrockPort bedrockService;
    private final RecommendationService recommendationService;
    private final NudgeService nudgeService;

    /**
     * RAG Course Assistant — streams Claude's answer token by token.
     *
     * Response type: text/event-stream (SSE)
     * Events:
     *   - "token"     — each word/token as Claude generates it
     *   - "citations" — JSON array of citations at the end
     *   - "error"     — if something goes wrong
     *
     * The browser receives each token immediately — learner sees
     * the answer being written in real time, like ChatGPT.
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @Valid @RequestBody          ChatRequest request,
            @RequestHeader("x-user-id")   String userId,
            @RequestHeader("x-user-role") String userRole) {

        log.info("Chat request: userId={}, courseId={}, sessionId={}",
                userId, request.getCourseId(), request.getSessionId());

        // Timeout set to 5 minutes — generous for long AI responses
        SseEmitter emitter = new SseEmitter(300_000L);

        // BedrockService handles streaming on a separate thread
        bedrockService.invokeWithRAG(
                request.getQuestion(),
                request.getSessionId(),
                emitter
        );

        return emitter;
    }

    /**
     * Get personalised course recommendations.
     * Results are cached in Redis for 1 hour per user.
     *
     * Query params:
     *   enrolledTopics — comma-separated list of topics from completed courses
     *   candidates     — JSON string of candidate courses (passed from course-service)
     */
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

    /**
     * Semantic course search using Titan Embeddings.
     * Converts query to a vector and finds semantically similar courses.
     * Results cached in Redis for 30 minutes per query.
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Double>>> semanticSearch(
            @RequestParam String query,
            @RequestHeader("x-user-id") String userId) {

        log.info("Semantic search: query='{}', userId={}", query, userId);

        // Generate embedding for the search query
        // The caller (frontend) sends this to OpenSearch for kNN search
        // This keeps OpenSearch access in the ai-service only
        List<Double> embedding = bedrockService.generateEmbedding(query);

        return ResponseEntity.ok(ApiResponse.success(embedding));
    }

    /**
     * Generate an AI course summary from a transcript.
     * INTERNAL endpoint — called by the kb-ingestion Lambda, not by the frontend.
     * The Lambda sends the transcript text after Transcribe completes.
     */
    @PostMapping("/summary")
    public ResponseEntity<ApiResponse<String>> generateSummary(
            @Valid @RequestBody SummaryRequest request) {

        log.info("Summary generation for courseId: {}", request.getCourseId());
        String summary = bedrockService.generateCourseSummary(
                request.getCourseTitle(),
                request.getTranscriptText()
        );

        return ResponseEntity.ok(ApiResponse.success(summary, "Summary generated"));
    }

    /**
     * Send a personalised progress nudge to a stalled learner.
     * INTERNAL endpoint — called by the daily EventBridge scheduler.
     * Not exposed to the public internet via API Gateway.
     */
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
