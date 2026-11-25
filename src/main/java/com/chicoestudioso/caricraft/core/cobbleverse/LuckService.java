package com.chicoestudioso.caricraft.core.cobbleverse;

import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Crate;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Fusion;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Gift;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Ingredient;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Slots;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Symbol;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Upgrade;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;

import java.util.Map;
import java.util.Random;

/**
 * Reglas de la parte «de suerte» del CobbleCentro: cajas, tragaperras, mejoras de Pokémon y fusiones de objetos.
 * Todo se paga en CobbleDollars; aquí solo se decide (límites, premios, pagos). El servidor cobra y entrega.
 */
public final class LuckService {
    private static final String SPINS = "luck:spins";

    private final CobbleverseService cobbleverse;

    public LuckService(CobbleverseService cobbleverse) {
        this.cobbleverse = cobbleverse;
    }

    private CobbleverseCatalog catalog() {
        return cobbleverse.catalog();
    }

    private com.chicoestudioso.caricraft.config.CaricraftConfig.CobbleverseConfig config() {
        return cobbleverse.config().cobbleverse;
    }

    public boolean cratesEnabled() {
        return config().crates && !catalog().crates().isEmpty();
    }

    public boolean slotsEnabled() {
        return config().slots && catalog().slots() != null;
    }

    public boolean upgradesEnabled() {
        return config().upgrades && !catalog().upgrades().isEmpty();
    }

    public boolean fusionsEnabled() {
        return config().fusions && !catalog().fusions().isEmpty();
    }

    // ------------------------------------------------------------------ cajas

    private static String crateKey(String crateId) {
        return "crate:" + crateId;
    }

    public int cratesOpened(PlayerProfile profile, String crateId, long day) {
        return CobbleverseService.counter(profile, crateKey(crateId), day);
    }

    public ActionResult canOpenCrate(PlayerProfile profile, String crateId, long day, long balance) {
        Crate crate = cratesEnabled() ? catalog().crate(crateId) : null;
        if (crate == null) {
            return ActionResult.fail("cv_offer_gone");
        }
        if (cratesOpened(profile, crateId, day) >= crate.dailyLimitOrDefault()) {
            return ActionResult.fail("cv_crate_limit", crate.dailyLimitOrDefault());
        }
        if (balance < crate.price()) {
            return ActionResult.fail("cv_insufficient", crate.price() - balance);
        }
        return ActionResult.ok("ok");
    }

    /** Índice del premio elegido al azar según los pesos. */
    public static int pickReward(Crate crate, Random random) {
        int roll = random.nextInt(crate.totalWeight());
        for (int i = 0; i < crate.rewards().size(); i++) {
            roll -= crate.rewards().get(i).weight();
            if (roll < 0) {
                return i;
            }
        }
        return crate.rewards().size() - 1;
    }

    /** Probabilidad (0–1) de un premio de la caja. */
    public static double chance(Crate crate, Gift reward) {
        return reward.weight() / (double) crate.totalWeight();
    }

    public void recordCrate(PlayerProfile profile, String crateId, long day) {
        CobbleverseService.count(profile, crateKey(crateId), day);
    }

    // ------------------------------------------------------------------ tragaperras

    public int spinsToday(PlayerProfile profile, long day) {
        return CobbleverseService.counter(profile, SPINS, day);
    }

    public int spinsLeft(PlayerProfile profile, long day) {
        Slots slots = catalog().slots();
        return slots == null ? 0 : Math.max(0, slots.spinsPerDayOrDefault() - spinsToday(profile, day));
    }

    /** Apuesta por su posición en la lista, o -1 si no existe. */
    public long bet(int index) {
        Slots slots = catalog().slots();
        return slots == null || index < 0 || index >= slots.bets().size() ? -1 : slots.bets().get(index);
    }

    public ActionResult canSpin(PlayerProfile profile, int betIndex, long day, long balance) {
        long bet = slotsEnabled() ? bet(betIndex) : -1;
        if (bet <= 0) {
            return ActionResult.fail("cv_offer_gone");
        }
        if (spinsLeft(profile, day) <= 0) {
            return ActionResult.fail("cv_spins_done");
        }
        if (balance < bet) {
            return ActionResult.fail("cv_insufficient", bet - balance);
        }
        return ActionResult.ok("ok");
    }

