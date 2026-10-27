# Plan de cambios

## Alcance seleccionado

Se incluyen todos los hallazgos de auth que están `Pendiente` en
`HALLAZGOS.md`: H-AUTH-01 a H-AUTH-07. Se seleccionan porque el usuario pidió
todos los pendientes. No se cambia el estado de ningún hallazgo en este plan.

## Contrato y condiciones de ejecución

- `docs/ENDPOINTS.md` no existe. Se preservará el contrato observado en
  `HALLAZGOS.md`: `POST /auth/login`, JSON, validaciones 400, credenciales
  inválidas 401 y éxito 200 con los campos `token`, `expiresIn` y `user`.
  Cualquier cambio del formato del token interno o de los campos de respuesta
  requiere confirmar primero compatibilidad con consumidores fuera de auth.
- El usuario instruyó explícitamente «ejecuta #PLAN.md» el 2026-10-26; se
  registra como autorización para ejecutar los siete IDs seleccionados. Esto
  no se presenta como revisión independiente por otra persona.
- Al aprobar la ejecución todavía no había resultados de pruebas. La
  caracterización, los resultados previos y posteriores y las limitaciones se
  registran en «Ejecución y resultados»; no se atribuyen resultados anteriores
  al momento en que se obtuvieron.
- Ejecutar pruebas con cwd `auth/`. La línea base documentada para auth es
  `mvn test`; no se ha ejecutado en este paso.
- Las pruebas y fixtures usarán exclusivamente credenciales y secretos
  ficticios, una base SQLite temporal y configuración aislada. No usar la base
  de datos de desarrollo para pruebas de migración.
- Respetar las capas Resource → Service → Repository, DTOs de salida,
  contraseñas con hash lento y salt, tokens firmados, configuración externa,
  SQL parametrizado y errores genéricos descritos en
  `docs/GOOD_PRACTICES.md`. Mantener métodos de una sola responsabilidad,
  nombres descriptivos, sin duplicación, sin anidamiento profundo y sin
  valores mágicos según `AGENTS.md`.

## H-AUTH-01 — Concatenación de credenciales en consulta SQL

- **Hallazgo:** concatenación de credenciales; Vulnerabilidad, severidad alta,
  `Pendiente`.
- **Problema:** `LegacyDatabase.findByCredentials` concatena `username` y
  `password` en el SQL y lo ejecuta con `Statement`
  (`auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:31-35`).
  Las comillas de una entrada pueden cambiar la sintaxis de la consulta.
- **Cambio propuesto:** reemplazar la consulta por `PreparedStatement` con
  parámetros enlazados para ambos valores. Mantener la búsqueda y los
  resultados esperados del endpoint; no devolver detalles SQL al cliente.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`;
  `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`; crear
  `auth/src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java` si hace
  falta aislar la prueba de persistencia.
- **Prueba de caracterización antes del cambio:** agregar pruebas de login
  válido (`student` / `Workshop123!`, 200 y campos de éxito observados),
  contraseña incorrecta (401), campos vacíos (400) y entrada con comilla que
  no corresponde a una cuenta (401). Después, agregar una regresión con una
  fila ficticia cuyo valor literal incluya una comilla y comprobar que la
  consulta parametrizada la recupera como dato, no como sintaxis SQL.

## H-AUTH-02 — Contraseñas almacenadas en texto plano

- **Hallazgo:** contraseñas almacenadas en texto plano; Vulnerabilidad,
  severidad alta, `Pendiente`.
- **Problema:** el esquema y las filas iniciales guardan contraseñas legibles
  (`auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:22-24`), y
  la consulta las compara directamente (`auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:32`).
- **Cambio propuesto:** introducir un servicio de hash lento con salt y
  verificar el hash al autenticar; almacenar hashes en nuevos registros. Definir
  una migración local, transaccional y revisable para las bases existentes
  antes de cambiar el esquema. No registrar contraseñas ni hashes.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`;
  crear `auth/src/main/java/com/bcp/workshop/auth/PasswordHashService.java`;
  `auth/pom.xml` solo si la solución elegida requiere una dependencia de hash
  mantenida y justificada; pruebas en `auth/src/test/java/com/bcp/workshop/auth/`.
- **Prueba de caracterización antes del cambio:** con SQLite temporal,
  comprobar que las credenciales ficticias sembradas autentican con 200 y que
  una contraseña incorrecta responde 401. Registrar el formato actual de
  almacenamiento únicamente dentro de la fixture de prueba. Tras implementar,
  comprobar que el login conserva sus respuestas y que la base solo contiene
  valores con hash; cubrir también la migración de una fila antigua.

