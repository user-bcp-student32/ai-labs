# API Unit Test Generation -> Output esperado

## 1. Summary
Pruebas unitarias propuestas para las 5 rutas de api/ (14 casos, TC-01 a TC-14). Estado global: propuesto, no ejecutado.

## 2. Scope
- Dentro del alcance: api/app/main.py y api/app/repository.py.
- Fuera del alcance: auth/, web/, servicios remotos.

## 3. Supuestos
- El secreto de prueba es el definido en api/app/main.py; no es un secreto real.
- El comportamiento de owner distinto al usuario autenticado es inferido del codigo; no hay contrato documentado.

## 4. Matriz de casos
| ID | Ruta | Caso | Entrada ficticia | Resultado esperado | Fuente | Estado |
|----|------|------|------------------|--------------------|--------|--------|
| TC-01 | GET /api/tasks | Lista semilla | token student | 200, 2 tareas | main.py:73-75; repository.py:13-17 | propuesto |
| TC-02 | GET /api/tasks | Owner con comilla | owner "x' OR '1'='1" | 200, lista vacia | repository.py:15 (parametro ?) | propuesto |
| TC-03 | GET /api/tasks | Sin token | ninguna | 401 | main.py:21-30 | propuesto |
| TC-04 | GET /api/tasks | Secreto incorrecto | token con otro secreto | 401 | main.py:21-30 | propuesto |
| TC-05 | GET /api/tasks | Token expirado | expiracion pasada | 401 | main.py:21-30 | propuesto |
| TC-06 | GET /api/tasks | Owner distinto al usuario | owner "trainer" | 200, tareas de trainer (inferido) | main.py:74 | propuesto, inferido |
| TC-07 | POST /api/tasks | Creacion completa | title "Add tests", priority HIGH | 201, owner "student" | main.py:78-87; repository.py:19-28 | propuesto |
| TC-08 | POST /api/tasks | Body vacio | {} | 201, title "", status TODO, priority MEDIUM, owner "student" | main.py:17-18, 78-87 | propuesto |
| TC-09 | POST /api/tasks | Comilla en title | title "O'Brien" | 201, title "O'Brien" literal | repository.py:21 (parametro ?) | propuesto |
| TC-10 | POST /api/tasks | Sin token | body {} | 401 | main.py:21-30 | propuesto |
| TC-11 | GET /api/summary | Conteo tras TC-07 | token student | 200, total 3, byStatus TODO 2 / IN_PROGRESS 1, highPriorityOpen 2 | main.py:89-103; repository.py:30-35 | propuesto |
| TC-12 | POST /api/requirements | Subida valida | requirements.txt | 201, fileName, size 24, archivo en uploads | main.py:106-110 | propuesto |
| TC-13 | POST /api/requirements | Sin token | ninguna | 401 | main.py:21-30 | propuesto |
| TC-14 | GET /api/backend-health | Health simulado | HTTP 200 simulado | 200, status 200, body "ok" | main.py:113-119 | propuesto |

## 5. Decisiones tecnicas
- create_app(db, uploads) con SQLite y directorio temporales.
- TestClient como contexto por prueba.
- Helper de token con expiracion futura; helper de token expirado.
- Parche de urllib3.PoolManager para TC-14; sin red.

## 6. Riesgos y mitigaciones
- Owner elegido por el cliente (TC-06, TC-08): caracterizar y registrar como pregunta abierta.
- Comilla simple (TC-02, TC-09): consultas parametrizadas; prueba de regresion.
- Nombre de archivo no controlado (TC-12): registrar como riesgo; no aceptado como mitigado.

## 7. Criterios de aceptacion (medibles)
- Cada ruta tiene al menos un caso 2xx y un caso 401 o 422 cuando aplique.
- Cobertura del codigo modificado >= 85%.
- Ningun caso usa secretos reales ni llamadas de red.
- Los 14 casos quedan marcados como propuesto hasta ejecutarse.

## 8. Plan de validacion
- Pruebas unitarias: api/tests/test_api.py.
- Pruebas funcionales: ninguna contra servicios remotos.
- Seguridad: TC-02, TC-04, TC-05, TC-09, TC-10, TC-13.
- Evidencia requerida: salida de python tools/lab.py test api y reporte term-missing.

## 9. Tareas sugeridas por rol
- Backend: separar validacion y acceso a datos antes de ampliar casos.
- QA: ejecutar la matriz y registrar el resultado real por caso.
- Cyber: revisar TC-02, TC-06 y el nombre de archivo en TC-12.

## 10. Preguntas abiertas
- Debe el owner en GET y POST estar restringido al usuario autenticado?
- Debe el nombre de archivo en la subida sanitizarse?
- Cual es el contrato esperado de backend-health cuando AUTH_HEALTH_URL no responde?

## 11. Comandos ejecutados y no ejecutados
- Ejecutados: ninguno.
- No ejecutados: python tools/lab.py test api; python -m pytest --cov=app --cov-report=term-missing --cov-fail-under=85.
- Las pruebas propuestas no se ejecutaron.