package com.olp.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.olp.ai.dto.Citation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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

@Service
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class BedrockService implements BedrockPort {

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

    private final ExecutorService streamingExecutor = Executors.newCachedThreadPool();

    /**
     * Checks if Knowledge Base is properly configured.
     * Returns false if KB ID is placeholder or empty.
     */
    private boolean isKbConfigured() {
        return knowledgeBaseId != null
                && !knowledgeBaseId.isEmpty()
                && !knowledgeBaseId.equals("placeholder");
    }

    @Override
    public List<Citation> invokeWithRAG(String question, String sessionId, SseEmitter emitter) {
        List<Citation> citations = new ArrayList<>();

        streamingExecutor.submit(() -> {
            try {
                String context = "";

                // Only query KB if properly configured
                if (isKbConfigured()) {
                    try {
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
                        StringBuilder contextBuilder = new StringBuilder();

                        for (KnowledgeBaseRetrievalResult result : retrieveResponse.retrievalResults()) {
                            Double score = result.score();
                            if (score != null && score >= similarityThreshold) {
                                String chunkText = result.content().text();
                                contextBuilder.append(chunkText).append("\n\n");
                                citations.add(Citation.builder()
                                        .chunkId(result.location().toString())
                                        .similarityScore(score)
                                        .excerpt(chunkText.substring(0, Math.min(200, chunkText.length())))
                                        .build());
                            }
                        }
                        context = contextBuilder.toString();
                    } catch (Exception e) {
                        log.warn("KB query failed, falling back to direct Claude: {}", e.getMessage());
                    }
                } else {
                    log.info("Knowledge Base not configured — using Claude directly");
                }

                // Build prompt — use KB context if available, otherwise answer directly
                String systemPrompt;
                String userPrompt;

                if (!context.isEmpty()) {
                    systemPrompt = "You are an expert course assistant. Answer questions based on the provided course transcript context. Be helpful and concise.";
                    userPrompt = "Context:\n" + context + "\n\nQuestion: " + question;
                } else {
                    systemPrompt = "You are an expert AWS and cloud computing course assistant. Answer questions about AWS services, DevOps practices, and cloud computing concepts. Be helpful, accurate and concise. If asked about specific course content you don't have access to, provide general expert knowledge on the topic.";
                    userPrompt = question;
                }

                String requestBody = buildClaudeRequest(systemPrompt, userPrompt, 1000);

                InvokeModelRequest invokeRequest = InvokeModelRequest.builder()
                        .modelId(sonnetModel)
                        .body(SdkBytes.fromString(requestBody, StandardCharsets.UTF_8))
                        .contentType("application/json")
                        .accept("application/json")
                        .build();

                InvokeModelResponse invokeResponse = bedrockClient.invokeModel(invokeRequest);
                JsonNode responseJson = objectMapper.readTree(invokeResponse.body().asUtf8String());
                String responseText = responseJson.path("content").get(0).path("text").asText();

                // Stream response word by word
                for (String word : responseText.split(" ")) {
                    emitter.send(SseEmitter.event().data(word + " ").name("token"));
                }

                emitter.send(SseEmitter.event()
                        .data(objectMapper.writeValueAsString(citations))
                        .name("citations"));
                emitter.complete();

            } catch (Exception e) {
                log.error("RAG error: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().data("Error occurred").name("error"));
                    emitter.complete();
                } catch (IOException ioEx) {
                    emitter.completeWithError(ioEx);
                }
            }
        });

        return citations;
    }

    @Override
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
            JsonNode responseJson = objectMapper.readTree(response.body().asUtf8String());
            List<Double> embedding = new ArrayList<>();
            responseJson.path("embedding").forEach(v -> embedding.add(v.asDouble()));
            return embedding;

        } catch (Exception e) {
            log.error("Titan embedding error: {}", e.getMessage());
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    @Override
    public String rankRecommendations(String userProfile, String candidates) {
        try {
            String systemPrompt = "You are a learning path advisor. Select top 3 courses. Respond ONLY with JSON: {\"recommendations\":[{\"courseId\":\"uuid\",\"reason\":\"reason\"}]}";
            String requestBody = buildClaudeRequest(systemPrompt,
                    "Learner profile:\n" + userProfile + "\n\nCandidates:\n" + candidates, 800);
            return invokeClaudeSonnet(requestBody);
        } catch (Exception e) {
            throw new RuntimeException("Failed to rank recommendations", e);
        }
    }

    @Override
    public String generateCourseSummary(String courseTitle, String transcriptText) {
        try {
            String truncated = transcriptText.length() > 50000
                    ? transcriptText.substring(0, 50000) + "... [truncated]"
                    : transcriptText;
            String systemPrompt = "Generate a course summary. Respond ONLY with JSON: {\"title\":\"...\",\"objectives\":[],\"summary\":\"...\",\"difficulty\":\"beginner|intermediate|advanced\",\"keyTakeaway\":\"...\"}";
            String requestBody = buildClaudeRequest(systemPrompt,
                    "Course: " + courseTitle + "\n\nTranscript:\n" + truncated, 500);
            return invokeClaudeSonnet(requestBody);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate summary", e);
        }
    }

    @Override
    public String generateNudgeMessage(String learnerName, String courseTitle,
                                       int percentComplete, int daysSinceLastWatch) {
        try {
            String systemPrompt = "Write a 2-sentence motivational message. Be warm and encouraging. Under 50 words.";
            String userMessage = String.format(
                    "Learner: %s\nCourse: %s\nProgress: %d%%\nDays inactive: %d",
                    learnerName, courseTitle, percentComplete, daysSinceLastWatch);
            String requestBody = buildClaudeRequest(systemPrompt, userMessage, 100);

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(haikuModel)
                    .body(SdkBytes.fromString(requestBody, StandardCharsets.UTF_8))
                    .contentType("application/json")
                    .accept("application/json")
                    .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);
            JsonNode json = objectMapper.readTree(response.body().asUtf8String());
            return json.path("content").get(0).path("text").asText();

        } catch (Exception e) {
            return String.format("Hi %s, you're %d%% through %s — keep going!",
                    learnerName, percentComplete, courseTitle);
        }
    }

    private String invokeClaudeSonnet(String requestBody) throws Exception {
        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(sonnetModel)
                .body(SdkBytes.fromString(requestBody, StandardCharsets.UTF_8))
                .contentType("application/json")
                .accept("application/json")
                .build();
        InvokeModelResponse response = bedrockClient.invokeModel(request);
        JsonNode json = objectMapper.readTree(response.body().asUtf8String());
        return json.path("content").get(0).path("text").asText();
    }

    private String buildClaudeRequest(String systemPrompt, String userMessage,
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
