package com.olp.ai.dto;

import lombok.*;
import java.util.List;
import java.util.UUID;

// ── Chat response (non-streaming) ────────────────────────────────

/**
 * Response for non-streaming chat requests.
 * For streaming responses, tokens are sent as SSE events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String answer;
    private String sessionId;
    private List<Citation> citations;
}
