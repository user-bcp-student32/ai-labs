# Plan de corrección

Fuente: hallazgos "Pendiente" de [HALLAZGOS.md](/c:/Users/Usuario/Documents/T14983-giancarlo-yarleque/ai-labs/HALLAZGOS.md).
Componente: api. Este plan no modifica código; cada cambio se implementa tras la revisión humana de este documento.

## api

### Contrato a preservar

docs/ENDPOINTS.md no existe. Se conserva el comportamiento observado en api/app/main.py:

| Ruta | Respuesta observada que debe mantenerse |
|---|---|
| GET /api/tasks | 200 lista de `{id,title,description,status,owner,priority,createdAt}`; 401 con token inválido |
| POST /api/tasks | 201 con la tarea creada; campos ausentes se toman como `""` o con los defaults `TODO` y `MEDIUM` |
| GET /api/summary | 200 `{owner,total,byStatus,highPriorityOpen}` |
| POST /api/requirements | 201 `{owner,fileName,storedAt,size}` |
| GET /api/backend-health | 200 `{requestedBy,status,body}` |

Pruebas existentes que deben seguir en verde: api/tests/test_api.py (`test_lists_seeded_tasks`, `test_creates_task_and_summary`, `test_uploads_requirement`, `test_rejects_invalid_token`). La firma `create_app(db_path, upload_dir)` y el objeto `app` a nivel de módulo también se conservan.

### Orden propuesto

1. Caracterización (prerrequisito de todo lo demás).
2. H-API-01: actualizar dependencias (cambio aislado).
3. H-API-04 + H-API-06 (parte de repositorio): SQL parametrizado dentro de la capa de repositorio.
4. H-API-03: autorización por identidad.
5. H-API-05: carga segura de archivos.
6. H-API-02: verificación real del token. Requiere coordinación con auth/ (ver apartado).
7. H-API-09: restringir orígenes CORS. Independiente de los demás; requiere confirmar el origen de web/.
8. H-API-08: mensajes genéricos en el 401 y en `backend-health`. Se hace junto con H-API-02, porque ambos cambian el mismo 401.
9. H-API-10: limitar el cuerpo reenviado por `backend-health`.
10. H-API-07: modelos Pydantic, condiciones y constantes. Va al final, cuando las pruebas de caracterización de los puntos anteriores existan.

---

### H-API-01 — SCA: urllib3 2.5.0 y python-multipart 0.0.21

- **Problema:** api/requirements.txt:4-5 fija versiones con CVE. urllib3 2.5.0 está afectada por CVE-2025-66418, CVE-2025-66471 y CVE-2026-21441 (corregidas en 2.6.0). python-multipart 0.0.21 está afectada por CVE-2026-24486 (corregida en 0.0.22). Los advisories provienen de búsqueda web secundaria; falta verificarlos en GitHub Advisory o NVD.
- **Verificación (2026-11-02, OSV):** urllib3 2.5.0 tiene 6 advisories; 2.8.0 no tiene ninguno. python-multipart 0.0.21 tiene 7; 0.0.33 (última en PyPI) no tiene ninguno. Estas cifras reemplazan la búsqueda web secundaria.
- **Cambio aplicado:** `urllib3==2.8.0` y `python-multipart==0.0.33` en api/requirements.txt:4-5. Versiones mínimas de 2.6.0 y 0.0.22 descartadas por insuficientes.
- **Pendiente:** `pip-audit` no se añadió (requiere aprobación como dependencia de desarrollo).
- **Archivos:** api/requirements.txt (y api/requirements-dev.txt si lo fija).
- **Caracterización previa:** ejecutar la suite completa antes y después del cambio. Sin cambios de comportamiento esperados.
- **Clean code:** no aplica.
- **Decisión pendiente:** si se acepta `pip-audit` como herramienta de desarrollo (dependencia nueva, justificada por el hallazgo SCA).

---

### H-API-04 — Filtros e inserts concatenados en SQL

