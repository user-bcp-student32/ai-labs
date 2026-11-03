# Evidencia de pruebas

## H-API-02 — JWT compartido entre auth y api

Fecha de ejecución: 2026-11-03. Las pruebas usan una clave fija solo de test; no se usó ni agregó una clave de producción.

| Componente | Comando | Resultado |
|---|---|---|
| api | `python tools/lab.py test api` | PASS — 22 pruebas; cobertura total 94.63%, sobre el umbral de 85%. |
| auth | `python tools/lab.py test auth` | PASS — 11 pruebas. JaCoCo: clases modificadas `AuthResource`, `AuthService` y `JwtTokenService` con 100% de cobertura de instrucciones y ramas. |

Las pruebas validan el contrato de `POST /auth/login`, el token HS256 emitido, sus claims, y la aceptación/rechazo de tokens en las rutas protegidas. También cubren firma incorrecta, audiencia distinta, expiración, claims de identidad incompletos, clave de configuración ausente o corta y rechazo del token legado.

`compose.yaml` se pudo parsear con PyYAML. `docker compose config --quiet` y una petición real entre los contenedores no se ejecutaron porque Docker no está disponible en este entorno. El frontend no se probó; no forma parte del alcance de H-API-02.