## H-AUTH-03 — Token Base64 con secreto embebido y sin firma visible

- **Hallazgo:** token basado en Base64 con secreto embebido y sin firma visible;
  Vulnerabilidad, severidad alta, `Pendiente`.
- **Problema:** `LegacyTokenUtil` incorpora el secreto al contenido y solo
  codifica el conjunto con Base64
  (`auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8,13-15`);
  Base64 no autentica ni protege la integridad del token.
- **Cambio propuesto:** generar un token firmado, con expiración y claims
  explícitos de usuario y rol, usando una implementación mantenida compatible
  con Quarkus. Conservar el campo JSON `token` y `expiresIn`; revisar con los
  consumidores si dependen del formato interno antes de cambiarlo. La
  dependencia se evaluará y justificará antes de añadirla.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`
  o un proveedor de tokens con nombre descriptivo; `auth/pom.xml` solo si se
  justifica una biblioteca; `auth/src/main/resources/application.properties`
  para la configuración compartida con H-AUTH-04; crear
  `auth/src/test/java/com/bcp/workshop/auth/LegacyTokenUtilTest.java` y ampliar
  `AuthResourceTest.java`.
- **Prueba de caracterización antes del cambio:** comprobar la forma actual
  del token para un usuario ficticio y que la respuesta del endpoint expone
  `token` y `expiresIn`. Después, probar firma válida, rechazo de firma
  alterada, claims esperados y expiración; no fijar el secreto real en las
  pruebas.

## H-AUTH-04 — Secreto embebido y CORS configurado para cualquier origen

- **Hallazgo:** secreto embebido y CORS configurado para cualquier origen;
  Vulnerabilidad, severidad media, `Pendiente`.
- **Problema:** el secreto está en código
  (`auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8`) y CORS
  está habilitado con `/.*/`
  (`auth/src/main/resources/application.properties:4-5`).
- **Cambio propuesto:** cargar el secreto desde configuración externa mediante
  variable de entorno, sin fallback a un secreto embebido; parametrizar la
  allow-list de orígenes CORS con configuración por entorno. No fijar una
  allow-list restrictiva por conjetura: el origen permitido de cada entorno
  debe acordarse antes de implementarla. Pruebas usarán un secreto ficticio.
- **Archivos a tocar:** `auth/src/main/resources/application.properties`;
  `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java` o el
  proveedor de tokens elegido para H-AUTH-03; pruebas en
  `auth/src/test/java/com/bcp/workshop/auth/`.
- **Prueba de caracterización antes del cambio:** documentar en una prueba
  local el comportamiento actual de CORS para una petición preflight y el
  origen reflejado; comprobar que el login conserva su respuesta. Después,
  probar un origen aprobado y uno no aprobado según la allow-list acordada, y
  verificar que el arranque requiere configuración de secreto sin exponerlo.

## H-AUTH-05 — La respuesta de error revela detalles internos

- **Hallazgo:** la respuesta de error revela detalles internos; Vulnerabilidad,
  severidad media, `Pendiente`.
- **Problema:** el `catch` de `AuthResource.login` devuelve el mensaje de la
  excepción y su clase (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:54-58`).
  Además, la base de datos captura excepciones y devuelve `null`, lo que puede
  hacer que un error de persistencia parezca una credencial incorrecta
  (`auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:39-42`).
