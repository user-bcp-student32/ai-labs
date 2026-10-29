# Design: HU-01 — Token firmado con expiración y claims mínimos

**Issue:** #24 (HU-01)
**Componente:** `auth/` (único componente tocado; `api/` y `web/` solo se consideran como consumidores afectados)
**Insumos:** `proposal.md`, `specs/auth-login/spec.md`, `specs/auth-token/spec.md`, código actual de `auth/`, `auth/pom.xml`, `auth/src/main/resources/application.properties`.
**Estado:** borrador para revisión humana. No hay código funcional en este documento.

## 1. Contexto técnico

Estado actual relevante (verificado en código):

| Elemento | Situación actual | Archivo |
|---|---|---|
| Ruta | `POST /auth/login`, JSON in/out | `AuthResource.java` |
| Validación | Orquestada dentro del método `login` con `if` anidados (request, username, password) | `AuthResource.java` |
| `client` | Existe en `LoginRequest` como `public String client`; no se valida ni se usa | `LoginRequest.java` |
| Token | Base64 de `username\|role\|expiry\|SECRET`, con `SECRET = "WORKSHOP_SECRET_2026"` literal | `LegacyTokenUtil.java` |
| `expiresIn` | Literal `3600` en la respuesta, no derivado de `exp` | `AuthResource.java` |
| Persistencia | SQLite vía JDBC; SQL concatenado con `username` y `password`; `printStackTrace()` en errores | `LegacyDatabase.java` |
| Errores 500 | `message` = `error.getMessage()` y `exception` = nombre de clase | `AuthResource.java` |
| Configuración | Sin `AUTH_TOKEN_SECRET`; CORS con perfil `%prod` (fuera de alcance) | `application.properties` |
| Build | Quarkus BOM 3.27.4, Java 21, JaCoCo 0.8.12 en `verify`, surefire con `AUTH_DB_URL` de prueba | `pom.xml` |
| Pruebas | `AuthResourceTest` (`@QuarkusTest`): login 200, CORS. No hay pruebas de token, validación ni errores | `AuthResourceTest.java` |

Observaciones adicionales:

- Los reportes de `auth/target/surefire-reports` mencionan clases (`SignedTokenServiceTest`, `AuthServiceTest`, `PasswordHashServiceTest`) que **no existen en `auth/src/`**. Son artefactos de compilaciones anteriores y no se usan como referencia.
- `HALLAZGOS.md` y `HU-01.md` citan `SignedTokenService`, que tampoco existe en el código actual. Se trata como referencia histórica.
- Existe acoplamiento con `api/`: `api/app/main.py` valida el mismo formato Base64 legacy (cuatro partes separadas por `|`, con `WORKSHOP_SECRET_2026`). Ver riesgo R1.

## 2. Goals

- G1. Emitir un JWT HS256 con `sub`, `client`, `iat`, `exp`, y vigencia exacta de 3600 s (`specs/auth-token`).
- G2. Exigir `client` válido (1–64 caracteres `[A-Za-z0-9._-]`) con el orden de validación de la spec.
- G3. Leer el secreto de `AUTH_TOKEN_SECRET` (≥ 32 bytes) y fallar explícitamente si falta o es corto.
- G4. Derivar `expiresIn` de `exp` con tolerancia ≤ 5 s.
- G5. No exponer tokens, secretos ni contraseñas en respuestas ni logs.
- G6. Mantener sin cambios la ruta, los campos de éxito (`token`, `expiresIn`, `user`, `debug`), los mensajes `400`/`401` existentes y CORS.
- G7. Cobertura ≥ 85 % del código modificado, medida con JaCoCo.

## 3. Non-goals

- NG1. Refresh tokens, revocación, lista de tokens invalidados, renovación.
- NG2. Autorización por roles en `auth/` o en otros componentes.
- NG3. Cambiar persistencia: no se migra a parámetros preparados ni se reemplaza SQLite. La inyección SQL de `LegacyDatabase.findByCredentials` queda como hallazgo aparte (ver R5).
- NG4. Hashing de contraseñas (no pedido por el Issue).
- NG5. Cambios en CORS, en `%prod` ni en `api/` o `web/`.
- NG6. Rediseño general de la aplicación: solo se separan las capas necesarias para el login y el token.

