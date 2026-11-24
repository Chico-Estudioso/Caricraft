# PROMPT 08 — Integración Cobblemon

Verifica `docs/COBBLEMON_API_AUDIT.md` contra la versión exacta. No asumas eventos ni firmas.

Crea adaptador opcional para capturas, evoluciones, combates/raids o descubrimientos solo si API pública fiable lo permite. Prefiere eventos; evita Mixins salvo ADR justificada. Core debe funcionar sin Cobblemon. No alterar balance ni redistribuir recursos. Prueba ausencia de dependencia y versión fijada.
