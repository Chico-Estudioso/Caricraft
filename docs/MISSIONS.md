# Misiones

## Tipos
- **Individual:** cada miembro tiene su propio progreso; la recompensa va al saldo del grupo.
- **Cooperativa:** progreso compartido; requiere un dúo completo. Puede exigir que **ambos** contribuyan (`requireAllMembers`).
- **Hito:** se activa sola y se reclama al completarse.
- **Diaria / semanal:** se reinicia al cambiar el día o la semana (UTC).

## Ciclo
`BLOQUEADA → DISPONIBLE → ACEPTADA → COMPLETADA → RECLAMADA`, más `ABANDONADA` (vuelve a estar disponible) y `CADUCADA` (diarias/semanales fuera de su ciclo).

## Objetivos
| Tipo | Origen | Ejemplo de objetivo |
|---|---|---|
| `TRAVEL` | Muestreo de posición | metros recorridos |
| `DISCOVER_BIOME` | Muestreo de bioma | biomas nuevos (o uno concreto) |
| `MINE_BLOCK` | `PlayerBlockBreakEvents.AFTER` | `minecraft:iron_ore` o `#minecraft:logs` |
| `CRAFT_ITEM` | Mixin `ItemStack.onCraftedBy` | `minecraft:bread` |
| `KILL_ENTITY` | `ServerLivingEntityEvents.AFTER_DEATH` | `minecraft:zombie` |
| `DELIVER_ITEM` | Acción "Entregar" del teléfono | quita ítems del inventario |
| `CATCH_POKEMON` | Cobblemon `POKEMON_CAPTURED` | cualquiera, especie, `shiny` |
| `EVOLVE_POKEMON` | Cobblemon `EVOLUTION_COMPLETE` | cualquiera o especie |
| `WIN_BATTLE` | Cobblemon `BATTLE_VICTORY` | `wild`, `npc` o cualquiera (no PvP) |

## Definición
Las misiones por defecto están en `data/caricraft/missions/default_missions.json` dentro del JAR. Un servidor puede sustituirlas con `config/caricraft_missions.json`. Las misiones que requieren Cobblemon (`requiresMod`) se ocultan si no está cargado.

## Reclamación
Idempotente: una misión `RECLAMADA` no paga dos veces y el crédito usa la clave `mission:<grupo>:<misión>:<ciclo>`.