- **Cambio propuesto:** propagar errores de persistencia tipados al servicio y
  traducirlos en la capa HTTP a mensajes genéricos; registrar el detalle solo
  en el servidor, sin credenciales. No convertir fallos de infraestructura en
  respuestas 401 ni ocultarlos como autenticación exitosa.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`;
  `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`; si se separa
  la lógica, crear `AuthService.java` y su interfaz de repositorio; ampliar o
  crear pruebas en `auth/src/test/java/com/bcp/workshop/auth/`.
- **Prueba de caracterización antes del cambio:** probar directamente el
  recurso con un `LegacyDatabase` de prueba que lance una excepción ficticia y
  registrar el 500 y los campos expuestos actuales. Mantener pruebas de 401
  para credenciales incorrectas. Tras el cambio, exigir error genérico sin
  mensaje ni clase interna y confirmar que el fallo no se confunde con 401.

## H-AUTH-06 — Revisar advisories de Quarkus y Commons Text

- **Hallazgo:** revisión pendiente de advisories; Sugerencia, severidad baja,
  `Pendiente`. El manifiesto confirma Quarkus `3.27.4` y Commons Text `1.9`,
  pero no se confirmó un CVE aplicable
  (`auth/pom.xml:13,49-50`).
- **Problema:** sin contraste con advisories y análisis de aplicabilidad no
  hay evidencia suficiente para confirmar o descartar riesgo en esas
  dependencias.
- **Cambio propuesto:** consultar Dependency Review/advisories aprobados,
  registrar identificador, alcance y versión corregida, y cambiar dependencias
  solo si la evidencia lo justifica. No silenciar scanners ni introducir
  actualizaciones especulativas.
- **Archivos a tocar:** `auth/pom.xml` solo si el análisis confirma una
  actualización necesaria; cualquier configuración de scanner fuera de auth
  queda fuera de alcance y requiere coordinación, no modificación.
- **Prueba de caracterización antes del cambio:** ejecutar y registrar la
  línea base de `mvn test` desde `auth/`, y obtener el análisis de dependencias
  antes de editar el POM. En la ejecución, las pruebas sí se corrieron antes y
  después; CodeQL y Dependency Review remotos no se ejecutaron. Commons Text
  se actualizó a 1.15.0, pero el hallazgo permanece pendiente hasta completar
  el análisis de dependencias y revisar Quarkus.

## H-AUTH-07 — Resource concentra validación, persistencia, token y respuesta

- **Hallazgo:** responsabilidades mezcladas en `AuthResource.login`; Refactor,
  severidad baja, `Pendiente`.
- **Problema:** el método valida, consulta la base, genera el token y construye
  las respuestas en un flujo con condicionales anidados
  (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:24-58`).
