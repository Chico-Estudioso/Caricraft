package com.chicoestudioso.caricraft.core.cobbleverse;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Catálogo de Cobbleverse: ofertas de la tienda (en CobbleDollars) y premios del regalo misterioso.
 * Se lee de {@code config/caricraft_cobbleverse.json} o, si no existe, del archivo por defecto del mod.
 */
public final class CobbleverseCatalog {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");
    private static final Gson GSON = new Gson();

    /** Rango de cantidades. */
    public record Range(Integer min, Integer max) {
        public int minOrOne() {
            return min == null ? 1 : Math.max(1, min);
        }

        public int maxOrMin() {
            return max == null ? minOrOne() : Math.max(minOrOne(), max);
        }

        public int roll(Random random) {
            return minOrOne() + random.nextInt(maxOrMin() - minOrOne() + 1);
        }
    }

    /**
     * Oferta de la tienda: un objeto ({@code item} × {@code count}) o un Pokémon ({@code pokemon}).
     *
     * @param dailyLimit compras por jugador y día
     * @param icon       ítem que se dibuja en el teléfono
     */
    public record Offer(String id, String item, Integer count, PokemonSpec pokemon, long price, Integer dailyLimit, String icon) {
        public boolean isPokemon() {
            return pokemon != null;
        }

        public int countOrOne() {
            return count == null ? 1 : Math.max(1, Math.min(64, count));
        }

        public int dailyLimitOrDefault() {
            return dailyLimit == null ? (isPokemon() ? 1 : 3) : Math.max(1, dailyLimit);
        }

        public String iconOrDefault() {
            return icon != null ? icon : isPokemon() ? "cobblemon:poke_ball" : item;
        }
    }

    /** Premio posible del regalo misterioso: un objeto, un Pokémon, Carins o CobbleDollars. */
    public record Gift(String id, String item, Range amount, PokemonSpec pokemon, Range carins, Range cobbledollars, int weight) {
        public Kind kind() {
            if (pokemon != null) {
                return Kind.POKEMON;
            }
            if (carins != null) {
                return Kind.CARINS;
            }
            if (cobbledollars != null) {
                return Kind.COBBLEDOLLARS;
            }
            return Kind.ITEM;
        }

        public enum Kind { ITEM, POKEMON, CARINS, COBBLEDOLLARS }
    }

    private record FileFormat(Integer dailyOffers, List<Offer> offers, List<Gift> gift) {
    }

    private final int dailyOffers;
    private final List<Offer> offers;
    private final List<Gift> gifts;

    private CobbleverseCatalog(int dailyOffers, List<Offer> offers, List<Gift> gifts) {
        this.dailyOffers = dailyOffers;
        this.offers = List.copyOf(offers);
        this.gifts = List.copyOf(gifts);
    }

    public static CobbleverseCatalog empty() {
        return new CobbleverseCatalog(0, List.of(), List.of());
    }

    /**
     * @param itemExists    descarta ofertas y premios de objetos que no existen en este servidor
     * @param speciesExists descarta especies que no existen (si ninguna existe, se descarta la oferta)
     */
    public static CobbleverseCatalog fromJson(Reader reader, Predicate<String> itemExists, Predicate<String> speciesExists) {
        FileFormat file;
        try {
            file = GSON.fromJson(reader, FileFormat.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("JSON de Cobbleverse no válido: " + e.getMessage(), e);
        }
        if (file == null || file.offers() == null || file.gift() == null) {
            throw new IllegalArgumentException("El JSON de Cobbleverse necesita \"offers\" y \"gift\"");
        }
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        List<Offer> offers = new ArrayList<>();
        for (Offer offer : file.offers()) {
            if (!validId(offer == null ? null : offer.id(), ids, errors)) {
                continue;
            }
            if (offer.price() <= 0) {
                errors.add(offer.id() + ": precio no válido");
            }
            if ((offer.item() == null) == (offer.pokemon() == null)) {
                errors.add(offer.id() + ": debe tener \"item\" o \"pokemon\"");
                continue;
            }
            Offer filtered = offer.isPokemon() ? withSpecies(offer, speciesExists) : offer;
            if (filtered != null && (filtered.isPokemon() || itemExists.test(filtered.item()))
                    && (filtered.icon() == null || itemExists.test(filtered.icon()))) {
                offers.add(filtered);
            }
        }
        Set<String> giftIds = new HashSet<>();
        List<Gift> gifts = new ArrayList<>();
        for (Gift gift : file.gift()) {
            if (!validId(gift == null ? null : gift.id(), giftIds, errors)) {
                continue;
            }
            if (gift.weight() <= 0) {
                errors.add(gift.id() + ": peso no válido");
            }
            switch (gift.kind()) {
                case ITEM -> {
                    if (gift.item() == null) {
                        errors.add(gift.id() + ": falta \"item\"");
                    } else if (itemExists.test(gift.item())) {
                        gifts.add(gift);
                    }
                }
                case POKEMON -> {
                    PokemonSpec spec = filterSpecies(gift.pokemon(), speciesExists);
                    if (spec != null) {
                        gifts.add(new Gift(gift.id(), null, null, spec, null, null, gift.weight()));
                    }
                }
                default -> gifts.add(gift);
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Catálogo de Cobbleverse no válido: " + String.join("; ", errors));
        }
        int daily = file.dailyOffers() == null ? 6 : Math.max(1, file.dailyOffers());
        return new CobbleverseCatalog(daily, offers, gifts);
    }

    private static boolean validId(String id, Set<String> seen, List<String> errors) {
        if (id == null || !ID.matcher(id).matches()) {
            errors.add("id no válido: " + id);
            return false;
        }
        if (!seen.add(id)) {
            errors.add("id duplicado: " + id);
            return false;
        }
        return true;
    }

    private static Offer withSpecies(Offer offer, Predicate<String> speciesExists) {
        PokemonSpec spec = filterSpecies(offer.pokemon(), speciesExists);
        return spec == null ? null : new Offer(offer.id(), null, null, spec, offer.price(), offer.dailyLimit(), offer.icon());
    }

    private static PokemonSpec filterSpecies(PokemonSpec spec, Predicate<String> speciesExists) {
        List<String> existing = spec.speciesList().stream().filter(speciesExists).toList();
        if (existing.isEmpty()) {
            return null;
        }
        return new PokemonSpec(existing, spec.level(), spec.shiny(), spec.shinyChance(), spec.perfectIvs(), spec.moves());
    }

    public int dailyOffers() {
        return Math.min(dailyOffers, offers.size());
    }

    public List<Offer> offers() {
        return offers;
    }

    public List<Gift> gifts() {
        return gifts;
    }

    public Offer offer(String id) {
        return offers.stream().filter(o -> o.id().equals(id)).findFirst().orElse(null);
    }

    /** Para mostrar todas las especies del catálogo en los tests. */
    Map<String, Integer> weights() {
        Map<String, Integer> weights = new java.util.LinkedHashMap<>();
        gifts.forEach(g -> weights.put(g.id(), g.weight()));
        return weights;
    }
}
