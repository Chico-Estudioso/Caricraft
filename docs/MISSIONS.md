# Misiones

## Tipos
- **Individual:** cada miembro tiene su propio progreso; la recompensa va al saldo del grupo.
- **Cooperativa:** progreso compartido; requiere un dúo completo. Puede exigir que **ambos** contribuyan (`requireAllMembers`).
- **De grupo (`PARTY`):** progreso compartido aunque el grupo tenga un solo miembro (se usa en los hitos).
- **Hito:** se activa sola y se reclama al completarse.
- **Diaria / semanal:** se reinicia al cambiar el día o la semana (UTC).

## Ciclo
`BLOQUEADA → DISPONIBLE → ACEPTADA → COMPLETADA → RECLAMADA`, más `ABANDONADA` (vuelve a estar disponible) y `CADUCADA` (diarias/semanales fuera de su ciclo).

## Objetivos
| Tipo | Origen | Ejemplo de objetivo |
|---|---|---|
| `TRAVEL` | Muestreo de posición | metros recorridos |
| `VISIT_BIOME` | Muestreo de bioma | entrar en un bioma (cada cambio cuenta) o en uno concreto |
| `DISCOVER_BIOME` | Muestreo de bioma | biomas nuevos para el grupo (o uno concreto) |
| `MINE_BLOCK` | `PlayerBlockBreakEvents.AFTER` | `minecraft:iron_ore` o `#minecraft:logs` |
| `CRAFT_ITEM` | Mixin `ItemStack.onCraftedBy` | `minecraft:bread` |
| `KILL_ENTITY` | `ServerLivingEntityEvents.AFTER_DEATH` | id de entidad o `#tag` (sin misiones por defecto, ver nota) |
| `DELIVER_ITEM` | Acción "Entregar" del teléfono | quita ítems del inventario |
| `CATCH_POKEMON` | Cobblemon `POKEMON_CAPTURED` | cualquiera, especie, `shiny`, `legendary`, `type:fire` |
| `EVOLVE_POKEMON` | Cobblemon `EVOLUTION_COMPLETE` | cualquiera o especie |
| `WIN_BATTLE` | Cobblemon `BATTLE_VICTORY` | `wild`, `npc` o cualquiera (no PvP) |

## Definición
Las misiones por defecto están en `caricraft/default_missions.json` dentro del JAR (26 misiones, 13 de ellas usan Cobblemon). Un servidor puede sustituirlas con `config/caricraft_missions.json` (mismo formato). Las misiones que requieren Cobblemon (`requiresMod`) se ocultan si no está cargado, y también las que dependen de ellas.

Formato de cada misión:

```json
{ "id": "duo_supplies", "scope": "COOP", "category": "STORY", "reward": 150, "icon": "minecraft:barrel",
  "requires": [], "requiresApp": null, "requiresMod": null, "requireAllMembers": false,
  "title": "Texto si no hay traducción", "description": "Idem",
  "objectives": [ { "type": "DELIVER_ITEM", "target": "#minecraft:logs", "amount": 64 } ] }
```

`target` vacío significa "cualquiera"; `#namespace:tag` es una tag de ítems o bloques.

## Notas de diseño para COBBLEVERSE
COBBLEVERSE 1.8.0 elimina 84 mobs vanilla (MobsBeGone: zombis, vacas, ovejas, esqueletos…) y desactiva el hambre con un datapack. Por eso las misiones por defecto no piden matar mobs ni entregar comida. El objetivo `KILL_ENTITY` sigue disponible para misiones personalizadas.

## Reclamación
Idempotente: una misión `RECLAMADA` no paga dos veces y el crédito usa la clave `mission:<grupo>:<misión>:<ciclo>`.
