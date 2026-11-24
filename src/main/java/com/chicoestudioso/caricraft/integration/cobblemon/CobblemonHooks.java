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
 * Solo traduce eventos a progreso del núcleo; no modifica nada de Cobblemon.
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
