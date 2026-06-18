package com.olp.progress.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Handles all Redis operations for progress tracking.
 *
 * Redis key format:
 *   progress:{userId}:{courseId}  →  percentComplete (integer string, e.g. "43")
 *
 * TTL: 60 seconds.
 * If a learner pauses for more than 60 seconds, the key expires.
 * On the next timeupdate event, the key is written again.
 * RDS is always the source of truth — Redis is just the fast write buffer.
 *
 * Why StringRedisTemplate:
 * We store progress as a simple string integer ("0" to "100").
 * No complex serialisation needed — keeps Redis memory minimal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisProgressService {

    private final StringRedisTemplate redisTemplate;

    @Value("${progress.redis-ttl-seconds:60}")
    private long redisTtlSeconds;

    /**
     * Redis key for a specific user's progress in a specific course.
     * Example: progress:550e8400-e29b-41d4-a716:660e8400-e29b-41d4-a716
     */
    private String progressKey(UUID userId, UUID courseId) {
        return String.format("progress:%s:%s", userId, courseId);
    }

    /**
     * Write progress percentage to Redis.
     * Called on every progress update — this is the HOT PATH.
     * Must be fast. Redis writes are typically sub-millisecond.
     *
     * Sets/refreshes the TTL on every write so active viewers
     * never have their keys expire mid-session.
     */
    public void writeProgress(UUID userId, UUID courseId, int percentComplete) {
        String key = progressKey(userId, courseId);
        redisTemplate.opsForValue().set(
                key,
                String.valueOf(percentComplete),
                Duration.ofSeconds(redisTtlSeconds)
        );
        log.debug("Redis write: key={}, value={}", key, percentComplete);
    }

    /**
     * Read progress percentage from Redis.
     * Returns null if the key has expired or was never written.
     * Caller should fall back to RDS when this returns null.
     */
    public Integer readProgress(UUID userId, UUID courseId) {
        String key = progressKey(userId, courseId);
        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            log.debug("Redis miss: key={}", key);
            return null;
        }

        log.debug("Redis hit: key={}, value={}", key, value);
        return Integer.parseInt(value);
    }

    /**
     * Delete a progress key from Redis.
     * Called when a learner unenrols from a course.
     */
    public void deleteProgress(UUID userId, UUID courseId) {
        String key = progressKey(userId, courseId);
        redisTemplate.delete(key);
        log.debug("Redis delete: key={}", key);
    }

    /**
     * Check if a progress key exists in Redis.
     * Used to determine whether to serve from cache or fall back to RDS.
     */
    public boolean hasProgress(UUID userId, UUID courseId) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(progressKey(userId, courseId))
        );
    }
}
