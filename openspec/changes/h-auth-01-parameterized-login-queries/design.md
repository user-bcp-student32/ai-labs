# Design

## Context

Ver `proposal.md` (Why) y `specs/auth-login/spec.md` para motivación y requisitos.

Estado actual observado en `auth/`:
- `AuthResource.login` valida la entrada y delega la verificación en `LegacyDatabase.findByCredentials(username, password)`; un resultado `null` se traduce en `401`.
- `findByCredentials` construye el SQL concatenando `username` y `password` (`LegacyDatabase.java:32`), ejecuta con un `Statement` y mapea la primera fila a `UserRecord`.
- Cualquier excepción dentro de `findByCredentials` se captura, se imprime y se devuelve `null`, de modo que un SQL malformado hoy termina en `401`.
- La conexión usa `AUTH_DB_URL` (por defecto `jdbc:sqlite:./data/workshop.db`) y la tabla `legacy_users` guarda `password` en texto plano; ambas cosas están fuera de alcance.
- `AuthResourceTest` tiene una única prueba (login legítimo con `student`). JaCoCo ya está configurado en `pom.xml`.

Restricción: el cambio se limita a `auth/` y no altera el contrato de `POST /auth/login` (`docs/ENDPOINTS.md`).

## Goals / Non-Goals

**Goals:**
- Que `username` y `password` lleguen a la base de datos como valores enlazados y nunca como texto SQL.
- Mantener la firma de `findByCredentials` y, con ella, el comportamiento de `AuthResource` sin tocarlo.
- Dejar el intento de inyección cubierto por una prueba de integración y el código modificado con cobertura de 85 % o más.

**Non-Goals:**
- Hashing de contraseñas o cambio del esquema de `legacy_users`.
- Separar `AuthResource` en capas, o refactorizar el manejo de errores y excepciones de `findByCredentials`.
- Cambiar el token, su secreto, `expiresIn`, el campo `debug` o los mensajes de `401`/`400`.
- Tocar `initialize()`, cuyo SQL es constante y no recibe entrada del usuario.

## Contratos protegidos

Contratos que el cambio no puede alterar. Fuente: `docs/ENDPOINTS.md` (rama `day1/prompting`), Issue #22 y `specs/auth-login/spec.md`.

| Contrato | Valor protegido | Cómo se protege |
|---|---|---|
| Ruta y entrada | `POST /auth/login`, JSON con `username` y `password`; campos extra (p. ej. `client`) se ignoran | Prueba de `200` enviando `client` |
| Éxito | `200` con `token`, `expiresIn`, `user.username`, `user.displayName`, `user.role` y `debug` | Pruebas de caracterización de `student` y `trainer` |
| Campos obligatorios | `400` si `username` o `password` faltan o están vacíos | Pruebas de `400` (usuario ausente, contraseña vacía) |
| Credenciales inválidas | `401` sin `token` | Pruebas de `401` (contraseña incorrecta, usuario inexistente) |
| Firma de acceso a datos | `UserRecord findByCredentials(String, String)`, con `null` para "sin coincidencia" | No se modifica la firma; `AuthResource` queda intacto |
| Formato del token | Sin cambios; el baseline habla de JWT HS256, el código emite un token legado | Las pruebas solo exigen `token` no vacío |
| Datos sembrados | Usuarios `student` y `trainer` creados por `initialize()` | `initialize()` no se toca |

## Componentes afectados

| Componente | Archivo | Cambio |
|---|---|---|
| Acceso a datos (`auth/`) | `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java` | Solo el cuerpo de `findByCredentials` |
| Pruebas (`auth/`) | `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` | Pruebas de caracterización e inyección |

Sin cambios: `AuthResource`, `LegacyTokenUtil`, `LoginRequest`, `UserRecord`, `pom.xml`, `application.properties`, `api/`, `web/`, y los documentos generales de otros componentes.

## Decisions

### D1. Consulta parametrizada con `PreparedStatement` en `findByCredentials`
Se reemplaza `Statement` + concatenación por una sentencia con marcadores `?` para `username` y `password`, enlazados con `setString`. La sentencia SQL pasa a ser una constante con nombre.

- **Por qué**: es la corrección que exige el Issue (criterio 1) y elimina la causa raíz: el motor nunca interpreta el valor como SQL, sea cual sea su contenido. Hace innecesario cualquier filtrado de caracteres.
- **Alternativas descartadas**:
  - *Escapar comillas o filtrar caracteres*: frágil, depende de una lista de patrones y es un parche, no una corrección.
  - *Validar el formato del usuario en `AuthResource`*: rechazaría usuarios legítimos y no protege otros llamadores del repositorio.
  - *Migrar a un ORM*: cambio desproporcionado para un hallazgo de una línea; introduce dependencia nueva.

### D2. Cambio acotado a `LegacyDatabase`
`AuthResource` y `LegacyTokenUtil` no se modifican. Se mantiene el contrato `UserRecord findByCredentials(String, String)`, el retorno `null` para "sin coincidencia" y la captura de excepciones existente.

- **Por qué**: preserva el comportamiento observable (`200`/`400`/`401`) con el mínimo diff y facilita la revisión, tal como pide el contrato de `auth` (cambios revisables por capa).
- **Alternativa descartada**: aprovechar para mover la lógica a servicio/repositorio. Es deseable por las reglas de clean code, pero es un cambio de estructura que merece su propio Issue y no debe mezclarse con una corrección de seguridad.

