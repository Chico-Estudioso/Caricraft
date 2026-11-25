# Auditoría de funcionalidades del modpack (M0)

Objetivo: no duplicar lo que COBBLEVERSE 1.8.0 ya ofrece. Decisiones: **reutilizar** (usar tal cual), **integrar** (escuchar sus eventos), **complementar** (añadir algo que no existe) o **no desarrollar**.

| Sistema | Ya existe en el modpack | Decisión para Caricraft Mod |
|---|---|---|
| Economía de tienda | CobbleDollars (moneda, tiendas, NPCs) | **Complementar.** Los Carins son una moneda propia del dúo, solo para progresión del CariPhone. No se intercambian con CobbleDollars ni se pueden comerciar entre jugadores fuera del dúo. |
| Pokédex | Pokédex integrada de Cobblemon 1.8.4 | **No desarrollar** un álbum de especies. CariLens es un álbum de **fotos reales** del dúo, no de especies. |
| Radar / PokéNav | cobblenav 2.4.2 | **No desarrollar.** |
| Mapa y minimapa | Xaero's Minimap y World Map | **No desarrollar** mapas. La app Exploración muestra solo el registro de biomas descubiertos por el dúo. |
| Teletransporte | Waystones | **No desarrollar.** |
| Entrenadores, gimnasios y medallas | Radical Cobblemon Trainers + CobbleverseBadges | **Integrar** de forma indirecta: las victorias contra NPC cuentan para misiones vía `BATTLE_VICTORY` de Cobblemon. |
| Incursiones | Cobblemon Raid Dens | **Aplazado.** Sin evento público auditado todavía. |
| Campamento / descanso | Comforts (sacos de dormir, hamacas), Waystones | **Complementar:** CariCamp no da camas ni red de teletransporte. Da un campamento propio del dúo en una hoguera, brújula hacia el campamento y el compañero, y viajes pagados con Carins. |
| Cría | Cobbreeding | **Aplazado** (posible objetivo futuro con `HATCH_EGG_POST`). |
| Mochilas y almacenamiento | Sophisticated Backpacks/Storage, Tom's Storage | **Complementar:** solo **CariBox**, un cofre compartido por el dúo y accesible desde cualquier parte (lo demás es personal o fijo). |
| Logros | Avances vanilla + Advancement Plaques | **Complementar:** las misiones son cooperativas, con recompensa y reclamables. |
| Sistema de misiones | No hay (no hay FTB Quests ni similar) | **Desarrollar:** misiones individuales, cooperativas, diarias, semanales e hitos. |
| Diario / crónicas | No hay | **Desarrollar:** Crónicas del dúo con notas de los jugadores. |
| Grupos / equipos | No hay | **Desarrollar:** vínculo de dúo propio. |
| Teléfono / interfaz central | No hay (cobblenav es solo radar) | **Desarrollar:** CariPhone. |
| Estadísticas del dúo | Estadísticas vanilla individuales | **Complementar:** app Estadísticas con datos compartidos del dúo. |
| Fotografía | No hay | **Desarrollado:** CariLens. Miniaturas de 256×144 guardadas en el mundo del servidor; nada sale a internet. |

## Cambios de jugabilidad del modpack que afectan a las misiones
- **MobsBeGone** elimina 78 de las 82 criaturas vanilla de 1.21.1 (lista en `config/mobsbegone-blacklist.json`, 84 entradas). Solo quedan aldeanos, comerciantes ambulantes y el dragón, que se quita con otro datapack. Lo hace con un mixin en `ServerLevel.addFreshEntity` que descarta cualquier entidad de la lista al entrar (natural, spawner, huevo o `/summon`), y otra vez al cargar chunks. El **Desafío Dúo** (`docs/CHALLENGE.md`) reactiva una selección de hostiles solo durante sus noches.
- El datapack **COBBLEVERSE - No Hunger** desactiva el hambre.
- **Terralith** está incluido como datapack: hay muchos más biomas que descubrir.

## Consecuencias

- El catálogo es:
  - Gratis: Misiones, Cartera, Dúo, Tienda, Ajustes, Mercado y Desafío Dúo.
  - De pago: Crónicas, Exploración, Estadísticas, CariLens (con ampliaciones de álbum) y CariCamp.
  - Temas del teléfono, como cosméticos personales.
- Las misiones pueden pedir ítems del modpack (p. ej. Poké Balls, bayas, bonguris) solo cuando Cobblemon está cargado; si no, se ocultan.
- Las misiones por defecto no piden matar mobs vanilla ni entregar comida.