## 4. Contratos protegidos

Estos contratos no deben cambiar salvo lo que fija el Issue #24:

| Contrato | Valor | Tratamiento |
|---|---|---|
| Ruta y método | `POST /auth/login`, `application/json` | Protegido |
| Éxito `200` | `token`, `expiresIn`, `user{username,displayName,role}`, `debug="authenticated-with-legacy-flow"` | Protegido; `token` cambia de formato (intencional) |
| `displayName` | Escapado HTML (`StringEscapeUtils.escapeHtml4`) | Protegido |
| `400` nulo | `{"message":"Request is null"}` | Protegido |
| `400` usuario | `{"message":"Username is required"}` | Protegido |
| `400` contraseña | `{"message":"Password is required"}` | Protegido |
| `400` client | Nuevo; ver decisión D5 | Intencional (Issue) |
| `401` | `{"message":"Invalid user {username} or password"}` | Protegido |
| `500` | `message` documentado en `ENDPOINTS.md` como `"Authentication failed"`; ver decisión D7 | Cambio sujeto a confirmación |
| CORS | `quarkus.http.cors.*` | Protegido, sin cambios |
| Orden de validación | request → username → password → client | Protegido por spec |

## 5. Componentes afectados

Propuesta de separación por capas dentro de `auth/src/main/java/com/bcp/workshop/auth/` (solo lo que el login necesita):

| Componente | Responsabilidad | Cambio |
|---|---|---|
| `AuthResource` | Adaptar HTTP: recibir `LoginRequest`, delegar, mapear a respuesta/código | Reducido a orquestación HTTP; sin `if` anidados |
| `LoginRequestValidator` (nuevo) | Validar request, username, password, client en orden; devolver el primer error | Nuevo; puro, testeable sin Quarkus |
| `AuthService` (nuevo) | Caso de uso `login`: validar, consultar `LegacyDatabase`, emitir token, armar datos de respuesta | Nuevo |
| `TokenService` (nuevo, reemplaza `LegacyTokenUtil`) | Emitir JWT HS256 con claims mínimos; calcular `expiresIn` | Nuevo |
| `TokenSecretConfig` (nuevo) | Leer `AUTH_TOKEN_SECRET`, validar longitud ≥ 32 bytes en arranque | Nuevo |
| `LegacyTokenUtil` | Formato Base64 con secreto literal | **Eliminar** cuando no queden referencias |
| `LoginRequest` | DTO | Sin cambios de forma; `client` pasa a validarse |
| `LegacyDatabase` | Persistencia SQLite | Solo ajuste de logging en `catch` (ver R5); SQL sin cambios |
| `application.properties` | Configuración | Añadir `auth.token.secret=${AUTH_TOKEN_SECRET}` sin valor por defecto |
| `pom.xml` | Dependencias y surefire | Añadir extensión JWT (ver D1) y `AUTH_TOKEN_SECRET` de prueba en `environmentVariables` de surefire |
| `docs/ENDPOINTS.md` | Contrato documentado | Actualizar token, `client`, secreto, algoritmo y duración |

Ninguna capa de `web/` ni `api/` se modifica en este cambio.

## 6. Decisiones técnicas y alternativas

### D1. Biblioteca para emitir JWT HS256

- **Elegida:** `io.quarkus:quarkus-smallrye-jwt-build` (versión gestionada por el BOM 3.27.4). Requiere justificación y revisión de dependencias, porque el gate de Dependency Review está activo.
- **Motivo:** mantenida por el mismo ecosistema Quarkus, versión alineada con el BOM, API de construcción de claims y firma HMAC.
- **Alternativa A — `io.jsonwebtoken:jjwt`:** madura y muy usada, pero versión fuera del BOM y una dependencia más para revisar.
- **Alternativa B — `javax.crypto` manual (HMAC-SHA256 + Base64URL):** sin dependencias, pero reimplementa firma y serialización; se descarta por riesgo de error criptográfico en un cambio de seguridad.
- **Verificación pendiente (spike):** confirmar en la versión 3.27.4 que la API permite fijar `iat` y `exp` explícitamente, forzar `alg=HS256` y firmar con clave simétrica de al menos 256 bits. Si no permite fijar `iat`, se usa el valor que genere la librería y se valida con la tolerancia de la spec.

