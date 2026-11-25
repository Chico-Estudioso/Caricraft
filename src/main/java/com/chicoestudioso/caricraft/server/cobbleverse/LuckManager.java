package com.chicoestudioso.caricraft.server.cobbleverse;

import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Crate;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Fusion;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Gift;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Ingredient;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Slots;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Upgrade;
import com.chicoestudioso.caricraft.core.cobbleverse.LuckService;
import com.chicoestudioso.caricraft.core.cobbleverse.PokemonSpec;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;
import com.chicoestudioso.caricraft.integration.cobbledollars.CobbleDollarsBridge;
import com.chicoestudioso.caricraft.integration.cobblemon.CobblemonCompat;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import com.chicoestudioso.caricraft.server.PhoneActionHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;
import java.util.UUID;

/**
 * Cajas, tragaperras, mejoras de Pokémon y fusiones de objetos del CobbleCentro. Se cobra antes en CobbleDollars;
 * si la entrega falla, se devuelve el dinero. El teléfono solo pide y anima el resultado que manda el servidor.
 */
public final class LuckManager {
    private static final Random RANDOM = new Random();
    /** Último resultado de cada jugador (solo para la animación). */
    private static final Map<UUID, PhoneSnapshot.LuckResultView> LAST = new HashMap<>();
    /** Multiplicador desde el que una tirada cuenta como premio gordo. */
    private static final double JACKPOT = 100;

    private LuckManager() {
    }

    private static LuckService luck(CaricraftServer mod) {
        return mod.core().luck();
    }

    private static PlayerProfile profile(CaricraftServer mod, ServerPlayer player) {
        return mod.core().onPlayerJoin(player.getUUID(), player.getGameProfile().getName());
    }

    private static void stat(CaricraftServer mod, ServerPlayer player, String key) {
        Party party = mod.core().partyOf(player.getUUID());
        if (party != null) {
            party.incrementStat(key, 1);
        }
    }

    public static void forget(UUID player) {
        LAST.remove(player);
    }

    // ------------------------------------------------------------------ teléfono

    static PhoneSnapshot.LuckView view(CaricraftServer mod, ServerPlayer player, PlayerProfile profile, long day) {
        LuckService luck = luck(mod);
        CobbleverseCatalog catalog = mod.core().cobbleverse().catalog();
        List<PhoneSnapshot.CrateView> crates = new ArrayList<>();
        if (luck.cratesEnabled()) {
            for (Crate crate : catalog.crates()) {
                List<PhoneSnapshot.RewardView> rewards = new ArrayList<>();
                for (Gift reward : crate.rewards()) {
                    rewards.add(reward(crate, reward));
                }
                crates.add(new PhoneSnapshot.CrateView(crate.id(), crate.price(), crate.icon(), luck.cratesOpened(profile, crate.id(), day),
                        crate.dailyLimitOrDefault(), rewards));
            }
        }
        PhoneSnapshot.SlotsView slots = null;
        if (luck.slotsEnabled()) {
            Slots machine = catalog.slots();
            slots = new PhoneSnapshot.SlotsView(machine.bets(), luck.spinsLeft(profile, day), machine.spinsPerDayOrDefault(),
                    machine.symbols().stream().map(s -> new PhoneSnapshot.SymbolView(s.id(), s.icon(), s.pay3(), s.pay2())).toList(),
                    LuckService.returnToPlayer(machine));
        }
        List<PhoneSnapshot.UpgradeView> upgrades = !luck.upgradesEnabled() ? List.of() : catalog.upgrades().stream()
                .map(u -> new PhoneSnapshot.UpgradeView(u.id(), u.kind(), u.price(), u.amountOrDefault(), u.chanceOrOne())).toList();
        List<PhoneSnapshot.FusionView> fusions = !luck.fusionsEnabled() ? List.of() : catalog.fusions().stream()
                .map(f -> new PhoneSnapshot.FusionView(f.id(), f.inputs().stream()
                        .map(i -> new PhoneSnapshot.IngredientView(i.item(), i.count())).toList(),
                        f.price(), f.output(), f.countOrOne(), f.chanceOrOne())).toList();
        if (crates.isEmpty() && slots == null && upgrades.isEmpty() && fusions.isEmpty()) {
            return null;
        }
        return new PhoneSnapshot.LuckView(crates, slots, upgrades, fusions, LAST.get(player.getUUID()));
    }

