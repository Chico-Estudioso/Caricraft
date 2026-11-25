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

## Eventos disponibles pero no usados todavía

`HATCH_EGG_POST`, `TRADE_EVENT_POST`, `STARTER_CHOSEN`, `POKEMON_SCANNED`, `POKEDEX_DATA_CHANGED_POST`, `BATTLE_FAINTED`.

## Reglas de integración

- Todo el código que toca clases de Cobblemon vive en `integration/cobblemon` y solo se carga si `FabricLoader.isModLoaded("cobblemon")`.
- Sin Mixins sobre Cobblemon.
- Los manejadores solo traducen el evento a un `ProgressEvent` del core; no cambian el balance ni el estado de Cobblemon.
- Las victorias contra jugadores (PvP) no cuentan, para evitar farmeo dentro del dúo.
