package com.olp.auth.dto;

import lombok.*;

/**
 * Response body returned by both /signup and /login.
 * The token is a Cognito-issued RS256 JWT valid for 24 hours.
 * The frontend stores this token and sends it as Authorization: Bearer {token}
 * on every subsequent request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    // Cognito RS256 JWT — validated by API Gateway Lambda Authoriser
    private String token;

    // UUID from the users table
    private String userId;

    private String email;
    private String name;

    // "user" or "instructor"
    private String role;

    // Token lifetime in seconds (86400 = 24 hours)
    private long expiresIn;
}