    private static PhoneSnapshot.RewardView reward(Crate crate, Gift reward) {
        PokemonSpec spec = reward.pokemon();
        int min = 0;
        int max = 0;
        switch (reward.kind()) {
            case ITEM -> {
                min = reward.amount() == null ? 1 : reward.amount().minOrOne();
                max = reward.amount() == null ? 1 : reward.amount().maxOrMin();
            }
            case CARINS -> {
                min = reward.carins().minOrOne();
                max = reward.carins().maxOrMin();
            }
            case COBBLEDOLLARS -> {
                min = reward.cobbledollars().minOrOne();
                max = reward.cobbledollars().maxOrMin();
            }
            case POKEMON -> {
                min = 1;
                max = 1;
            }
        }
        return new PhoneSnapshot.RewardView(reward.id(), reward.kind().name(), reward.iconOrDefault(), reward.item(), min, max,
                spec == null ? List.of() : spec.speciesList(), spec == null ? 0 : spec.levelOrDefault(),
                spec != null && spec.alwaysShiny(), spec == null ? 0 : spec.perfectIvsOrDefault(), reward.rarityOrDefault(),
                LuckService.chance(crate, reward));
    }

    private static void remember(ServerPlayer player, String kind, String id, int reward, String species, boolean shiny, int amount,
                                 List<Integer> reels, long payout, boolean success) {
        List<String> aspects = shiny ? List.of("shiny") : List.of();
        LAST.put(player.getUUID(), new PhoneSnapshot.LuckResultView(kind, id, System.currentTimeMillis(), reward, species, aspects,
                shiny, amount, reels, payout, success));
    }

    /** Comprueba que la parte está activa y devuelve el saldo, o un fallo. */
    private static ActionResult precheck(CaricraftServer mod, ServerPlayer player, boolean enabled, OptionalLong[] balance) {
        if (!CobbleverseManager.active(mod) || !enabled) {
            return ActionResult.fail("cv_disabled");
        }
        balance[0] = CobbleDollarsBridge.balance(player);
        if (balance[0].isEmpty()) {
            return ActionResult.fail("cv_no_cobbledollars");
        }
        return null;
    }

    // ------------------------------------------------------------------ cajas

    public static ActionResult openCrate(CaricraftServer mod, ServerPlayer player, String crateId) {
        LuckService luck = luck(mod);
        OptionalLong[] balance = new OptionalLong[1];
        ActionResult pre = precheck(mod, player, luck.cratesEnabled(), balance);
        if (pre != null) {
            return pre;
        }
        PlayerProfile profile = profile(mod, player);
        long day = mod.core().cobbleverse().day();
        ActionResult check = luck.canOpenCrate(profile, crateId, day, balance[0].getAsLong());
        if (!check.success()) {
            return check;
        }
        Crate crate = mod.core().cobbleverse().catalog().crate(crateId);
        if (!CobbleDollarsBridge.remove(player, crate.price())) {
            return ActionResult.fail("cv_insufficient", crate.price());
        }
        int index = LuckService.pickReward(crate, RANDOM);
        Gift reward = crate.rewards().get(index);
        ActionResult result;
        String species = null;
        boolean shiny = false;
        int amount = 1;
        try {
            switch (reward.kind()) {
                case ITEM -> {
                    amount = reward.amount() == null ? 1 : reward.amount().roll(RANDOM);
                    PhoneActionHandler.giveItemTo(player, reward.item(), amount);
                    result = ActionResult.ok("cv_crate_item", amount, reward.item());
                }
                case POKEMON -> {
                    PokemonSpec spec = reward.pokemon();
                    shiny = spec.rollShiny(RANDOM);
                    species = CobblemonCompat.backend().give(player, spec.pickSpecies(RANDOM), spec.levelOrDefault(), shiny,
                            spec.perfectIvsOrDefault(), spec.movesList());
                    amount = spec.levelOrDefault();
                    result = ActionResult.ok(shiny ? "cv_crate_pokemon_shiny" : "cv_crate_pokemon", species, amount);
                }
                case COBBLEDOLLARS -> {
                    amount = reward.cobbledollars().roll(RANDOM);
                    if (!CobbleDollarsBridge.add(player, amount)) {
                        throw new IllegalStateException("CobbleDollars no aceptó el premio");
                    }
                    result = ActionResult.ok("cv_crate_dollars", amount);
                }
                case CARINS -> {
                    amount = reward.carins().roll(RANDOM);
                    Party party = mod.core().partyOf(player.getUUID());
                    if (party == null || mod.core().economy().credit(party.wallet(), amount, "cobble_crate", crateId, null)
                            != EconomyService.Result.OK) {
                        throw new IllegalStateException("No se pudieron abonar los Carins");
                    }
                    result = ActionResult.ok("cv_crate_carins", amount);
                }
                default -> throw new IllegalStateException();
            }
        } catch (RuntimeException e) {
            CobbleDollarsBridge.add(player, crate.price());
            CaricraftMod.LOGGER.error("No se pudo entregar el premio {} de la caja {} a {}; se devuelve el dinero.", reward.id(), crateId,
                    player.getGameProfile().getName(), e);
            return ActionResult.fail("internal_error");
        }
        luck.recordCrate(profile, crateId, day);
        stat(mod, player, "crates_opened");
        remember(player, "crate", crateId, index, species, shiny, amount, List.of(), 0, true);
        player.playNotifySound(SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.8f, 1.2f);
        mod.markDirty();
        return result;
    }