    /** Tres rodillos independientes; cada uno es el índice de un símbolo. */
    public static int[] spin(Slots slots, Random random) {
        int[] reels = new int[3];
        for (int r = 0; r < 3; r++) {
            int roll = random.nextInt(slots.totalWeight());
            for (int i = 0; i < slots.symbols().size(); i++) {
                roll -= slots.symbols().get(i).weight();
                if (roll < 0) {
                    reels[r] = i;
                    break;
                }
            }
        }
        return reels;
    }

    /** Multiplicador de la apuesta: tres iguales → {@code pay3}; dos iguales → {@code pay2} de ese símbolo; si no, 0. */
    public static double multiplier(Slots slots, int[] reels) {
        if (reels[0] == reels[1] && reels[1] == reels[2]) {
            return slots.symbols().get(reels[0]).pay3();
        }
        if (reels[0] == reels[1] || reels[0] == reels[2]) {
            return slots.symbols().get(reels[0]).pay2();
        }
        if (reels[1] == reels[2]) {
            return slots.symbols().get(reels[1]).pay2();
        }
        return 0;
    }

    public static long payout(Slots slots, int[] reels, long bet) {
        return Math.round(bet * multiplier(slots, reels));
    }

    /** Lo que devuelve la máquina a la larga por cada CobbleDollar apostado (exacto, no simulado). */
    public static double returnToPlayer(Slots slots) {
        double total = slots.totalWeight();
        double rtp = 0;
        for (Symbol symbol : slots.symbols()) {
            double p = symbol.weight() / total;
            rtp += p * p * p * symbol.pay3() + 3 * p * p * (1 - p) * symbol.pay2();
        }
        return rtp;
    }

    public void recordSpin(PlayerProfile profile, long day) {
        CobbleverseService.count(profile, SPINS, day);
    }

    // ------------------------------------------------------------------ mejoras de Pokémon

    /** Estado de un Pokémon para decidir una mejora. IV en el orden PS, Ataque, Defensa, At. esp., Def. esp., Velocidad. */
    public record MonState(int level, boolean shiny, int[] ivs, boolean legendary) {
    }

    /** Clave del motivo por el que no se puede aplicar la mejora, o {@code null}. */
    public static String upgradeBlocked(Upgrade upgrade, MonState mon, boolean inBattle) {
        if (inBattle) {
            return "in_battle";
        }
        return switch (upgrade.kind()) {
            case "iv" -> lowestIv(mon.ivs()) < 0 ? "maxed" : null;
            case "level" -> mon.level() >= 100 ? "maxed" : null;
            case "shiny" -> mon.shiny() ? "maxed" : null;
            default -> "unknown";
        };
    }

    /** Estadística con el IV más bajo (la primera si empatan), o -1 si todas están a 31. */
    public static int lowestIv(int[] ivs) {
        int best = -1;
        for (int i = 0; i < ivs.length; i++) {
            if (ivs[i] < 31 && (best < 0 || ivs[i] < ivs[best])) {
                best = i;
            }
        }
        return best;
    }

    public static int levelAfter(Upgrade upgrade, int level) {
        return Math.min(100, level + upgrade.amountOrDefault());
    }

    public ActionResult canUpgrade(Upgrade upgrade, long balance) {
        if (!upgradesEnabled() || upgrade == null) {
            return ActionResult.fail("cv_offer_gone");
        }
        if (balance < upgrade.price()) {
            return ActionResult.fail("cv_insufficient", upgrade.price() - balance);
        }
        return ActionResult.ok("ok");
    }

    public static boolean roll(double chance, Random random) {
        return chance >= 1.0 || random.nextDouble() < chance;
    }

    // ------------------------------------------------------------------ fusiones

    /**
     * @param inventory cuántos tiene el jugador de cada objeto (id → cantidad)
     */
    public ActionResult canFuse(Fusion fusion, Map<String, Integer> inventory, long balance) {
        if (!fusionsEnabled() || fusion == null) {
            return ActionResult.fail("cv_offer_gone");
        }
        for (Ingredient ingredient : fusion.inputs()) {
            int have = inventory.getOrDefault(ingredient.item(), 0);
            if (have < ingredient.count()) {
                return ActionResult.fail("cv_fuse_missing", ingredient.count() - have, ingredient.item());
            }
        }
        if (balance < fusion.price()) {
            return ActionResult.fail("cv_insufficient", fusion.price() - balance);
        }
        return ActionResult.ok("ok");
    }
}
