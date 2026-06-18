package com.olp.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Request body for POST /api/auth/signup.
 * Spring validates all annotations before the controller method is called.
 * If validation fails, GlobalExceptionHandler returns a 400 with field errors.
 */
@Data
public class SignupRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    // Defaults to "user" if not provided.
    // Only "user" and "instructor" are valid — anything else gets a 400.
    @Pattern(
        regexp = "^(user|instructor)$",
        message = "Role must be either 'user' or 'instructor'"
    )
    private String role = "user";
}