- **Problema:** api/app/main.py:70 (owner del query), api/app/main.py:84-85 (campos del body en INSERT), api/app/main.py:93 (owner en resumen), api/app/main.py:87 (`lastrowid` concatenado). Una comilla rompe la consulta; un valor malicioso puede alterar la sentencia.
- **Cambio propuesto:** mover las consultas a una capa de repositorio (`api/app/repository.py`) con placeholders `?` y tuplas de parámetros. Ninguna consulta recibe texto del cliente concatenado. El `lastrowid` se usa como parámetro. Las columnas de `SELECT` se definen una sola vez como constante.
- **Archivos:** api/app/repository.py (nuevo), api/app/main.py (las rutas llaman al repositorio), api/tests/test_api.py (pruebas nuevas).
- **Caracterización previa:**
  - Mantener: listado con seed, resumen con conteos, creación con respuesta 201 y `owner` del token.
  - Nuevo, escrito antes del cambio: `title` con comilla (`O'Brien`) debe devolver 201 y guardar el valor literal. Hoy falla con 500, así que la prueba quedará en rojo antes del cambio. Es un bug, no contrato.
  - Mantener la semántica de campos ausentes: `title` ausente → `""`, `status` ausente → `TODO`, `priority` ausente → `MEDIUM`.
- **Clean code:** una función por consulta; constantes para columnas y estados por defecto.
- **Decisión pendiente:** ninguna.

---

### H-API-03 — El cliente puede consultar o crear tareas de otro `owner`

- **Problema:** api/app/main.py:69 (`owner` del query reemplaza al username en listado), api/app/main.py:92 (en resumen), api/app/main.py:83 (`owner` del body en creación).
- **Cambio propuesto:** la identidad del token es la fuente de verdad.
  - Listado y resumen: si no se envía `owner`, se usa `identity["username"]`. Si se envía un `owner` distinto, responder 403, salvo que el rol del token permita consultar a otros (por ejemplo `TRAINER`). Esa regla se confirma antes de implementar.
  - Creación: el `owner` siempre es `identity["username"]`. El campo `owner` del body se ignora o se rechaza con 403 si es distinto. Esta decisión cambia el contrato de POST, así que debe aprobarse.
- **Archivos:** api/app/security.py (regla de autorización), api/app/services.py (decisión), api/app/main.py (rutas), api/tests/test_api.py.
- **Caracterización previa:**
  - Mantener: GET sin `owner` devuelve solo tareas del usuario del token (`student`).
  - Mantener: POST sin `owner` asigna `student`.
  - Nuevo, escrito antes: `owner=trainer` con token de `student` hoy devuelve tareas de `trainer` (comportamiento actual, inferido de api/app/main.py:69). La prueba registra el comportamiento actual y se reemplaza por el de autorización tras aprobación.
- **Clean code:** una función que decide el owner efectivo; sin repetir la regla en listado, resumen y creación.
- **Decisión pendiente:** ¿el rol `TRAINER` puede consultar a otros owners? ¿Se rechaza el `owner` del body (403) o se ignora en silencio? Confirmar con el responsable del producto. Confirmar también si el frontend (web/) envía `owner`; no se revisó desde api/.

---

### H-API-05 — Se conserva `upload.filename` tal cual

- **Problema:** api/app/main.py:109 une `selected_uploads` con `str(file.filename)`. `..` y rutas absolutas escapan del directorio. api/app/main.py:110 lee todo el archivo sin límite. api/app/main.py:112 expone la ruta absoluta en `storedAt`.
- **Cambio propuesto:** módulo `api/app/uploads.py` con tres pasos pequeños:
  1. Normalizar el nombre: tomar solo el nombre base (`Path(name).name`), reemplazar caracteres fuera de una lista permitida y rechazar nombres vacíos o `.`/`..`.
  2. Validar extensión contra una lista permitida (por ejemplo `.txt`, `.md`, `.pdf`; la lista se confirma).
  3. Leer en bloques y rechazar cuando supere `MAX_UPLOAD_BYTES` (constante con nombre, valor a confirmar).
  - Responder con 400 para nombre o extensión inválidos, y con 413 para tamaño excedido.
  - `storedAt` conserva el nombre de la clave pero con la ruta relativa al directorio de subida, no la absoluta. Este cambio de valor debe aprobarse.
