# Proposal: HU-01 — Token firmado con expiración y claims mínimos

**Issue:** #24 (HU-01 — Token firmado con expiración y claims mínimos)
**Componente:** `auth/`
**Fuente de alcance:** cuerpo del Issue #24. Baseline de comportamiento: `docs/ENDPOINTS.md`, contrastado con el código real de `auth/`.

## Why

`POST /auth/login` devuelve hoy un token que no es un JWT: es Base64 de `username|role|expiry|SECRET`, con un secreto fijo en `LegacyTokenUtil`. Cualquier consumidor que lo reciba no puede verificar su firma con un secreto configurable, no tiene `iat` ni `client`, y el secreto queda expuesto en el código fuente. El Issue #24 pide un token firmado, con claims mínimos verificables y expiración comprobable, para que los clientes identifiquen su contexto y rechacen tokens vencidos.

## What Changes

- `POST /auth/login` emite un JWT firmado con HS256 y los claims `sub`, `client`, `iat` y `exp`, con vigencia de 3600 segundos.
- `client` pasa a ser **obligatorio** en la solicitud: 1 a 64 caracteres del conjunto `[A-Za-z0-9._-]`. Hoy el campo existe en el DTO pero es opcional y no se valida. **BREAKING** para clientes que hoy omiten `client`.
- `expiresIn` se calcula como segundos restantes hasta `exp`, con tolerancia máxima de 5 segundos frente a `exp - now`.
- El secreto de firma se toma de la variable de entorno `AUTH_TOKEN_SECRET`. Se elimina el secreto literal del camino de producción. **BREAKING** para tokens emitidos con el formato anterior.
- Los errores y logs de login no deben exponer tokens, secretos ni contraseñas.

## Capabilities

### New Capabilities

- `auth-login`: contrato observable de `POST /auth/login` (validación de la solicitud, credenciales, respuesta exitosa y errores).
- `auth-token`: contrato del token emitido por el login (formato JWT HS256, claims, vigencia, verificación de firma y expiración, configuración del secreto).

### Modified Capabilities

- Ninguna. `openspec/specs/` está vacío en este repositorio, así que ambas capacidades se introducen como nuevas. Su contrato de referencia es el comportamiento actual documentado en `docs/ENDPOINTS.md` y verificado en el código.

## Comportamiento que debe preservarse

- Ruta, método y tipo de contenido: `POST /auth/login`, JSON en entrada y salida.
- Respuesta `200` con los campos `token`, `expiresIn` y `user` (`username`, `displayName`, `role`). `displayName` sigue escapado como HTML.
- Campo `debug` en la respuesta exitosa: el Issue no pide retirarlo y está fuera de alcance cambiar campos existentes; se mantiene sin cambios.
- Validaciones previas existentes, con sus mensajes: `Request is null`, `Username is required`, `Password is required` (todas `400`).
- `401` con `Invalid user {username} or password` para credenciales incorrectas.
- Orden de validación: primero las existentes (request, usuario, contraseña) y después la de `client`.
- Configuración CORS, endpoints distintos de `POST /auth/login`, persistencia y verificación de contraseñas: fuera de alcance según el Issue.

## Discrepancias entre `docs/ENDPOINTS.md` y el código

`docs/ENDPOINTS.md` no coincide con el código actual de `auth/`. Las specs usan el código como punto de partida y el Issue como fuente de cambios intencionales.

