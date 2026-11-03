---
name: api-unit-test-generation
description: Genera pruebas unitarias para las rutas de api/ (FastAPI + SQLite) con TestClient y una base temporal, cubriendo autenticacion, validacion, consultas parametrizadas y casos de borde, siguiendo los procedimientos de prompts/api.md.
---

# Skill: API Unit Test Generation

## 1) Metadata
- **Skill ID:** api-unit-test-generation
- **Version:** 0.1.0
- **Purpose:** Generar pruebas unitarias trazables para las rutas HTTP de `api/`, verificando contratos reales del codigo y dejando evidencia reproducible.
- **Component:** api (IDs H-API-NN).
- **Audience (roles):** Backend, QA, Cyber
- **When to use:** Antes de separar el monolito de `api/app/main.py`, al agregar o cambiar una ruta, o al cerrar hallazgos de `prompts/api.md`.
- **When NOT to use:** Cambios solo de documentacion, cambios en `auth/` o `web/` (fuera de alcance), o pruebas de integracion contra servicios remotos.
- **Sensitive inputs:** No uses secretos reales, tokens de produccion ni URLs internas. Usa SQLite temporal (`tmp_path`) y valores ficticios.
- **Expected output:** Archivo de pruebas en `api/tests/` y un reporte Markdown con la estructura de la seccion 3.
- **Note:** `inputtest.sample.md` y `outputtest.sample.md` estan vacios; hasta completarlos, el formato de entrada y salida sigue la estructura de `input.sample.md` y `output.sample.md` del mismo skill.

## 2) Inputs
Proporciona la informacion en este formato:

- **Component:** api
- **Routes under test:** (ruta, metodo, parametros de query, body, auth requerida)
  - `GET /api/tasks` - query `owner` opcional; identidad desde `Authorization: Bearer <token>`.
  - `POST /api/tasks` - body JSON con `title`, `description`, `status`, `priority`, `owner` (todos opcionales).
  - `GET /api/summary` - query `owner` opcional; respuesta con `total`, `byStatus`, `highPriorityOpen`.
  - `POST /api/requirements` - multipart `file`; guarda en el directorio de uploads.
  - `GET /api/backend-health` - depende de `AUTH_HEALTH_URL`; debe simularse, nunca llamarse.
- **Source of truth:** `api/app/main.py`, `api/app/repository.py`, `api/tests/test_api.py` y `prompts/api.md`.
- **Test inputs reference:** `.github/skills/feature-intake-spec-checklist/examples/inputtest.sample.md` (formato de entrada de casos; actualmente vacio, 0 bytes).
- **Test outputs reference:** `.github/skills/feature-intake-spec-checklist/examples/outputtest.sample.md` (formato de salida esperado; actualmente vacio, 0 bytes).
- **Known constraints:** Python 3.11-3.13 (recomendado 3.13); pytest 9.0.2 y pytest-cov 7.0.0 de `api/requirements-dev.txt`.
- **Known risks:** Token mal formado o expirado; valores con comilla simple en filtros e inserciones; owner elegido por el cliente; subida de archivos con nombre no controlado.

## 3) Output
Devuelve **solo** un documento Markdown con estas secciones y bullets:

1. **Summary** - rutas cubiertas y numero de casos.
2. **Scope**
    - Dentro del alcance: rutas de `api/app/main.py` y consultas de `api/app/repository.py`.
    - Fuera del alcance: `auth/`, `web/`, pruebas contra servicios reales.
3. **Supuestos** - comportamiento inferido, separado del contrato verificado.
4. **Matriz de casos** - tabla: `ID | Ruta | Caso | Entrada ficticia | Resultado esperado | Fuente (archivo:linea) | Estado`.
5. **Decisiones tecnicas** - uso de `create_app(db, uploads)`, `TestClient` como contexto, helpers de token y de cabeceras.
6. **Riesgos y mitigaciones** - minimo 2 riesgos con mitigacion.
7. **Criterios de aceptacion (medibles)**
    - Cada ruta tiene al menos un caso 2xx y un caso 401 o 422 cuando aplique.
    - Cobertura del codigo modificado >= 85%.
    - Ningun caso usa secretos reales ni llamadas de red.
8. **Plan de validacion**
    - Pruebas unitarias: archivo y funciones de `api/tests/`.
    - Pruebas funcionales: ninguna contra servicios remotos.
    - Seguridad: token invalido, token expirado, comilla simple en `owner` y `title`.
    - Evidencia requerida: salida de `pytest` y reporte de cobertura `term-missing`.
