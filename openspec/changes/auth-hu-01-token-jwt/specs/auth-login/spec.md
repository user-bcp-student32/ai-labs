# Spec Delta: auth-login

## Purpose

Define el contrato observable de `POST /auth/login` del componente `auth/`: validación de la solicitud, verificación de credenciales, respuesta exitosa y errores, preservando el contrato actual salvo los cambios que fija el Issue #24 (HU-01).

## ADDED Requirements

### Requirement: Login exitoso conserva los campos de respuesta
El sistema MUST responder `200 OK` a un login con credenciales válidas y `client` válido, con los campos `token`, `expiresIn` y `user`. `user` MUST contener `username`, `displayName` y `role`. El campo `debug` se mantiene sin cambios.

#### Scenario: Login válido
- **WHEN** se envía `POST /auth/login` con `username` y `password` válidos y `client` de formato válido
- **THEN** la respuesta es `200 OK` y el cuerpo contiene `token`, `expiresIn` y `user` con `username`, `displayName` y `role`

#### Scenario: Campo debug preservado
- **WHEN** un login es exitoso
- **THEN** el cuerpo de la respuesta contiene el campo `debug` con el mismo valor que antes de este cambio

### Requirement: Solicitud nula se rechaza
El sistema MUST responder `400 Bad Request` con `message` igual a `Request is null` cuando el cuerpo de la solicitud está ausente.

#### Scenario: Cuerpo ausente
- **WHEN** se envía `POST /auth/login` sin cuerpo
- **THEN** la respuesta es `400 Bad Request` con `{"message": "Request is null"}` y no contiene `token`

### Requirement: Usuario obligatorio
El sistema MUST responder `400 Bad Request` con `message` igual a `Username is required` cuando `username` es nulo, vacío o compuesto solo por espacios.

#### Scenario: Usuario vacío
- **WHEN** se envía `username` como cadena vacía o solo con espacios
- **THEN** la respuesta es `400 Bad Request` con `{"message": "Username is required"}` y no contiene `token`

### Requirement: Contraseña obligatoria
El sistema MUST responder `400 Bad Request` con `message` igual a `Password is required` cuando `password` es nulo, vacío o compuesto solo por espacios.

#### Scenario: Contraseña vacía
- **WHEN** se envía `password` como cadena vacía o solo con espacios
- **THEN** la respuesta es `400 Bad Request` con `{"message": "Password is required"}` y no contiene `token`

### Requirement: Campo client obligatorio y con formato definido
El sistema MUST exigir `client` como identificador de 1 a 64 caracteres del conjunto `[A-Za-z0-9._-]`. Una solicitud con `client` ausente, vacío, mayor de 64 caracteres o con otros caracteres MUST responder `400 Bad Request` con `message` de error de validación y MUST NOT emitir token.

#### Scenario: Client válido en su longitud máxima
- **WHEN** se envía `client` de 64 caracteres `[A-Za-z0-9._-]` con credenciales válidas
- **THEN** la respuesta es `200 OK` con `token`

#### Scenario: Client ausente
- **WHEN** se envía `POST /auth/login` con credenciales válidas y sin el campo `client`
- **THEN** la respuesta es `400 Bad Request` con un `message` de error de validación y no contiene `token`

#### Scenario: Client vacío o solo espacios
- **WHEN** se envía `client` como cadena vacía o solo con espacios
- **THEN** la respuesta es `400 Bad Request` con un `message` de error de validación y no contiene `token`

#### Scenario: Client demasiado largo
- **WHEN** se envía `client` de 65 caracteres
- **THEN** la respuesta es `400 Bad Request` con un `message` de error de validación y no contiene `token`

#### Scenario: Client con caracteres no permitidos
- **WHEN** se envía `client` con espacios, `/`, acentos o `ñ`, por ejemplo `"mi cliente"`
- **THEN** la respuesta es `400 Bad Request` con un `message` de error de validación y no contiene `token`

### Requirement: Orden de validación
El sistema MUST validar en este orden: solicitud nula, `username`, `password` y después `client`. Ante varios errores, MUST devolver el primero que aplique.

#### Scenario: Usuario y client inválidos
- **WHEN** se envía `username` vacío y `client` ausente
- **THEN** la respuesta es `400 Bad Request` con `{"message": "Username is required"}`

### Requirement: Credenciales incorrectas se rechazan
El sistema MUST responder `401 Unauthorized` cuando el usuario no existe o la contraseña no coincide, con `message` igual a `Invalid user {username} or password`, y MUST NOT emitir token.

#### Scenario: Contraseña incorrecta
- **WHEN** se envía un `username` existente con una contraseña incorrecta y `client` válido
- **THEN** la respuesta es `401 Unauthorized` con `{"message": "Invalid user {username} or password"}` y no contiene `token`

#### Scenario: Usuario inexistente
- **WHEN** se envía un `username` que no existe con `client` válido
- **THEN** la respuesta es `401 Unauthorized` y no contiene `token`

### Requirement: Error interno no expone datos sensibles
El sistema MUST responder `500 Internal Server Error` ante una excepción no controlada y MUST NOT incluir en el cuerpo de la respuesta ni en los errores tokens, secretos, contraseñas ni valores de `AUTH_TOKEN_SECRET`.

#### Scenario: Fallo interno al emitir token
- **WHEN** falla la emisión del token durante un login
- **THEN** la respuesta es `500 Internal Server Error`, el cuerpo tiene la clave `message` y no contiene el secreto, la contraseña ni ningún token

#### Scenario: Contraseña ausente en errores
- **WHEN** cualquier solicitud devuelve un error `400`, `401` o `500`
- **THEN** el cuerpo de la respuesta no contiene el valor de `password`

### Requirement: Endpoint sin cambios de ruta
El sistema MUST mantener `POST /auth/login` como ruta de login con entrada y salida en JSON, sin introducir endpoints nuevos en este cambio.

#### Scenario: Ruta y tipo de contenido
- **WHEN** se envía `POST /auth/login` con `Content-Type: application/json`
- **THEN** el endpoint responde con JSON y la ruta no cambia
