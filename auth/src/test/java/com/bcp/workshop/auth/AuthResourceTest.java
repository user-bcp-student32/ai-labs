package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class AuthResourceTest {
    private static final String ALLOW_ORIGIN_HEADER = "Access-Control-Allow-Origin";
    private static final String APPROVED_ORIGIN = "http://localhost:4200";
    private static final String APPROVED_LOOPBACK_ORIGIN = "http://127.0.0.1:4200";
    private static final String UNAPPROVED_ORIGIN = "https://sitio-no-aprobado.example";
    private static final String LOGIN_BODY = "{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}";
    private static final String LOGIN_PATH = "/auth/login";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String VALID_PASSWORD_JSON = "\"Workshop123!\"";
    private static final String VALID_USERNAME_JSON = "\"student\"";
    private static final String VALID_CLIENT_JSON = "\"angular-workshop\"";
    private static final String ESCAPE_USERNAME = "escape-user";
    private static final String ESCAPE_PASSWORD = "Escape123!";
    private static final String ESCAPE_DISPLAY_NAME = "<b>Ana & Co</b>";
    private static final String DATABASE_URL_PROPERTY = "AUTH_DB_URL";

    @BeforeAll
    static void registerUserWithHtmlSensitiveDisplayName() throws SQLException {
        try (Connection connection = DriverManager.getConnection(System.getenv(DATABASE_URL_PROPERTY));
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR REPLACE INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
            statement.setString(1, ESCAPE_USERNAME);
            statement.setString(2, ESCAPE_PASSWORD);
            statement.setString(3, ESCAPE_DISPLAY_NAME);
            statement.setString(4, "STUDENT");
            statement.executeUpdate();
        }
    }

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue());
    }

    @Test
    void allowsApprovedLocalhostOriginOnLogin() {
        given()
                .header("Origin", APPROVED_ORIGIN)
                .contentType("application/json")
                .body(LOGIN_BODY)
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .header(ALLOW_ORIGIN_HEADER, is(APPROVED_ORIGIN));
    }

    @Test
    void allowsApprovedLoopbackOriginOnLogin() {
        given()
                .header("Origin", APPROVED_LOOPBACK_ORIGIN)
                .contentType("application/json")
                .body(LOGIN_BODY)
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .header(ALLOW_ORIGIN_HEADER, is(APPROVED_LOOPBACK_ORIGIN));
    }

    @Test
    void answersPreflightForApprovedOrigin() {
        given()
                .header("Origin", APPROVED_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .when().options("/auth/login")
                .then()
                .header(ALLOW_ORIGIN_HEADER, is(APPROVED_ORIGIN));
    }

    @Test
    void doesNotAllowUnapprovedOriginOnLogin() {
        given()
                .header("Origin", UNAPPROVED_ORIGIN)
                .contentType("application/json")
                .body(LOGIN_BODY)
                .when().post("/auth/login")
                .then()
                .header(ALLOW_ORIGIN_HEADER, not(anyOf(is("*"), is(UNAPPROVED_ORIGIN))));
    }

    @Test
    void returnsExistingSuccessfulLoginFields() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body(LOGIN_BODY)
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("expiresIn", is(3600))
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    @Test
    void escapesDisplayNameAsHtml() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body(loginBody(quoted(ESCAPE_USERNAME), quoted(ESCAPE_PASSWORD), VALID_CLIENT_JSON))
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(200)
                .body("user.displayName", is("&lt;b&gt;Ana &amp; Co&lt;/b&gt;"));
    }

    @Test
    void rejectsNullRequestWithExactMessage() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"   \""})
    void rejectsMissingOrBlankUsernameWithExactMessage(String username) {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body(loginBody(username, VALID_PASSWORD_JSON, VALID_CLIENT_JSON))
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"   \""})
    void rejectsMissingOrBlankPasswordWithExactMessage(String password) {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body(loginBody(VALID_USERNAME_JSON, password, VALID_CLIENT_JSON))
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void reportsUsernameBeforePasswordWhenBothAreInvalid() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body(loginBody("\"\"", "\"\"", VALID_CLIENT_JSON))
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsWrongPasswordWithoutToken() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body(loginBody(VALID_USERNAME_JSON, "\"WrongPassword!\"", VALID_CLIENT_JSON))
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"))
                .body("token", nullValue());
    }

    @Test
    void rejectsUnknownUserWithoutToken() {
        given()
                .contentType(JSON_CONTENT_TYPE)
                .body(loginBody("\"ghost\"", VALID_PASSWORD_JSON, VALID_CLIENT_JSON))
                .when().post(LOGIN_PATH)
                .then()
                .statusCode(401)
                .body("message", is("Invalid user ghost or password"))
                .body("token", nullValue());
    }

    @Test
    void neverIssuesTokenWithoutJsonContentType() {
        Response response = given()
                .body(LOGIN_BODY)
                .when().post(LOGIN_PATH);

        assertEquals(415, response.statusCode());
        assertFalse(response.asString().contains("\"token\""));
    }

    @Test
    void neverIssuesTokenForMalformedJson() {
        Response response = given()
                .contentType(JSON_CONTENT_TYPE)
                .body("{\"username\":\"student\",")
                .when().post(LOGIN_PATH);

        assertEquals(400, response.statusCode());
        assertFalse(response.asString().contains("\"token\""));
    }

    private static String loginBody(String username, String password, String client) {
        return "{\"username\":" + username + ",\"password\":" + password + ",\"client\":" + client + "}";
    }

    private static String quoted(String value) {
        return "\"" + value + "\"";
    }
}

