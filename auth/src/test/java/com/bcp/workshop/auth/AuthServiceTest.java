package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {
    private static final String TEST_SECRET = "test-only-token-signing-key-for-auth-workshop";
    private final PasswordHashService passwordHashService = new PasswordHashService();

    @Test
    void rejectsNullAndIncompleteRequests() {
        AuthService authService = service(username -> Optional.empty());

        assertEquals("Request is null", assertThrows(
                InvalidLoginRequestException.class,
                () -> authService.login(null)).getMessage());

        LoginRequest missingUsername = new LoginRequest();
        missingUsername.password = "test-password";
        assertEquals("Username is required", assertThrows(
                InvalidLoginRequestException.class,
                () -> authService.login(missingUsername)).getMessage());

        LoginRequest missingPassword = new LoginRequest();
        missingPassword.username = "test-user";
        assertEquals("Password is required", assertThrows(
                InvalidLoginRequestException.class,
                () -> authService.login(missingPassword)).getMessage());
    }

    @Test
    void rejectsUnknownUserAndMismatchedPassword() {
        AuthService missingUserService = service(username -> Optional.empty());
        assertThrows(InvalidCredentialsException.class, () -> missingUserService.login(request()));

        UserRecord existingUser = new UserRecord(
                1L,
                "test-user",
                "Test User",
                "STUDENT",
                passwordHashService.hash("different-password"));
        AuthService wrongPasswordService = service(username -> Optional.of(existingUser));
        assertThrows(InvalidCredentialsException.class, () -> wrongPasswordService.login(request()));
    }

    @Test
    void authenticatesAndMapsTheUserToResponseDto() {
        UserRecord existingUser = new UserRecord(
                1L,
                "test-user",
                "<Student>",
                "STUDENT",
                passwordHashService.hash("test-password"));
        AuthService authService = service(username -> Optional.of(existingUser));
        LoginRequest request = request();
        request.password = "test-password";

        LoginResponse response = authService.login(request);

        assertNotNull(response.token());
        assertEquals(SignedTokenService.TOKEN_VALIDITY_SECONDS, response.expiresIn());
        assertEquals("test-user", response.user().username());
        assertEquals("&lt;Student&gt;", response.user().displayName());
        assertEquals("STUDENT", response.user().role());
        assertEquals("authenticated-with-legacy-flow", response.debug());
    }

    @Test
    void propagatesRepositoryFailureForGenericHttpMapping() {
        AuthService authService = service(username -> {
            throw new AuthenticationServiceException("test-only-failure", new IllegalStateException());
        });

        assertThrows(AuthenticationServiceException.class, () -> authService.login(request()));
    }

    private AuthService service(UserRepository userRepository) {
        return new AuthService(
                userRepository,
                passwordHashService,
                new SignedTokenService(TEST_SECRET));
    }

    private LoginRequest request() {
        LoginRequest request = new LoginRequest();
        request.username = "test-user";
        request.password = "test-password";
        return request;
    }
}
