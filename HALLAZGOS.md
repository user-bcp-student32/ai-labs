# Hallazgos de seguimiento

Fuente: Discovery de api (solo lectura). Las rutas son relativas a la raíz de ai-labs.
Estado inicial de todos los hallazgos: Pendiente. Ninguna prueba se ejecutó en este paso.

## api

### Matriz de seguimiento

Orden: severidad de mayor a menor. H-API-01 a H-API-07 corresponden a la tabla de hallazgos sembrados de RETO.md; H-API-08 a H-API-10 son hallazgos adicionales encontrados en el código y no listados en RETO.md.

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-API-02 | api | Token Base64 con secreto embebido y verificación incompleta | Vulnerabilidad | El secreto `WORKSHOP_SECRET_2026` está en el código, así que cualquiera puede fabricar un token con cualquier `username` y `role`. El token no tiene firma, emisor, audiencia ni `iat`; solo se comprueba la expiración con `<`. Un token falsificado accede a todas las rutas con la identidad elegida. | api/app/main.py:14, api/app/main.py:19-22, api/app/main.py:23-24 | alta | Aprobado |
| H-API-04 | api | Filtros e inserts concatenados en SQL | Vulnerabilidad | El `owner` del query, los campos del body y el `owner` se insertan con `+` en SQL sin parámetros. Una comilla rompe la consulta (500) y un valor malicioso puede alterar la sentencia: lectura o escritura de tablas no previstas. | api/app/main.py:70, api/app/main.py:84-85, api/app/main.py:93, api/app/main.py:87 (el `lastrowid` es entero; riesgo bajo) | alta | Aprobado |
| H-API-03 | api | El cliente puede consultar o crear tareas de otro `owner` | Vulnerabilidad | `owner` del query reemplaza la identidad autenticada en GET /api/tasks y GET /api/summary. En POST /api/tasks el `owner` del body también sobrescribe la identidad. Es un fallo de autorización independiente de la concatenación SQL. | api/app/main.py:69, api/app/main.py:83, api/app/main.py:92 | alta | Aprobado |
| H-API-05 | api | Se conserva `upload.filename` tal cual; sin límite de tamaño ni extensión | Vulnerabilidad | `Path(dir) / file.filename` acepta `..` y rutas absolutas (en pathlib una ruta absoluta reemplaza el directorio base), así que puede escribir fuera de `uploads/`. Además el archivo se lee completo en memoria sin límite y se guarda cualquier extensión. La respuesta también expone la ruta absoluta del servidor. | api/app/main.py:109-111, api/app/main.py:112 | alta | Aprobado |
| H-API-01 | api | SCA: `urllib3 2.5.0` y `python-multipart 0.0.21` | Vulnerabilidad | urllib3 2.5.0 tiene 6 advisories en OSV (CVE-2025-66418, CVE-2025-66471, CVE-2026-21441, CVE-2026-44431, CVE-2026-97687, CVE-2026-97689); la corrección completa requiere 2.8.0. python-multipart 0.0.21 tiene 7 advisories en OSV (p. ej. CVE-2026-24486); la corrección completa requiere 0.0.33 (última en PyPI, 0 hits en OSV). Las versiones del plan (2.6.0 y 0.0.22) eran insuficientes. El vector de python-multipart requiere opciones no default, pero el propio código tiene el mismo patrón de H-API-05. | api/requirements.txt:4-5 | media | Aprobado |
| H-API-06 | api | Rutas, DB, reglas y serialización en un solo archivo | Refactor | `create_app` mezcla creación de tablas, seeds, middleware, SQL y lógica de resumen. Sin capas de servicio ni repositorio, cualquier cambio de SQL o de reglas toca las rutas. Además `app = create_app()` se ejecuta al importar y crea la base y carpetas. | api/app/main.py:29-54, api/app/main.py:67-124, api/app/main.py:130 | media | Aprobado |
| H-API-08 | api | Errores que exponen detalle interno (adicional, no en RETO.md) | Vulnerabilidad | El `str(error)` se devuelve al cliente en el 401 (`"Invalid token: " + str(error)`) y en backend-health, lo que revela detalles del parser y de la conexión. Contrario a GOOD_PRACTICES.md (api, Errores). | api/app/main.py:26, api/app/main.py:121 | media | Aprobado |
| H-API-09 | api | CORS `allow_origins=["*"]` con `allow_credentials=True` (adicional, no en RETO.md) | Vulnerabilidad | Combinación permisiva e inconsistente: origen abierto con credenciales habilitadas. La API usa Bearer y no cookies, así que el impacto real parece limitado (comportamiento exacto del middleware no verificado en ejecución). Configuración hardcodeada, sin variable de entorno. | api/app/main.py:49-53 | media | Aprobado |
| H-API-07 | api | Calidad: `dict` crudo, excepciones amplias, condicionales anidados y duplicación | Refactor | `payload: dict[str, Any]` sin modelo Pydantic; `except Exception` en dos lugares; condicionales anidados de 3 niveles en summary; lista de columnas `SELECT` repetida; valores mágicos (`"TODO"`, `"MEDIUM"`, `3.0`, `"Bearer "`). | api/app/main.py:78, api/app/main.py:25, api/app/main.py:120, api/app/main.py:101-104, api/app/main.py:70, api/app/main.py:87, api/app/main.py:81-82, api/app/main.py:118, api/app/main.py:19 | baja | Aprobado |
| H-API-10 | api | backend-health devuelve el cuerpo de un servicio externo (adicional, no en RETO.md) | Vulnerabilidad | La URL sale de `AUTH_HEALTH_URL` (no del cliente), pero el cuerpo de respuesta remota y el error de conexión se reenvían al cliente. Riesgo bajo: solo revela el contenido de un health check. | api/app/main.py:116-121 | baja | Aprobado |

