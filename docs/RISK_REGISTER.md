# Registro de riesgos

| # | Riesgo | Prob. | Impacto | Mitigación |
|---|---|---|---|---|
| R1 | El modpack se actualiza y cambian Fabric API o Cobblemon | Media | Alto | Versiones fijadas; repetir `ENVIRONMENT_AUDIT` antes de subir versiones; adaptador Cobblemon aislado. |
| R2 | Pérdida o duplicación de Carins (reintentos, doble clic, desconexión) | Media | Alto | Servidor autoritativo, operaciones en el hilo del servidor, ledger, claves de idempotencia, estados de misión `CLAIMED`. |
| R3 | Corrupción o pérdida de datos guardados | Baja | Alto | `SavedData` de Minecraft con `schemaVersion` y migraciones; tests de ida y vuelta NBT. |
| R4 | Paquetes manipulados desde un cliente | Media | Medio | Validación de longitud, enum de acciones, permisos, límite de frecuencia por jugador. |
| R5 | Coste de rendimiento por seguimiento de progreso | Baja | Medio | Muestreo de bioma/distancia cada 40 ticks por jugador; sin escaneos de mundo. |
| R6 | Conflictos de teclas con otros mods | Media | Bajo | La tecla del teléfono es reasignable; el ítem es la vía principal. |
| R7 | Duplicar funciones del modpack | Media | Medio | `MODPACK_FEATURE_AUDIT.md`; CariLens y CariCamp aplazados. |
| R8 | Abuso de vincular/desvincular para mover Carins | Baja | Bajo | Reparto 50 %, enfriamiento tras desvincular, confirmación explícita. |
| R9 | Licencias (Cobblemon MPL-2.0, assets de terceros) | Baja | Alto | No redistribuir JAR ni assets; iconos con ítems vanilla dibujados por el juego. |
| R10 | Mobs del desafío que dañan a Pokémon sacados o rompen construcciones | Media | Medio | Creepers fuera por defecto; lista configurable; desaparecen al amanecer. Pendiente probar el daño a Pokémon en COBBLEVERSE. |
| R11 | Otro mod también modifica `addFreshEntity` | Baja | Medio | El filtro solo actúa sobre los tipos de la lista; si falla, deja pasar la entidad y lo registra. |
