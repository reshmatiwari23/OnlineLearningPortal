package com.olp.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Online Learning Portal — Auth Service
 *
 * Responsibilities:
 *   POST /api/auth/signup  — register new user, BCrypt hash, save to RDS, sync to Cognito
 *   POST /api/auth/login   — verify BCrypt, get JWT from Cognito
 *
 * Port: 8081
 * Database: RDS PostgreSQL (olp_db, users table)
 * Auth provider: Amazon Cognito User Pool
 */
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