Resultado de la comparación con RETO.md (api): los siete hallazgos sembrados existen en el código. Ninguno queda como "No encontrado".

### Contrato observado (sin docs/ENDPOINTS.md)

docs/ENDPOINTS.md no existe. Este contrato se deriva del código, es decir, es **observado** y no **acordado**. Contrato acordado: Aprobado.

| Método y ruta | Entrada | Respuesta observada | Autenticación | Evidencia |
|---|---|---|---|---|
| GET /api/tasks | query `owner` opcional | 200 lista de tareas `{id,title,description,status,owner,priority,createdAt}`; 401 si el token es inválido | `Authorization` (ver flujo) | api/app/main.py:67-75 |
| POST /api/tasks | body JSON libre (`title`, `description`, `status`, `priority`, `owner`) | 201 la tarea creada; 401 si el token es inválido | igual | api/app/main.py:77-88 |
| GET /api/summary | query `owner` opcional | 200 `{owner,total,byStatus,highPriorityOpen}` | igual | api/app/main.py:90-105 |
| POST /api/requirements | multipart `file` | 201 `{owner,fileName,storedAt,size}` | igual | api/app/main.py:107-112 |
| GET /api/backend-health | ninguna | 200 `{requestedBy,status,body}`; si falla la conexión devuelve `status: 0` | igual | api/app/main.py:114-121 |

- No existe `POST /auth/login` en api/. La ruta de login pertenece a auth/ y no se verificó aquí.
- Documentación automática de FastAPI en `/docs`: la genera FastAPI y no está definida en api/app/main.py.

### Flujo del token y acceso a SQLite