| Tema | ENDPOINTS.md / código actual | Issue #24 / spec propuesta | Tratamiento |
|---|---|---|---|
| Formato del token | Doc: JWT HS256 con `sub`, `role`, `exp`. Código: Base64 `username\|role\|expiry\|SECRET` | JWT HS256 con `sub`, `client`, `iat`, `exp` | Cambio intencional (Issue) |
| `client` | Doc: opcional y no validado. Código: opcional, no usado | Obligatorio, 1–64 caracteres `[A-Za-z0-9._-]` | Cambio intencional (Issue) |
| Secreto | Doc: `AUTH_TOKEN_SECRET`, mínimo 32 bytes. Código: literal `WORKSHOP_SECRET_2026` | `AUTH_TOKEN_SECRET` desde entorno, sin fallback fuera de laboratorio | Cambio intencional (Issue); el mínimo de 32 bytes se toma del doc y debe confirmarse |
| Error `500` | Doc: `{"message": "Authentication failed"}`. Código: `message` + `exception` (nombre de clase) | Sin secretos, tokens ni credenciales en errores | Se conserva `500` y `message`; se exige no exponer datos sensibles. El campo `exception` queda como pregunta abierta |
| CORS | Doc: cualquier origen. Código: orígenes de desarrollo en local y `/.*/` en `%prod` (H-AUTH-04) | No cubierto (fuera de alcance) | Se conserva sin cambios |
| Clases | Doc: `AuthService`, `SignedTokenService`, `PasswordHashService`, `LoginResponse`, etc. Código: no existen | No aplica | El documento no es referencia fiable de estructura interna |
| Claim `role` | Doc y código actual lo incluyen | No aparece entre los claims mínimos | Pregunta abierta (ver abajo) |

## Preguntas abiertas (a decidir antes de `design.md`)

1. ¿El token debe conservar el claim `role`? El Issue lista solo `sub`, `client`, `iat` y `exp` como claims obligatorios.
2. Código HTTP y forma del cuerpo para `client` inválido. Se propone `400` con `{"message": "..."}`, igual que las validaciones existentes; el Issue solo habla de "error de validación".
3. ¿Se retira el campo `exception` del cuerpo del `500`? Hoy expone el nombre de la clase de excepción.
4. Longitud mínima de `AUTH_TOKEN_SECRET`: el doc la fija en 32 bytes; el Issue no la menciona.
5. Consumidores: ¿hay clientes que dependan del formato Base64 actual o que no envían `client`? El frontend (`web/`) debe confirmarse antes de hacer obligatorio `client`; no se revisó aquí por estar fuera del alcance.

## Riesgos observados fuera de alcance

- `LegacyDatabase` construye la consulta SQL concatenando `username` y `password`. Es una vulnerabilidad de inyección SQL, pero el Issue excluye persistencia y contraseñas. Debe tratarse como hallazgo aparte y no resolverse en este cambio sin ampliar el alcance.
- La respuesta `401` revela el nombre de usuario recibido.

## Alcance

Dentro:
- Validación de `client`.
- Emisión de JWT HS256 con `sub`, `client`, `iat`, `exp`, vigencia de 3600 segundos.
- `expiresIn` coherente con `exp` dentro de 5 segundos.
- Configuración de `AUTH_TOKEN_SECRET` desde entorno, con fallo explícito si falta fuera de laboratorio.
- Pruebas de login válido, credenciales inválidas, `client` inválido y token expirado.
- Revisión de logs y errores para evitar exposición de tokens, secretos y contraseñas.
- Documentación de `AUTH_TOKEN_SECRET`, duración, tolerancia y algoritmo.

Fuera:
- Endpoints distintos de `POST /auth/login` y cambios a los campos existentes de la respuesta.
- Persistencia, contraseñas (incluida la inyección SQL de `LegacyDatabase`), CORS y componentes distintos de `auth/`.
- Renovación, refresh tokens, revocación y autorización por roles.
- Elección de biblioteca JWT y diseño técnico: se resuelven en `design.md`.

## Impact

- **Código:** `auth/src/main/java/com/bcp/workshop/auth/` (`AuthResource`, `LegacyTokenUtil`, `LoginRequest`) y configuración en `auth/src/main/resources/application.properties`.
- **Pruebas:** `auth/src/test/java/com/bcp/workshop/auth/`. Meta de cobertura del código modificado: 85 % o superior.
- **APIs:** `POST /auth/login` cambia el formato del token y exige `client`. Cambios de contrato a comunicar.
- **Dependencias:** posible biblioteca JWT. Requiere justificación y revisión antes de incorporarla; no se decide aquí.
- **Documentación:** `docs/ENDPOINTS.md` y la configuración de `AUTH_TOKEN_SECRET` deben actualizarse en la implementación.
- **Seguridad:** elimina el secreto literal del código; no añade secretos reales al repositorio.
