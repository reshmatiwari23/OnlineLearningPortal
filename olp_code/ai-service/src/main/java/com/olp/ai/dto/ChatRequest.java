package com.olp.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.List;
import java.util.UUID;

// ── Chat (RAG Course Assistant) ───────────────────────────────────

/**
 * Request for POST /ai/chat
 * Sent by the learner when they type a question in the AI chat drawer.
 */
@Data
public class ChatRequest {

    @NotBlank(message = "Question is required")
    @Size(max = 1000, message = "Question cannot exceed 1000 characters")
    private String question;

    // The course whose transcript the question is about
    private UUID courseId;

    // Session ID for multi-turn conversation context
    // If null, a new session is started
    private String sessionId;
}
