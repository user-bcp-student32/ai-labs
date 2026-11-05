# Proposal

## Why

El Issue [#22](https://github.com/user-bcp-student32/ai-labs/issues/22) (hallazgo **H-AUTH-01**, vulnerabilidad de severidad alta, componente `auth/`) reporta que `LegacyDatabase.findByCredentials` (`auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:32`) arma la consulta SQL concatenando `username` y `password`. Un valor como `' OR '1'='1` altera la consulta y permite iniciar sesión sin conocer la contraseña, lo que anula la autenticación de toda la plataforma. Hoy no existe ninguna prueba que cubra este intento, por lo que la corrección debe quedar definida en requisitos verificables antes de implementarse.

## What Changes

- Las credenciales recibidas en `POST /auth/login` pasan a tratarse únicamente como datos y nunca como parte de la sentencia SQL.
- Un intento de inyección SQL en `username` o `password` responde `401` y no entrega `token`.
- El comportamiento observable de `POST /auth/login` para credenciales válidas, campos ausentes y credenciales inválidas permanece igual.
- Se exige una prueba automatizada del intento de inyección en `AuthResourceTest`, junto con la ejecución exitosa de `mvn test` desde `auth/`.

No hay cambios **BREAKING**: ningún campo, código de estado ni formato de respuesta cambia para usuarios legítimos.

## Capabilities

### New Capabilities
- `auth-login`: autenticación por usuario y contraseña mediante `POST /auth/login`: contrato de respuesta preservado y resistencia a inyección SQL en las credenciales. Es una capability nueva porque `openspec/specs/` está vacío.

### Modified Capabilities
<!-- Ninguna: no existen specs previas. -->

## Alcance

- Componente `auth/` únicamente, endpoint `POST /auth/login`.
- Requisitos y escenarios observables para: inicio de sesión legítimo, intento de inyección en `username`, intento de inyección en `password`, y preservación de los códigos `400` y `401`.
- Cobertura de la corrección con una prueba en `AuthResourceTest` y `mvn test` en verde desde `auth/`.
- Meta de cobertura del código modificado: 85 % o superior (contrato de agentes de `auth`).

## Fuera de alcance

- Cambiar el formato o la firma del token. El baseline (`docs/ENDPOINTS.md`) describe un JWT HS256, pero el código actual de esta rama emite un token legado en Base64 (`LegacyTokenUtil`). Esa diferencia **no** la cambia el Issue #22, por lo que los escenarios solo exigen que `token` exista y no esté vacío, sin asumir su formato.
- Almacenamiento de contraseñas (hoy en texto plano), hashing y migración de datos.
- Secretos embebidos, expiración del token, el campo `debug`, los mensajes de error y el manejo de `500`.
- Refactorización por capas de `AuthResource`, y cualquier cambio en `api/` o `web/`.
- Dependencias, CI, CodeQL, Dependency Review o JaCoCo.
- Decisiones de implementación técnica: se definen en `design.md`, no en esta propuesta.

## Comportamiento que debe preservarse

Fuente: `docs/ENDPOINTS.md` (contrato del Día 1, leído de la rama local `day1/prompting`, pues no existe en la rama actual) y criterios de aceptación del Issue #22.

- Entrada JSON con `username` y `password`; campos adicionales como `client` se ignoran.
- `student` / `Workshop123!` responde `200` con `token`, `expiresIn`, `user.username`, `user.displayName`, `user.role` y el campo `debug` existente.
- `400` cuando `username` o `password` están ausentes o vacíos.
- `401` para credenciales inválidas.
- Coherencia con el baseline: el Issue no modifica el contrato, por lo que ningún escenario lo contradice.

## Impact

- **Código afectado**: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java` (consulta de credenciales) y `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` (nuevas pruebas). Estos cambios se harán en la fase de implementación, no en esta propuesta.
- **API**: sin cambios de contrato en `POST /auth/login`.
- **Dependencias y otros componentes**: ninguno; `api/` y `web/` no se ven afectados.
- **Riesgo principal**: regresión del login legítimo, mitigada por los escenarios de preservación.
