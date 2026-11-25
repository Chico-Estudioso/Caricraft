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

    /**
     * Premio posible del regalo misterioso o de una caja: un objeto, un Pokémon, Carins o CobbleDollars.
     *
     * @param rarity común, raro, épico o legendario (solo para mostrarlo en las cajas)
     * @param icon   ítem que se dibuja en la ruleta de la caja (por defecto, el del premio)
     */
    public record Gift(String id, String item, Range amount, PokemonSpec pokemon, Range carins, Range cobbledollars, int weight,
                       String rarity, String icon) {
        public static final List<String> RARITIES = List.of("common", "rare", "epic", "legendary");

        public String rarityOrDefault() {
            return rarity != null && RARITIES.contains(rarity) ? rarity : "common";
        }

        public String iconOrDefault() {
            if (icon != null) {
                return icon;
            }
            return switch (kind()) {
                case ITEM -> item;
                case POKEMON -> pokemon.alwaysShiny() ? "cobblemon:cherish_ball" : "cobblemon:poke_ball";
                case CARINS -> "minecraft:gold_ingot";
                case COBBLEDOLLARS -> "cobblemon:relic_coin";
            };
        }

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

    /** Caja: se paga en CobbleDollars y da uno de sus premios al azar según los pesos. */
    public record Crate(String id, long price, String icon, Integer dailyLimit, List<Gift> rewards) {
        public int dailyLimitOrDefault() {
            return dailyLimit == null ? 5 : Math.max(1, dailyLimit);
        }

        public int totalWeight() {
            return rewards.stream().mapToInt(Gift::weight).sum();
        }
    }

    /** Símbolo de la tragaperras: paga {@code pay3} × apuesta con tres iguales y {@code pay2} × apuesta con dos. */
    public record Symbol(String id, String icon, int weight, double pay3, double pay2) {
    }

    /** Tragaperras: apuestas posibles, tiradas por jugador y día y símbolos de los rodillos. */
    public record Slots(List<Long> bets, Integer spinsPerDay, List<Symbol> symbols) {
        public int spinsPerDayOrDefault() {
            return spinsPerDay == null ? 30 : Math.max(1, spinsPerDay);
        }

        public int totalWeight() {
            return symbols.stream().mapToInt(Symbol::weight).sum();
        }
    }

    /**
     * Mejora de un Pokémon del equipo.
     *
     * @param kind   {@code iv} (el IV más bajo a 31), {@code level} (+{@code amount} niveles) o {@code shiny}
     *               (variocolor con probabilidad {@code chance})
     * @param chance probabilidad de éxito; si falla, se pierde lo pagado
     */
    public record Upgrade(String id, String kind, long price, Integer amount, Double chance) {
        public static final List<String> KINDS = List.of("iv", "level", "shiny");

        public int amountOrDefault() {
            return amount == null ? 5 : Math.max(1, amount);
        }

        public double chanceOrOne() {
            return chance == null ? 1.0 : chance;
        }
    }

    public record Ingredient(String item, int count) {
    }

    /** Fusión de objetos: se gastan los ingredientes y los CobbleDollars, y con probabilidad {@code chance} sale el resultado. */
    public record Fusion(String id, List<Ingredient> inputs, long price, String output, Integer count, Double chance) {
        public int countOrOne() {
            return count == null ? 1 : Math.max(1, Math.min(64, count));
        }

        public double chanceOrOne() {
            return chance == null ? 1.0 : chance;
        }
    }

    private record FileFormat(Integer dailyOffers, List<Offer> offers, List<Gift> gift, List<Crate> crates, Slots slots,
                              List<Upgrade> upgrades, List<Fusion> fusions) {
    }

    private final int dailyOffers;
    private final List<Offer> offers;
    private final List<Gift> gifts;
    /** Las secciones nuevas son {@code null} si el archivo no las trae (entonces se completan con las del mod). */
    private final List<Crate> crates;
    private final Slots slots;
    private final List<Upgrade> upgrades;
    private final List<Fusion> fusions;

    private CobbleverseCatalog(int dailyOffers, List<Offer> offers, List<Gift> gifts, List<Crate> crates, Slots slots,
                               List<Upgrade> upgrades, List<Fusion> fusions) {
        this.dailyOffers = dailyOffers;
        this.offers = List.copyOf(offers);
        this.gifts = List.copyOf(gifts);
        this.crates = crates == null ? null : List.copyOf(crates);
        this.slots = slots;
        this.upgrades = upgrades == null ? null : List.copyOf(upgrades);
        this.fusions = fusions == null ? null : List.copyOf(fusions);
    }

    public static CobbleverseCatalog empty() {
        return new CobbleverseCatalog(0, List.of(), List.of(), List.of(), null, List.of(), List.of());
    }

    /** Completa las secciones que el archivo del servidor no trae (p. ej. uno anterior a las cajas) con las del mod. */
    public CobbleverseCatalog withMissingFrom(CobbleverseCatalog defaults) {
        return new CobbleverseCatalog(dailyOffers, offers, gifts, crates != null ? crates : defaults.crates,
                slots != null ? slots : defaults.slots, upgrades != null ? upgrades : defaults.upgrades,
                fusions != null ? fusions : defaults.fusions);
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
        List<Gift> gifts = gifts(file.gift(), "", itemExists, speciesExists, errors);
        List<Crate> crates = null;
        if (file.crates() != null) {
            crates = new ArrayList<>();
            Set<String> crateIds = new HashSet<>();
            for (Crate crate : file.crates()) {
                if (!validId(crate == null ? null : crate.id(), crateIds, errors)) {
                    continue;
                }
                if (crate.price() <= 0) {
                    errors.add(crate.id() + ": precio no válido");
                }
                if (crate.rewards() == null || crate.rewards().isEmpty()) {
                    errors.add(crate.id() + ": la caja necesita \"rewards\"");
                    continue;
                }
                List<Gift> rewards = gifts(crate.rewards(), crate.id() + ".", itemExists, speciesExists, errors);
                if (!rewards.isEmpty()) {
                    String icon = crate.icon() != null && itemExists.test(crate.icon()) ? crate.icon() : "minecraft:chest";
                    crates.add(new Crate(crate.id(), crate.price(), icon, crate.dailyLimit(), rewards));
                }
            }
        }
        Slots slots = file.slots() == null ? null : slots(file.slots(), errors);
        List<Upgrade> upgrades = null;
        if (file.upgrades() != null) {
            upgrades = new ArrayList<>();
            Set<String> upgradeIds = new HashSet<>();
            for (Upgrade upgrade : file.upgrades()) {
                if (!validId(upgrade == null ? null : upgrade.id(), upgradeIds, errors)) {
                    continue;
                }
                if (!Upgrade.KINDS.contains(upgrade.kind())) {
                    errors.add(upgrade.id() + ": tipo de mejora desconocido " + upgrade.kind());
                } else if (upgrade.price() <= 0 || !validChance(upgrade.chance())) {
                    errors.add(upgrade.id() + ": precio o probabilidad no válidos");
                } else {
                    upgrades.add(upgrade);
                }
            }
        }
        List<Fusion> fusions = null;
        if (file.fusions() != null) {
            fusions = new ArrayList<>();
            Set<String> fusionIds = new HashSet<>();
            for (Fusion fusion : file.fusions()) {
                if (!validId(fusion == null ? null : fusion.id(), fusionIds, errors)) {
                    continue;
                }
                if (fusion.inputs() == null || fusion.inputs().isEmpty() || fusion.output() == null || fusion.price() < 0
                        || !validChance(fusion.chance()) || fusion.inputs().stream().anyMatch(i -> i == null || i.item() == null
                        || i.count() <= 0 || i.count() > 64 * 36)) {
                    errors.add(fusion.id() + ": fusión no válida");
                } else if (itemExists.test(fusion.output()) && fusion.inputs().stream().allMatch(i -> itemExists.test(i.item()))) {
                    fusions.add(fusion);
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Catálogo de Cobbleverse no válido: " + String.join("; ", errors));
        }
        int daily = file.dailyOffers() == null ? 6 : Math.max(1, file.dailyOffers());
        return new CobbleverseCatalog(daily, offers, gifts, crates, slots, upgrades, fusions);
    }

    private static boolean validChance(Double chance) {
        return chance == null || (chance > 0 && chance <= 1);
    }

    private static List<Gift> gifts(List<Gift> source, String prefix, Predicate<String> itemExists, Predicate<String> speciesExists,
                                    List<String> errors) {
        Set<String> ids = new HashSet<>();
        List<Gift> gifts = new ArrayList<>();
        for (Gift gift : source) {
            if (!validId(gift == null ? null : gift.id(), ids, errors)) {
                continue;
            }
            if (gift.weight() <= 0) {
                errors.add(prefix + gift.id() + ": peso no válido");
            }
            String icon = gift.icon() != null && itemExists.test(gift.icon()) ? gift.icon() : null;
            switch (gift.kind()) {
                case ITEM -> {
                    if (gift.item() == null) {
                        errors.add(prefix + gift.id() + ": falta \"item\"");
                    } else if (itemExists.test(gift.item())) {
                        gifts.add(new Gift(gift.id(), gift.item(), gift.amount(), null, null, null, gift.weight(), gift.rarity(), icon));
                    }
                }
                case POKEMON -> {
                    PokemonSpec spec = filterSpecies(gift.pokemon(), speciesExists);
                    if (spec != null) {
                        gifts.add(new Gift(gift.id(), null, null, spec, null, null, gift.weight(), gift.rarity(), icon));
                    }
                }
                default -> gifts.add(new Gift(gift.id(), null, null, null, gift.carins(), gift.cobbledollars(), gift.weight(),
                        gift.rarity(), icon));
            }
        }
        return gifts;
    }

    private static Slots slots(Slots slots, List<String> errors) {
        if (slots.bets() == null || slots.bets().isEmpty() || slots.bets().stream().anyMatch(b -> b == null || b <= 0)) {
            errors.add("slots: apuestas no válidas");
            return null;
        }
        if (slots.symbols() == null || slots.symbols().size() < 3) {
            errors.add("slots: hacen falta al menos 3 símbolos");
            return null;
        }
        Set<String> ids = new HashSet<>();
        for (Symbol symbol : slots.symbols()) {
            if (!validId(symbol == null ? null : symbol.id(), ids, errors)) {
                return null;
            }
            if (symbol.weight() <= 0 || symbol.pay3() < 0 || symbol.pay2() < 0) {
                errors.add("slots." + symbol.id() + ": peso o pagos no válidos");
            }
        }
        return new Slots(List.copyOf(slots.bets().subList(0, Math.min(5, slots.bets().size()))), slots.spinsPerDay(),
                slots.symbols());
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

    public List<Crate> crates() {
        return crates == null ? List.of() : crates;
    }

    public Crate crate(String id) {
        return crates().stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }

    /** Tragaperras, o {@code null} si el catálogo no la trae. */
    public Slots slots() {
        return slots;
    }

    public List<Upgrade> upgrades() {
        return upgrades == null ? List.of() : upgrades;
    }

    public Upgrade upgrade(String id) {
        return upgrades().stream().filter(u -> u.id().equals(id)).findFirst().orElse(null);
    }

    public List<Fusion> fusions() {
        return fusions == null ? List.of() : fusions;
    }

    public Fusion fusion(String id) {
        return fusions().stream().filter(f -> f.id().equals(id)).findFirst().orElse(null);
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
