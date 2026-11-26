package com.chicoestudioso.caricraft.integration.cobblemon;

import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.mission.ObjectiveType;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import com.chicoestudioso.caricraft.server.ServerEvents;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionCompleteEvent;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Suscripciones a eventos públicos de Cobblemon 1.8.4 (ver docs/COBBLEMON_API_AUDIT.md).
 * Traduce eventos a progreso del núcleo. Lo único que cambia es el nivel de los salvajes que aparecen (o cancela
 * la aparición) cuando están activas las apariciones según el level cap de RCT.
 */
final class CobblemonHooks {
    static final String STAT_SHINY = "shiny_caught";

    private CobblemonHooks() {
    }

    static void register() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL,
                (Consumer<PokemonCapturedEvent>) event -> onServer(event.getPlayer(), () -> onCaptured(event.getPlayer(), event.getPokemon())));
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe(Priority.NORMAL,
                (Consumer<EvolutionCompleteEvent>) CobblemonHooks::onEvolution);
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL,
                (Consumer<BattleVictoryEvent>) CobblemonHooks::onVictory);
        // Apariciones según el level cap: antes que nadie, para que el resto vea ya el nivel final.
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.HIGH,
                (kotlin.jvm.functions.Function1<com.cobblemon.mod.common.api.events.entity.SpawnEvent<
                        com.cobblemon.mod.common.entity.pokemon.PokemonEntity>, kotlin.Unit>) event -> {
                    onLevelCap(event);
                    return kotlin.Unit.INSTANCE;
                });
        // Variocolor salvaje recién aparecido: aviso con brújula al dúo.
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.LOWEST,
                (kotlin.jvm.functions.Function1<com.cobblemon.mod.common.api.events.entity.SpawnEvent<
                        com.cobblemon.mod.common.entity.pokemon.PokemonEntity>, kotlin.Unit>) event -> {
                    if (!event.isCanceled()) {
                        onWildSpawn(event.getEntity());
                    }
                    return kotlin.Unit.INSTANCE;
                });
    }

    /** Distancia horizontal a la que un jugador cuenta para el level cap de una aparición. */
    private static final double LEVEL_CAP_RANGE = 96;

    private static void onLevelCap(com.cobblemon.mod.common.api.events.entity.SpawnEvent<com.cobblemon.mod.common.entity.pokemon.PokemonEntity> event) {
        try {
            CaricraftServer mod = CaricraftServer.get();
            if (mod == null || event.isCanceled()) {
                return;
            }
            var config = mod.core().config().cobbleverse;
            if (!config.enabled || !config.levelCapSpawns || !com.chicoestudioso.caricraft.integration.rct.RctBridge.available()) {
                return;
            }
            com.cobblemon.mod.common.entity.pokemon.PokemonEntity entity = event.getEntity();
            Pokemon pokemon = entity.getPokemon();
            if (pokemon.getOwnerUUID() != null || !(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) {
                return;
            }
            int cap = lowestCapNear(level, entity.getX(), entity.getZ());
            boolean legendary = pokemon.isLegendary() || pokemon.isMythical() || pokemon.isUltraBeast();
            com.chicoestudioso.caricraft.core.cobbleverse.LevelCapRule.Decision decision = com.chicoestudioso.caricraft.core.cobbleverse
                    .LevelCapRule.decide(pokemon.getLevel(), legendary, cap, config.levelCapMargin, config.legendaryMinLevelCap);
            if (cap <= 0) {
                return;
            }
            if (!decision.spawn()) {
                event.cancel(); // Cobblemon prueba otra aparición en la siguiente pasada
                com.chicoestudioso.caricraft.server.cobbleverse.LevelCapStats.CANCELLED.incrementAndGet();
                if (legendary) {
                    com.chicoestudioso.caricraft.server.cobbleverse.LevelCapStats.LEGENDS_BLOCKED.incrementAndGet();
                }
            } else if (decision.level() != pokemon.getLevel()) {
                pokemon.setLevel(decision.level());
                com.chicoestudioso.caricraft.server.cobbleverse.LevelCapStats.CLAMPED.incrementAndGet();
            } else {
                com.chicoestudioso.caricraft.server.cobbleverse.LevelCapStats.KEPT.incrementAndGet();
            }
        } catch (RuntimeException e) {
            CaricraftMod.LOGGER.warn("No se pudo aplicar el level cap a una aparición", e);
        }
    }

    /** El level cap más bajo de los jugadores (no espectadores) cerca de ese punto, o 0 si no hay ninguno con cap. */
    static int lowestCapNear(net.minecraft.server.level.ServerLevel level, double x, double z) {
        int lowest = 0;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            if (dx * dx + dz * dz > LEVEL_CAP_RANGE * LEVEL_CAP_RANGE) {
                continue;
            }
            var cap = com.chicoestudioso.caricraft.integration.rct.RctBridge.levelCap(player);
            if (cap.isPresent() && (lowest == 0 || cap.getAsInt() < lowest)) {
                lowest = cap.getAsInt();
            }
        }
        return lowest;
    }

    private static void onWildSpawn(com.cobblemon.mod.common.entity.pokemon.PokemonEntity entity) {
        try {
            Pokemon pokemon = entity.getPokemon();
            if (!pokemon.getShiny() || pokemon.getOwnerUUID() != null || !(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) {
                return;
            }
            String species = pokemon.getSpecies().getResourceIdentifier().toString();
            int pokemonLevel = pokemon.getLevel();
            // Se mira en el siguiente tick, cuando la entidad ya está en el mundo (otro mod podría cancelar la aparición).
            level.getServer().execute(() -> {
                CaricraftServer mod = CaricraftServer.get();
                if (mod != null && entity.isAlive() && level.getEntity(entity.getUUID()) != null) {
                    com.chicoestudioso.caricraft.server.cobbleverse.ShinyAlerts.onShinySpawn(mod, level, entity, species, pokemonLevel);
                }
            });
        } catch (RuntimeException e) {
            CaricraftMod.LOGGER.warn("No se pudo procesar la aparición de un variocolor", e);
        }
    }

    /** La captura en términos del núcleo de Caricraft. */
    static com.chicoestudioso.caricraft.core.cobbleverse.CaptureInfo capture(Pokemon pokemon) {
        return new com.chicoestudioso.caricraft.core.cobbleverse.CaptureInfo(pokemon.getSpecies().getResourceIdentifier().toString(),
                CobblemonBackend.types(pokemon), pokemon.getLevel(), pokemon.getShiny(),
                pokemon.isLegendary() || pokemon.isMythical() || pokemon.isUltraBeast(), pokemon.getSpecies().getCatchRate());
    }

    /** Ejecuta en el hilo del servidor (los eventos de combate pueden llegar desde otro hilo). */
    private static void onServer(ServerPlayer player, Runnable action) {
        if (player == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        Runnable safe = () -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                CaricraftMod.LOGGER.error("Error procesando un evento de Cobblemon", e);
            }
        };
        if (server == null || server.isSameThread()) {
            safe.run();
        } else {
            server.execute(safe);
        }
    }

    static Set<String> keys(Pokemon pokemon) {
        Set<String> keys = new HashSet<>();
        keys.add(pokemon.getSpecies().getResourceIdentifier().toString());
        keys.add("type:" + pokemon.getPrimaryType().getName().toLowerCase(Locale.ROOT));
        if (pokemon.getShiny()) {
            keys.add("shiny");
        }
        if (pokemon.isLegendary()) {
            keys.add("legendary");
        }
        return keys;
    }

    private static void onCaptured(ServerPlayer player, Pokemon pokemon) {
        CaricraftServer mod = CaricraftServer.get();
        if (mod == null) {
            return;
        }
        mod.core().onPlayerJoin(player.getUUID(), player.getGameProfile().getName());
        Party party = mod.core().partyOf(player.getUUID());
        String species = pokemon.getSpecies().getResourceIdentifier().toString();
        boolean first = party.stat(ObjectiveType.CATCH_POKEMON.statKey()) == 0;

        ServerEvents.progress(player, ObjectiveType.CATCH_POKEMON, keys(pokemon), 1);

        if (first) {
            mod.core().recordChronicle(player.getUUID(), ChronicleEntry.FIRST_CATCH, species);
        }
        if (pokemon.getShiny()) {
            party.incrementStat(STAT_SHINY, 1);
            mod.core().recordChronicle(player.getUUID(), ChronicleEntry.SHINY_CATCH, species);
            mod.notifyParty(player.getUUID(), "caricraft.notify.shiny_catch", List.of(player.getGameProfile().getName(), species));
        }
        if (pokemon.isLegendary()) {
            mod.core().recordChronicle(player.getUUID(), ChronicleEntry.LEGENDARY_CATCH, species);
        }
        com.chicoestudioso.caricraft.server.cobbleverse.DuoPokemonManager.onCapture(mod, player, capture(pokemon));
        mod.markDirty();
    }

    private static void onEvolution(EvolutionCompleteEvent event) {
        Pokemon evolved = event.getPokemon();
        ServerPlayer owner = evolved.getOwnerPlayer();
        onServer(owner, () -> {
            CaricraftServer mod = CaricraftServer.get();
            if (mod == null) {
                return;
            }
            ServerEvents.progress(owner, ObjectiveType.EVOLVE_POKEMON, keys(evolved), 1);
            String from = event.getSourcePokemon().getSpecies().getResourceIdentifier().toString();
            String to = evolved.getSpecies().getResourceIdentifier().toString();
            mod.core().recordChronicle(owner.getUUID(), ChronicleEntry.EVOLUTION, from, to);
            mod.markDirty();
        });
    }

    private static void onVictory(BattleVictoryEvent event) {
        if (event.getWasWildCapture()) {
            return; // la captura ya cuenta por su lado
        }
        boolean wild = false;
        boolean npc = false;
        for (BattleActor loser : event.getLosers()) {
            ActorType type = loser.getType();
            if (type == ActorType.PLAYER) {
                return; // PvP no cuenta: evita farmear Carins dentro del dúo
            }
            wild |= type == ActorType.WILD;
            npc |= type == ActorType.NPC;
        }
        Set<String> keys = new HashSet<>();
        if (wild) {
            keys.add("wild");
        }
        if (npc) {
            keys.add("npc");
        }
        for (BattleActor winner : event.getWinners()) {
            if (winner.getType() != ActorType.PLAYER) {
                continue;
            }
            for (UUID uuid : winner.getPlayerUUIDs()) {
                CaricraftServer mod = CaricraftServer.get();
                ServerPlayer player = mod == null ? null : mod.server().getPlayerList().getPlayer(uuid);
                onServer(player, () -> ServerEvents.progress(player, ObjectiveType.WIN_BATTLE, keys, 1));
            }
        }
    }
}
