# Tareas: HU-01 — Token firmado con expiración y claims mínimos

**Issue:** #24 (HU-01) · **Componente:** `auth/` · **Insumos aprobados:** `proposal.md`, `specs/auth-login/spec.md`, `specs/auth-token/spec.md`, `design.md`

Este checklist es la guía de implementación de este cambio OpenSpec. El Issue #24 sigue siendo la unidad de trabajo del producto.

**Reglas de ejecución**
- Comandos de prueba desde la raíz: `python tools/lab.py test auth`. Git desde la raíz de `ai-labs`.
- No tocar `api/` ni `web/`. No tocar CORS, persistencia SQL (incluida la inyección de `LegacyDatabase`), ni renovación, refresh, revocación o roles.
- Cada tarea de implementación empieza por su prueba (en rojo) y termina con la suite de `auth/` en verde.
- Ninguna tarea se ejecuta sin que sus decisiones bloqueantes (sección 2) estén aprobadas.

---

## 1. Caracterización del contrato actual (sin cambio de comportamiento)

Objetivo: fijar el comportamiento actual antes de tocar código. Estas pruebas deben pasar sin modificar `AuthResource` ni `LegacyDatabase`.

- [x] 1.1 Leer `AuthResource`, `LoginRequest`, `LegacyTokenUtil`, `LegacyDatabase` y `AuthResourceTest` para registrar el comportamiento real (no modificar nada).
- [x] 1.2 Caracterizar `200`: campos `token`, `expiresIn`, `user` (`username`, `displayName`, `role`) y `debug = "authenticated-with-legacy-flow"`, usando un usuario de la base de prueba.
- [x] 1.3 Caracterizar que `displayName` sale escapado HTML (usuario de prueba con caracteres como `<`, `&`).
- [x] 1.4 Caracterizar `400` con mensajes exactos: `Request is null` (sin cuerpo), `Username is required` (nulo, vacío y solo espacios) y `Password is required` (nulo, vacío y solo espacios).
- [x] 1.5 Caracterizar el orden de validación: `username` inválido con `password` inválida devuelve `Username is required`.
- [x] 1.6 Caracterizar `401` con `Invalid user {username} or password` para contraseña incorrecta y para usuario inexistente. Verificar que no hay `token` en el cuerpo.
- [x] 1.7 Registrar (sin imponer) el comportamiento real ante `Content-Type` ausente o JSON inválido, para que los cambios posteriores no lo alteren sin querer.
- [x] 1.8 Confirmar que las pruebas de ruta `POST /auth/login` y CORS existentes siguen en `AuthResourceTest`.
- [x] 1.9 Ejecutar `python tools/lab.py test auth`. Resultado esperado: todas las pruebas de caracterización en verde.
- [x] 1.10 Registrar la línea base como evidencia `H-AUTH-01` en la raíz de `ai-labs`, conservando las secciones existentes.

---

## 2. Decisiones bloqueantes y spike técnico

Ninguna tarea de las secciones 3 a 10 empieza sin la decisión que la bloquea. Las respuestas se piden al revisor humano; no se asumen.

- [ ] 2.1 **Q4 — secreto:** confirmar mínimo de 32 bytes y ausencia total de valor por defecto, incluido en laboratorio. *Bloquea la sección 5.*
- [ ] 2.2 **Q2 — mensajes de `client`:** aprobar `"Client is required"` (ausente o vacío) y `"Client is invalid"` (longitud o caracteres). *Bloquea la sección 4.*
- [ ] 2.3 **Q1 — claim `role`:** confirmar que el token no incluye `role` (recomendado). *Bloquea la sección 6.*
- [ ] 2.4 **Q3 — `500`:** confirmar retirar el campo `exception` y fijar `message = "Authentication failed"`. *Bloquea la sección 8.*
- [ ] 2.5 **Q6 — R5:** autorizar el ajuste de logging en `LegacyDatabase` (`printStackTrace()` → log solo con la clase de excepción), sin tocar el SQL. *Bloquea la tarea 8.4.*
- [ ] 2.6 **Spike D1:** en la versión 3.27.4 del BOM, comprobar con `quarkus-smallrye-jwt-build` que se pueden fijar `iat` y `exp`, forzar `alg=HS256` y firmar con clave de al menos 256 bits. Registrar el resultado en el cambio. Si no se pueden fijar `iat`/`exp`, documentar el ajuste que requiere la spec. *Bloquea la sección 6.*
- [ ] 2.7 **R4 — dependencia:** justificar la nueva dependencia (motivo, versión del BOM, alternativas descartadas) para la revisión de Dependency Review. No silenciar el gate. *Bloquea la tarea 6.3.*
- [ ] 2.8 **Q5 / R1 / R2 — consumidores:** documentar la estrategia para `api/` (que valida el formato Base64 legacy) y para `web/` (que debe enviar `client`). Es requisito de integración y despliegue, no de la implementación local. No modificar `api/` ni `web/` sin aprobación.

