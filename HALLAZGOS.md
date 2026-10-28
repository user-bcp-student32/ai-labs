# Hallazgos

## Contrato observado de auth en Discovery

`docs/ENDPOINTS.md` no existe. Lo siguiente se deriva del código inspeccionado
y antes de implementar los cambios; no representa un contrato acordado.

- `POST /auth/login` consume y produce JSON: `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:15-24`.
- La petición acepta `username`, `password` y `client`; solo valida que la petición, `username` y `password` no sean nulos o vacíos. `client` no se valida ni se usa: `auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java:3-6`, `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:26-35`.
- Ante credenciales no encontradas responde `401` con un mensaje que incluye el nombre de usuario. Ante excepciones que escapen al método devuelve `500` con el mensaje y la clase de la excepción: `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:37,54-58`.
- El éxito responde `200` con `token`, `expiresIn`, `user` (`username`, `displayName`, `role`) y `debug`: `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:39-49`.

## Flujo observado en Discovery (antes de cambios)

- `AuthResource.login` valida la petición y pasa `username` y `password` a `LegacyDatabase.findByCredentials`: `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:24-35`.
- SQLite usa `AUTH_DB_URL` o, si no está definida, `jdbc:sqlite:./data/workshop.db`. Al iniciar se crea `legacy_users` y se insertan las cuentas ficticias con contraseñas en texto plano: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:15,18-24`.
- La búsqueda forma el SQL concatenando las credenciales y lo ejecuta con `Statement`. Si falla, captura la excepción y devuelve `null`; por ello la ruta la trata como credenciales no encontradas y responde `401`: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:31-42`, `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:35-37`.
- Si la búsqueda devuelve un usuario, `LegacyTokenUtil.create` construye el token y `AuthResource` arma la respuesta: `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:39-49`, `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8-15`.
- La configuración HTTP activa CORS y configura `/.*/` como origen permitido; en producción enlaza el host a `0.0.0.0`: `auth/src/main/resources/application.properties:1-7`.

## Estado tras ejecutar PLAN.md

- `POST /auth/login` conserva el JSON de éxito observado (`token`, `expiresIn`, `user` y `debug`) y sus estados de validación; los errores de autenticación interna ahora responden un mensaje genérico.
- La búsqueda por usuario usa parámetros enlazados; las contraseñas nuevas y migradas se guardan como hashes PBKDF2 con salt.
- El token ahora es JWT HS256 con `sub`, `role` y expiración; el secreto se requiere mediante `AUTH_TOKEN_SECRET`.
- CORS sigue permitiendo cualquier origen (`auth/src/main/resources/application.properties:4-5`); no se configuró una allow-list sin recibir los orígenes aprobados.
- `mvn verify` pasó con 21 pruebas. La cobertura agregada por líneas de las clases de producción modificadas fue 167/182 (91.8%); CodeQL y Dependency Review remotos no se ejecutaron.

## Matriz de seguimiento

Estados iniciales conforme a Discovery. Las versiones de dependencias están
confirmadas en el manifiesto, pero no se consultaron advisories externos.

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-AUTH-01 | auth | Concatenación de credenciales en consulta SQL | Vulnerabilidad | Resuelto: el repositorio consulta por usuario con `PreparedStatement` y parámetro enlazado; la verificación de contraseña se realiza fuera del SQL. Las pruebas cubren una credencial ficticia con comilla. | `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:59-65`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:29-30` | alta | Resuelto |
| H-AUTH-02 | auth | Contraseñas almacenadas en texto plano | Vulnerabilidad | Resuelto: las cuentas iniciales y nuevas usan PBKDF2 con salt; la inicialización migra valores legibles en una transacción. La prueba local verifica el login migrado y que la base contenga el hash. | `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:37-54,82-103,119-124`; `auth/src/main/java/com/bcp/workshop/auth/PasswordHashService.java:17-24` | alta | Resuelto |
| H-AUTH-03 | auth | Token basado en Base64 con secreto embebido y sin firma visible | Vulnerabilidad | Resuelto: se emite JWT HS256 con `sub`, `role` y expiración. Se conservan los nombres de campos JSON; la compatibilidad de consumidores fuera de `auth/` no se verificó por el alcance acordado. | `auth/src/main/java/com/bcp/workshop/auth/SignedTokenService.java:31-39`; `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:28-30` | alta | Resuelto |
| H-AUTH-04 | auth | Secreto embebido y CORS configurado para cualquier origen | Vulnerabilidad | Parcial: el secreto ya se carga de `AUTH_TOKEN_SECRET`, sin valor de respaldo; CORS aún permite cualquier origen. No se recibió la allow-list aprobada, por lo que este hallazgo permanece pendiente. | `auth/src/main/resources/application.properties:4-5,9`; `auth/src/main/java/com/bcp/workshop/auth/SignedTokenService.java:20-27` | media | Pendiente |
| H-AUTH-05 | auth | La respuesta de error revela detalles internos | Vulnerabilidad | Resuelto: errores internos tipados se registran en el servidor y el cliente recibe un mensaje genérico, sin nombre de clase ni detalle de la excepción. | `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:39-43` | media | Resuelto |
| H-AUTH-06 | auth | Revisar advisories de Quarkus y Commons Text | Sugerencia | Parcial: Commons Text se actualizó de 1.9 a 1.15.0 tras revisar la exposición conocida; no se confirmó un advisory aplicable a Quarkus 3.27.4 y no se ejecutaron CodeQL ni Dependency Review remotos. Mantener pendiente hasta completar esos controles. | `auth/pom.xml:13,49-50` | baja | Pendiente |
| H-AUTH-07 | auth | Resource concentra validación, persistencia, token y respuesta | Refactor | Resuelto: validación/orquestación, persistencia y emisión de token se separaron en Resource, Service, Repository y servicio de tokens; se usan DTOs para la respuesta. Las pruebas de contrato y de servicio pasan. | `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:24-43`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:18-37`; `auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:5-7` | baja | Resuelto |
