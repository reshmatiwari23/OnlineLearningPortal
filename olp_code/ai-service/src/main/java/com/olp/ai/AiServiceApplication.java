package com.olp.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Online Learning Portal — AI Service
 *
 * THE ONLY service with AWS Bedrock IAM permissions.
 * All Gen AI features in the platform flow through here.
 *
 * Features:
 *   POST /ai/chat       — RAG Course Assistant (Claude Sonnet, SSE streaming)
 *   GET  /ai/recommend  — Personalised Recommendations (Titan + Claude Sonnet)
 *   GET  /ai/search     — Semantic Search (Titan Embeddings v2)
 *   POST /ai/summary    — Auto Course Summary (Claude Sonnet, JSON mode)
 *   POST /ai/nudge      — Progress Nudges (Claude Haiku — 20x cheaper)
 *
 * Port: 8085
 * Models: Claude Sonnet 4.6, Claude Haiku 4.5, Titan Embeddings v2
 * Region: us-east-1 (Bedrock), ap-south-1 (DynamoDB, SES)
 */
@SpringBootApplication
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