### D2. Origen y forma del secreto

- **Elegida:** `auth.token.secret=${AUTH_TOKEN_SECRET}` en `application.properties`, **sin valor por defecto y sin perfil de laboratorio**. Quarkus no arranca si la variable no está definida, lo que cumple "fallo explícito" en todos los perfiles.
- **Validación de longitud:** `TokenSecretConfig` comprueba en arranque que la longitud en bytes UTF-8 sea ≥ 32. El mensaje de error indica el nombre de la variable y la longitud mínima, nunca el valor.
- **Alternativa A — perfil `%dev`/`%test` con secreto de laboratorio en `application.properties`:** cómodo para el taller, pero deja un secreto literal en el repositorio. Se descarta, aunque la spec permite un perfil de laboratorio.
- **Alternativa B — validar solo al login:** cumple la spec, pero retrasa el fallo a la primera petición. Se descarta en favor del fallo temprano.
- **Pruebas:** `pom.xml` define `AUTH_TOKEN_SECRET` de prueba (valor aislado, solo en surefire) en `environmentVariables`, igual que ya se hace con `AUTH_DB_URL`.

### D3. Claims y vigencia

- `sub` = `user.username`; `client` = valor validado; `iat` = instante `now` de la emisión; `exp` = `iat + 3600`.
- `TOKEN_TTL_SECONDS = 3600` como constante con nombre.
- **`role` en el token:** se propone **no incluirlo**, porque el Issue solo lista `sub`, `client`, `iat` y `exp`, y el rol ya viaja en `user.role` de la respuesta. Queda como decisión pendiente (ver pregunta abierta Q1 del proposal).
- Un `java.time.Clock` se inyecta en `TokenService` para que las pruebas controlen `now` sin esperas.

### D4. `expiresIn`

- Se calcula en `AuthService` como `exp - now` usando el mismo `Clock` con que se emitió el token. Así la diferencia con `exp - now` medida después queda dentro de 5 s (en la práctica, 0 s).
- Alternativa descartada: mantener el literal `3600`. Cumple con el valor actual, pero no con la spec de coherencia con `exp`.

### D5. Validación de `client`

- Orden: request nula → username → password → client (spec).
- Regla: `client` no nulo, no vacío tras `trim`, longitud 1–64 y que coincida con `^[A-Za-z0-9._-]+$`.
- Respuesta: `400` con `{"message": ...}`, igual que las validaciones existentes.
- **Mensajes propuestos (pendientes de confirmación, Q2 del proposal):** `"Client is required"` cuando está ausente o vacío, y `"Client is invalid"` cuando excede la longitud o tiene caracteres no permitidos.
- Nota sobre `trim`: la spec pide rechazar "solo espacios". Sin `trim` el valor `" "` tendría longitud 1 y fallaría por caracteres, así que el resultado es el mismo, pero la regla explícita evita ambigüedad.

### D6. Ubicación de la validación

- `LoginRequestValidator` devuelve un resultado con el primer error (código HTTP + mensaje) o "válido". Así `AuthResource` no contiene `if` anidados y la validación se prueba sin Quarkus.
- Alternativa: Bean Validation (`@NotBlank`, `@Pattern`). Se descarta porque el orden de validación y los mensajes exactos existentes no coinciden con su salida por defecto, y se perdería el control del orden.

### D7. Errores `500`

- **Propuesta:** `message` fijo `"Authentication failed"` (coincide con `ENDPOINTS.md`) y no devolver el nombre de clase de la excepción.
- **Riesgo que se evita:** el `message` actual viene de `error.getMessage()`, que en fallos de firma o de configuración podría incluir valores sensibles.
- **Cambio de contrato:** se retira el campo `exception`. Se mantiene `message`. Requiere confirmación (Q3 del proposal).

### D8. Verificación en pruebas

