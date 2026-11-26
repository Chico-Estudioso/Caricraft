package com.chicoestudioso.caricraft.core.routine;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.challenge.ChallengeService;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.party.Party;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Vitrina de medallas del dúo: logros por lo que habéis hecho juntos, con tres tramos (bronce, plata y oro) y
 * Carins al conseguir cada uno. Se miden con las estadísticas del grupo, así que cuentan también las de antes.
 */
public final class MedalService {
    /**
     * @param source   estadística del grupo, o una especial: {@code challenge_level}, {@code biomes}, {@code streak_best}
     * @param tiers    valores para bronce, plata y oro
     * @param requires {@code null}, {@code "challenge"} o {@code "cobblemon"}
     */
    public record Medal(String id, String icon, String source, long[] tiers, String requires) {
    }

    /** Un tramo recién conseguido. */
    public record Unlock(Medal medal, int tier, long reward) {
    }

    public static final List<Medal> MEDALS = List.of(
            new Medal("missions", "minecraft:writable_book", "missions_claimed", new long[]{10, 50, 200}, null),
            new Medal("streak", "minecraft:campfire", "streak_best", new long[]{7, 30, 100}, null),
            new Medal("weekly", "minecraft:firework_rocket", WeeklyEventService.STAT_COMPLETED, new long[]{1, 5, 20}, null),
            new Medal("challenge_nights", "minecraft:clock", "challenge_nights", new long[]{5, 25, 100}, "challenge"),
            new Medal("challenge_kills", "minecraft:iron_sword", "challenge_kills", new long[]{100, 1_000, 5_000}, "challenge"),
            new Medal("challenge_level", "minecraft:netherite_sword", "challenge_level", new long[]{5, 15, 30}, "challenge"),
            new Medal("bosses", "minecraft:wither_skeleton_skull", ChallengeService.STAT_BOSSES, new long[]{1, 3, 6}, "challenge"),
            new Medal("explorer", "minecraft:compass", "biomes", new long[]{10, 30, 60}, null),
            new Medal("traveler", "minecraft:leather_boots", "distance_m", new long[]{10_000, 100_000, 500_000}, null),
            new Medal("miner", "minecraft:iron_pickaxe", "blocks_mined", new long[]{1_000, 10_000, 50_000}, null),
            new Medal("hunter", "minecraft:bow", "mobs_defeated", new long[]{250, 2_500, 10_000}, null),
            new Medal("crafter", "minecraft:crafting_table", "items_crafted", new long[]{500, 5_000, 20_000}, null),
            new Medal("photographer", "minecraft:spyglass", "photos", new long[]{10, 50, 150}, null),
            new Medal("trainer", "cobblemon:poke_ball", "pokemon_caught", new long[]{50, 250, 1_000}, "cobblemon"),
            new Medal("shiny", "minecraft:nether_star", "shiny_caught", new long[]{1, 5, 15}, "cobblemon"),
            new Medal("battler", "cobblemon:great_ball", "battles_won", new long[]{50, 250, 1_000}, "cobblemon"));

    /** Carins por bronce, plata y oro. */
    public static final long[] REWARDS = {25, 75, 200};

    private final CaricraftConfig config;
    private final EconomyService economy;
    private final ChallengeService challenge;
    private Predicate<String> available = feature -> false;

    public MedalService(CaricraftConfig config, EconomyService economy, ChallengeService challenge) {
        this.config = config;
        this.economy = economy;
        this.challenge = challenge;
    }

    public void setAvailable(Predicate<String> available) {
        this.available = available == null ? feature -> false : available;
    }

    public boolean enabled() {
        return config.routine.medals;
    }

    public List<Medal> medals() {
        List<Medal> list = new ArrayList<>();
        for (Medal medal : MEDALS) {
            if (medal.requires() == null || ("challenge".equals(medal.requires()) ? config.challenge.enabled
                    : available.test(medal.requires()))) {
                list.add(medal);
            }
        }
        return list;
    }

    public long reward(int tier) {
        return Math.max(0, Math.round(REWARDS[Math.max(1, Math.min(3, tier)) - 1] * config.routine.medalRewardMultiplier));
    }

    public long value(Party party, Medal medal) {
        return switch (medal.source()) {
            case "challenge_level" -> challenge.level(party);
            case "biomes" -> party.discoveredBiomes().size();
            case "streak_best" -> party.routine().streakBest();
            default -> party.stats().getOrDefault(medal.source(), 0L);
        };
    }

    public int tier(Party party, Medal medal) {
        return party.routine().medals.getOrDefault(medal.id(), 0);
    }

    /** Comprueba todas las medallas y paga los tramos nuevos (uno o varios de golpe). */
    public List<Unlock> check(Party party) {
        if (!enabled()) {
            return List.of();
        }
        List<Unlock> unlocked = new ArrayList<>();
        RoutineState s = party.routine();
        for (Medal medal : medals()) {
            long value = value(party, medal);
            int have = s.medals.getOrDefault(medal.id(), 0);
            int reach = 0;
            for (int i = 0; i < medal.tiers().length; i++) {
                if (value >= medal.tiers()[i]) {
                    reach = i + 1;
                }
            }
            for (int tier = have + 1; tier <= reach; tier++) {
                long reward = reward(tier);
                if (reward > 0) {
                    economy.credit(party.wallet(), reward, "medal", medal.id() + ":" + tier,
                            "medal:" + party.id() + ":" + medal.id() + ":" + tier);
                }
                unlocked.add(new Unlock(medal, tier, reward));
            }
            if (reach > have) {
                s.medals.put(medal.id(), reach);
            }
        }
        return unlocked;
    }
}
