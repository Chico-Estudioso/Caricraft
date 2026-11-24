# Integración Cobblemon

Versión objetivo: Cobblemon 1.8.4+1.21.1 (la de COBBLEVERSE 1.8.0). API auditada en `docs/COBBLEMON_API_AUDIT.md`.

- El core funciona sin Cobblemon. El adaptador `integration.cobblemon` solo se carga si el mod está presente.
- Se usan eventos públicos (`CobblemonEvents`) con la sobrecarga `subscribe(Priority, Consumer)`; sin Mixins.
- Eventos: captura, evolución y victoria en combate (salvaje o NPC; PvP no cuenta).
- Crónicas: primera captura del grupo, capturas shiny y legendarias, evoluciones.
- No se redistribuye Cobblemon ni recursos de Cobbleverse.
