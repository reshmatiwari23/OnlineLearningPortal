package com.olp.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Standard API response envelope used by all OLP microservices.
 *
 * Every endpoint returns this wrapper so the frontend always knows
 * what structure to expect.
 *
 * Success example:
 * {
 *   "success": true,
 *   "message": "User created successfully",
 *   "data": { "token": "...", "userId": "..." },
 *   "timestamp": "2025-01-01T10:00:00"
 * }
 *
 * Error example:
 * {
 *   "success": false,
 *   "message": "Email already exists",
 *   "data": null,
 *   "timestamp": "2025-01-01T10:00:00"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // omit null fields from JSON output
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;

    // ── Static factory methods — use these instead of the constructor ──

    /**
     * Use when the operation succeeded and you have data to return.
     * Example: return ApiResponse.success(authResponse);
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Use when the operation succeeded with a custom message.
     * Example: return ApiResponse.success(authResponse, "User created successfully");
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Use when the operation failed.
     * Example: return ApiResponse.error("Email already exists");
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
