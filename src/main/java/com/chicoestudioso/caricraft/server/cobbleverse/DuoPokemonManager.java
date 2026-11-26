package com.chicoestudioso.caricraft.server.cobbleverse;

import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.cobbleverse.CaptureInfo;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseService;
import com.chicoestudioso.caricraft.core.cobbleverse.DuoDexService;
import com.chicoestudioso.caricraft.core.cobbleverse.DuoPokemonState;
import com.chicoestudioso.caricraft.core.cobbleverse.HuntService;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;
import com.chicoestudioso.caricraft.integration.cobbledollars.CobbleDollarsBridge;
import com.chicoestudioso.caricraft.integration.cobblemon.CobblemonCompat;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Caza del día, Pokédex del dúo y Centro Pokémon de bolsillo (servidor). El núcleo decide qué cuenta y cuánto se
 * paga; aquí se reparte: Carins al monedero del dúo y CobbleDollars a cada miembro (a los desconectados se les
 * guardan para cuando entren).
 */
public final class DuoPokemonManager {
    private static List<String> pool;

    private DuoPokemonManager() {
    }

    /** Especies de la caza que existen de verdad en esta instalación (se calcula una vez). */
    static List<String> pool() {
        if (pool == null) {
            PokemonBackend backend = CobblemonCompat.backend();
            List<String> available = backend == null ? HuntService.SPECIES_POOL
                    : HuntService.SPECIES_POOL.stream().filter(s -> backend.speciesExists("cobblemon:" + s)).toList();
            pool = available.size() >= 10 ? available : HuntService.SPECIES_POOL;
        }
        return pool;
    }

    // ------------------------------------------------------------------ capturas