- **Archivos:** api/app/uploads.py (nuevo), api/app/main.py (ruta), api/tests/test_api.py.
- **Caracterización previa:**
  - Mantener: `requirements.txt` con texto se guarda con el mismo nombre y devuelve 201 (`test_uploads_requirement` ya lo cubre).
  - Mantener: `size` igual al tamaño del contenido y `fileName` igual al nombre original para nombres seguros.
  - Nuevo, escrito antes: `../x.txt` no debe escribir fuera de `uploads/`; extensión `.exe` debe rechazarse; archivo mayor al límite debe rechazarse.
- **Clean code:** funciones separadas para normalizar, validar extensión y guardar; constantes para límite y extensiones.
- **Decisiones pendientes:** lista de extensiones, tamaño máximo y si `storedAt` debe ser relativo (cambio de valor).

---

### H-API-06 — Rutas, DB, reglas y serialización en un solo archivo

- **Problema:** api/app/main.py:29-54 crea tablas, seed, middleware y estado; api/app/main.py:67-124 mezcla SQL, reglas de resumen y respuestas; api/app/main.py:130 ejecuta `create_app()` al importar.
- **Cambio propuesto:** separar por capas, sin cambiar comportamiento:
  - `api/app/schemas.py`: modelos Pydantic de entrada y salida. Los campos de `POST /api/tasks` tienen defaults que replican el comportamiento observado (`""`, `TODO`, `MEDIUM`). Pydantic no debe rechazar con 422 lo que hoy se acepta.
  - `api/app/config.py`: lectura de variables de entorno (`WORKSHOP_DB_PATH`, `WORKSHOP_UPLOAD_DIR`, `AUTH_HEALTH_URL`) en un solo lugar.
  - `api/app/repository.py`: SQL (ver H-API-04).
  - `api/app/services.py`: reglas de resumen y de owner efectivo (ver H-API-03).
  - `api/app/main.py`: solo composición de la app y rutas delgadas.
  - Mover la creación de la base fuera del import: `app = create_app()` puede mantenerse por compatibilidad, pero la conexión se crea dentro de `create_app`.
- **Archivos:** api/app/schemas.py, config.py, repository.py, services.py (nuevos); api/app/main.py (reducido).
- **Caracterización previa:** las pruebas de caracterización de H-API-03 a H-API-05 cubren el comportamiento; agregar una prueba de la forma exacta del resumen (`byStatus` como diccionario) y de la respuesta de health con upstream inalcanzable (`status: 0`).
- **Clean code:** una responsabilidad por archivo y por función; eliminar `dict[str, Any]` de entradas y salidas; reemplazar condicionales anidados del resumen (api/app/main.py:101-104) por una condición combinada; eliminar valores mágicos (`3.0` como timeout, `"Bearer "`, nombres de estados) con constantes.
- **Decisión pendiente:** ninguna adicional; el orden de implementación se decide con H-API-04.

---

### H-API-02 — Token Base64 con secreto embebido y verificación incompleta

- **Estado:** aprobado por el usuario para planificar (confirmado en el formulario de aprobación). Esta aprobación no autoriza cambios de código ni resuelve las decisiones pendientes de abajo.
- **Problema:** api/app/main.py:14 contiene `WORKSHOP_SECRET_2026`. api/app/main.py:19-22 decodifica Base64 y compara el secreto, sin firma, emisor ni audiencia. Cualquiera puede fabricar un token con cualquier identidad.
- **Cambio propuesto:**
  - Usar un JWT firmado con HS256 o similar. Verificar firma, `exp`, `iat`, `iss` y `aud` (los claims exactos se definen con auth/).
  - El secreto viene de una variable de entorno (por ejemplo `WORKSHOP_JWT_SECRET`). Si falta, la app no arranca. Sin valor por defecto en el código.
  - Dependencia nueva propuesta: `PyJWT`, justificada porque implementa verificación de firma y claims de forma probada. Alternativa sin dependencia: `hmac` de la biblioteca estándar, que requiere implementar el formato a mano; no se recomienda por riesgo de error.
  - Mantener el mensaje 401 genérico (`Invalid token`); el detalle `str(error)` se corrige en H-API-08, fuera de este plan.
  - Tiempo de expiración: constante con nombre (valor a confirmar, por ejemplo 3600 segundos).
