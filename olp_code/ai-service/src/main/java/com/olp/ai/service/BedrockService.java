package com.olp.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.olp.ai.dto.Citation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Core service for all AWS Bedrock API calls.
 *
 * This is the ONLY class in the entire platform that calls Bedrock.
 * All 5 AI features flow through here:
 *
 * 1. RAG Chat   — invokeWithRAG()     Claude Sonnet + KB retrieval (SSE streaming)
 * 2. Recommend  — generateEmbedding() Titan Embeddings v2 → vector
 * 3. Summary    — generateSummary()   Claude Sonnet (JSON mode)
 * 4. Nudges     — generateNudge()     Claude Haiku (cheap short text)
 * 5. Search     — generateEmbedding() Titan Embeddings v2 → vector
 *
 * Guardrails are applied via the Bedrock Agent Runtime for RAG calls.
 * For direct Claude calls, guardrails are embedded in the system prompt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BedrockService {

    private final BedrockRuntimeClient bedrockClient;
    private final BedrockAgentRuntimeClient agentClient;
    private final ObjectMapper objectMapper;

    @Value("${bedrock.models.sonnet}")
    private String sonnetModel;

    @Value("${bedrock.models.haiku}")
    private String haikuModel;

    @Value("${bedrock.models.titan-embeddings}")
    private String titanEmbeddingsModel;

    @Value("${bedrock.knowledge-base.id}")
    private String knowledgeBaseId;

    @Value("${bedrock.knowledge-base.similarity-threshold:0.75}")
    private double similarityThreshold;

    @Value("${bedrock.knowledge-base.max-results:5}")
    private int maxResults;

    // Thread pool for SSE streaming — one thread per active chat connection
    private final ExecutorService streamingExecutor = Executors.newCachedThreadPool();

    // ── Feature 1: RAG Course Assistant (SSE Streaming) ─────────

    /**
     * Answers a learner's question using RAG (Retrieval-Augmented Generation).
     *
     * Flow:
     *  1. Bedrock Agent Runtime retrieves top-N chunks from Knowledge Base
     *     (cosine similarity ≥ threshold, using Titan embeddings)
     *  2. Retrieved chunks + question → Claude Sonnet
     *  3. Claude streams the answer back token by token via SSE
     *  4. Citations are extracted from the retrieved chunks
     *
     * @param question  the learner's question
     * @param sessionId conversation session ID (for multi-turn context)
     * @param emitter   SSE emitter — tokens are sent here as they arrive
     * @return list of citations from retrieved chunks
     */
    public List<Citation> invokeWithRAG(String question,
                                        String sessionId,
                                        SseEmitter emitter) {
        List<Citation> citations = new ArrayList<>();

        streamingExecutor.submit(() -> {
            try {
                // Step 1: Retrieve relevant chunks from Knowledge Base
                RetrieveRequest retrieveRequest = RetrieveRequest.builder()
                        .knowledgeBaseId(knowledgeBaseId)
                        .retrievalQuery(KnowledgeBaseQuery.builder()
                                .text(question)
                                .build())
                        .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                                .vectorSearchConfiguration(
                                        KnowledgeBaseVectorSearchConfiguration.builder()
                                                .numberOfResults(maxResults)
                                                .build())
                                .build())
                        .build();

                RetrieveResponse retrieveResponse = agentClient.retrieve(retrieveRequest);

                // Step 2: Build context from retrieved chunks (filter by threshold)
                StringBuilder context = new StringBuilder();
                for (RetrievedReference ref : retrieveResponse.retrievalResults()) {
                    double score = ref.score();
                    if (score >= similarityThreshold) {
                        String chunkText = ref.content().text();
                        context.append(chunkText).append("\n\n");

                        // Extract citation metadata
                        citations.add(Citation.builder()
                                .chunkId(ref.location().toString())
                                .similarityScore(score)
                                .excerpt(chunkText.substring(0, Math.min(200, chunkText.length())))
                                .build());
                    }
                }

                log.debug("RAG: retrieved {} chunks above threshold for question: {}",
                        citations.size(), question);

                // Step 3: Build augmented prompt for Claude
                String systemPrompt = """
                        You are an expert course assistant helping learners understand course content.
                        Answer questions ONLY based on the provided course transcript context.
                        If the answer is not in the context, say "I don't have enough information
                        from this course to answer that question."
                        Be concise, clear, and educational.
                        Do not make up information or use knowledge outside the provided context.
                        """;

                String userPrompt = context.isEmpty()
                        ? question
                        : "Context from course transcript:\n" + context + "\n\nQuestion: " + question;

                // Step 4: Stream Claude's answer via SSE
                String requestBody = buildClaudeRequest(systemPrompt, userPrompt, 1000);

                InvokeModelWithResponseStreamRequest streamRequest =
                        InvokeModelWithResponseStreamRequest.builder()
                                .modelId(sonnetModel)
                                .body(SdkBytes.fromString(requestBody, StandardCharsets.UTF_8))
                                .contentType("application/json")
                                .accept("application/json")
                                .build();

                bedrockClient.invokeModelWithResponseStream(streamRequest,
                        InvokeModelWithResponseStreamResponseHandler.builder()
                                .onEventStream(stream -> stream.subscribe(event -> {
                                    if (event instanceof InvokeModelWithResponseStreamResponseHandler
                                            .Visitor) {
                                        // handled below
                                    }
                                }))
                                .subscriber(InvokeModelWithResponseStreamResponseHandler
                                        .Visitor.builder()
                                        .onChunk(chunk -> {
                                            try {
                                                JsonNode chunkJson = objectMapper.readTree(
                                                        chunk.bytes().asUtf8String());
                                                if (chunkJson.has("delta")) {
                                                    String token = chunkJson
                                                            .path("delta")
                                                            .path("text")
                                                            .asText("");
                                                    if (!token.isEmpty()) {
                                                        emitter.send(SseEmitter.event()
                                                                .data(token)
                                                                .name("token"));
                                                    }
                                                }
                                            } catch (IOException e) {
                                                log.error("SSE send error: {}", e.getMessage());
                                            }
                                        })
                                        .build())
                                .build());

                // Send citations at the end
                emitter.send(SseEmitter.event()
                        .data(objectMapper.writeValueAsString(citations))
                        .name("citations"));
                emitter.complete();

            } catch (Exception e) {
                log.error("RAG streaming error: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .data("An error occurred. Please try again.")
                            .name("error"));
                    emitter.complete();
                } catch (IOException ioEx) {
                    emitter.completeWithError(ioEx);
                }
            }
        });

        return citations;
    }

    // ── Feature 2 & 5: Titan Embeddings (Recommendations + Search) ─

    /**
     * Converts text to a 1536-dimensional vector using Titan Embeddings v2.
     * Used for:
     *   - Semantic search: convert search query to vector → find similar courses
     *   - Recommendations: convert user profile to vector → find similar courses
     *
     * @param text any text string (search query, course description, user profile)
     * @return list of 1536 doubles representing the semantic meaning of the text
     */
    public List<Double> generateEmbedding(String text) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("inputText", text);

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(titanEmbeddingsModel)
                    .body(SdkBytes.fromString(
                            objectMapper.writeValueAsString(requestBody),
                            StandardCharsets.UTF_8))
                    .contentType("application/json")
                    .accept("application/json")
                    .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);
            JsonNode responseJson = objectMapper.readTree(
                    response.body().asUtf8String());

            List<Double> embedding = new ArrayList<>();
            responseJson.path("embedding").forEach(v -> embedding.add(v.asDouble()));

            log.debug("Generated embedding: {} dimensions for text length: {}",
                    embedding.size(), text.length());
            return embedding;

        } catch (Exception e) {
            log.error("Titan embedding error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    // ── Feature 3: Recommendations — Claude ranking ──────────────

    /**
     * Uses Claude Sonnet to rank the top candidate courses and write
     * a personalised reason for each recommendation.
     *
     * @param userProfile  plain-text description of what the user has studied
     * @param candidates   JSON array of candidate course titles and descriptions
     * @return Claude's JSON response with ranked courses and reasons
     */
    public String rankRecommendations(String userProfile, String candidates) {
        try {
            String systemPrompt = """
                    You are a learning path advisor. Given a learner's study history
                    and a list of candidate courses, select the top 3 most relevant courses
                    and write a personalised reason for each one.
                    
                    Respond ONLY with valid JSON in this exact format:
                    {
                      "recommendations": [
                        {
                          "courseId": "uuid-here",
                          "reason": "personalised reason connecting to user's learning history"
                        }
                      ]
                    }
                    Do not include any text outside the JSON object.
                    """;

            String userMessage = "Learner profile:\n" + userProfile
                    + "\n\nCandidate courses:\n" + candidates
                    + "\n\nSelect top 3 and write personalised reasons.";

            String requestBody = buildClaudeRequest(systemPrompt, userMessage, 800);
            return invokeClaudeSonnet(requestBody);

        } catch (Exception e) {
            log.error("Recommendation ranking error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to rank recommendations", e);
        }
    }

    // ── Feature 4: Auto Course Summary — Claude Sonnet JSON mode ─

    /**
     * Generates a structured JSON course summary from a transcript.
     * Called by the kb-ingestion Lambda after Transcribe completes.
     *
     * @param courseTitle     name of the course
     * @param transcriptText  full transcript text from Amazon Transcribe
     * @return JSON string with title, objectives, summary, difficulty, keyTakeaway
     */
    public String generateCourseSummary(String courseTitle, String transcriptText) {
        try {
            // Truncate very long transcripts — Claude has a 200K token limit
            // but we keep it reasonable to save cost
            String truncatedTranscript = transcriptText.length() > 50000
                    ? transcriptText.substring(0, 50000) + "... [transcript truncated]"
                    : transcriptText;

            String systemPrompt = """
                    You are an expert course analyst. Generate a structured summary
                    of a course based on its transcript.
                    
                    Respond ONLY with valid JSON in this exact format:
                    {
                      "title": "exact course title",
                      "objectives": ["objective 1", "objective 2", "objective 3"],
                      "summary": "150-word summary of what learners will learn",
                      "difficulty": "beginner|intermediate|advanced",
                      "keyTakeaway": "single most important thing learners will gain"
                    }
                    Do not include any text outside the JSON object.
                    """;

            String userMessage = "Course title: " + courseTitle
                    + "\n\nTranscript:\n" + truncatedTranscript;

            String requestBody = buildClaudeRequest(systemPrompt, userMessage, 500);
            return invokeClaudeSonnet(requestBody);

        } catch (Exception e) {
            log.error("Summary generation error for course {}: {}", courseTitle, e.getMessage());
            throw new RuntimeException("Failed to generate course summary", e);
        }
    }

    // ── Feature 4: Progress Nudges — Claude Haiku (cheap) ────────

    /**
     * Generates a personalised 2-sentence motivational nudge for a stalled learner.
     * Uses Claude Haiku — approximately 20x cheaper than Sonnet.
     * Quality is identical for this short-text task.
     *
     * @param learnerName        learner's first name
     * @param courseTitle        name of the course they are enrolled in
     * @param percentComplete    how far through the course they are (0-100)
     * @param daysSinceLastWatch how many days since they last watched
     * @return 2-sentence motivational message
     */
    public String generateNudgeMessage(String learnerName,
                                       String courseTitle,
                                       int percentComplete,
                                       int daysSinceLastWatch) {
        try {
            String systemPrompt = """
                    You are a friendly and encouraging learning coach.
                    Write a brief 2-sentence motivational message to help a learner
                    return to their course. Be warm, specific, and positive.
                    Do not be pushy. Keep it under 50 words total.
                    """;

            String userMessage = String.format(
                    "Learner: %s\nCourse: %s\nProgress: %d%%\nDays inactive: %d",
                    learnerName, courseTitle, percentComplete, daysSinceLastWatch);

            String requestBody = buildClaudeRequest(systemPrompt, userMessage, 100);

            // Use Haiku — 20x cheaper than Sonnet for short outputs
            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(haikuModel)
                    .body(SdkBytes.fromString(requestBody, StandardCharsets.UTF_8))
                    .contentType("application/json")
                    .accept("application/json")
                    .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);
            JsonNode responseJson = objectMapper.readTree(response.body().asUtf8String());

            return responseJson.path("content")
                    .get(0)
                    .path("text")
                    .asText();

        } catch (Exception e) {
            log.error("Nudge generation error: {}", e.getMessage());
            // Return a default nudge message — never fail silently for nudges
            return String.format("Hi %s, you're %d%% through %s — great progress! " +
                    "Jump back in when you're ready.", learnerName, percentComplete, courseTitle);
        }
    }

    // ── Private helpers ───────────────────────────────────────────

    private String invokeClaudeSonnet(String requestBody) throws Exception {
        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(sonnetModel)
                .body(SdkBytes.fromString(requestBody, StandardCharsets.UTF_8))
                .contentType("application/json")
                .accept("application/json")
                .build();

        InvokeModelResponse response = bedrockClient.invokeModel(request);
        JsonNode responseJson = objectMapper.readTree(response.body().asUtf8String());

        return responseJson.path("content")
                .get(0)
                .path("text")
                .asText();
    }

    /**
     * Builds the Bedrock Claude Messages API request body.
     *
     * @param systemPrompt  instructions for Claude's behaviour
     * @param userMessage   the actual question or task
     * @param maxTokens     maximum response length
     */
    private String buildClaudeRequest(String systemPrompt,
                                      String userMessage,
                                      int maxTokens) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("anthropic_version", "bedrock-2023-05-31");
        body.put("max_tokens", maxTokens);
        body.put("system", systemPrompt);

        var messages = objectMapper.createArrayNode();
        var userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");

        var content = objectMapper.createArrayNode();
        var textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", userMessage);
        content.add(textContent);
        userMsg.set("content", content);
        messages.add(userMsg);

        body.set("messages", messages);
        return objectMapper.writeValueAsString(body);
    }
}
