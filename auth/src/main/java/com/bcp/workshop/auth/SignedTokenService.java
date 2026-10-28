package com.bcp.workshop.auth;

import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtClaimsBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class SignedTokenService {
    public static final int TOKEN_VALIDITY_SECONDS = 3600;
    private static final int MINIMUM_SIGNING_KEY_BYTES = 32;
    private static final String SIGNATURE_ALGORITHM = "HmacSHA256";

    private final SecretKey signingKey;

    @Inject
    public SignedTokenService(@ConfigProperty(name = "auth.token.secret") String secret) {
        byte[] secretBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SIGNING_KEY_BYTES) {
            throw new IllegalArgumentException("AUTH_TOKEN_SECRET must contain at least 32 bytes");
        }
        signingKey = new SecretKeySpec(secretBytes, SIGNATURE_ALGORITHM);
    }

    public String create(String username, String role) {
        try {
            JwtClaimsBuilder claims = Jwt.claims()
                    .subject(username)
                    .claim("role", role)
                    .expiresIn(TOKEN_VALIDITY_SECONDS);
            return claims.jws()
                    .algorithm(SignatureAlgorithm.HS256)
                    .sign(signingKey);
        } catch (RuntimeException error) {
            throw new AuthenticationServiceException("Unable to issue authentication token", error);
        }
    }
}