- Token observado: cabecera `Authorization` con el valor `Bearer <base64("usuario|rol|expira_epoch|secreto")>`. El prefijo `Bearer ` se elimina con `replace` sin validar, así que también se acepta sin prefijo (api/app/main.py:19).
- `legacy_identity` se aplica con `Depends` en las cinco rutas (api/app/main.py:68, 78, 91, 108, 115).
- La identidad viene del token. Sin `owner`, se usa `identity["username"]` (api/app/main.py:69, 92). Con `owner`, el valor del cliente manda.
- SQLite: una sola conexión compartida (`check_same_thread=False`, api/app/main.py:35), creada al importar el módulo y cerrada en shutdown (api/app/main.py:123-124).
- Consultas:
  - Lectura por `owner`: api/app/main.py:70-71 (concatenada).
  - Inserción: api/app/main.py:84-86 (concatenada, con `commit`); selección posterior por `id`: api/app/main.py:87 (concatenación de entero).
  - Resumen: api/app/main.py:93 (concatenada).
  - Carga de archivos: no toca SQLite; escribe en disco en api/app/main.py:109-111.
  - Backend-health: no toca SQLite; hace una petición HTTP con urllib3 (api/app/main.py:118).

### Pruebas existentes (leídas, no ejecutadas)

- api/tests/test_api.py: listado de semilla (2 tareas), creación + resumen (total 3), carga de archivo y token inválido (401).
- api/tests/test_api.py:16 se mostró con contenido enmascarado en la lectura; no pude confirmar el encabezado construido por `headers()`.
- Ninguna prueba cubre concatenación SQL, autorización por `owner`, nombres de archivo o expiración.

### Pruebas propuestas (no ejecutadas)

Todas usan `TestClient` y SQLite temporal (`tmp_path`), con datos ficticios.

| Caso | Entrada ficticia | Resultado esperado | Estado |
|---|---|---|---|
| Token válido lista tareas | Bearer válido para `student` | 200 con 2 tareas | Respaldado por api/tests/test_api.py (`test_lists_seeded_tasks`) |
| Token inválido | `Authorization` con base64 inválido | 401 | Respaldado por api/tests/test_api.py (`test_rejects_invalid_token`) |
| Token con secreto distinto | `student|STUDENT|<futuro>|OTRO` | 401 (inferido de api/app/main.py:22) | Hipótesis, no hay prueba |
| Token expirado | exp en el pasado | 401 (inferido de api/app/main.py:22) | Hipótesis, no hay prueba |
| Comilla en `title` del POST | `title` = `O'Brien` | Sin contrato. Comportamiento inferido: 500 por SQL mal formado | Hipótesis, no hay prueba |
| Comilla en `owner` del GET | `owner` = `o'x` | Sin contrato. Comportamiento inferido: 500 | Hipótesis, no hay prueba |
| Owner ajeno | token de `student` y `owner=trainer` | Sin contrato explícito. Inferido: devuelve tarea de `trainer` (api/app/main.py:69) | Hipótesis; RETO.md pide autorización por identidad |

### Preguntas abiertas

1. ¿Se permite que un usuario autenticado vea o cree tareas de otro `owner`? Si no, ¿qué rol habilitaría la excepción?
2. ¿Debe mantenerse el token legado en api/ o migrar a un token firmado compartido con auth/? Eso afecta el contrato de login y lo debe decidir el equipo que coordina auth/.
3. ¿Qué formato de nombre, extensión y tamaño máximo se acepta para `/api/requirements`?
4. ¿Se acepta `status` y `priority` libres o deben limitarse a un conjunto (TODO, IN_PROGRESS, DONE, ARCHIVED; LOW, MEDIUM, HIGH)?

### Pendientes de evidencia

- Verificación de advisories de H-API-01 realizada en OSV (2026-11-02); fuente primaria pendiente de confirmar en GitHub Advisory o NVD.
- Ejecutar las pruebas existentes y las propuestas. No se ejecutó ningún comando durante este Discovery.

### Comandos no ejecutados

- `python tools/lab.py test api`
- `cd api` seguido de `pytest` (o el comando configurado en `api/pyproject.toml`)
- Cualquier petición a un servicio remoto. No se realizó ninguna.
