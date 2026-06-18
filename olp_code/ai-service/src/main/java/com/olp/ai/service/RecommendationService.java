package com.olp.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.ai.dto.RecommendationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates personalised course recommendations using semantic similarity.
 *
 * Flow:
 *  1. Check Redis cache (rec:{userId}, TTL 1h)
 *  2. Cache miss: build user profile from enrolled course topics
 *  3. Embed profile using Titan Embeddings v2 → 1536-dim vector
 *  4. kNN search in OpenSearch for top-10 unenrolled similar courses
 *  5. Claude Sonnet ranks top-3 and writes personalised reasons
 *  6. Cache result in Redis
 *  7. Cache invalidated on new enrollment
 *
 * Why semantic and not collaborative filtering:
 * Semantic matching compares the CONTENT of what a learner studied
 * to the CONTENT of courses they haven't taken.
 * "You studied concurrency + distributed systems → here are Kafka,
 *  gRPC, and service mesh courses" — not "other users like you took X".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final BedrockPort bedrockService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${cache.recommendations-ttl-minutes:60}")
    private long cacheTtlMinutes;

    private String cacheKey(String userId) {
        return "rec:" + userId;
    }

    /**
     * Get personalised recommendations for a user.
     * Checks Redis first — only calls Bedrock on cache miss.
     *
     * @param userId         the learner's UUID
     * @param enrolledTopics list of topic descriptions from completed/in-progress courses
     * @param candidateCourses JSON string of courses the user is NOT enrolled in
     * @return top 3 recommended courses with personalised AI reasons
     */
    public List<RecommendationResponse> getRecommendations(
            String userId,
            List<String> enrolledTopics,
            String candidateCourses) {

        String cacheKey = cacheKey(userId);

        // Step 1: Check Redis cache
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Recommendation cache hit for userId: {}", userId);
            return parseRecommendations(cached);
        }

        log.debug("Recommendation cache miss for userId: {}", userId);

        // Step 2: Build user profile as plain text
        String userProfile = "The learner has studied: "
                + String.join(", ", enrolledTopics);

        // Step 3 + 4 + 5: Embed profile, kNN search, Claude ranking
        // (kNN search happens in BedrockService via Knowledge Base)
        String claudeResponse = bedrockService.rankRecommendations(
                userProfile, candidateCourses);

        // Step 6: Cache the result
        redisTemplate.opsForValue().set(
                cacheKey,
                claudeResponse,
                Duration.ofMinutes(cacheTtlMinutes));

        log.info("Recommendations generated and cached for userId: {}", userId);
        return parseRecommendations(claudeResponse);
    }

    /**
     * Invalidate the recommendation cache for a user.
     * Called when the user enrols in a new course —
     * their profile has changed so the recommendations are stale.
     */
    public void invalidateCache(String userId) {
        redisTemplate.delete(cacheKey(userId));
        log.debug("Recommendation cache invalidated for userId: {}", userId);
    }

    private List<RecommendationResponse> parseRecommendations(String json) {
        List<RecommendationResponse> results = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode recs = root.path("recommendations");
            recs.forEach(rec -> results.add(
                    RecommendationResponse.builder()
                            .courseId(UUID.fromString(rec.path("courseId").asText()))
                            .aiReason(rec.path("reason").asText())
                            .build()
            ));
        } catch (Exception e) {
            log.error("Failed to parse recommendation response: {}", e.getMessage());
        }
        return results;
    }
}
