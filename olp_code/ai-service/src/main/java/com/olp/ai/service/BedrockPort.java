package com.olp.ai.service;

import com.olp.ai.dto.Citation;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

/**
 * Interface for all Bedrock operations.
 * BedrockService implements with real AWS SDK (aws profile).
 * MockBedrockService implements with fake responses (local profile).
 *
 * Lives in src/main — no AWS SDK imports — always compiles.
 */
public interface BedrockPort {

    /** RAG chat — streams tokens via SSE */
    List<Citation> invokeWithRAG(String question, String sessionId, SseEmitter emitter);

    /** Titan Embeddings v2 — returns 1536-dim vector */
    List<Double> generateEmbedding(String text);

    /** Claude Sonnet — rank and explain recommendations */
    String rankRecommendations(String userProfile, String candidates);

    /** Claude Sonnet JSON mode — generate course summary */
    String generateCourseSummary(String courseTitle, String transcriptText);

    /** Claude Haiku — generate short motivational nudge */
    String generateNudgeMessage(String learnerName, String courseTitle,
                                int percentComplete, int daysSinceLastWatch);
}