    /** Un miembro acaba de capturar un Pokémon salvaje. */
    public static void onCapture(CaricraftServer mod, ServerPlayer player, CaptureInfo info) {
        if (!CobbleverseManager.active(mod)) {
            return;
        }
        Party party = mod.core().partyOf(player.getUUID());
        if (party == null) {
            return;
        }
        String name = player.getGameProfile().getName();
        boolean changed = false;
        HuntService hunt = mod.core().hunt();
        if (hunt.enabled()) {
            long day = mod.core().cobbleverse().day();
            HuntService.Result result = hunt.onCapture(party, player.getUUID(), info, day, pool());
            if (result.counted()) {
                changed = true;
                List<HuntService.Target> targets = HuntService.targets(day, pool());
                for (int index : result.completed()) {
                    HuntService.Target target = targets.get(index);
                    mod.notifyParty(player.getUUID(), "caricraft.notify.hunt_target", List.of(name, targetArg(target)));
                }
                if (result.cleared()) {
                    mod.notifyParty(player.getUUID(), result.duoBonus() ? "caricraft.notify.hunt_cleared_duo"
                            : party.isDuo() ? "caricraft.notify.hunt_cleared_solo" : "caricraft.notify.hunt_cleared", List.of());
                }
                if (result.weekly()) {
                    mod.notifyParty(player.getUUID(), "caricraft.notify.hunt_weekly", List.of(String.valueOf(hunt.weeklyClears())));
                }
                pay(mod, party, result.payout(), "daily_hunt");
                if (!result.completed().isEmpty()) {
                    player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, result.cleared() ? 1.6f : 1.3f);
                }
            }
        }
        DuoDexService dex = mod.core().duoDex();
        if (mod.core().config().cobbleverse.duoDex) {
            changed = true;
            for (DuoDexService.Goal goal : dex.onCapture(party, name, info, System.currentTimeMillis())) {
                mod.notifyParty(player.getUUID(), "caricraft.notify.dex_goal", List.of("caricraft.phone.cv.goal." + goal.id()));
                pay(mod, party, new HuntService.Payout(goal.dollars(), goal.carins()), "duo_dex");
            }
            if (info.shiny()) {
                mod.notifyParty(player.getUUID(), "caricraft.notify.dex_shiny", List.of(name, "cobblemon:" + info.species()));
            }
        }
        if (changed) {
            mod.markDirty();
            mod.sendPartySnapshots(player.getUUID());
        }
    }

    private static String targetArg(HuntService.Target target) {
        return switch (target.kind()) {
            case SPECIES -> "cobblemon:" + target.value();
            case TYPE -> "cobblemon.type." + target.value();
            case ANY -> "caricraft.phone.cv.hunt_any";
        };
    }

    /** Carins al monedero del grupo y CobbleDollars a cada miembro. */
    static void pay(CaricraftServer mod, Party party, HuntService.Payout payout, String reason) {
        if (payout.carins() > 0 && mod.core().economy().credit(party.wallet(), payout.carins(), reason, "", null) != EconomyService.Result.OK) {
            CaricraftMod.LOGGER.warn("No se pudieron abonar {} Carins ({}) al grupo {}", payout.carins(), reason, party.id());
        }
        if (payout.dollars() <= 0) {
            return;
        }
        for (UUID member : List.copyOf(party.members())) {
            ServerPlayer online = mod.server().getPlayerList().getPlayer(member);
            if (online == null || !CobbleDollarsBridge.add(online, payout.dollars())) {
                PlayerProfile profile = mod.core().state().profiles().get(member);
                if (profile != null) {
                    profile.setPendingDollars(profile.pendingDollars() + payout.dollars());
                }
            }
        }
    }

    /** Al entrar: se cobran los CobbleDollars que se ganaron mientras estaba desconectado. */
    public static void onJoin(CaricraftServer mod, ServerPlayer player) {
        PlayerProfile profile = mod.core().state().profiles().get(player.getUUID());
        if (profile == null || profile.pendingDollars() <= 0 || !CobbleDollarsBridge.available()) {
            return;
        }
        long pending = profile.pendingDollars();
        if (CobbleDollarsBridge.add(player, pending)) {
            profile.setPendingDollars(0);
            mod.markDirty();
            mod.notify(player, true, "caricraft.notify.pending_dollars", List.of(String.valueOf(pending)));
        }
    }

    // ------------------------------------------------------------------ Centro Pokémon de bolsillo

    public static ActionResult heal(CaricraftServer mod, ServerPlayer player) {
        if (!CobbleverseManager.active(mod)) {
            return ActionResult.fail("cv_disabled");
        }
        CobbleverseService service = mod.core().cobbleverse();
        PokemonBackend backend = CobblemonCompat.backend();
        PlayerProfile profile = mod.core().onPlayerJoin(player.getUUID(), player.getGameProfile().getName());
        long price = service.healPrice();
        OptionalLong balance = price > 0 ? CobbleDollarsBridge.balance(player) : OptionalLong.of(0);
        if (balance.isEmpty()) {
            return ActionResult.fail("cv_no_cobbledollars");
        }
        ActionResult check = service.canHeal(profile, backend.partyNeedsHeal(player), backend.inBattle(player), balance.getAsLong());
        if (!check.success()) {
            return check;
        }
        if (price > 0 && !CobbleDollarsBridge.remove(player, price)) {
            return ActionResult.fail("cv_insufficient", price - balance.getAsLong());
        }
        try {
            backend.healParty(player);
        } catch (RuntimeException e) {
            if (price > 0) {
                CobbleDollarsBridge.add(player, price);
            }
            CaricraftMod.LOGGER.error("No se pudo curar el equipo de {}; se devuelve el dinero.", player.getGameProfile().getName(), e);
            return ActionResult.fail("internal_error");
        }
        service.recordHeal(profile);
        Party party = mod.core().partyOf(player.getUUID());
        if (party != null) {
            party.incrementStat("pocket_heals", 1);
        }
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.2f);
        mod.markDirty();
        return ActionResult.ok("cv_healed", price);
    }

    // ------------------------------------------------------------------ vistas

    static PhoneSnapshot.HealView healView(CaricraftServer mod, ServerPlayer player, PlayerProfile profile) {
        CobbleverseService service = mod.core().cobbleverse();
        if (!service.pocketHealEnabled()) {
            return null;
        }
        PokemonBackend backend = CobblemonCompat.backend();
        return new PhoneSnapshot.HealView(service.healPrice(), service.healReadyAt(profile), backend.partyNeedsHeal(player),
                backend.inBattle(player));
    }

    static PhoneSnapshot.HuntView huntView(CaricraftServer mod, Party party) {
        HuntService hunt = mod.core().hunt();
        if (!hunt.enabled() || party == null) {
            return null;
        }
        long day = mod.core().cobbleverse().day();
        DuoPokemonState s = hunt.state(party, day);
        List<PhoneSnapshot.HuntTargetView> targets = new ArrayList<>();
        List<HuntService.Target> list = HuntService.targets(day, pool());
        for (int i = 0; i < list.size(); i++) {
            HuntService.Target t = list.get(i);
            HuntService.Payout reward = hunt.scaled(HuntService.reward(t.kind()));
            targets.add(new PhoneSnapshot.HuntTargetView(t.kind().name(), t.value(), t.count(), Math.min(t.count(), s.huntProgress(i)),
                    s.huntPaid(i), reward.dollars(), reward.carins()));
        }
        Map<String, Integer> contrib = new LinkedHashMap<>();
        for (UUID member : party.members()) {
            contrib.put(mod.core().state().nameOf(member), s.huntContrib().getOrDefault(member, 0));
        }
        HuntService.Payout clear = hunt.scaled(HuntService.CLEAR_REWARD);
        HuntService.Payout duo = hunt.scaled(HuntService.DUO_REWARD);
        HuntService.Payout weekly = hunt.scaled(HuntService.WEEKLY_REWARD);
        return new PhoneSnapshot.HuntView(targets, contrib, hunt.duoMinEach(), party.isDuo(), s.huntCleared(), s.huntDuoBonus(),
                s.weekClears(), hunt.weeklyClears(), s.weekPaid(), clear.dollars(), clear.carins(), duo.dollars(), duo.carins(),
                weekly.dollars(), weekly.carins());
    }

    static PhoneSnapshot.DexView dexView(CaricraftServer mod, Party party) {
        if (!mod.core().config().cobbleverse.duoDex || party == null) {
            return null;
        }
        DuoPokemonState s = party.pokemon();
        List<PhoneSnapshot.GoalView> goals = new ArrayList<>();
        for (DuoDexService.Goal goal : DuoDexService.GOALS) {
            goals.add(new PhoneSnapshot.GoalView(goal.id(), goal.kind().name(), goal.type(), goal.count(),
                    Math.min(goal.count(), DuoDexService.progress(s, goal)), s.goalsDone().contains(goal.id()), goal.carins(), goal.dollars()));
        }
        return new PhoneSnapshot.DexView(s.species().size(), s.captures(), s.shinies().size(), s.legends().size(),
                entries(s.shinies(), 24), entries(s.rares(), 12), entries(s.legends(), 24), goals, Map.copyOf(s.typeCounts()));
    }

    private static List<PhoneSnapshot.DexEntryView> entries(List<DuoPokemonState.DexEntry> list, int max) {
        return list.stream().limit(max).map(e -> new PhoneSnapshot.DexEntryView("cobblemon:" + e.species(), e.player(), e.level(),
                e.shiny(), e.catchRate(), e.time())).toList();
    }
}
