package com.olp.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.UUID;

// ── Semantic Search ───────────────────────────────────────────────

/** Request for GET /ai/search?query=... */
@Data
public class SearchRequest {

    @NotBlank(message = "Search query is required")
    @Size(max = 500, message = "Query cannot exceed 500 characters")
    private String query;
}