- **Archivos:** api/app/security.py (nuevo: verificación), api/app/config.py (secreto), api/app/main.py (dependencia), api/requirements.txt (`PyJWT`), api/tests/test_api.py (token helper).
- **Dependencia de otro componente:** auth/ emite hoy el token legado `usuario|rol|exp|secreto` en Base64. Si api/ exige JWT firmado, auth/ debe emitir el nuevo formato antes del corte. Eso cambia el contrato de `POST /auth/login` y requiere coordinar con el responsable de auth/ y revisar web/ (que consume el token). Este plan no cambia auth/.
- **Caracterización previa:**
  - Mantener: token legado válido sigue funcionando hasta el corte coordinado. La prueba actual `test_lists_seeded_tasks` lo cubre.
  - Mantener: token inválido devuelve 401 (`test_rejects_invalid_token`).
  - Nuevo, escrito antes: token con secreto distinto → 401; token expirado → 401; token sin prefijo `Bearer` → comportamiento actual (aceptado) que se decide explícitamente.
  - Nuevo: con `WORKSHOP_JWT_SECRET` ausente, la app no arranca (falla explícita).
- **Clean code:** una función para decodificar y otra para verificar claims; sin anidamiento en la verificación.
- **Decisiones pendientes:** aprobar PyJWT; definir claims (`iss`, `aud`) y la fecha de corte con auth/; decidir si el token legado se acepta durante la transición.

### H-API-09 — CORS `allow_origins=["*"]` con `allow_credentials=True` (adicional, no en RETO.md)

- **Hallazgo:** H-API-09 en [HALLAZGOS.md](/c:/Users/Usuario/Documents/T14983-giancarlo-yarleque/ai-labs/HALLAZGOS.md), estado "Pendiente". Su sección se escribió cuando la matriz mostraba "Aprobado" por una sustitución de texto; esa marca no era una aprobación humana (ver pendientes).
- **Problema:** api/app/main.py:49-53 combina origen abierto con credenciales habilitadas. Cualquier sitio puede hacer peticiones con el navegador de un usuario y leer la respuesta. La API autentica con cabecera `Authorization`, no con cookies, así que el impacto real parece limitado; no se verificó en ejecución.
- **Cambio propuesto:**
  - Leer los orígenes de una variable de entorno (por ejemplo `WORKSHOP_CORS_ORIGINS`, separada por comas) con una constante con nombre como respaldo.
  - Poner `allow_credentials=False`, salvo que web/ demuestre que necesita credenciales de navegador.
  - Mover la construcción de opciones CORS a una función propia (`build_cors_options()`) para que `create_app` no mezcle responsabilidades.
- **Contrato:** el frontend debe seguir funcionando. Su origen (web/) no se revisó porque está fuera del alcance de api/; hay que confirmarlo antes de restringir.
- **Archivos:** api/app/main.py (bloque CORS dentro de `create_app`), api/tests/test_api.py (pruebas nuevas). Sin dependencias nuevas.
- **Caracterización previa (escribir antes de tocar):**
  - Petición `GET /api/tasks` con `Origin: https://otro.example` y token válido: registrar el valor real de `Access-Control-Allow-Origin` y de `Access-Control-Allow-Credentials` que devuelve hoy. No se asume un valor; se fija con la ejecución.
  - Preflight `OPTIONS /api/tasks` desde ese origen: registrar la respuesta actual.
  - Tras el cambio, el origen permitido recibe sus cabeceras y un origen no listado no las recibe.
- **Clean code:** constante para el nombre de la variable y los orígenes por defecto; una función por responsabilidad; sin anidamiento.
- **Decisiones pendientes:**
  - Lista de orígenes por defecto. Confirmar con el responsable de web/ (no se inventa aquí).
  - Confirmar si el frontend necesita `allow_credentials=True`.

---

### H-API-08 — Errores que exponen detalle interno (adicional, no en RETO.md)

