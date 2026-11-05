# Tasks

Unidad de trabajo del producto: Issue [#22](https://github.com/user-bcp-student32/ai-labs/issues/22) (H-AUTH-01). Este checklist cubre solo la implementación de este cambio dentro de `auth/`. Las pruebas de `auth/` se ejecutan desde ese directorio; Git y OpenSpec, desde la raíz de `ai-labs`.

## 1. Línea base

- [x] 1.1 Ejecutar `mvn test` desde `auth/` sin cambios y verificar que pasa la única prueba existente (`logsInWithWorkshopUser`); anotar el resultado como línea base.

## 2. Pruebas de caracterización (sin modificar código funcional)

- [x] 2.1 Ampliar la prueba existente de `student` en `AuthResourceTest` para verificar `expiresIn`, `user.displayName`, `user.role` y `debug` sin asertar el formato del token; verificar con `mvn test` desde `auth/` que pasa contra el código actual.
- [x] 2.2 Añadir una prueba de login de `trainer` / `Trainer123!` (`200`, `user.username` igual a `trainer`, `token` no vacío); verificar con `mvn test` desde `auth/` que pasa contra el código actual.
- [x] 2.3 Añadir pruebas de `400` por `username` ausente y por `password` vacío, ambas sin `token`; verificar con `mvn test` desde `auth/` que pasan contra el código actual.
- [x] 2.4 Añadir pruebas de `401` por contraseña incorrecta y por usuario inexistente, ambas sin `token`; verificar con `mvn test` desde `auth/` que pasan contra el código actual.

## 3. Pruebas del intento de inyección (H-AUTH-01)

- [x] 3.1 Añadir en `AuthResourceTest` las tres pruebas que hoy detectan la vulnerabilidad, cada una con `401` y sin `token` como resultado esperado: `' OR '1'='1` en `password` con `username` `student`, el mismo valor en ambos campos, y `student'--` como `username` con contraseña arbitraria; verificar con `mvn test` desde `auth/` que las tres fallan por responder `200` y que las pruebas del grupo 2 siguen en verde, y registrar el resultado como evidencia (no integrar esta etapa por separado con la suite en rojo).
- [x] 3.2 Añadir la guarda de regresión con `' OR '1'='1` como `username` y una contraseña arbitraria (`401` sin `token`); verificar con `mvn test` desde `auth/` que ya pasa contra el código actual, por la precedencia de `AND` sobre `OR`.

## 4. Corrección en `LegacyDatabase`

- [x] 4.1 Reemplazar en `LegacyDatabase.findByCredentials` la concatenación y el `Statement` por una constante SQL con nombre y marcadores `?`, ejecutada con `PreparedStatement` y `setString` para `username` y `password`; conservar la firma, el retorno `null` y el mapeo a `UserRecord`, y no modificar `initialize()`; verificar que el proyecto compila con `mvn test` desde `auth/`.
- [x] 4.2 Verificar con `grep` sobre `auth/src/main` que ningún valor del usuario se concatena en el SQL de `findByCredentials` y que `AuthResource` y `LegacyTokenUtil` quedan sin cambios.
- [x] 4.3 Ejecutar `mvn test` desde `auth/` y verificar que pasan todas las pruebas de los grupos 2 y 3: las tres de inyección que estaban en rojo, la guarda de regresión y las de caracterización.

## 5. Validación final del cambio

- [x] 5.1 Ejecutar `python tools/lab.py test auth` desde la raíz (corre `mvn verify` en `auth/`) y verificar que termina en verde y genera el reporte JaCoCo en `auth/target/site/jacoco`.
- [x] 5.2 Verificar en el reporte JaCoCo que el código modificado (`findByCredentials`) tiene cobertura igual o superior a 85 %; si no la alcanza, añadir solo la prueba faltante dentro de este alcance.
- [x] 5.3 Verificar con `git diff --stat` desde la raíz que solo cambiaron `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java` y `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`, además de los archivos de este cambio OpenSpec, sin cambios en `api/` ni `web/`.
- [x] 5.4 Verificar uno por uno los cuatro criterios de aceptación del Issue #22 (consulta parametrizada, `200` con `student`, `401` con inyección, prueba de inyección y `mvn test` en verde) y anotar la evidencia real de cada uno para la revisión humana, sin dar por aprobado lo que no se ejecutó.

## Workflow follow-up

- Solicitar revisión y aprobación humanas del cambio antes de integrarlo.
- Archivar el cambio de OpenSpec una vez satisfechos los requisitos de revisión del proyecto, y verificar el resultado archivado.
