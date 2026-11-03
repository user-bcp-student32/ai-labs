package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuthService {
    @Inject
    LegacyDatabase database;

    @Inject
    JwtTokenService tokenService;

    public AuthenticatedSession authenticate(String username, String password) {
        UserRecord user = database.findByCredentials(username, password);
        if (user == null) {
            return null;
        }
        return new AuthenticatedSession(
                user,
                tokenService.create(user.username(), user.role())
        );
    }
}