    // ------------------------------------------------------------------ tragaperras

    public static ActionResult spin(CaricraftServer mod, ServerPlayer player, String betArg) {
        LuckService luck = luck(mod);
        OptionalLong[] balance = new OptionalLong[1];
        ActionResult pre = precheck(mod, player, luck.slotsEnabled(), balance);
        if (pre != null) {
            return pre;
        }
        int betIndex;
        try {
            betIndex = Integer.parseInt(betArg);
        } catch (NumberFormatException e) {
            return ActionResult.fail("invalid_request");
        }
        PlayerProfile profile = profile(mod, player);
        long day = mod.core().cobbleverse().day();
        ActionResult check = luck.canSpin(profile, betIndex, day, balance[0].getAsLong());
        if (!check.success()) {
            return check;
        }
        long bet = luck.bet(betIndex);
        if (!CobbleDollarsBridge.remove(player, bet)) {
            return ActionResult.fail("cv_insufficient", bet);
        }
        Slots slots = mod.core().cobbleverse().catalog().slots();
        int[] reels = LuckService.spin(slots, RANDOM);
        long payout = LuckService.payout(slots, reels, bet);
        if (payout > 0 && !CobbleDollarsBridge.add(player, payout)) {
            CobbleDollarsBridge.add(player, bet);
            return ActionResult.fail("cv_no_cobbledollars");
        }
        luck.recordSpin(profile, day);
        stat(mod, player, "slot_spins");
        boolean jackpot = LuckService.multiplier(slots, reels) >= JACKPOT;
        if (jackpot) {
            stat(mod, player, "slot_jackpots");
            mod.notifyParty(player.getUUID(), "caricraft.notify.slot_jackpot", List.of(player.getGameProfile().getName(),
                    String.valueOf(payout)));
        }
        remember(player, "spin", String.valueOf(betIndex), 0, null, false, 0, List.of(reels[0], reels[1], reels[2]), payout, payout > bet);
        mod.markDirty();
        if (payout == 0) {
            return ActionResult.ok("cv_spin_lose", bet);
        }
        return ActionResult.ok(jackpot ? "cv_spin_jackpot" : payout > bet ? "cv_spin_win" : "cv_spin_back", payout, bet);
    }

    // ------------------------------------------------------------------ mejoras

    private static final List<String> STAT_KEYS = List.of("hp", "attack", "defence", "special_attack", "special_defence", "speed");

