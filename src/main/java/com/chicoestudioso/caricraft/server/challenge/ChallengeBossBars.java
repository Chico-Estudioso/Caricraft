package com.chicoestudioso.caricraft.server.challenge;

import com.chicoestudioso.caricraft.core.challenge.ChallengeService;
import com.chicoestudioso.caricraft.core.challenge.ChallengeState;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Barra de jefe del Desafío Dúo: durante la noche muestra nivel, oleada, bajas y lo que queda para el amanecer;
 * poco antes de anochecer avisa de que el desafío está a punto de empezar.
 */
public final class ChallengeBossBars {
    /** Ticks de aviso antes de que empiece la noche de desafío. */
    static final long WARNING_TICKS = 1_500L;
    private static final long NIGHT_LENGTH = ChallengeService.NIGHT_END - ChallengeService.NIGHT_START;

    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private ChallengeBossBars() {
    }

    /** Una vez por segundo, desde {@link ChallengeManager#tick}. */
    static void update(CaricraftServer mod, Map<UUID, Party> parties, long dayTime, long day) {
        Set<UUID> keep = new HashSet<>();
        if (mod.core().config().challenge.enabled && mod.core().config().challenge.bossBar) {
            long t = Math.floorMod(dayTime, 24_000L);
            for (Party party : parties.values()) {
                ChallengeState state = party.challenge();
                Component title;
                float progress;
                BossEvent.BossBarColor color;
                if (state.active() && ChallengeService.isNight(dayTime)) {
                    long left = ChallengeService.NIGHT_END - t;
                    int wave = ChallengeService.wave(dayTime);
                    int level = mod.core().challenge().effectiveLevel(party, wave);
                    title = mod.core().config().challenge.waves
                            ? Component.translatable("caricraft.bossbar.challenge", level, wave + 1, ChallengeService.WAVES,
                                    state.nightKills(), clock(left))
                            : Component.translatable("caricraft.bossbar.challenge_no_waves", level, state.nightKills(), clock(left));
                    progress = (float) left / NIGHT_LENGTH;
                    color = BossEvent.BossBarColor.RED;
                } else if (!state.active() && state.scheduledDay() == day && t < ChallengeService.NIGHT_START
                        && t >= ChallengeService.NIGHT_START - WARNING_TICKS) {
                    long left = ChallengeService.NIGHT_START - t;
                    title = Component.translatable("caricraft.bossbar.challenge_soon", clock(left));
                    progress = (float) left / WARNING_TICKS;
                    color = BossEvent.BossBarColor.YELLOW;
                } else {
                    continue;
                }
                ServerBossEvent bar = BARS.computeIfAbsent(party.id(), id -> {
                    ServerBossEvent created = new ServerBossEvent(title, color, BossEvent.BossBarOverlay.NOTCHED_10);
                    created.setPlayBossMusic(false);
                    return created;
                });
                bar.setName(title);
                bar.setColor(color);
                bar.setProgress(Math.max(0f, Math.min(1f, progress)));
                syncPlayers(mod, bar, party);
                keep.add(party.id());
            }
        }
        for (UUID id : new ArrayList<>(BARS.keySet())) {
            if (!keep.contains(id)) {
                BARS.remove(id).removeAllPlayers();
            }
        }
    }

    /** Los miembros conectados en el mundo normal ven la barra; el resto no. */
    private static void syncPlayers(CaricraftServer mod, ServerBossEvent bar, Party party) {
        List<ServerPlayer> wanted = new ArrayList<>();
        for (UUID member : party.members()) {
            ServerPlayer player = mod.server().getPlayerList().getPlayer(member);
            if (player != null && player.level().dimension() == Level.OVERWORLD) {
                wanted.add(player);
            }
        }
        for (ServerPlayer shown : new ArrayList<>(bar.getPlayers())) {
            if (!wanted.contains(shown)) {
                bar.removePlayer(shown);
            }
        }
        for (ServerPlayer player : wanted) {
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        }
    }

    public static void forget(ServerPlayer player) {
        BARS.values().forEach(bar -> bar.removePlayer(player));
    }

    public static void clear() {
        BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BARS.clear();
    }

    /** Ticks de juego a «m:ss» de tiempo real (20 ticks por segundo). */
    static String clock(long ticks) {
        long seconds = Math.max(0, ticks / 20);
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
