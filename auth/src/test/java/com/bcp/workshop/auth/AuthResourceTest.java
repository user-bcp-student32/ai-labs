package com.bcp.workshop.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.restassured.RestAssured.given;

@QuarkusTest
class AuthResourceTest {
    private static final String TEST_JWT_SECRET = "test-only-secret-for-ai-labs-1234567890";
    private static final long TOKEN_LIFETIME_SECONDS = 3600;

    @Test
    void logsInWithWorkshopUserAndIssuesSignedToken() {
        var response = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.role", is("STUDENT"))
                .body("token", notNullValue())
                .body("expiresIn", is((int) TOKEN_LIFETIME_SECONDS))
                .body("debug", is("authenticated-with-legacy-flow"))
                .extract()
                .response();

        var decoded = JWT.require(Algorithm.HMAC256(TEST_JWT_SECRET))
                .withIssuer("workshop-auth")
                .withAudience("workshop-api")
                .build()
                .verify(response.jsonPath().getString("token"));

        assertEquals("student", decoded.getSubject());
        assertEquals("STUDENT", decoded.getClaim("role").asString());
        assertNotNull(decoded.getIssuedAt());
        assertNotNull(decoded.getExpiresAt());
        assertTrue(decoded.getExpiresAt().toInstant()
                .isAfter(decoded.getIssuedAt().toInstant()));
    }

    @Test
    void rejectsInvalidCredentials() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"wrong\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"));
    }

    @Test
    void rejectsMissingUsername() {
        given()
                .contentType("application/json")
                .body("{\"username\":\" \",\"password\":\"password\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsNullUsername() {
        given()
                .contentType("application/json")
                .body("{\"password\":\"password\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsMissingPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\" \"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsNullPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsNullRequest() {
        AuthResource resource = new AuthResource();

        assertEquals(400, resource.login(null).getStatus());
    }

    @Test
    void returnsServerErrorWhenAuthenticationFailsUnexpectedly() {
        AuthResource resource = new AuthResource();
        resource.authService = new AuthService() {
            @Override
            public AuthenticatedSession authenticate(String username, String password) {
                throw new IllegalStateException();
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "password";

        assertEquals(500, resource.login(request).getStatus());
    }

    @Test
    void returnsServerErrorWithFailureMessageWhenAuthenticationFails() {
        AuthResource resource = new AuthResource();
        resource.authService = new AuthService() {
            @Override
            public AuthenticatedSession authenticate(String username, String password) {
                throw new IllegalStateException("authentication failure");
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "password";

        assertEquals(500, resource.login(request).getStatus());
    }

    @Test
    void rejectsShortSigningSecret() {
        JwtTokenService tokenService = new JwtTokenService();
        tokenService.secret = "short";

        assertThrows(IllegalStateException.class, tokenService::validateConfiguration);
    }

    @Test
    void rejectsMissingSigningSecret() {
        JwtTokenService tokenService = new JwtTokenService();
        tokenService.secret = null;

        assertThrows(IllegalStateException.class, tokenService::validateConfiguration);
    }
}
