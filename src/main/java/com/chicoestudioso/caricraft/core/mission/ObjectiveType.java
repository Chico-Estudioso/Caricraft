package com.chicoestudioso.caricraft.core.mission;

/** Tipos de objetivo. Cada uno tiene una estadística asociada del grupo. */
public enum ObjectiveType {
    /** Metros recorridos (muestreo de posición). */
    TRAVEL("distance_m"),
    /** Entrar en un bioma (cada cambio de bioma). Objetivo: id de bioma o vacío. */
    VISIT_BIOME(null),
    /** Bioma nuevo para el grupo. Objetivo: id de bioma o vacío. */
    DISCOVER_BIOME("biomes_discovered"),
    /** Romper bloques. Objetivo: id de bloque o {@code #tag}. */
    MINE_BLOCK("blocks_mined"),
    /** Fabricar ítems. Objetivo: id de ítem o {@code #tag}. */
    CRAFT_ITEM("items_crafted"),
    /** Matar entidades. Objetivo: id de entidad o {@code #tag}. */
    KILL_ENTITY("mobs_defeated"),
    /** Entregar ítems desde el teléfono. Objetivo: id de ítem o {@code #tag}. */
    DELIVER_ITEM("items_delivered"),
    /** Cobblemon: capturar. Objetivo: especie ({@code cobblemon:pikachu}), {@code shiny}, {@code legendary}, {@code type:fire} o vacío. */
    CATCH_POKEMON("pokemon_caught"),
    /** Cobblemon: evolucionar. Objetivo: especie resultante o vacío. */
    EVOLVE_POKEMON("pokemon_evolved"),
    /** Cobblemon: ganar combates. Objetivo: {@code wild}, {@code npc} o vacío. */
    WIN_BATTLE("battles_won");

    private final String statKey;

    ObjectiveType(String statKey) {
        this.statKey = statKey;
    }

    /** Estadística que incrementa este tipo de evento, o {@code null}. */
    public String statKey() {
        return statKey;
    }

    public boolean requiresCobblemon() {
        return this == CATCH_POKEMON || this == EVOLVE_POKEMON || this == WIN_BATTLE;
    }
}