---

## 3. Refactor puro: validación y servicio (sin cambio de comportamiento)

Objetivo: separar capas manteniendo las pruebas de la sección 1 en verde.

- [x] 3.1 Crear pruebas unitarias de `LoginRequestValidator` (sin Quarkus) para request nula, `username` y `password` con el orden y los mensajes existentes.
- [x] 3.2 Crear `LoginRequestValidator` con la validación existente. Devuelve el primer error (código HTTP y mensaje) o "válido".
- [x] 3.3 Crear `AuthService` con el caso de uso `login` (validar, consultar `LegacyDatabase`, armar datos de respuesta) reutilizando el token actual.
- [x] 3.4 Reducir `AuthResource` a adaptador HTTP: sin `if` anidados y sin lógica de validación ni de acceso a datos.
- [x] 3.5 Ejecutar `python tools/lab.py test auth`. Las pruebas de caracterización de la sección 1 deben seguir en verde sin cambiar sus expectativas.

---

## 4. Validación de `client` (primer cambio de comportamiento)

*Requiere 2.2 aprobada.*

- [ ] 4.1 Escribir pruebas en rojo: `client` ausente, vacío, solo espacios, 65 caracteres (`400`), `"mi cliente"`, `ñ` y `/` (`400`), y 64 caracteres `[A-Za-z0-9._-]` con credenciales válidas (`200` con `token`).
- [ ] 4.2 Escribir prueba de orden: `username` vacío con `client` ausente devuelve `Username is required`.
- [ ] 4.3 Implementar la regla en `LoginRequestValidator`: no nulo, no vacío tras `trim`, longitud 1–64 y patrón `^[A-Za-z0-9._-]+$`. Constantes con nombre para la longitud máxima y el patrón.
- [ ] 4.4 Verificar que en los `400` de `client` no aparece `token`. Ejecutar `python tools/lab.py test auth` (verde).

---

## 5. Configuración del secreto `AUTH_TOKEN_SECRET`

*Requiere 2.1 aprobada.*

- [ ] 5.1 Escribir pruebas de `TokenSecretConfig` en rojo: 31 bytes UTF-8 falla, 32 bytes pasa, y el mensaje de error nombra la variable y la longitud mínima sin mostrar el valor.
- [ ] 5.2 Implementar `TokenSecretConfig`, que valida la longitud en bytes UTF-8 al arrancar.
- [ ] 5.3 En `application.properties`, añadir `auth.token.secret=${AUTH_TOKEN_SECRET}` sin valor por defecto y sin perfil de laboratorio.
- [ ] 5.4 En `pom.xml`, definir `AUTH_TOKEN_SECRET` de prueba (valor aislado, de al menos 32 bytes y no real) solo en `environmentVariables` de surefire, igual que `AUTH_DB_URL`.
- [ ] 5.5 Validar el fallo explícito sin variable: ejecutar el arranque en un proceso hijo con `AUTH_TOKEN_SECRET` ausente, sin modificar el entorno global. Debe fallar sin emitir tokens y sin mostrar secretos.
- [ ] 5.6 Ejecutar `python tools/lab.py test auth` (verde).

---

## 6. Emisión de JWT HS256 con claims mínimos

*Requiere 2.3, 2.6 y 2.7 aprobadas y 5.x completada.*

- [ ] 6.1 Escribir un verificador independiente en el código de prueba con `javax.crypto` (HMAC-SHA256 y Base64URL). Solo acepta HS256 y no usa la librería de emisión.
- [ ] 6.2 Escribir pruebas en rojo con el verificador: `alg=HS256`; claims `sub`, `client`, `iat` y `exp` con tipos correctos; `exp - iat = 3600`; `sub = student` y `client = angular-workshop` para ese caso; firma correcta; firma alterada rechazada; secreto distinto rechazado; `alg=none` y RS256 rechazados; token con `exp` en el pasado rechazado.
- [ ] 6.3 Agregar `quarkus-smallrye-jwt-build` (versión del BOM) en `pom.xml`, con la justificación de la tarea 2.7.
- [ ] 6.4 Implementar `TokenService` con `Clock` inyectable y `TOKEN_TTL_SECONDS = 3600`. `sub` = usuario validado, `client` = valor validado, `iat` = ahora, `exp` = `iat + 3600`.
- [ ] 6.5 Conectar `AuthService` con `TokenService` y reemplazar el formato Base64 por el JWT.
- [ ] 6.6 Ejecutar `python tools/lab.py test auth` (verde).

---

## 7. `expiresIn` derivado de `exp`

