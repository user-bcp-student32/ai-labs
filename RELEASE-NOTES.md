# Notas de versión (borrador, no publicado)

## Intervalo
- Base: `150f7953902e7fb4b3a6f0f89447e60f191c4cb6` (commit de integración de PR #23; se usa el commit de merge como referencia inicial).
- Head: `150f7953902e7fb4b3a6f0f89447e60f191c4cb6` (`main`, resuelta mediante GitHub MCP).
- Commits en `base..head`: ninguno; ambos extremos son el mismo commit.

## Cambios
No hay PRs integrados dentro del intervalo, por lo que no hay cambios de producto, acciones requeridas ni incompatibilidades que reportar.

## Excluidos
- PR [#23 — Day2/skill mcp](https://github.com/user-bcp-student32/ai-labs/pull/23): es el límite inicial del intervalo; su commit de integración también es el head actual, por lo que no pertenece a `base..head`.
- PR [#21 — day1: prompts, correcciones, pruebas y documentación](https://github.com/user-bcp-student32/ai-labs/pull/21): fue integrada el 2026-10-26, antes del límite inicial (PR #23 se integró el 2026-10-28).

## Incertidumbre y evidencia
- GitHub MCP confirmó que PR #23 está integrada y devolvió `head` `304d9c1e73df87bd9823f8a895b60ebb16c1d89e` y `base` `0bdb5ec2b0a75480d06243fc0bd1621a428e75ca`. La resolución MCP de `main` devolvió `150f7953902e7fb4b3a6f0f89447e60f191c4cb6`, cuyo mensaje es `Merge pull request #23 ...`.
- GitHub MCP confirmó que PR #21 está integrada y que su fecha de integración es 2026-10-26T22:11:07Z.
- La rama local `main` está en `0bdb5ec2b0a75480d06243fc0bd1621a428e75ca`, mientras que `origin/main` está en `150f7953902e7fb4b3a6f0f89447e60f191c4cb6`. No se modificó ninguna rama local.
- No se infieren acciones operativas ni cambios incompatibles, porque el intervalo no contiene commits.

## Verificación
| Comando / consulta | Resultado |
|---|---|
| GitHub MCP `get_commit(main)` | Resolvió `main` a `150f7953902e7fb4b3a6f0f89447e60f191c4cb6`; mensaje de merge de PR #23. |
| GitHub MCP `pull_request_read(get, #23)` | PR #23 `merged`; confirma SHA de cabeza y base. |
| `git fetch origin` | Actualizó `origin/main` a `150f7953902e7fb4b3a6f0f89447e60f191c4cb6`; no modificó ramas locales. |
| `git cat-file -e 150f7953902e7fb4b3a6f0f89447e60f191c4cb6^{commit}` | Éxito; el SHA existe localmente. |
| `git rev-parse main` / `git rev-parse origin/main` | `main` local = `0bdb5ec2b0a75480d06243fc0bd1621a428e75ca`; `origin/main` = `150f7953902e7fb4b3a6f0f89447e60f191c4cb6`. |
| `git --no-pager log --oneline --graph 150f7953902e7fb4b3a6f0f89447e60f191c4cb6..origin/main` | Sin salida; no hay commits en el intervalo. |
| `git merge-base --is-ancestor 150f7953902e7fb4b3a6f0f89447e60f191c4cb6 origin/main` | Código de salida `0`; la base es ancestro de `origin/main` (idéntica al head). |