- **Hallazgo:** H-API-08 en [HALLAZGOS.md](/c:/Users/Usuario/Documents/T14983-giancarlo-yarleque/ai-labs/HALLAZGOS.md), estado en la matriz "Aprobado" (marca no confirmada, ver pendientes).
- **Problema:** api/app/main.py:26 devuelve `"Invalid token: " + str(error)` en el 401. api/app/main.py:121 devuelve `str(error)` en `backend-health`. docs/GOOD_PRACTICES.md (api, Errores) exige mensajes genéricos al cliente y el detalle solo en el log del servidor.
- **Cambio propuesto:**
  - 401 con `detail="Invalid token"` exacto. El motivo real se registra con `logging.getLogger(__name__)` (biblioteca estándar, sin dependencia nueva).
  - `backend-health` en error: `status: 0` y `body` genérico (por ejemplo `"backend unavailable"`); el error real va al log.
  - El contrato de las respuestas (`{requestedBy,status,body}` y el 401) se mantiene; solo cambia el texto del detalle.
- **Archivos:** api/app/main.py (o api/app/security.py si H-API-02 lo crea), api/tests/test_api.py.
- **Caracterización previa:**
  - Mantener: token inválido → 401 (`test_rejects_invalid_token`). Antes de cambiar, registrar el `detail` actual con un token mal formado; tras el cambio debe ser exactamente `Invalid token`.
  - Mantener: `backend-health` con upstream inalcanzable devuelve `status: 0` y `requestedBy`.
  - Nuevo: el `body` de la respuesta de error no contiene el texto de la excepción (no se reenvía `str(error)`).
- **Clean code:** una función para mapear el error a la respuesta genérica; mensaje como constante con nombre.
- **Decisión pendiente:** ninguna. Se implementa junto con H-API-02, porque ambos cambian el mismo 401.

### H-API-10 — backend-health devuelve el cuerpo de un servicio externo (adicional, no en RETO.md)

- **Hallazgo:** H-API-10 en [HALLAZGOS.md](/c:/Users/Usuario/Documents/T14983-giancarlo-yarleque/ai-labs/HALLAZGOS.md), estado en la matriz "Aprobado" (marca no confirmada, ver pendientes).
- **Problema:** api/app/main.py:116-121. La URL sale de `AUTH_HEALTH_URL` y no del cliente, así que no hay SSRF por parámetro. Pero el cuerpo completo del servicio remoto se reenvía al cliente sin límite de tamaño. El riesgo es bajo.
- **Cambio propuesto:**
  - Mantener la forma `{requestedBy,status,body}` y el `status` remoto.
  - Limitar el `body` reenviado a una constante (`MAX_HEALTH_BODY_CHARS`, valor a confirmar).
  - El error de conexión no se reenvía (ver H-API-08).
  - Separar en dos funciones: una que consulta el upstream y otra que arma la respuesta.
- **Archivos:** api/app/main.py (o api/app/health.py, nuevo), api/app/config.py (URL, timeout y límite), api/tests/test_api.py.
- **Caracterización previa:**
  - Con `AUTH_HEALTH_URL` apuntando a un servidor HTTP local de prueba (no a red externa), registrar `status` y `body` actuales.
  - Con puerto cerrado, registrar `status: 0`.
  - Nuevo: un cuerpo mayor al límite se devuelve truncado.
- **Clean code:** constantes para timeout (`3.0` hoy, api/app/main.py:118) y límite; sin números mágicos.
- **Decisión pendiente:** si el cuerpo del health remoto debe ocultarse por completo (cambio de contrato) o solo limitarse. Se propone limitarlo para no cambiar el contrato.

### H-API-07 — Calidad: `dict` crudo, excepciones amplias, condicionales anidados y duplicación

- **Hallazgo:** H-API-07 en [HALLAZGOS.md](/c:/Users/Usuario/Documents/T14983-giancarlo-yarleque/ai-labs/HALLAZGOS.md), estado en la matriz "Aprobado" (marca no confirmada, ver pendientes).
- **Problema:**
  - `payload: dict[str, Any]` sin modelo (api/app/main.py:78).
  - `except Exception` amplio en api/app/main.py:25 y api/app/main.py:120.
  - Condicionales anidados de tres niveles en el resumen (api/app/main.py:101-104).
  - Valores mágicos: `"Bearer "` (api/app/main.py:19), `"HIGH"`, `"DONE"`, `"ARCHIVED"` (api/app/main.py:101-104), `3.0` (api/app/main.py:118).
  - La lista de columnas de `SELECT` se repite (ver H-API-04).