- **Cambio propuesto:** separar Resource (HTTP), Service (reglas de login),
  Repository (SQLite) y proveedor de tokens; usar DTOs de salida y métodos con
  una responsabilidad. Reutilizar validaciones y mapeos comunes sin duplicar
  lógica. Aplicar guard clauses y constantes con nombre.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`;
  `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`; crear
  `auth/src/main/java/com/bcp/workshop/auth/AuthService.java`,
  `auth/src/main/java/com/bcp/workshop/auth/UserRepository.java` y DTOs de
  respuesta en auth; ampliar `auth/src/test/java/com/bcp/workshop/auth/`.
- **Prueba de caracterización antes del cambio:** añadir pruebas HTTP para
  petición nula/mal formada, username y password ausentes, credenciales
  inválidas y válidas; verificar estados y forma observada de la respuesta
  (`token`, `expiresIn`, `user` y `debug`). Repetir tras separar capas y
  conservar el mismo contrato observado.

## Secuencia y pendientes

1. Escribir las pruebas de caracterización y obtener línea base antes de
   cambios de producción. Completado para el flujo de login y contrato HTTP.
2. Resolver con una persona responsable la lista de orígenes CORS permitidos y
   verificar compatibilidad de consumidores con el formato de token; esos
   contratos no están documentados en `docs/ENDPOINTS.md`. No se modificó CORS
   porque no se proporcionó la lista.
3. Completar Dependency Review/CodeQL y la evidencia de aplicabilidad para
   Quarkus. El gate remoto no se ejecutó en este recorrido local.

## Ejecución y resultados

Autorización: la instrucción del usuario «ejecuta #PLAN.md» autorizó
implementar H-AUTH-01 a H-AUTH-07. No equivale a una revisión independiente ni
a la aprobación final para integrar.

### Caracterización antes de producción

- **auth — H-AUTH-01, H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-05 y H-AUTH-07:**
  la caracterización inicial de login completó 8 pruebas. Credenciales válidas,
  inválidas y la respuesta de entrada con comilla mostraron el comportamiento
  previo; en particular, la contraseña ficticia con comilla produjo error SQL
  y el endpoint respondió 401. El preflight confirmó que CORS reflejaba un
  origen no restringido.
- **auth — H-AUTH-01, H-AUTH-02, H-AUTH-03 y H-AUTH-05:** al agregar las
  regresiones de corrección antes de cambiar producción, 4 de 10 pruebas
  fallaron como se esperaba: consulta de un valor literal con comilla,
  almacenamiento/verificación con hash, firma JWT y error genérico. Estas
  fallas eran la evidencia de que la implementación previa no satisfacía las
  correcciones; no se presentan como fallas del estado final.
- El primer intento de pruebas chocó con el puerto local 8081 ocupado por
  `wslrelay.exe` (PID 12184). No se terminó ese proceso; las siguientes
  ejecuciones usaron `QUARKUS_HTTP_TEST_PORT=0` y una base SQLite temporal
  mediante `AUTH_DB_URL`.

### Cambios y prueba antes/después por hallazgo

| ID | Cambio realizado | Antes del cambio | Después del cambio |
|---|---|---|---|
| H-AUTH-01 | `LegacyDatabase` consulta por usuario con `PreparedStatement`; `AuthService` verifica la contraseña fuera del SQL. | La entrada con comilla causaba error SQL; la prueba de regresión del valor literal falló en la fase roja. | La prueba de login con valor ficticio que contiene comilla pasa; el conjunto final también pasa. |
| H-AUTH-02 | `PasswordHashService` usa PBKDF2 con salt; la inicialización migra contraseñas legibles y los nuevos registros almacenan hashes. | La caracterización de credenciales válidas/incorrectas pasaba con el esquema plano; la regresión de hash/migración falló en la fase roja. | Pruebas de migración y autenticación pasan; `LegacyDatabaseTest` confirma que el valor almacenado ya no es la contraseña legible. |
| H-AUTH-03 | `SignedTokenService` emite HS256 con sujeto, rol y expiración; se conserva la forma JSON de login. | El contrato de campos pasaba, pero la prueba de firma JWT falló en la fase roja con el token anterior. | Pruebas verifican firma y claims y rechazan una firma alterada; pasan en la suite final. |
| H-AUTH-04 | El secreto se externalizó mediante `AUTH_TOKEN_SECRET`; no se tocó la regla CORS. | El preflight aceptaba un origen arbitrario. | La configuración del secreto se comprueba con clave ficticia; CORS sigue aceptando cualquier origen y no hay prueba de allow-list, por lo que el hallazgo queda `Pendiente`. |
| H-AUTH-05 | `AuthResource` traduce excepciones tipadas a un mensaje genérico y registra el fallo en servidor. | La respuesta anterior exponía detalle interno; la regresión de error genérico falló en la fase roja. | La prueba de error genérico pasa y no encuentra el detalle interno en el cuerpo. |
| H-AUTH-06 | Commons Text pasó de 1.9 a 1.15.0; Quarkus se conserva sin cambio especulativo. | La línea base de pruebas fue ejecutada; no se ejecutó análisis remoto de dependencias. | `mvn verify` pasa; CodeQL y Dependency Review no se ejecutaron y no hay confirmación de advisory aplicable a Quarkus. Permanece `Pendiente`. |
| H-AUTH-07 | Se separaron Resource, Service, Repository, emisión de token y DTOs de salida. | La caracterización HTTP inicial de 8 pruebas pasó con el comportamiento anterior. | Las pruebas HTTP y unitarias preservan estados y campos observados; todas pasan en la suite final. |

### Verificación final

- **Componente/IDs:** auth — H-AUTH-01 a H-AUTH-07.
- **Comando:** `mvn verify`, ejecutado desde `auth/` con
  `AUTH_DB_URL=jdbc:sqlite:<archivo SQLite temporal>` y
  `QUARKUS_HTTP_TEST_PORT=0`.
- **Resultado:** BUILD SUCCESS; 21 pruebas, 0 fallos, 0 errores, 0 omitidas.
- **JaCoCo:** clases de producción modificadas, agregado por líneas: 167/182
  (91.8%). `LegacyDatabase`: 69/77 (89.6%); `PasswordHashService`: 32/37
  (86.5%); `SignedTokenService`: 13/15 (86.7%); `AuthResource` y `AuthService`:
  100%. Se supera el objetivo agregado de 85%.
- Una ejecución de verificación intermedia falló porque una prueba esperaba
  que el constructor de JWT rechazara un sujeto nulo, pero la biblioteca lo
  acepta. Se retiró esa expectativa inválida sin cambiar producción; la
  verificación final descrita arriba pasó.
- CodeQL y Dependency Review remotos: **no ejecutados** en el recorrido local.
- No se modificaron otros componentes ni se crearon
  `docs/ENDPOINTS.md`/`docs/TESTING.md`. La revisión humana independiente y la
  integración/checkpoint siguen pendientes.
4. Implementar en cambios revisables, con persistencia/token/configuración
   separados; ejecutar regresión y verificar la meta de cobertura de 85% del
   código modificado sin reducir gates.
5. Registrar resultados reales y aprobación humana cuando ocurran; no están
   asumidos en este plan.

## Registro de ejecución

- 2026-10-26: autorización del usuario para ejecutar H-AUTH-01 a H-AUTH-07
  recibida mediante «ejecuta #PLAN.md». No implica una revisión independiente.
- 2026-10-26: prueba de caracterización previa, componente auth, H-AUTH-01 a
  H-AUTH-07. `mvn test` desde `auth/` con `AUTH_DB_URL` temporal no llegó a las
  aserciones: Quarkus no pudo abrir el puerto de prueba 8081, ocupado por
  `wslrelay.exe` (PID 12184). No se terminó ninguna prueba (`7` quedaron
  omitidas). Se repetirá usando puerto de prueba dinámico; no se detuvo el
  proceso ajeno.
- 2026-10-26: repetición de la prueba de caracterización previa, componente
  auth, H-AUTH-01 a H-AUTH-07. `mvn test` desde `auth/` con
  `AUTH_DB_URL=jdbc:sqlite:<archivo temporal>` y
  `QUARKUS_HTTP_TEST_PORT=0`: correcto, 8 pruebas, 0 fallos, 0 errores y 0
  omitidas. La entrada con comilla produjo la excepción de sintaxis SQLite
  esperada con la consulta concatenada y el endpoint respondió 401. Resultado
  previo a cualquier cambio de producción.
- 2026-10-26: pruebas de corrección antes del cambio, componente auth,
  H-AUTH-01, H-AUTH-02, H-AUTH-03 y H-AUTH-05. Con SQLite temporal y puerto
  dinámico, `mvn test` ejecutó 10 pruebas: 4 fallaron y 6 pasaron. Fallaron las
  expectativas de credencial literal con comilla, contraseña con hash, JWT
  firmado y mensaje de error genérico; la configuración CORS observada y las
  demás pruebas de caracterización pasaron.
- 2026-10-26: el usuario autorizó el formato JWT firmado conservando las claves
  JSON. No proporcionó una lista de orígenes CORS; la allow-list queda
  bloqueada y no se cambiará por conjetura.
- 2026-10-26: compilación focalizada de H-AUTH-03, componente auth, falló
  porque `SignatureAlgorithm` está en `io.smallrye.jwt.algorithm`, no en
  `io.smallrye.jwt`. Se verificó la firma del API en el JAR SmallRye incluido
  por el BOM, se corrigió el import y se repetirá la prueba.
- 2026-10-26: prueba de integración después de H-AUTH-03/H-AUTH-05/H-AUTH-07,
  componente auth, no compiló porque `LegacyDatabaseTest` llamaba al método
  pre-refactor `findByCredentials`. Se actualizó la prueba para comprobar
  `findByUsername` y verificar el hash; falta repetirla.
- 2026-10-26: `mvn verify`, componente auth, ejecutó 18 pruebas; 17 pasaron y
  `LegacyDatabaseTest.reportsDatabaseErrorsInsteadOfReturningNoUser` falló
  porque ese test abría SQLite antes de cargar el driver JDBC. Se añadirá la
  carga explícita al fixture y se repetirá la verificación.
- 2026-10-26: H-AUTH-01, componente auth. La primera prueba focalizada de
  `treatsQuotedCredentialsAsLiteralValues` falló al insertar la fixture porque
  el test intentó usar la base antes de que Quarkus inicializara el esquema.
  Se corrigió el orden de la prueba con una solicitud válida de inicialización;
  repetición focalizada con SQLite temporal y puerto dinámico: correcta, 1
  prueba, 0 fallos/errores. Tras enlazar ambos valores con `PreparedStatement`,
  una credencial ficticia con comilla se recuperó como dato literal.
- 2026-10-26: prueba focalizada de H-AUTH-02, componente auth, no compiló
  porque el test de H-AUTH-05 todavía construía `LegacyDatabase` sin los nuevos
  argumentos del constructor. Se actualizó el test para usar la construcción
  explícita del servicio; aún falta repetir la prueba focalizada.
- 2026-10-26: repetición de pruebas de H-AUTH-02, componente auth, ejecutó
  5 pruebas: las 2 de `AuthResourceTest` y las 2 de `PasswordHashServiceTest`
  pasaron; `LegacyDatabaseTest` no pudo iniciar su SQLite por falta de carga
  explícita del driver en esa prueba. Se agregó `Class.forName` solo al
  fixture; falta repetir el comando focalizado.
