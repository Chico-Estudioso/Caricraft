package com.chicoestudioso.caricraft.server.routine;

import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.CaricraftCore;
import com.chicoestudioso.caricraft.core.GameCalendar;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.routine.MedalService;
import com.chicoestudioso.caricraft.core.routine.RoutineState;
import com.chicoestudioso.caricraft.core.routine.StreakService;
import com.chicoestudioso.caricraft.core.routine.WeeklyEventService;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Racha diaria, evento semanal y medallas en el servidor: avisa, paga (lo decide el núcleo) y prepara la vista del
 * teléfono. Cada 5 s mira los grupos conectados.
 */
public final class RoutineManager {
    private static long ticks;

    private RoutineManager() {
    }

    /** Reclama una misión (desde el teléfono) y anuncia lo que suma a la racha. */
    public static ActionResult claimMission(CaricraftServer mod, ServerPlayer player, String missionId) {
        CaricraftCore.ClaimOutcome outcome = mod.core().claimMission(player.getUUID(), missionId);
        announceStreak(mod, player.getUUID(), outcome.streak());
        return outcome.result();
    }

    private static void announceStreak(CaricraftServer mod, UUID player, StreakService.Result result) {
        if (result.counted()) {
            if (result.broken()) {
                mod.notifyParty(player, "caricraft.notify.streak_broken", List.of());
            }
            if (result.shieldUsed()) {
                mod.notifyParty(player, "caricraft.notify.streak_shield", List.of());
            }
            mod.notifyParty(player, "caricraft.notify.streak_day", List.of(String.valueOf(result.days()), String.valueOf(result.reward())));
            ServerPlayer online = mod.server().getPlayerList().getPlayer(player);
            if (online != null) {
                online.playNotifySound(SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6f, 1.4f);
            }
        }
        if (result.duoBonus() > 0) {
            mod.notifyParty(player, "caricraft.notify.streak_duo", List.of(String.valueOf(result.duoBonus())));
        }
        if (result.counted() || result.duoBonus() > 0) {
            mod.markDirty();
            mod.sendPartySnapshots(player);
        }
    }

    /** Cada tick: cada 5 s, actividad del día, evento semanal y medallas de los grupos conectados. */
    public static void tick(MinecraftServer server, CaricraftServer mod) {
        if (++ticks % 100 != 0) {
            return;
        }
        CaricraftCore core = mod.core();
        long day = core.day();
        long week = core.week();
        Map<UUID, Party> online = new LinkedHashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Party party = core.partyOf(player.getUUID());
            if (party == null) {
                continue;
            }
            online.putIfAbsent(party.id(), party);
            Long last = party.routine().activeDay().get(player.getUUID());
            if (last == null || last != day) {
                announceStreak(mod, player.getUUID(), core.streak().onActive(party, player.getUUID(), day));
                mod.markDirty();
            }
        }
        for (Party party : online.values()) {
            UUID anyone = party.members().iterator().next();
            boolean changed = false;
            int reached = core.weekly().check(party, week);
            if (reached > 0) {
                WeeklyEventService.Event event = core.weekly().eventOf(week);
                mod.notifyParty(anyone, reached == 2 ? "caricraft.notify.weekly_done" : "caricraft.notify.weekly_half",
                        List.of("caricraft.weekly." + event.id(), String.valueOf(reached == 2 ? core.weekly().fullReward() : core.weekly().halfReward())));
                changed = true;
            }
            for (MedalService.Unlock unlock : core.medals().check(party)) {
                mod.notifyParty(anyone, "caricraft.notify.medal", List.of("caricraft.medal." + unlock.medal().id(),
                        "caricraft.medal.tier." + unlock.tier(), String.valueOf(unlock.reward())));
                core.chronicle().add(party, ChronicleEntry.MEDAL, null, "", unlock.medal().id(), String.valueOf(unlock.tier()));
                for (UUID member : party.members()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(member);
                    if (player != null) {
                        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1.2f);
                    }
                }
                changed = true;
            }
            if (changed) {
                mod.markDirty();
                mod.sendPartySnapshots(anyone);
            }
        }
    }

    // ------------------------------------------------------------------ vista

    public static PhoneSnapshot.RoutineView view(CaricraftServer mod, Party party, UUID viewer) {
        CaricraftCore core = mod.core();
        long day = core.day();
        long now = core.clock().now();
        GameCalendar calendar = GameCalendar.of(core.config().timeZone);
        RoutineState s = party.routine();

        PhoneSnapshot.StreakView streak = null;
        StreakService service = core.streak();
        if (service.enabled()) {
            boolean done = service.doneToday(s, day);
            int current = service.current(s, day);
            int next = done ? current : current + 1;
            boolean partnerActive = party.isDuo() && party.members().stream().filter(m -> !m.equals(viewer))
                    .allMatch(m -> s.activeDay().getOrDefault(m, -1L) == day);
            List<Long> rewards = new ArrayList<>();
            for (int i = 1; i <= StreakService.REWARDS.size(); i++) {
                rewards.add(service.rewardFor(i));
            }
            streak = new PhoneSnapshot.StreakView(current, s.streakBest(), done, service.alive(s, day), service.shieldAvailable(s, day),
                    party.isDuo(), partnerActive, s.duoPaidDay() == day, service.rewardFor(next), service.duoBonusFor(next),
                    Math.floorMod(next - 1, StreakService.REWARDS.size()) + 1, rewards, calendar.nextDayAt(now));
        }

        PhoneSnapshot.WeeklyView weekly = null;
        WeeklyEventService events = core.weekly();
        long week = core.week();
        WeeklyEventService.Event event = events.eventOf(week);
        if (event != null) {
            weekly = new PhoneSnapshot.WeeklyView(event.id(), event.icon(), event.stat(), event.goal(), events.progress(party, week),
                    s.eventPaid(), events.halfReward(), events.fullReward(), calendar.nextWeekAt(now), core.config().routine.weeklyBonus,
                    event.effect().name());
        }

        List<PhoneSnapshot.MedalView> medals = new ArrayList<>();
        MedalService medalService = core.medals();
        if (medalService.enabled()) {
            for (MedalService.Medal medal : medalService.medals()) {
                List<Long> tiers = new ArrayList<>();
                List<Long> rewards = new ArrayList<>();
                for (int i = 0; i < medal.tiers().length; i++) {
                    tiers.add(medal.tiers()[i]);
                    rewards.add(medalService.reward(i + 1));
                }
                medals.add(new PhoneSnapshot.MedalView(medal.id(), medal.icon(), medalService.tier(party, medal),
                        medalService.value(party, medal), tiers, rewards));
            }
        }
        List<PhoneSnapshot.BossRecordView> trophies = party.challenge().bosses().stream()
                .map(b -> new PhoneSnapshot.BossRecordView(b.level(), b.boss(), b.time(), b.players())).toList();
        if (streak == null && weekly == null && medals.isEmpty()) {
            return null;
        }
        return new PhoneSnapshot.RoutineView(streak, weekly, medals, trophies);
    }
}
