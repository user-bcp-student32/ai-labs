package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class AuthService {
    private static final int OK = 200;
    private static final int UNAUTHORIZED = 401;
    private static final int LEGACY_EXPIRES_IN_SECONDS = 3600;
    private static final String LEGACY_DEBUG_FLAG = "authenticated-with-legacy-flow";

    @Inject
    LegacyDatabase database;

    @Inject
    LoginRequestValidator validator;

    public LoginOutcome login(LoginRequest request) {
        Optional<LoginRequestValidator.ValidationError> error = validator.validate(request);
        if (error.isPresent()) {
            return new LoginOutcome(error.get().status(), Map.of("message", error.get().message()));
        }
        UserRecord user = database.findByCredentials(request.username, request.password);
        if (user == null) {
            return new LoginOutcome(UNAUTHORIZED, Map.of("message", "Invalid user " + request.username + " or password"));
        }
        return new LoginOutcome(OK, successBody(user));
    }

    private static Map<String, Object> successBody(UserRecord user) {
        Map<String, Object> response = new HashMap<>();
        response.put("token", LegacyTokenUtil.create(user.username(), user.role()));
        response.put("expiresIn", LEGACY_EXPIRES_IN_SECONDS);
        response.put("user", Map.of(
                "username", user.username(),
                "displayName", StringEscapeUtils.escapeHtml4(user.displayName()),
                "role", user.role()
        ));
        response.put("debug", LEGACY_DEBUG_FLAG);
        return response;
    }

    public record LoginOutcome(int status, Map<String, Object> body) {}
}