### D3. Estrategia de pruebas: caracterización primero, luego inyección
Las pruebas viven en `AuthResourceTest` (`@QuarkusTest` + RestAssured), consumiendo el endpoint real, como la prueba existente.

**Pruebas de caracterización necesarias antes de modificar `findByCredentials`** (deben pasar contra el código actual):

| Prueba | Entrada | Esperado hoy y después |
|---|---|---|
| Login `student` (existente, ampliada) | `student` / `Workshop123!` + `client` | `200`, `token` no vacío, `expiresIn`, `user.*`, `debug` |
| Login `trainer` | `trainer` / `Trainer123!` | `200`, `user.username=trainer` |
| Usuario ausente | solo `password` | `400`, sin `token` |
| Contraseña vacía | `student` / `""` | `400`, sin `token` |
| Contraseña incorrecta | `student` / `incorrecta` | `401`, sin `token` |
| Usuario inexistente | `desconocido` / `Workshop123!` | `401`, sin `token` |

**Pruebas de inyección** (escenarios de la spec). Se comprobó el SQL concatenado actual en una base SQLite en memoria; por la precedencia de `AND` sobre `OR`, el resultado antes de la corrección es:

| Entrada | Antes de la corrección | Después | Rol de la prueba |
|---|---|---|---|
| `username=student`, `password=' OR '1'='1` | `200` (vulnerable) | `401` | Prueba roja que pasa a verde |
| `username` y `password` = `' OR '1'='1` | `200` (vulnerable) | `401` | Prueba roja que pasa a verde |
| `username=student'--`, `password=cualquiera` | `200` (vulnerable) | `401` | Prueba roja que pasa a verde |
| `username=' OR '1'='1`, `password` arbitraria | `401` | `401` | Guarda de regresión; ya pasa hoy |

- **Por qué**: confirma que el comportamiento preservado no cambia y demuestra que las pruebas detectan H-AUTH-01. Solo tres de las cuatro variantes son rojas antes del cambio; la cuarta se conserva porque la spec la exige y protege contra regresiones.
- **Alternativa descartada**: prueba unitaria directa de `LegacyDatabase`. Requeriría construir la base a mano y no validaría el contrato HTTP que importa al Issue.

### D4. Datos de prueba aislados
Las pruebas usan los usuarios sembrados por `initialize()` (`student`, `trainer`) y valores de prueba ficticios para el resto; no se añaden secretos ni usuarios nuevos. Se aprovecha `AUTH_DB_URL` si hace falta aislar la base de datos, sin modificar el código para ello.

## Estrategia incremental

Pasos pequeños y revisables, cada uno con `mvn test` desde `auth/` en verde salvo lo indicado:
1. **Caracterización**: añadir las pruebas del contrato vigente. Todas pasan sin tocar código funcional.
2. **Inyección en rojo**: añadir las pruebas de inyección. Deben fallar las tres variantes vulnerables y pasar la cuarta; este resultado se registra como evidencia.
3. **Corrección**: cambiar solo `findByCredentials` (D1). Pasan las pruebas de los pasos 1 y 2.
4. **Verificación**: cobertura JaCoCo del código modificado ≥ 85 %, diff limitado a los dos archivos de "Componentes afectados" y `python tools/lab.py test auth` desde la raíz.

Los pasos 1-2 (pruebas) y 3 (corrección) se pueden revisar y revertir por separado.

## Risks / Trade-offs

- **[Regresión en el login legítimo]** → Pruebas de caracterización escritas y en verde antes de modificar `findByCredentials`.
- **[Una excepción SQL sigue devolviendo `null` y se confunde con "credenciales inválidas"]** → Es el comportamiento actual y está fuera de alcance (Non-Goals). Con parámetros enlazados, la entrada del usuario deja de poder provocar errores de sintaxis SQL.
- **[Cobertura menor a 85 % en el código modificado]** → El método modificado se ejercita con rutas de éxito, sin coincidencia y entradas hostiles; se verifica con el reporte de JaCoCo (`auth/target/site/jacoco`).
- **[Contraseñas en texto plano siguen expuestas]** → Riesgo conocido, no abordado aquí; debe tratarse en un hallazgo separado.
- **[Divergencia entre el baseline (JWT) y el token legado emitido]** → No se toca; las pruebas solo verifican que `token` exista, para no acoplarse al formato.

## Migration Plan

- No hay migración de datos ni cambio de esquema, ni nuevas variables de entorno ni dependencias.
- Despliegue: se publica junto con el resto de `auth`; el contrato HTTP no cambia, por lo que `api/` y `web/` no requieren coordinación.

## Rollback

- Revertir únicamente el commit que modifica `LegacyDatabase.java`; no hay datos ni configuración que restaurar.
- Las pruebas de inyección no se revierten: tras un rollback las tres pruebas rojas volverían a fallar, lo que deja visible que la vulnerabilidad regresó.
- Criterio para revertir: cualquier fallo de las pruebas de caracterización (`200`, `400` o `401`) tras la corrección que no se resuelva en el mismo cambio.
- Dejar la vulnerabilidad activa solo es aceptable de forma temporal mientras se corrige la regresión.
