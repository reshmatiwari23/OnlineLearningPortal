package com.olp.ai.config.local;

import com.olp.ai.dto.Citation;
import com.olp.ai.service.BedrockPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mock Bedrock — active on local profile only.
 * Implements BedrockPort directly — no AWS SDK needed.
 * Returns fake but realistic responses for local testing.
 */
@Service
@Primary
@Profile("local")
@Slf4j
public class MockBedrockService implements BedrockPort {

    @Override
    public List<Citation> invokeWithRAG(String question, String sessionId, SseEmitter emitter) {
        log.info("[LOCAL MOCK] Bedrock RAG chat: {}", question);
        try {
            String mockAnswer = "This is a mock AI response for local testing. " +
                    "In production, Claude Sonnet would answer your question: \"" + question + "\" " +
                    "based on the course transcript content from the Bedrock Knowledge Base.";

            for (String word : mockAnswer.split(" ")) {
                emitter.send(SseEmitter.event().data(word + " ").name("token"));
                Thread.sleep(30);
            }
            emitter.send(SseEmitter.event()
                    .data("[{\"chunkId\":\"mock-chunk-1\",\"similarityScore\":0.92," +
                            "\"excerpt\":\"Mock transcript excerpt for local testing\"}]")
                    .name("citations"));
            emitter.complete();
        } catch (Exception e) {
            log.error("Mock SSE error: {}", e.getMessage());
            emitter.completeWithError(e);
        }
        return List.of();
    }

    @Override
    public List<Double> generateEmbedding(String text) {
        log.info("[LOCAL MOCK] Titan embedding for text length: {}", text.length());
        Random rng = new Random(text.hashCode());
        List<Double> embedding = new ArrayList<>();
        for (int i = 0; i < 1536; i++) {
            embedding.add(rng.nextDouble() * 2 - 1);
        }
        return embedding;
    }

    @Override
    public String rankRecommendations(String userProfile, String candidates) {
        log.info("[LOCAL MOCK] Claude ranking recommendations");
        return "{\"recommendations\":[" +
                "{\"courseId\":\"00000000-0000-0000-0000-000000000001\"," +
                "\"reason\":\"Mock recommendation based on your profile\"}]}";
    }

    @Override
    public String generateCourseSummary(String courseTitle, String transcriptText) {
        log.info("[LOCAL MOCK] Claude summary for: {}", courseTitle);
        return "{\"title\":\"" + courseTitle + "\"," +
                "\"objectives\":[\"Understand core concepts\",\"Build practical skills\"]," +
                "\"summary\":\"This is a mock AI-generated summary for local testing. " +
                "In production Claude Sonnet would generate a real summary from the transcript.\"," +
                "\"difficulty\":\"intermediate\"," +
                "\"keyTakeaway\":\"Mock key takeaway for local testing\"}";
    }

    @Override
    public String generateNudgeMessage(String learnerName, String courseTitle,
                                       int percentComplete, int daysSinceLastWatch) {
        log.info("[LOCAL MOCK] Claude Haiku nudge for: {}", learnerName);
        return String.format("Hi %s, you're %d%% through %s — great progress! " +
                "Come back and keep the momentum going.", learnerName, percentComplete, courseTitle);
    }
}
