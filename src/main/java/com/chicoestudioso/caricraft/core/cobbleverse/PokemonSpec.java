package com.chicoestudioso.caricraft.core.cobbleverse;

import java.util.List;
import java.util.Random;

/**
 * Cómo es un Pokémon que se regala o se vende en la tienda Cobbleverse. Se lee del JSON del catálogo, así
 * que los campos pueden faltar: usar los métodos de acceso normalizados.
 *
 * @param species     especies posibles (se elige una al azar), con o sin {@code cobblemon:}
 * @param level       nivel (1–100)
 * @param shiny       variocolor seguro
 * @param shinyChance probabilidad de variocolor si no es seguro (0–1)
 * @param perfectIvs  estadísticas con IV 31 (0–6)
 * @param moves       movimientos que se le enseñan, aunque no pueda aprenderlos (ids de Showdown, p. ej. {@code extremespeed})
 */
public record PokemonSpec(List<String> species, Integer level, Boolean shiny, Double shinyChance, Integer perfectIvs,
                          List<String> moves) {

    public List<String> speciesList() {
        return species == null ? List.of() : species.stream().map(PokemonSpec::plainSpecies).toList();
    }

    public int levelOrDefault() {
        return level == null ? 10 : Math.max(1, Math.min(100, level));
    }

    public boolean alwaysShiny() {
        return Boolean.TRUE.equals(shiny);
    }

    public double shinyChanceOrDefault() {
        return alwaysShiny() ? 1.0 : shinyChance == null ? 0.0 : Math.max(0.0, Math.min(1.0, shinyChance));
    }

    public int perfectIvsOrDefault() {
        return perfectIvs == null ? 0 : Math.max(0, Math.min(6, perfectIvs));
    }

    public List<String> movesList() {
        return moves == null ? List.of() : moves.stream().limit(4).toList();
    }

    /** Especie elegida al azar. */
    public String pickSpecies(Random random) {
        List<String> list = speciesList();
        return list.get(random.nextInt(list.size()));
    }

    public boolean rollShiny(Random random) {
        return alwaysShiny() || random.nextDouble() < shinyChanceOrDefault();
    }

    static String plainSpecies(String id) {
        String s = id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
        return s.startsWith("cobblemon:") ? s.substring("cobblemon:".length()) : s;
    }
}
