AMBITO
Workspace ai-labs abierto en su raiz. Lee codigo solo en api/.
Todas las rutas parten de la raiz. Lee README.md, RETO.md y docs/ generales.
Este pedido es de solo lectura; no escribas ni modifiques archivos.
Git se ejecuta desde la raiz; las pruebas propuestas usan cwd api/.
No leas ni modifiques codigo de auth/ ni web/. Usa IDs H-API-NN y componente api.

CONTEXTO
La API es FastAPI y persiste tareas en SQLite. Lee README.md, RETO.md y
lee docs/ENDPOINTS.md si existe. No des por cierta la lista de RETO.md.
Localiza cada ruta HTTP relevante hasta la consulta SQLite, incluyendo los
valores que llegan desde query parameters y el body. Distingue los valores
del cliente de los derivados de la identidad del token y de SQLite.
No supongas que api/ implementa POST /auth/login: verifica las rutas reales.

OBJETIVO
Determinar si entradas recibidas por las rutas de api/ llegan concatenadas
a consultas SQL, si se usan parametros SQL o si algun recorrido no puede
confirmarse con el codigo disponible.

RESTRICCIONES
No edites codigo ni ejecutes ataques contra servicios remotos. Usa solo
lectura de codigo y, si una persona ejecuta posteriormente las pruebas,
datos ficticios y SQLite temporal local. No cambies contratos ni leas otros
componentes. No ejecutes las pruebas durante este analisis. Si falta un
archivo o no puedes ejecutar una comprobacion, dilo.

CRITERIOS DE ACEPTACION
Identifica el recorrido de cada entrada relevante desde la ruta HTTP hasta
la consulta, con archivo y linea.
Distingue concatenacion, parametros enlazados y consultas con valores que no
provienen directamente del request. Considera como minimo el owner de GET
/api/tasks, los campos de POST /api/tasks y el owner de GET /api/summary,
si esas rutas siguen presentes en el codigo.
Indica si el cliente puede elegir un owner distinto al username de la
identidad autenticada; separa ese riesgo de la concatenacion SQL.
Propone pruebas locales con token valido, token invalido y valores de texto
con una comilla en las entradas que se insertan o filtran. Usa TestClient y
una base SQLite temporal. Escribe el resultado esperado solo cuando lo
respalden el contrato o las pruebas existentes; si no hay contrato para el
caso, decláralo y separa el comportamiento inferido del esperado.
No afirmes que una prueba paso si solo la propusiste. No incluyas payloads
que modifiquen o extraigan datos; las entradas propuestas deben ser valores
ficticios inocuos.

FORMATO DE RESPUESTA
Tabla: afirmacion | evidencia archivo:linea | comprobado o hipotesis |
prueba propuesta.
Termina con preguntas abiertas y comandos no ejecutados. Indica expresamente
que las pruebas propuestas no se ejecutaron.