- **Cambio propuesto:**
  - `api/app/schemas.py`: `TaskCreate` (campos con defaults `""`, `""`, `TODO`, `MEDIUM` y `owner` opcional), `TaskOut` y `SummaryOut`.
  - Resumen: una condición combinada, `priority == HIGH and status not in CLOSED_STATUSES`, con `CLOSED_STATUSES = ("DONE", "ARCHIVED")`.
  - Captura de excepciones específicas (`ValueError`, `UnicodeDecodeError`, `binascii.Error`) en la verificación del token (en coordinación con H-API-02).
  - Constantes en `api/app/config.py`.
- **Archivos:** api/app/schemas.py (nuevo), api/app/config.py (nuevo), api/app/main.py, api/app/repository.py.
- **Caracterización previa:**
  - Mantener: creación con campos completos y con campos ausentes (defaults `TODO` y `MEDIUM`).
  - Nuevo, antes de tocar: un `title` numérico. Hoy `str()` lo convierte en `"5"` y responde 201. Con Pydantic v2 el tipo no se coerciona y respondería 422. Esa prueba fija el comportamiento actual.
  - Mantener: resumen con `byStatus` y `highPriorityOpen` para tareas `HIGH` en `TODO`, `DONE` y `ARCHIVED`.
- **Clean code:** una responsabilidad por función y archivo; sin `dict[str, Any]` en entradas ni salidas; sin condicionales anidados; sin valores mágicos.
- **Decisión pendiente:** aceptar 422 para tipos no-string en POST (cambio menor de contrato) o mantener la coerción con un validador explícito. Se propone mantener la coerción para no cambiar el contrato.

---

## Pendientes y dependencias

- Alcance de este plan: se planifican todos los IDs de api (H-API-01 a H-API-10), por instrucción "Todos los IDs". Planificar no equivale a aprobar la implementación ni cambia estados en HALLAZGOS.md.
- Estados en HALLAZGOS.md: el usuario confirmó (2026-11-02, "aprobadas") H-API-01, 02, 06, 07, 08, 09 y 10 como aprobados para planificar. Esta aprobación cubre el plan; no autoriza cambios de código ni resuelve las decisiones abiertas de cada apartado. H-API-03, 04 y 05 no figuran como aprobados; H-API-04 sigue en revisión humana.
- Selección de hallazgos: HALLAZGOS.md mostró "Aprobado" en casi todas las filas de api por una sustitución de texto, no por aprobaciones humanas. El usuario pidió restaurar "Pendiente" (sin planificar nada aún). Las marcas de H-API-04 "Corregido en código" se conservan. Queda pendiente que el usuario indique qué hallazgos aprueba de verdad.
- Estado: plan aprobado por el usuario (2026-11-02). La aprobación cubre el plan y su orden; no cubre las decisiones abiertas de abajo, que siguen sin resolver.
- Aprobación de H-API-02 para planificar: confirmada por el usuario. Sigue pendiente su implementación, que requiere decidir PyJWT, claims `iss`/`aud`, fecha de corte con auth/ y si el token legado se acepta en la transición.
- Decisiones humanas antes de implementar: H-API-03 (regla de rol y de body), H-API-05 (extensiones, tamaño, `storedAt`), H-API-02 (PyJWT y corte con auth/).
- Coordinación con auth/ y web/ para H-API-02 y, si se confirma, para H-API-03.
- Verificación de advisories de H-API-01 realizada en OSV (2026-11-02); falta confirmar en GitHub Advisory o NVD.
- H-API-04 implementado: api/app/repository.py (consultas con `?`) y api/app/main.py llama al repositorio. Pruebas en api/tests/test_api.py con `python -m pytest -p no:warnings -rA` (cwd `api/`): 7 pasan, incluida la de comilla en título y la de inyección por `owner`. La prueba de comilla pasó tras el cambio (antes fallaba con `sqlite3.OperationalError`). Autorización sin cambios (H-API-03 pendiente).
- Dependencias de prueba `pytest` y `httpx` instaladas en el entorno local, no declaradas en api/requirements.txt. Decidir si se declaran (pendiente de aprobación).
- Comando pendiente: `python tools/lab.py test api` (desde la raíz).
