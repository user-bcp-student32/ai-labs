package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthResourceTest {
    private static final String ALLOW_ORIGIN_HEADER = "Access-Control-Allow-Origin";
    private static final String APPROVED_ORIGIN = "http://localhost:4200";
    private static final String APPROVED_LOOPBACK_ORIGIN = "http://127.0.0.1:4200";
    private static final String UNAPPROVED_ORIGIN = "https://sitio-no-aprobado.example";
    private static final String LOGIN_BODY = "{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}";

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
}
