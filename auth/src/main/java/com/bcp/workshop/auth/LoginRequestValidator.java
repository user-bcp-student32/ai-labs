package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class LoginRequestValidator {
    private static final int BAD_REQUEST = 400;

    public record ValidationError(int status, String message) {}

    public Optional<ValidationError> validate(LoginRequest request) {
        if (request == null) {
            return badRequest("Request is null");
        }
        if (isBlank(request.username)) {
            return badRequest("Username is required");
        }
        if (isBlank(request.password)) {
            return badRequest("Password is required");
        }
        return Optional.empty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Optional<ValidationError> badRequest(String message) {
        return Optional.of(new ValidationError(BAD_REQUEST, message));
    }
}
