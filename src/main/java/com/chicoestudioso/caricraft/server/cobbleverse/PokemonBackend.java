package com.chicoestudioso.caricraft.server.cobbleverse;

import com.chicoestudioso.caricraft.core.cobbleverse.PokemonPricing;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que la tienda Cobbleverse necesita de Cobblemon, sin tipos de Cobblemon (la implementación está en
 * {@code integration.cobblemon} y solo se carga si el mod está instalado).
 */
public interface PokemonBackend {
    /** Un Pokémon del equipo. {@code species} es el id ({@code cobblemon:pikachu}); {@code nickname} puede ir vacío. */
    record PartyMon(int slot, UUID uuid, String species, String nickname, int level, boolean fainted,
                    PokemonPricing.PokemonInfo info) {
    }

    List<PartyMon> party(ServerPlayer player);

    boolean inBattle(ServerPlayer player);

    /** Quita el Pokémon del equipo. Devuelve un objeto con el que deshacerlo, o vacío si ya no está. */
    Optional<Object> take(ServerPlayer player, UUID pokemon);

    /** Devuelve al equipo (o al PC) un Pokémon quitado con {@link #take}. */
    void restore(ServerPlayer player, Object taken);

    /**
     * Crea un Pokémon y se lo da al jugador (al PC si el equipo está lleno).
     *
     * @param moves movimientos que se le enseñan aunque no pueda aprenderlos
     * @return id de la especie entregada
     */
    String give(ServerPlayer player, String species, int level, boolean shiny, int perfectIvs, List<String> moves);

    boolean speciesExists(String species);
}
