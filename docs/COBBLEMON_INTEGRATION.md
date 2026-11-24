# Integración Cobblemon

Versión objetivo: Cobblemon 1.8.4+1.21.1 (la de COBBLEVERSE 1.8.0). API auditada en `docs/COBBLEMON_API_AUDIT.md`.

- El core funciona sin Cobblemon. El adaptador `integration.cobblemon` solo se carga si el mod está presente.
- Se usan eventos públicos (`CobblemonEvents`) con la sobrecarga `subscribe(Priority, Consumer)`; sin Mixins.
- Eventos: captura, evolución y victoria en combate (salvaje o NPC; PvP no cuenta).
- Crónicas: primera captura del grupo, capturas shiny y legendarias, evoluciones.
- No se redistribuye Cobblemon ni recursos de Cobbleverse.

## Implementación
- `integration/cobblemon/CobblemonCompat`: comprueba si Cobblemon está cargado y activa los ganchos. Si la API no encaja (versión distinta), registra el error y el resto del mod sigue funcionando.
- `integration/cobblemon/CobblemonHooks`:
  - **Captura:** cuenta para `CATCH_POKEMON` con claves de especie (`cobblemon:pikachu`), `type:<tipo>`, `shiny` y `legendary`. Escribe en las crónicas la primera captura del grupo, las variocolor (con aviso al dúo) y las legendarias.
  - **Evolución:** cuenta para `EVOLVE_POKEMON` y escribe en las crónicas "X evoluciona a Y".
  - **Victoria:** cuenta para `WIN_BATTLE` con `wild` y/o `npc`. No cuentan el PvP ni los combates que terminan en captura.
  - Si un evento llega desde otro hilo, se reenvía al hilo del servidor.

## Estado de las pruebas
- Probado en un servidor COBBLEVERSE 1.8.0 de producción: el adaptador se activa y las 26 misiones se cargan.
- Sin probar con un jugador real (capturar, evolucionar o combatir requiere un cliente con el modpack completo).
