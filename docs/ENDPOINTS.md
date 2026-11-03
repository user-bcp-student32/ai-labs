# Contratos de endpoints

## auth

### `POST /auth/login`

- Entrada JSON: `username` y `password`.
- Respuesta `200`: `token`, `expiresIn` y `user` (`username`, `displayName`, `role`); se conserva el campo `debug` existente.
- El token es JWT HS256 con `iss=workshop-auth`, `aud=workshop-api`, `sub`, `role`, `iat` y `exp`.
- Respuestas observadas: `400` para campos obligatorios ausentes y `401` para credenciales inválidas.

## api

Las rutas protegidas aceptan `Authorization: Bearer <JWT>`. Un token legado Base64 ya no es válido. El verificador exige firma HS256 y los claims `iss=workshop-auth`, `aud=workshop-api`, `sub`, `role`, `iat` y `exp`. Tanto auth como api requieren la variable de entorno `WORKSHOP_JWT_SECRET`, de al menos 32 bytes.

| Método y ruta | Entrada | Respuesta de éxito |
|---|---|---|
| `GET /api/tasks` | Query opcional `owner` | `200` lista `{id,title,description,status,owner,priority,createdAt}` |
| `POST /api/tasks` | JSON `title`, `description`, `status`, `priority`, `owner` | `201` tarea creada |
| `GET /api/summary` | Query opcional `owner` | `200` `{owner,total,byStatus,highPriorityOpen}` |
| `POST /api/requirements` | Multipart `file` | `201` `{owner,fileName,storedAt,size}` |
| `GET /api/backend-health` | Ninguna | `200` `{requestedBy,status,body}` |

Las rutas conservan `401` para autenticación inválida. El alcance no cambia las reglas de autorización por `owner`.
