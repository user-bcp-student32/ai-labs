package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthResourceTest {
    private static ValidatableResponse login(String jsonBody) {
        return given()
                .contentType("application/json")
                .body(jsonBody)
                .when().post("/auth/login")
                .then();
    }

    // Spec "Inicio de sesión legítimo preservado": 200 con token, expiresIn, user.* y debug; ignora campos extra (client).
    @Test
    void logsInWithWorkshopUser() {
        login("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("token", not(emptyOrNullString()))
                .body("expiresIn", notNullValue())
                .body("debug", notNullValue());
    }

    // Spec "Inicio de sesión legítimo preservado": segundo usuario registrado.
    @Test
    void logsInWithTrainerUser() {
        login("{\"username\":\"trainer\",\"password\":\"Trainer123!\"}")
                .statusCode(200)
                .body("user.username", is("trainer"))
                .body("token", not(emptyOrNullString()));
    }

    // Spec "Campos obligatorios preservados": username ausente responde 400 sin token.
    @Test
    void rejectsMissingUsernameWithBadRequest() {
        login("{\"password\":\"Workshop123!\"}")
                .statusCode(400)
                .body("token", nullValue());
    }

    // Spec "Campos obligatorios preservados": password vacío responde 400 sin token.
    @Test
    void rejectsEmptyPasswordWithBadRequest() {
        login("{\"username\":\"student\",\"password\":\"\"}")
                .statusCode(400)
                .body("token", nullValue());
    }

    // Spec "Credenciales inválidas rechazadas": contraseña incorrecta responde 401 sin token.
    @Test
    void rejectsWrongPasswordWithUnauthorized() {
        login("{\"username\":\"student\",\"password\":\"incorrecta\"}")
                .statusCode(401)
                .body("token", nullValue());
    }

    // Spec "Credenciales inválidas rechazadas": usuario inexistente responde 401 sin token.
    @Test
    void rejectsUnknownUserWithUnauthorized() {
        login("{\"username\":\"desconocido\",\"password\":\"Workshop123!\"}")
                .statusCode(401)
                .body("token", nullValue());
    }

    // Spec "Credenciales tratadas como datos": guarda de regresión; hoy pasa por la precedencia de AND sobre OR.
    @Test
    void rejectsSqlInjectionInUsername() {
        login("{\"username\":\"' OR '1'='1\",\"password\":\"cualquiera\"}")
                .statusCode(401)
                .body("token", nullValue());
    }

    // Spec "Credenciales tratadas como datos": inyección en el campo contraseña no evade la verificación.
    @Test
    void rejectsSqlInjectionInPassword() {
        login("{\"username\":\"student\",\"password\":\"' OR '1'='1\"}")
                .statusCode(401)
                .body("token", nullValue());
    }

    // Spec "Credenciales tratadas como datos": inyección en ambos campos no evade la verificación.
    @Test
    void rejectsSqlInjectionInBothFields() {
        login("{\"username\":\"' OR '1'='1\",\"password\":\"' OR '1'='1\"}")
                .statusCode(401)
                .body("token", nullValue());
    }

    // Spec "Credenciales tratadas como datos": un comentario SQL no omite la contraseña.
    @Test
    void rejectsSqlCommentInUsername() {
        login("{\"username\":\"student'--\",\"password\":\"cualquiera\"}")
                .statusCode(401)
                .body("token", nullValue());
    }
}