    public static ActionResult upgrade(CaricraftServer mod, ServerPlayer player, String upgradeId, String pokemonId) {
        LuckService luck = luck(mod);
        OptionalLong[] balance = new OptionalLong[1];
        ActionResult pre = precheck(mod, player, luck.upgradesEnabled(), balance);
        if (pre != null) {
            return pre;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(pokemonId);
        } catch (IllegalArgumentException e) {
            return ActionResult.fail("invalid_request");
        }
        Upgrade upgrade = mod.core().cobbleverse().catalog().upgrade(upgradeId);
        PokemonBackend backend = CobblemonCompat.backend();
        PokemonBackend.PartyMon mon = backend.party(player).stream().filter(m -> m.uuid().equals(uuid)).findFirst().orElse(null);
        Optional<int[]> ivs = backend.ivs(player, uuid);
        if (mon == null || ivs.isEmpty()) {
            return ActionResult.fail("cv_pokemon_gone");
        }
        if (upgrade != null) {
            String blocked = LuckService.upgradeBlocked(upgrade, new LuckService.MonState(mon.level(), mon.info().shiny(), ivs.get(),
                    mon.info().legendary()), backend.inBattle(player));
            if (blocked != null) {
                return ActionResult.fail("cv_up_blocked_" + blocked);
            }
        }
        ActionResult check = luck.canUpgrade(upgrade, balance[0].getAsLong());
        if (!check.success()) {
            return check;
        }
        if (!CobbleDollarsBridge.remove(player, upgrade.price())) {
            return ActionResult.fail("cv_insufficient", upgrade.price());
        }
        boolean success = LuckService.roll(upgrade.chanceOrOne(), RANDOM);
        ActionResult result;
        int value = 0;
        try {
            if (!success) {
                result = ActionResult.fail("cv_up_" + upgrade.kind() + "_fail", mon.species(), upgrade.price());
            } else {
                String extra;
                switch (upgrade.kind()) {
                    case "iv" -> {
                        value = LuckService.lowestIv(ivs.get());
                        extra = "caricraft.cc.stat." + STAT_KEYS.get(value);
                    }
                    case "level" -> {
                        value = LuckService.levelAfter(upgrade, mon.level());
                        extra = String.valueOf(value);
                    }
                    default -> extra = "";
                }
                if (!backend.improve(player, uuid, upgrade.kind(), value)) {
                    CobbleDollarsBridge.add(player, upgrade.price());
                    return ActionResult.fail("cv_pokemon_gone");
                }
                result = ActionResult.ok("cv_up_" + upgrade.kind(), mon.species(), extra);
            }
        } catch (RuntimeException e) {
            CobbleDollarsBridge.add(player, upgrade.price());
            CaricraftMod.LOGGER.error("No se pudo aplicar la mejora {} a {}; se devuelve el dinero.", upgradeId,
                    player.getGameProfile().getName(), e);
            return ActionResult.fail("internal_error");
        }
        stat(mod, player, "pokemon_upgrades");
        boolean shinyNow = mon.info().shiny() || (success && "shiny".equals(upgrade.kind()));
        remember(player, "upgrade", upgradeId, 0, mon.species(), shinyNow, value, List.of(), 0, success);
        player.playNotifySound(success ? SoundEvents.PLAYER_LEVELUP : SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7f,
                success ? 1.5f : 1.0f);
        mod.markDirty();
        return result;
    }

    // ------------------------------------------------------------------ fusiones

    /** Cuántos tiene el jugador en el inventario principal de cada objeto. */
    static Map<String, Integer> inventoryCounts(ServerPlayer player) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    private static void removeItems(ServerPlayer player, String itemId, int count) {
        int left = count;
        for (ItemStack stack : player.getInventory().items) {
            if (left <= 0) {
                break;
            }
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
        player.getInventory().setChanged();
    }

    public static ActionResult fuse(CaricraftServer mod, ServerPlayer player, String fusionId) {
        LuckService luck = luck(mod);
        OptionalLong[] balance = new OptionalLong[1];
        ActionResult pre = precheck(mod, player, luck.fusionsEnabled(), balance);
        if (pre != null) {
            return pre;
        }
        Fusion fusion = mod.core().cobbleverse().catalog().fusion(fusionId);
        ActionResult check = luck.canFuse(fusion, inventoryCounts(player), balance[0].getAsLong());
        if (!check.success()) {
            return check;
        }
        if (fusion.price() > 0 && !CobbleDollarsBridge.remove(player, fusion.price())) {
            return ActionResult.fail("cv_insufficient", fusion.price());
        }
        for (Ingredient ingredient : fusion.inputs()) {
            removeItems(player, ingredient.item(), ingredient.count());
        }
        boolean success = LuckService.roll(fusion.chanceOrOne(), RANDOM);
        if (success) {
            PhoneActionHandler.giveItemTo(player, fusion.output(), fusion.countOrOne());
        }
        stat(mod, player, "fusions");
        remember(player, "fusion", fusionId, 0, null, false, success ? fusion.countOrOne() : 0, List.of(), 0, success);
        player.playNotifySound(success ? SoundEvents.ANVIL_USE : SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.2f);
        mod.markDirty();
        return success ? ActionResult.ok("cv_fuse_ok", fusion.countOrOne(), fusion.output())
                : ActionResult.fail("cv_fuse_fail", fusion.output());
    }
}
