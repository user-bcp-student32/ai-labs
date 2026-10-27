# Endpoints

## auth

### Estado del contrato

- **Contrato observado:** lo descrito a continuación se deriva del código actual de `auth/` y de sus pruebas.
- **Contrato acordado:** no se encontró evidencia de aprobación de consumidores externos ni de una revisión humana de integración. Confirmar compatibilidad antes de tratar este documento como contrato acordado.
- **Riesgos y preguntas pendientes:** la allow-list CORS sigue abierta a cualquier origen; no se verificaron los consumidores del token; el endpoint de salud lo proporciona Quarkus y su respuesta no está caracterizada en las pruebas actuales.

## `POST /auth/login`

### 1. Qué hace

Autentica las credenciales del usuario y, si son válidas, devuelve los datos básicos de la cuenta y un token para usar en integraciones posteriores. Evidencia: `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:27-42`.

### 2. Autenticación y headers

- No requiere un token previo según la ruta implementada; no hay una anotación de seguridad en `AuthResource`.
- Envía `Content-Type: application/json`; la ruta consume y produce JSON (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:14-16`).
- No se documenta un header `Authorization` requerido para esta operación. La seguridad adicional que pudiera aplicar un proxy o gateway queda fuera de este componente.

### 3. Request

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `username` | string | Sí | Nombre de usuario. No puede ser nulo ni estar vacío o compuesto solo por espacios. |
| `password` | string | Sí | Contraseña. No puede ser nula ni estar vacía o compuesta solo por espacios. |
| `client` | string | No | Campo aceptado por el DTO; actualmente no se valida ni se usa en el servicio. |

Evidencia de campos: `auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java:3-6`. Evidencia de validación: `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:44-53`.

Ejemplo JSON completo con las credenciales ficticias usadas por la prueba de integración:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

La prueba envía este request en `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:38-40`.

### 4. Response exitosa

**Estado:** `200 OK`.

| Campo | Tipo | Descripción |
|---|---|---|
| `token` | string | JWT firmado con HS256. Su valor se genera para cada login. |
| `expiresIn` | integer | Vigencia del token en segundos; actualmente `3600`. |
| `user` | object | Datos básicos del usuario autenticado. |
| `user.username` | string | Nombre de usuario. |
| `user.displayName` | string | Nombre para mostrar; se escapa como HTML antes de incluirlo. |
| `user.role` | string | Rol de la cuenta. |
| `debug` | string | Valor fijo actual: `authenticated-with-legacy-flow`. |

Evidencia del DTO: `auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java:3` y `auth/src/main/java/com/bcp/workshop/auth/LoginUserResponse.java:3`. Evidencia de valores y mapeo: `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:11,32-42`.

Ejemplo de estructura JSON completa (el token es dinámico; el valor indicado es un marcador documental, no un token utilizable):

```json
{
  "token": "<JWT HS256 generado por la instancia>",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

La prueba confirma el estado y los campos de respuesta en `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:38-50`; también comprueba firma HS256, sujeto, rol y expiración en `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:147-174`. Para publicar un ejemplo de respuesta capturado y completo, queda pendiente obtenerlo de una instancia configurada; no se fija un token en este documento porque sería dinámico y expiraría.

### 5. Respuestas de error

#### `400 Bad Request` — usuario ausente o vacío

Ocurre si `username` es nulo, vacío o solo contiene espacios.

```json
{
  "message": "Username is required"
}
```

Comprobado por `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:65-73`.

#### `400 Bad Request` — contraseña ausente o vacía

Ocurre si `password` es nula, vacía o solo contiene espacios.

```json
{
  "message": "Password is required"
}
```

Comprobado por `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:76-84`.

#### `400 Bad Request` — request nulo

El servicio rechaza un request nulo y la ruta mapea la excepción de validación a `400`. El cuerpo definido por el código es:

```json
{
  "message": "Request is null"
}
```

Evidencia: `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:44-47` y `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:31-34`. La prueba de request nulo es de servicio, no HTTP (`auth/src/test/java/com/bcp/workshop/auth/AuthServiceTest.java:15-21`).

#### `401 Unauthorized` — credenciales inválidas

Ocurre cuando no existe el usuario o la contraseña no coincide. El mensaje actual incluye el nombre de usuario enviado.

```json
{
  "message": "Invalid user student or password"
}
```

Comprobado por `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:54-62`; el formato se construye en `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:35-38`.

#### `500 Internal Server Error` — error interno de autenticación

Ocurre cuando el servicio lanza `AuthenticationServiceException`; el detalle se registra en el servidor y el cuerpo expone un mensaje genérico.

```json
{
  "message": "Authentication failed"
}
```

Evidencia: `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:39-43` y `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:177-195`.

**Pregunta abierta:** no hay pruebas que caractericen el cuerpo de error del framework para JSON mal formado, un `Content-Type` no admitido u otros errores previos a la invocación del método. No se afirma aquí un estado o cuerpo para esos casos.

### 6. Notas de seguridad

- El JWT se firma con HS256, incluye `sub`, `role` y expiración, y dura `3600` segundos (`auth/src/main/java/com/bcp/workshop/auth/SignedTokenService.java:16-17,32-39`). La firma aporta integridad, no cifrado del contenido del token.
- El secreto se suministra con `AUTH_TOKEN_SECRET` (`auth/src/main/resources/application.properties:9`) y el servicio exige un mínimo de 32 bytes (`auth/src/main/java/com/bcp/workshop/auth/SignedTokenService.java:17,23-27`). No incluir el secreto en clientes ni en este documento.
- La respuesta de `401` revela el nombre de usuario recibido (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:35-38`).
- La respuesta de éxito incluye el campo `debug` con el valor `authenticated-with-legacy-flow`; confirmar si debe permanecer en el contrato de integración.
- CORS está habilitado para cualquier origen (`auth/src/main/resources/application.properties:4-7`). La allow-list aprobada sigue pendiente, tal como se registra en `HALLAZGOS.md` (H-AUTH-04).
- La compatibilidad de consumidores externos con el formato interno del JWT no se verificó; revisar antes de cambiar claims, firma o vigencia.

## `GET /q/health`

### 1. Qué hace

Endpoint de salud de Quarkus destinado a que una operación o plataforma de despliegue consulte el estado del servicio. La dependencia que habilita SmallRye Health aparece en `auth/pom.xml:39-42`; no hay un recurso de salud propio en el código de `auth/`.

### 2. Autenticación y headers

No se encontró configuración de autenticación específica para este endpoint en `auth/`. La protección que pudiera aplicar un proxy o gateway de despliegue no está comprobada. No se ha identificado un header de autenticación requerido por el componente.

### 3. Request

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| — | — | No aplica | Es una petición `GET`; no se ha identificado un cuerpo ni campos de request propios del componente. |

Ejemplo de petición:

```http
GET /q/health HTTP/1.1
Host: localhost:8080
Accept: application/json
```

La ruta y el formato exacto de respuesta los proporciona la extensión Quarkus; no hay una prueba de integración para este endpoint en `auth/src/test/`.

### 4. Response exitosa

**Estado y cuerpo:** pregunta abierta. No se capturó una respuesta real ni hay código o prueba del componente que confirme el estado, los campos o el ejemplo JSON exacto. No se afirma un payload por inferencia de la dependencia.

### 5. Respuestas de error

**Estados, condiciones y cuerpos:** pregunta abierta. El componente no define ni prueba respuestas de error propias para `/q/health`; confirmar mediante una ejecución de la aplicación con la versión actual de Quarkus/SmallRye Health antes de fijar ejemplos.

### 6. Notas de seguridad

Confirmar si el endpoint debe ser accesible públicamente o limitarse a la red de operación. La configuración de CORS de auth permite cualquier origen (`auth/src/main/resources/application.properties:4-7`), pero no se ha verificado cómo interactúa con el endpoint de salud ni si el despliegue agrega controles.

## Documentos consultados

- `README.md`
- `RETO.md`
- `docs/GOOD_PRACTICES.md`
- `HALLAZGOS.md`
- `PLAN.md`
- Código, configuración y pruebas existentes de `auth/` citados en cada sección
