package com.chicoestudioso.caricraft.core.cobbleverse;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Expediciones: un Pokémon del equipo se va unas horas reales a un destino y vuelve con algo. Es un ingreso
 * pasivo y modesto a propósito (unos ₽100–150 por hora más algún objeto común), con pocas por día; no compite
 * con jugar. Los Pokémon cuyo tipo encaja con el destino traen un poco más.
 */
public final class ExpeditionService {
    public static final List<Integer> HOURS = List.of(1, 4, 8);
    private static final String COUNT_KEY = "exp:count";

    public record Loot(String item, int min, int max, int weight) {
    }

    public record Destination(String id, String icon, List<String> types, int dollarsMin, int dollarsMax, List<Loot> loot) {
    }

    /** CobbleDollars y objetos (id → cantidad). */
    public record Reward(long dollars, Map<String, Integer> items) {
    }

    public static final List<Destination> DESTINATIONS = List.of(
            new Destination("forest", "minecraft:oak_sapling", List.of("grass", "bug", "normal", "fairy", "poison"), 80, 160, List.of(
                    new Loot("cobblemon:oran_berry", 2, 4, 30), new Loot("cobblemon:sitrus_berry", 1, 2, 12),
                    new Loot("cobblemon:poke_ball", 1, 3, 25), new Loot("cobblemon:great_ball", 1, 2, 10),
                    new Loot("cobblemon:exp_candy_xs", 1, 3, 18), new Loot("cobblemon:exp_candy_s", 1, 1, 6),
                    new Loot("cobblemon:leaf_stone", 1, 1, 3))),
            new Destination("cave", "minecraft:iron_pickaxe", List.of("rock", "ground", "steel", "dark", "ghost", "fighting", "fire"), 60, 140,
                    List.of(new Loot("minecraft:coal", 2, 6, 25), new Loot("minecraft:raw_iron", 1, 3, 20),
                            new Loot("minecraft:raw_gold", 1, 2, 10), new Loot("minecraft:amethyst_shard", 1, 3, 12),
                            new Loot("cobblemon:exp_candy_s", 1, 2, 12), new Loot("cobblemon:fire_stone", 1, 1, 3),
                            new Loot("cobblemon:thunder_stone", 1, 1, 3), new Loot("cobblemon:moon_stone", 1, 1, 2),
                            new Loot("minecraft:diamond", 1, 1, 1))),
            new Destination("coast", "minecraft:nautilus_shell", List.of("water", "ice", "flying", "electric", "psychic", "dragon"), 150, 300,
                    List.of(new Loot("cobblemon:great_ball", 1, 2, 15), new Loot("cobblemon:relic_coin", 1, 3, 18),
                            new Loot("minecraft:prismarine_shard", 1, 3, 15), new Loot("cobblemon:exp_candy_s", 1, 2, 12),
                            new Loot("cobblemon:water_stone", 1, 1, 3), new Loot("cobblemon:ice_stone", 1, 1, 2),
                            new Loot("cobblemon:ultra_ball", 1, 1, 4), new Loot("minecraft:nautilus_shell", 1, 1, 2))));

    private final CaricraftConfig config;

    public ExpeditionService(CaricraftConfig config) {
        this.config = config;
    }

    public boolean enabled() {
        return config.cobbleverse.enabled && config.cobbleverse.expeditions;
    }

    public int perDay() {
        return config.cobbleverse.expeditionsPerDay;
    }

    public static Destination destination(String id) {
        return DESTINATIONS.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
    }

    /** Tiradas de botín según la duración: 1 h → 1, 4 h → 3, 8 h → 5 (las largas rinden menos por hora). */
    public static int rolls(int hours) {
        return hours >= 8 ? 5 : hours >= 4 ? 3 : 1;
    }

    public static boolean typeMatch(Destination destination, List<String> types) {
        return types.stream().anyMatch(destination.types()::contains);
    }

    /** Multiplicador del dinero: hasta ×1,5 por nivel y ×1,25 si el tipo encaja con el destino. */
    public static double factor(int level, boolean typeMatch) {
        return (1 + Math.min(100, Math.max(1, level)) / 200.0) * (typeMatch ? 1.25 : 1.0);
    }

    public Reward roll(Destination destination, int hours, int level, boolean typeMatch, Random random) {
        double factor = factor(level, typeMatch) * config.cobbleverse.expeditionRewardMultiplier;
        long dollars = 0;
        Map<String, Integer> items = new LinkedHashMap<>();
        int totalWeight = destination.loot().stream().mapToInt(Loot::weight).sum();
        for (int i = 0; i < rolls(hours); i++) {
            dollars += destination.dollarsMin() + random.nextInt(destination.dollarsMax() - destination.dollarsMin() + 1);
            int pick = random.nextInt(totalWeight);
            for (Loot loot : destination.loot()) {
                pick -= loot.weight();
                if (pick < 0) {
                    items.merge(loot.item(), loot.min() + random.nextInt(loot.max() - loot.min() + 1), Integer::sum);
                    break;
                }
            }
        }
        return new Reward(Math.round(dollars * factor), items);
    }

    public int startedToday(PlayerProfile profile, long day) {
        return CobbleverseService.counter(profile, COUNT_KEY, day);
    }

    public ActionResult canStart(PlayerProfile profile, long day, boolean alreadyAway, String destinationId, int hours) {
        if (!enabled()) {
            return ActionResult.fail("cv_disabled");
        }
        if (destination(destinationId) == null || !HOURS.contains(hours)) {
            return ActionResult.fail("invalid_request");
        }
        if (alreadyAway) {
            return ActionResult.fail("cv_exp_busy");
        }
        if (startedToday(profile, day) >= perDay()) {
            return ActionResult.fail("cv_exp_limit", perDay());
        }
        return ActionResult.ok("ok");
    }

    public void recordStart(PlayerProfile profile, long day) {
        CobbleverseService.count(profile, COUNT_KEY, day);
    }
}
