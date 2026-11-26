# Auditoría de la API de Cobblemon (M0)

Versión auditada: **Cobblemon 1.8.4+1.21.1** (`com.cobblemon:fabric:1.8.4+1.21.1`, Maven de ImpactDev), la misma que trae COBBLEVERSE 1.8.0. Licencia: **MPL-2.0** (se compila contra ella; no se redistribuye).

Método: inspección con `javap` de las clases del JAR publicado. Todo lo de esta página es **[H]** salvo que se indique.

## Suscripción a eventos

`com.cobblemon.mod.common.api.events.CobblemonEvents` expone campos estáticos `EventObservable<T>`.
`Observable<T>` ofrece sobrecargas aptas para Java:

```java
ObservableSubscription<T> subscribe(Priority priority, java.util.function.Consumer<T> handler);
```

`Priority` (`com.cobblemon.mod.common.api.Priority`): `HIGHEST, HIGH, NORMAL, LOW, LOWEST`.

## Eventos usados por Caricraft Mod

| Evento | Clase | Datos usados |
|---|---|---|
| `POKEMON_CAPTURED` | `api.events.pokemon.PokemonCapturedEvent` | `getPokemon()`, `getPlayer()` (`ServerPlayer`) |
| `EVOLUTION_COMPLETE` | `api.events.pokemon.evolution.EvolutionCompleteEvent` | `getPokemon()` → `getOwnerPlayer()` |
| `BATTLE_VICTORY` | `api.events.battles.BattleVictoryEvent` | `getWinners()`, `getLosers()`, `getWasWildCapture()`; `BattleActor.getType()` (`WILD`, `PLAYER`, `NPC`) y `getPlayerUUIDs()` |

Datos de `Pokemon` usados: `getSpecies()` (`Species.getResourceIdentifier()`, `getName()`), `getShiny()`, `isLegendary()`, `getOwnerPlayer()`.

## API usada por la pestaña Cobbleverse (`CobblemonBackend`)
Verificada en el JAR de Cobblemon 1.8.4+1.21.1 y en un servidor COBBLEVERSE 1.8.0 real (`/caricraft cobbleverse prueba`).

| Uso | API |
|---|---|
| Equipo del jugador | `Cobblemon.INSTANCE.getStorage().getParty(ServerPlayer)` → `PlayerPartyStore`: `size()`, `get(int)`, `add(Pokemon)` (si el equipo está lleno, va al PC con `getOverflowPC`; pone al jugador como entrenador original) y `remove(Pokemon)` |
| Datos del Pokémon | `Pokemon.getUuid()`, `getSpecies()` (`getResourceIdentifier()`, `getCatchRate()`), `getLevel()`, `getShiny()`/`setShiny`, `getIvs().get/set(Stats, int)`, `getNickname()`, `isFainted()`, `isLegendary()`, `isMythical()`, `isUltraBeast()`, `getEntity()` |
| Crear Pokémon | `PokemonProperties.Companion.parse("species=… level=… shiny=true").create(ServerPlayer)` |
| Movimientos | `Moves.getByName(id)` → `MoveTemplate.create()`; `pokemon.getMoveSet().setMove(i, move)` |
| Especies | `PokemonSpecies.getByName(id)` |
| Combate | `BattleRegistry.getBattleByParticipatingPlayer(ServerPlayer)` |
| Centro Pokémon de bolsillo | `PlayerPartyStore.heal()`; para saber si hace falta: `Pokemon.isFainted()`, `getCurrentHealth()`, `getMaxHealth()`, `getStatus()`, `getMoveSet().getMoves()` → `Move.getCurrentPp()`/`getMaxPp()`. QA: `setCurrentHealth(int)` |
| Tipos | `Pokemon.getTypes()` → `ElementalType.getName()` |

## Apariciones salvajes (`CobblemonHooks`)
Verificado en el JAR de Cobblemon 1.8.4:
- `CobblemonEvents.POKEMON_ENTITY_SPAWN` (`TransformObservable` sobre `ENTITY_SPAWN`, que es cancelable). `SingleEntitySpawnAction` coloca la entidad, emite el evento y, si nadie lo cancela, la añade al mundo. El `SpawnEvent` lleva la entidad ya colocada: `getEntity()`, `isCanceled()`, `cancel()`.
- **Aviso de variocolor** (prioridad `LOWEST`): solo lee la especie, el nivel y la posición; en el tick siguiente comprueba que la entidad sigue en el mundo.
- **Opción `levelCapSpawns`** (prioridad `HIGH`, desactivada por defecto): `Pokemon.setLevel(int)` o `cancel()`. El nivel de partida lo pone `PlayerLevelRangeInfluence`: rango de la especie ∩ [nivel máximo del equipo − 5, + 5] (`TYPICAL_VARIATION` = 5; sin equipo, 1 a `minimumLevelRangeMax`), recalculado cada 5 s; si no se cruzan, el cuarto bajo o alto del rango de la especie.

## Level cap de RCT (`RctBridge`)
Radical Cobblemon Trainers 0.19.2-beta (LGPL-3), por reflexión y sin compilar contra él: `RCTMod.getInstance().getTrainerManager().getData(Player).getLevelCap()`. RCT usa el cap para dos cosas: los Pokémon en el cap o por encima no ganan experiencia (`allowOverLeveling = false` en COBBLEVERSE) y los entrenadores no combaten si llevas uno por encima. No limita apariciones ni capturas.

## Eventos disponibles pero no usados todavía

`HATCH_EGG_POST`, `TRADE_EVENT_POST`, `STARTER_CHOSEN`, `POKEMON_SCANNED`, `POKEDEX_DATA_CHANGED_POST`, `BATTLE_FAINTED`.

## Reglas de integración

- Todo el código que toca clases de Cobblemon vive en `integration/cobblemon` y solo se carga si `FabricLoader.isModLoaded("cobblemon")`.
- Sin Mixins sobre Cobblemon.
- Los manejadores solo traducen el evento a un `ProgressEvent` del core; no cambian el balance ni el estado de Cobblemon. Única excepción, opcional y desactivada: `levelCapSpawns` ajusta o cancela apariciones salvajes.
- Las victorias contra jugadores (PvP) no cuentan, para evitar farmeo dentro del dúo.
