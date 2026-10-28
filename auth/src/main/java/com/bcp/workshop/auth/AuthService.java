package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;

import java.util.Optional;

@ApplicationScoped
public class AuthService {
    private static final String AUTHENTICATION_DEBUG_VALUE = "authenticated-with-legacy-flow";

    private final UserRepository userRepository;
    private final PasswordHashService passwordHashService;
    private final SignedTokenService signedTokenService;

    @Inject
    public AuthService(
            UserRepository userRepository,
            PasswordHashService passwordHashService,
            SignedTokenService signedTokenService) {
        this.userRepository = userRepository;
        this.passwordHashService = passwordHashService;
        this.signedTokenService = signedTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        String username = validateRequest(request);
        Optional<UserRecord> user = userRepository.findByUsername(username)
                .filter(record -> passwordHashService.matches(request.password, record.passwordHash()));
        UserRecord authenticatedUser = user.orElseThrow(() -> new InvalidCredentialsException(username));
        String token = signedTokenService.create(authenticatedUser.username(), authenticatedUser.role());
        LoginUserResponse responseUser = new LoginUserResponse(
                authenticatedUser.username(),
                StringEscapeUtils.escapeHtml4(authenticatedUser.displayName()),
                authenticatedUser.role());
        return new LoginResponse(
                token,
                SignedTokenService.TOKEN_VALIDITY_SECONDS,
                responseUser,
                AUTHENTICATION_DEBUG_VALUE);
    }

    private String validateRequest(LoginRequest request) {
        if (request == null) {
            throw new InvalidLoginRequestException("Request is null");
        }
        if (request.username == null || request.username.trim().isEmpty()) {
            throw new InvalidLoginRequestException("Username is required");
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            throw new InvalidLoginRequestException("Password is required");
        }
        return request.username;
    }
}