9. **Tareas sugeridas por rol** - Backend, QA, Cyber.
10. **Preguntas abiertas** - comportamientos sin contrato documentado.
11. **Comandos ejecutados y no ejecutados** - indicar explicitamente cuales se corrieron.

## 4) Procedure
Sigue estos pasos en orden. No modifiques codigo de `api/app/` al generar pruebas.

1. **Leer el codigo real.** Localiza cada ruta en `api/app/main.py` y cada consulta SQL en `api/app/repository.py`. No asumas rutas de `RETO.md` ni de `auth/`.
2. **Clasificar cada entrada.** Distingue:
   - Valores del cliente: query `owner`, body JSON, archivo multipart.
   - Valores derivados de la identidad: `identity["username"]` a partir del token.
   - Valores por defecto: `DEFAULT_STATUS`, `DEFAULT_PRIORITY`.
   - Consultas: `?` enlazado (parametrizado) frente a f-strings (solo `TASK_COLUMNS`, constante interna).
3. **Preparar fixtures.** Reutiliza el patron de `api/tests/test_api.py`:
   - `create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))`.
   - `TestClient(app)` como contexto.
   - Token generado por helper con expiracion futura y el secreto de prueba del codigo.
   - Un helper para token expirado y otro para secreto incorrecto.
4. **Generar casos por ruta:**
   - `GET /api/tasks`: lista semilla (2 tareas del owner `student`); token invalido -> 401; `owner` con comilla simple -> 200 y lista vacia.
   - `POST /api/tasks`: creacion completa -> 201; body vacio -> valores por defecto; comilla en `title` -> se guarda literal; sin token -> 401.
   - `GET /api/summary`: conteo `total` y `byStatus`; `highPriorityOpen` excluye `DONE` y `ARCHIVED`.
   - `POST /api/requirements`: archivo guardado en uploads -> 201; sin token -> 401.
   - `GET /api/backend-health`: parchear la llamada HTTP (monkeypatch de `urllib3.PoolManager`); nunca conectar a `localhost:8080`.
5. **Caracterizar lo no contractual.** Si un comportamiento no tiene contrato (por ejemplo, `owner` distinto al usuario autenticado), escribe la prueba como caracterizacion y marcalo en la matriz como "inferido".
6. **Reutilizar helpers.** Un bloque repetido en dos pruebas se extrae a una funcion (p. ej. cabeceras de autenticacion).
7. **Ejecutar y registrar.** Ejecuta las pruebas y registra el resultado real; no marques "paso" sin ejecutarlas.

## 5) Constraints
- No inventes contratos: si el codigo no lo define, declaralo en **Supuestos** o **Preguntas abiertas**.
- No uses `# noqa`, `pytest.mark.skip` ni exclusiones de scanners para ocultar fallos.
- No incluyas payloads que modifiquen o extraigan datos reales; solo valores ficticios inocuos.
- No llames a servicios remotos ni a `localhost:8080`.
- No introduzcas dependencias nuevas sin justificarlas; usa pytest y TestClient existentes.
- Usa constantes con nombre en lugar de numeros o strings magicos (por ejemplo, `TOKEN_TTL_SECONDS = 3600`).
- Cada funcion de prueba hace una sola verificacion conceptual; nombres que describan el comportamiento (`test_rejects_invalid_token`), no el tipo.
- Evita anidamiento profundo en helpers; usa retorno anticipado.
- Mantén la salida en un maximo de 2 paginas.

## 6) Validation Rules
La salida es valida si:
- Incluye todas las secciones definidas en Output.
- Cada ruta de la seccion 2 tiene al menos un caso en la matriz.
- Cada caso de la matriz cita `archivo:linea` de `api/app/main.py` o `api/app/repository.py`.
- Todos los casos marcados como "comprobado" tienen evidencia de ejecucion.
- No contiene secretos reales ni datos sensibles.
- No contiene supuestos no declarados.

## 7) Commands
Desde la raiz del repositorio (recomendado):

```powershell
python tools/lab.py test api
```

Equivale a ejecutar desde `api/`:

```powershell
python -m pytest --cov=app --cov-report=term-missing --cov-report=html --cov-fail-under=85
```

Para una prueba puntual, desde `api/`: `python -m pytest tests/test_api.py::test_rejects_invalid_token`.

## 8) Evidence
Adjunta o referencia estos elementos en el PR o entrega:
- `SKILLTEST.md` versionado.
- Archivo de pruebas actualizado en `api/tests/`.
- Salida de `python tools/lab.py test api` con el porcentaje de cobertura.
- Carpeta `api/htmlcov/` generada, si aplica.
- Identificadores H-API-NN asociados a cada hallazgo o caso.
- Tag de version para el Skill, como `skill-api-unit-test-generation-v0.1.0`.
