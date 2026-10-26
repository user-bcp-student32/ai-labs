package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthResourceTest {
    private static final String LOGIN_PATH = "/auth/login";
    private static final String VALID_USERNAME = "student";
    private static final String VALID_PASSWORD = "Workshop123!";
    private static final String TEST_TOKEN_SECRET = "test-only-token-signing-key-for-auth-workshop";
    private static final String TEST_DATABASE_URL = System.getenv()
            .getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("token", notNullValue())
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    @Test
    void rejectsInvalidPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"not-the-password\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"));
    }

    @Test
    void rejectsMissingUsername() {
        given()
                .contentType("application/json")
                .body("{\"password\":\"not-the-password\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsMissingPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsQuotedInvalidPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"not'valid\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"));
    }

    @Test
    void preflightAllowsAnArbitraryOriginInTheCurrentConfiguration() {
        given()
                .header("Origin", "https://unlisted.example")
                .header("Access-Control-Request-Method", "POST")
                .when().options(LOGIN_PATH)
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", is("https://unlisted.example"));
    }

    @Test
    void storesSeededPasswordAsSlowHash() throws SQLException {
        try (Connection connection = DriverManager.getConnection(TEST_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password FROM legacy_users WHERE username = ?")) {
            statement.setString(1, VALID_USERNAME);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertTrue(result.getString("password").startsWith("$pbkdf2-sha256$"));
            }
        }
    }

    @Test
    void treatsQuotedCredentialsAsLiteralValues() throws SQLException {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(200);

        String username = "quote-" + UUID.randomUUID().toString().replace("-", "");
        String password = "pass'word";
        insertTestUser(username, password);

        try {
            given()
                    .contentType("application/json")
                    .body("{\"username\":\"" + username + "\",\"password\":\"pass'word\"}")
                    .when().post(LOGIN_PATH)
                    .then()
                    .statusCode(200)
                    .body("user.username", is(username));
        } finally {
            deleteTestUser(username);
        }
    }

    @Test
    void issuesSignedJwtWithUsernameRoleAndExpiryClaims() throws Exception {
        String token = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(200)
                .extract()
                .path("token");

        String[] tokenParts = token.split("\\.", -1);
        assertEquals(3, tokenParts.length);
        String header = decodeTokenPart(tokenParts[0]);
        String claims = decodeTokenPart(tokenParts[1]);
        assertTrue(header.contains("\"alg\":\"HS256\""));
        assertTrue(claims.contains("\"sub\":\"student\""));
        assertTrue(claims.contains("\"role\":\"STUDENT\""));

        Matcher expiryMatcher = Pattern.compile("\"exp\":(\\d+)").matcher(claims);
        assertTrue(expiryMatcher.find());
        assertTrue(Long.parseLong(expiryMatcher.group(1)) > Instant.now().getEpochSecond());
        assertEquals(calculateSignature(tokenParts[0] + "." + tokenParts[1]), tokenParts[2]);
        String alteredClaims = (tokenParts[1].startsWith("A") ? "B" : "A")
                + tokenParts[1].substring(1);
        assertNotEquals(calculateSignature(tokenParts[0] + "." + alteredClaims), tokenParts[2]);
        assertFalse(claims.contains(TEST_TOKEN_SECRET));
    }

    @Test
    void returnsGenericErrorWithoutExceptionDetails() {
        UserRepository failingRepository = username -> {
            throw new AuthenticationServiceException("controlled-test-error", new IllegalStateException());
        };
        AuthService authService = new AuthService(
                failingRepository,
                new PasswordHashService(),
                new SignedTokenService(TEST_TOKEN_SECRET));
        AuthResource resource = new AuthResource(authService);
        LoginRequest request = new LoginRequest();
        request.username = VALID_USERNAME;
        request.password = VALID_PASSWORD;

        try (Response response = resource.login(request)) {
            assertEquals(500, response.getStatus());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getEntity();
            assertEquals("Authentication failed", body.get("message"));
            assertFalse(body.containsKey("exception"));
            assertFalse(body.containsValue("controlled-test-error"));
        }
    }

    private static String decodeTokenPart(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String calculateSignature(String signingInput) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                TEST_TOKEN_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    private static void insertTestUser(String username, String password) throws SQLException {
        try (Connection connection = DriverManager.getConnection(TEST_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, username);
            statement.setString(2, new PasswordHashService().hash(password));
            statement.setString(3, "Quoted Credential");
            statement.setString(4, "TEST");
            statement.executeUpdate();
        }
    }

    private static void deleteTestUser(String username) throws SQLException {
        try (Connection connection = DriverManager.getConnection(TEST_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM legacy_users WHERE username = ?")) {
            statement.setString(1, username);
            statement.executeUpdate();
        }
    }
}
