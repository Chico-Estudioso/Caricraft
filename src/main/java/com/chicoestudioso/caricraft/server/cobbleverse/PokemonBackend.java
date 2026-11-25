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
                    PokemonPricing.PokemonInfo info, List<String> aspects) {
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

    /** Crea un Pokémon sin dárselo a nadie (se entrega con {@link #restore}). */
    Object create(String species, int level, boolean shiny, int perfectIvs, List<String> moves);

    /** Datos de un Pokémon quitado, creado o cargado. */
    PartyMon describe(Object pokemon);

    /** Guarda un Pokémon con el formato NBT de Cobblemon. */
    net.minecraft.nbt.CompoundTag save(Object pokemon, net.minecraft.core.RegistryAccess registries);

    /** Carga un Pokémon guardado con {@link #save}. */
    Object load(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.RegistryAccess registries);

    /** Especie al azar entre las implementadas que no son legendarias, singulares, ultraentes ni paradoja. */
    String randomSpecies(java.util.Random random);

    /** IV de un Pokémon del equipo en el orden PS, Ataque, Defensa, At. esp., Def. esp., Velocidad; vacío si ya no está. */
    Optional<int[]> ivs(ServerPlayer player, UUID uuid);

    /**
     * Mejora un Pokémon del equipo: {@code iv} pone a 31 la estadística {@code value} (índice como en {@link #ivs}),
     * {@code level} lo sube al nivel {@code value} y {@code shiny} lo hace variocolor.
     *
     * @return {@code false} si el Pokémon ya no está en el equipo
     */
    boolean improve(ServerPlayer player, UUID uuid, String kind, int value);
}
