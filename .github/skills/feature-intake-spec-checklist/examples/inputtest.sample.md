## Component
api

## Routes under test
- GET /api/tasks: query owner opcional; auth Bearer requerida.
- POST /api/tasks: body JSON con title, description, status, priority, owner (opcionales); auth Bearer requerida.
- GET /api/summary: query owner opcional; auth Bearer requerida.
- POST /api/requirements: multipart file; auth Bearer requerida.
- GET /api/backend-health: depende de AUTH_HEALTH_URL; simular, no llamar.

## Source of truth
- api/app/main.py
- api/app/repository.py
- api/tests/test_api.py
- prompts/api.md

## Test fixtures (datos ficticios)
- Base de datos: SQLite temporal en tmp_path.
- Uploads: directorio temporal en tmp_path.
- Identidad valida: username "student", role "STUDENT", expiracion en 3600 segundos.
- Identidad expirada: misma identidad con expiracion en el pasado.
- Secreto: el secreto de prueba definido en LEGACY_SECRET (api/app/main.py); no usar secretos reales.

## Test cases (entrada)
| ID | Ruta | Entrada ficticia | Auth |
|----|------|------------------|------|
| TC-01 | GET /api/tasks | sin query | token valido (student) |
| TC-02 | GET /api/tasks | owner = "x' OR '1'='1" | token valido (student) |
| TC-03 | GET /api/tasks | sin token | ninguna |
| TC-04 | GET /api/tasks | token con secreto incorrecto | token invalido |
| TC-05 | GET /api/tasks | token expirado | token expirado |
| TC-06 | GET /api/tasks | owner = "trainer" | token valido (student) |
| TC-07 | POST /api/tasks | title "Add tests", description "Improve coverage", status TODO, priority HIGH | token valido |
| TC-08 | POST /api/tasks | body {} | token valido |
| TC-09 | POST /api/tasks | title "O'Brien", description "d", status TODO, priority LOW | token valido |
| TC-10 | POST /api/tasks | sin token, body {} | ninguna |
| TC-11 | GET /api/summary | sin query, tras TC-07 | token valido (student) |
| TC-12 | POST /api/requirements | file "requirements.txt", contenido "Login must be accessible" | token valido |
| TC-13 | POST /api/requirements | sin token | ninguna |
| TC-14 | GET /api/backend-health | respuesta simulada HTTP 200 con body "ok" | token valido |

## Known risks
- Token mal formado o expirado.
- Comilla simple en owner y title.
- Owner elegido por el cliente en GET y POST.
- Nombre de archivo no controlado en la subida.

## Constraints
- Sin llamadas de red ni datos reales.
- Pruebas en api/tests/ con pytest y TestClient.
- Cobertura objetivo del codigo modificado: 85% o superior.