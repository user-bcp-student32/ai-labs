---

name: release-notes-writer
description: Procedimiento para redactar notas de versión en un archivo local a partir de los PR realmente integrados entre dos referencias de un repositorio, con SHA, enlaces y acciones requeridas respaldadas por evidencia. No publica releases.
---

# Release Notes Writer

## Cuándo usar

Cuando hay que documentar qué cambió entre dos referencias (ramas, tags o SHA) de un repositorio y la nota debe ser rastreable a PR integrados.

## Entradas

```text
repositorio (owner/repo)
referencia inicial (base)
referencia final (head)
ruta del archivo de salida local
```

## Procedimiento

1. **Elige las referencias.** Usa la base anterior al cambio que se documenta y el head que se quiere publicar. Anota por qué se eligieron.
2. **Resuelve cada referencia a SHA mediante MCP.** Usa `get_commit` o `list_commits` (primer resultado) del servidor GitHub MCP. Registra el SHA completo. Si la referencia es ambigua o no resuelve, detente y pide la referencia.
3. **Verifica localmente la ascendencia.** Con `git fetch origin` y `git rev-parse` confirma que ambos SHA existen. Lista los commits del intervalo con `git --no-pager log --oneline --graph <base>..<head>`. Si el repositorio local tiene `main` desfasado respecto a `origin/main`, regístralo y no lo modifiques.
4. **Enumera los PR candidatos.** Usa `list_pull_requests` o `search_pull_requests` con `state: all`. Solo son candidatos los PR con `merged_at` no nulo.
5. **Prueba la integración de cada PR en el intervalo.** Un PR cuenta como integrado solo si se cumple al menos una condición demostrable:
   - su `merge_commit_sha` es ascendente de `head` y no de `base` (`git merge-base --is-ancestor`);
   - es un squash o rebase: el árbol de su commit de integración coincide con el árbol de la cabeza del PR (`git diff --stat <cabeza_PR> <commit_integración>` vacío) y ese commit es ascendente de `head` y no de `base`;
   - si el PR se integró en otra rama, sigue la cadena de integraciones hasta `head` (por ejemplo, PR A en `dev`, luego PR B de `dev` a `main`) y demuestra cada eslabón.

   Un PR abierto, cerrado sin fusión, o mencionado en un documento **no** se incluye. Si no puedes demostrar la integración, déjalo fuera y registra la incertidumbre en la sección de evidencia.
6. **Obtén el contenido de cada PR integrado.** Usa `pull_request_read` con `get`, `get_files` y `get_commits`. Lee título, cuerpo, archivos y commits. El cuerpo puede ser una plantilla sin rellenar; en ese caso, deriva el efecto del diff y de los commits, y dilo.
7. **Redacta cada cambio** solo con evidencia:
   - **Efecto:** qué cambia para quien usa el producto, respaldado por el diff o el PR.
   - **Acciones requeridas:** pasos que el usuario o el operador deben ejecutar (configurar variables, instalar dependencias, reautenticar, cambiar clientes). Cada acción debe citar el archivo, función o commit que la justifica.
   - **Cambios incompatibles:** cualquier cambio de contrato, de formato o de comportamiento que rompa a un cliente existente.
   - **Enlace** al PR y a su commit de integración.
8. **Registra la evidencia.** Incluye SHA base y head, PR integrados, PR excluidos con motivo, comandos ejecutados y resultados, e incertidumbres.
9. **Escribe el archivo local** en la ruta indicada. No hagas push, no crees tags, releases ni PR.
10. **Verifica el archivo:** los SHA citados existen (`git cat-file -e <sha>`), cada enlace corresponde a un PR integrado y cada acción tiene su justificación.

## Estructura sugerida

```markdown
# Notas de versión (borrador, no publicado)

## Intervalo
- Base: <SHA completo> (<motivo de elección>)
- Head: <SHA completo> (<rama o referencia resuelta vía MCP>)
- Commits en el intervalo: <lista>

## Cambios
### PR #<n> — <título> ([enlace](<url>))
- Integración: <commit de integración y cómo se demostró>
- Efecto:
- Acciones requeridas:
  - <acción> — evidencia: <archivo/función/commit>
- Cambios incompatibles: <sí/no y detalle>

## Excluidos
- PR #<n> — <motivo> - <límite de verificación>

## Incertidumbre y evidencia
- <punto>

## Verificación
| Comando | Resultado |
|---|---|
```

## Reglas

- no incluir PR sin integración demostrada dentro del intervalo;
- no inventar beneficios, métricas, correcciones ni compatibilidad;
- no tomar como verificado un resultado de CI o de escáner que no se haya reejecutado: márcalo como "reportado por el PR";
- no convertir una acción en requisito si la evidencia no lo demuestra;
- no publicar release, tag, PR, comentario ni push;
- no usar secretos reales en ejemplos de configuración;
- no modificar ramas locales para resolver la integración.
