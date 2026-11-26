package com.chicoestudioso.caricraft.core.cobbleverse;

import java.util.List;

/**
 * Una captura de Cobblemon, sin tipos de Cobblemon.
 *
 * @param species   especie sin namespace (p. ej. {@code pikachu})
 * @param types     tipos en minúsculas ({@code fire}, {@code water}…)
 * @param legendary legendario, singular o ultraente
 * @param catchRate ratio de captura de la especie (3 rarísimo … 255 muy común)
 */
public record CaptureInfo(String species, List<String> types, int level, boolean shiny, boolean legendary, int catchRate) {
    public CaptureInfo {
        species = PokemonSpec.plainSpecies(species);
        types = types == null ? List.of() : types.stream().map(t -> t.toLowerCase(java.util.Locale.ROOT)).toList();
    }
}
