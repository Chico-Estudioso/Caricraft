# Caricraft Mod | Inicio rápido

Mod de Minecraft Java 1.21.1 + Fabric para el servidor privado **Caricraft 2**, que corre sobre el modpack **COBBLEVERSE 1.8.0**.

## Concepto
Dos jugadores comparten una aventura mediante un teléfono dentro del juego (el **CariPhone**): aplicaciones desbloqueables, una moneda propia (**Carins**), misiones individuales y cooperativas, crónicas del dúo y exploración.

## Versiones objetivo (fijadas)
| Componente | Versión |
|---|---|
| Modpack | COBBLEVERSE 1.8.0 |
| Minecraft | 1.21.1 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.116.18+1.21.1 |
| Cobblemon | 1.8.4+1.21.1 (opcional) |
| Java | 21 |

Origen de los datos: `modrinth.index.json` del `.mrpack` oficial de COBBLEVERSE 1.8.0. Detalle en `docs/ENVIRONMENT_AUDIT.md`. No se actualiza nada automáticamente.

> Caricraft Mod es un mod aparte: no modifica el modpack ni lo redistribuye. Si el modpack cambia de versión, se repite la auditoría antes de tocar dependencias.

## Git
- `main` es la rama estable; no se trabaja directamente sobre ella.
- Una rama por tarea: `feat/...`, `fix/...`, `docs/...`, `test/...`.
- Integración mediante Pull Request.
- No `push --force`.
- Commits pequeños y descriptivos.
- Mantener `ROADMAP.md` y `CHANGELOG.md`.

## Orden de trabajo
00 investigación → 01 arquitectura → 02 base Fabric → 03 teléfono/UI → 04 economía → 05 misiones → 06 vínculo/crónicas → 07 apps de contenido → 08 Cobblemon → 09 QA → 10 release.

Los prompts de `prompts/` son guías de trabajo, no hay que ejecutarlos todos a la vez.
