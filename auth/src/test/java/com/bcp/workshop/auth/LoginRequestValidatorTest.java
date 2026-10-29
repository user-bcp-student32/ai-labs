package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRequestValidatorTest {
    private static final String VALID_USERNAME = "student";
    private static final String VALID_PASSWORD = "Workshop123!";
    private static final String VALID_CLIENT = "angular-workshop";
    private static final int BAD_REQUEST = 400;

    private final LoginRequestValidator validator = new LoginRequestValidator();

    @Test
    void rejectsNullRequest() {
        assertRejected(validator.validate(null), BAD_REQUEST, "Request is null");
    }

    @Test
    void acceptsCompleteRequest() {
        assertTrue(validator.validate(request(VALID_USERNAME, VALID_PASSWORD, VALID_CLIENT)).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingOrBlankUsername(String username) {
        assertRejected(validator.validate(request(username, VALID_PASSWORD, VALID_CLIENT)), BAD_REQUEST, "Username is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingOrBlankPassword(String password) {
        assertRejected(validator.validate(request(VALID_USERNAME, password, VALID_CLIENT)), BAD_REQUEST, "Password is required");
    }

    @Test
    void reportsUsernameBeforePasswordWhenBothAreInvalid() {
        assertRejected(validator.validate(request("", "", VALID_CLIENT)), BAD_REQUEST, "Username is required");
    }

    private static LoginRequest request(String username, String password, String client) {
        LoginRequest request = new LoginRequest();
        request.username = username;
        request.password = password;
        request.client = client;
        return request;
    }

    private static void assertRejected(Optional<LoginRequestValidator.ValidationError> error, int status, String message) {
        assertTrue(error.isPresent());
        assertEquals(status, error.get().status());
        assertEquals(message, error.get().message());
    }
}