- [ ] 7.1 Escribir prueba en rojo con `Clock` fijo: `|expiresIn - (exp - now)| ≤ 5`, con un instante distinto del de la emisión para que el literal `3600` falle.
- [ ] 7.2 Calcular `expiresIn` en `AuthService` como `exp - now` con el mismo `Clock` de la emisión. Eliminar el literal `3600` de `AuthResource`.
- [ ] 7.3 Ejecutar `python tools/lab.py test auth` (verde).

---

## 8. Errores y logs sin datos sensibles

*Requiere 2.4 aprobada; 8.4 requiere 2.5.*

- [ ] 8.1 Escribir pruebas en rojo: el cuerpo de `400`, `401` y `500` no contiene la contraseña; el `500` tiene `message` y no tiene `exception`.
- [ ] 8.2 Escribir prueba de fallo de emisión simulado (`TokenService` lanza excepción): respuesta `500` con `message = "Authentication failed"` y sin token ni secreto.
- [ ] 8.3 Escribir pruebas de logs: capturar los logs de un login exitoso y de uno fallido; no deben contener token, valor de `AUTH_TOKEN_SECRET` ni contraseña.
- [ ] 8.4 *(Con 2.5 aprobada)* En `LegacyDatabase`, reemplazar `printStackTrace()` por un log con solo la clase de excepción. No cambiar el SQL ni la lógica de persistencia.
- [ ] 8.5 Implementar el `500` saneado en `AuthResource` según la decisión 2.4.
- [ ] 8.6 Confirmar que el `401` conserva su mensaje exacto (caracterización de 1.6). Registrar como hallazgo aparte que refleja el `username` (R7).
- [ ] 8.7 Ejecutar `python tools/lab.py test auth` (verde).

---

## 9. Retirar `LegacyTokenUtil`

- [ ] 9.1 Con `grep` sobre `auth/src/main`, confirmar que no quedan referencias a `LegacyTokenUtil` ni a `WORKSHOP_SECRET_2026` en el camino de emisión.
- [ ] 9.2 Eliminar `LegacyTokenUtil` y sus referencias en `auth/`.
- [ ] 9.3 Ejecutar `python tools/lab.py test auth` (verde). No modificar el parser Base64 de `api/` (queda cubierto por R1 en la tarea 2.8).

---

## 10. Documentación, cobertura y validaciones finales

- [ ] 10.1 Actualizar `docs/ENDPOINTS.md`: formato JWT HS256, claims, `client` obligatorio con su formato, vigencia de 3600 s, tolerancia de `expiresIn`, `500` saneado, y aviso de cambios incompatibles (formato del token y `client`).
- [ ] 10.2 Documentar `AUTH_TOKEN_SECRET` (nombre, mínimo de 32 bytes, obligatoria, sin valor por defecto) con un ejemplo de valor ficticio, sin secretos reales.
- [ ] 10.3 Ejecutar `python tools/lab.py test auth` y revisar el reporte JaCoCo de `auth/`. Meta: ≥ 85 % en `AuthResource`, `AuthService`, `LoginRequestValidator`, `TokenService`, `TokenSecretConfig`.
- [ ] 10.4 Revisar el diff desde la raíz: `git diff --check`, búsqueda de `WORKSHOP_SECRET_2026` y de patrones de secretos, y ausencia de tokens reales en pruebas o docs.
- [ ] 10.5 Confirmar con `git status` desde la raíz que no hay cambios en `api/` ni en `web/`.
- [ ] 10.6 Registrar la evidencia final como `H-AUTH-02` en la raíz de `ai-labs`, conservando las secciones existentes.

---

## 11. Gates de integración (fuera de la implementación local)

No se ejecutan en el Día 1. Solo ramas locales; sin `push`, sin PR y sin tags.

- [ ] 11.1 Revisión y aprobación humana del cambio y de las decisiones de la sección 2.
- [ ] 11.2 Resolver R1 (`api/`) antes de desplegar. Si `api/` no se migra en este alcance, el despliegue queda bloqueado o requiere ventana de corte aprobada.
- [ ] 11.3 Confirmar que `web/` envía `client` antes de desplegar (R2).
- [ ] 11.4 Pruebas reales en el entorno de prueba con `AUTH_TOKEN_SECRET` definido: login válido, `client` inválido y token verificable.
- [ ] 11.5 Verificar en la integración que CodeQL, Dependency Review y JaCoCo pasan sin silenciar alertas ni excluir clases.

---

## Fuera de alcance (no implementar en este cambio)

- Cambios en `api/` y `web/`, incluido el parser Base64 de `app/main.py`.
- Persistencia y SQL de `LegacyDatabase`, incluida la inyección SQL en `findByCredentials` (hallazgo aparte).
- CORS y perfil `%prod`.
- Hashing de contraseñas, refresh tokens, revocación y autorización por roles.
- Quitar o mantener el campo `debug` (se conserva sin cambios).