- Las pruebas no usan la misma librería para emitir y para verificar. Un verificador HMAC-SHA256 independiente en el código de prueba (`javax.crypto`) comprueba firma, `alg` y claims. Así se evita comprobar la librería contra sí misma.

## 7. Estrategia incremental

Cada paso debe quedar verde antes del siguiente. Ninguno cambia el contrato sin que la prueba previa lo haya fijado.

1. **Pruebas de caracterización (sin cambio de comportamiento).** Ampliar `AuthResourceTest` para fijar el contrato actual: `200` con `token`/`expiresIn`/`user`/`debug`; `400` para cuerpo nulo, usuario vacío, contraseña vacía (mensajes exactos); `401` con mensaje exacto; `POST` sin `Content-Type` o con cuerpo inválido según el comportamiento real observado. Ejecutar `python tools/lab.py test auth`.
2. **Extraer validación y servicio (refactor puro).** Crear `LoginRequestValidator` y `AuthService` con la validación existente. Las pruebas del paso 1 deben seguir verdes.
3. **Validar `client` (primer cambio de comportamiento).** Añadir la regla D5. Pruebas nuevas: ausente, vacío, espacios, 64 caracteres (OK), 65 caracteres, caracteres no permitidos (`"mi cliente"`, `ñ`, `/`).
4. **Configurar el secreto.** Añadir `TokenSecretConfig`, la propiedad `${AUTH_TOKEN_SECRET}` y la variable de prueba en surefire. Pruebas unitarias: secreto de 31 bytes falla, de 32 pasa, mensaje sin valor del secreto. Prueba de arranque sin variable: se valida con un intento de build/arranque controlado, sin modificar el entorno global.
5. **Emitir JWT.** Añadir `TokenService` con `Clock` y la librería de D1. Pruebas con verificador independiente: `alg=HS256`, claims, `exp - iat = 3600`, firma correcta, firma alterada rechazada, secreto distinto rechazado, `alg=none` rechazado (el verificador de prueba solo acepta HS256), token vencido rechazado (`Clock` en el pasado).
6. **Calcular `expiresIn` desde `exp`.** Prueba: `|expiresIn - (exp - now)| ≤ 5`.
7. **Sanear errores y logs.** Cambiar el `500` según D7. En `LegacyDatabase`, sustituir `printStackTrace()` por un log que incluya solo la clase de excepción, sin SQL ni contraseña (ver R5). Pruebas: el cuerpo de `400/401/500` no contiene la contraseña; captura de logs no contiene token, secreto ni contraseña.
8. **Retirar `LegacyTokenUtil`.** Solo cuando `grep` confirme que no quedan referencias en `auth/`.
9. **Documentación y cobertura.** Actualizar `docs/ENDPOINTS.md` (token, `client`, `AUTH_TOKEN_SECRET`, algoritmo, vigencia). Ejecutar `python tools/lab.py test auth` y revisar JaCoCo: ≥ 85 % en clases modificadas.

## 8. Pruebas de caracterización necesarias antes de modificar comportamiento crítico

Antes del paso 3 deben existir pruebas que fijen:

- Respuesta `200` completa (`token`, `expiresIn`, `user.username|displayName|role`, `debug`) y `displayName` escapado HTML.
- `400` exactos: `Request is null`, `Username is required`, `Password is required`, y el orden de validación.
- `401` exacto con `Invalid user {username} or password`, para usuario inexistente y contraseña incorrecta.
- Ruta, método y CORS (las pruebas actuales ya cubren orígenes aprobados y no aprobados).

Antes del paso 5 deben existir pruebas que fijen que la respuesta no contiene contraseña en ningún código de error.

## 9. Riesgos y trade-offs

| # | Riesgo | Impacto | Mitigación / decisión |
|---|---|---|---|
| R1 | **`api/` valida el formato Base64 legacy** (`api/app/main.py`, `legacy_identity`). Los tokens nuevos serán rechazados con `401` por `api/`. | Alto: rompe la autenticación de `api/` en cuanto se despliega `auth/`. | **Bloqueante.** Hay que decidir: (a) ampliar alcance para migrar `api/` a verificar JWT, (b) coordinar una ventana de corte, o (c) mantener compatibilidad temporal. No se modifica `api/` sin aprobación. |
| R2 | `web/` debe enviar `client` obligatorio. | Medio: login falla con `400` si el frontend no lo envía. | Confirmar con el frontend antes de desplegar (Q5 del proposal). No se revisa `web/` en este alcance. |
| R3 | Sin `AUTH_TOKEN_SECRET`, la aplicación no arranca en ningún perfil. | Medio: el entorno local y el de CI deben definir la variable. | Documentar en `ENDPOINTS.md`; `pom.xml` define valor de prueba para surefire. Trade-off aceptado frente a un secreto literal. |
| R4 | Dependencia nueva (`quarkus-smallrye-jwt-build`). | Medio: superficie de revisión y Dependency Review. | Justificación en D1; no silenciar el gate. |
| R5 | **`LegacyDatabase` puede filtrar la contraseña en logs.** `findByCredentials` concatena la contraseña en el SQL y `printStackTrace()` puede imprimir el texto de la sentencia en errores de SQLite. Viola la spec de logs. | Alto para la spec de logs. | Cambio mínimo en el `catch`: loguear solo la clase de excepción. La inyección SQL en sí queda fuera de alcance y se registra como hallazgo aparte, según el proposal. **Requiere confirmación** porque toca persistencia. |
| R6 | Inyección SQL en `findByCredentials` (ya existente). | Alto, fuera de alcance. | Hallazgo separado; no se corrige en HU-01. |
| R7 | La respuesta `401` refleja el `username` recibido. | Bajo-medio, fuera de alcance. | Se preserva el contrato exacto; registrar como hallazgo. |
| R8 | Tolerancia de 5 s con reloj desincronizado entre instancias. | Bajo. | Se calcula con el mismo `Clock` en el mismo nodo; no hay estado compartido. |
| R9 | Quitar `role` del token puede romper consumidores que lo lean (hoy solo Base64). | Bajo-medio. | Pendiente Q1; si se decide conservarlo, se añade como claim adicional sin cambiar los obligatorios. |
| R10 | Secreto corto o débil en producción. | Alto. | Validación de ≥ 32 bytes en arranque (D2). |

**Trade-offs principales:**

- Fallo temprano (arranque) frente a laboratorio cómodo: se elige seguridad; el taller debe exportar la variable.
- Librería Quarkus frente a jjwt: se elige coherencia con el BOM; se acepta una dependencia nueva para revisar.
- Refactor en capas frente a cambio mínimo: se separa solo lo que el login necesita, sin rediseñar la aplicación.

## 10. Rollback

- **Código:** revertir el commit o la rama del cambio restaura `LegacyTokenUtil` y el comportamiento previo. No hay migraciones de datos ni cambios de esquema SQLite, así que el rollback es limpio.
- **Tokens:** los tokens JWT emitidos son stateless y no se persisten; al volver a la versión anterior, los clientes deben iniciar sesión de nuevo.
- **Configuración:** `AUTH_TOKEN_SECRET` puede quedar definido sin efecto en la versión anterior.
- **Consumidores:** si `api/` ya fue migrado (R1), el rollback debe revertir también ese cambio. Por eso R1 debe resolverse antes del despliegue, no después.
- **Criterio de rollback:** fallo de login exitoso en el entorno de prueba, `api/` rechazando tokens válidos, o logs con secretos o contraseñas.

## 11. Preguntas abiertas que bloquean la implementación

1. **Q1 — `role` en el token:** ¿se omite (recomendado) o se conserva como claim adicional?
2. **Q2 — mensajes de `client`:** ¿se aprueban `"Client is required"` y `"Client is invalid"`?
3. **Q3 — `exception` en `500`:** ¿se retira (recomendado) y se fija `message = "Authentication failed"`?
4. **Q4 — secreto:** ¿se confirma mínimo de 32 bytes y ausencia total de valor por defecto, incluido en laboratorio?
5. **Q5 — consumidores:** ¿cómo se resuelve R1 (`api/`) y R2 (`web/` envía `client`)?
6. **Q6 — R5:** ¿se autoriza el ajuste de logging en `LegacyDatabase` dentro de HU-01, aunque toque persistencia?
