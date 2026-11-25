package com.chicoestudioso.caricraft.core.challenge;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleService;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.party.Party;

/**
 * Reglas del Desafío Dúo, sin Minecraft.
 *
 * <p>El servidor pasa un índice de día que cambia al amanecer (ver {@link #dayIndex(long)}), de modo que la
 * noche pertenece al mismo índice que la tarde anterior. Cada {@code intervalNights} noches hay un desafío
 * obligatorio; el dúo puede adelantarlo a la noche actual, lo que reinicia la cuenta.</p>
 */
public final class ChallengeService {
    public static final long TICKS_PER_DAY = 24_000L;
    public static final long NIGHT_START = 13_000L;
    public static final long NIGHT_END = 23_000L;
    public static final String STAT_KILLS = "challenge_kills";

    private final CaricraftConfig config;
    private final EconomyService economy;
    private final ChronicleService chronicle;

    public ChallengeService(CaricraftConfig config, EconomyService economy, ChronicleService chronicle) {
        this.config = config;
        this.economy = economy;
        this.chronicle = chronicle;
    }

    /** Índice de día que cambia al amanecer (tick 23000), no a medianoche. */
    public static long dayIndex(long dayTime) {
        return Math.floorDiv(dayTime + (TICKS_PER_DAY - NIGHT_END), TICKS_PER_DAY);
    }

    public static boolean isNight(long dayTime) {
        long t = Math.floorMod(dayTime, TICKS_PER_DAY);
        return t >= NIGHT_START && t < NIGHT_END;
    }

    public boolean enabled() {
        return config.challenge.enabled;
    }

    public int level(Party party) {
        CaricraftConfig.ChallengeConfig c = config.challenge;
        return Math.min(c.maxLevel, 1 + party.challenge().nightsCompleted / c.nightsPerLevel);
    }

    /** Noches que faltan para el próximo desafío obligatorio (0 = esta noche). */
    public long nightsUntilForced(Party party, long day) {
        ChallengeState s = party.challenge();
        if (s.scheduledDay == day || s.active) {
            return 0;
        }
        long last = s.lastChallengeDay == ChallengeState.UNSET ? day : s.lastChallengeDay;
        return Math.max(0, config.challenge.intervalNights - (day - last));
    }

    /** ¿Es noche de desafío para este grupo (activa o a punto de empezar)? */
    public boolean isChallengeNight(Party party, long day, boolean night) {
        ChallengeState s = party.challenge();
        return enabled() && (s.active || (night && s.scheduledDay == day));
    }

    /**
     * Avanza el estado. Se llama periódicamente (cada segundo) mientras algún miembro está conectado.
     *
     * @return evento producido, o {@code null}
     */
    public ChallengeEvent update(Party party, long day, boolean night) {
        ChallengeState s = party.challenge();
        if (s.lastChallengeDay == ChallengeState.UNSET) {
            s.lastChallengeDay = day; // el primer desafío obligatorio llega dentro de N noches
        }
        if (s.active) {
            boolean ended = !enabled() || !night || day != s.scheduledDay;
            if (!ended) {
                return null;
            }
            boolean survived = enabled() && (day == s.scheduledDay || day == s.scheduledDay + 1);
            return end(party, survived);
        }
        if (!enabled()) {
            s.scheduledDay = ChallengeState.NONE;
            return null;
        }
        if (s.scheduledDay != ChallengeState.NONE && s.scheduledDay < day) {
            s.scheduledDay = ChallengeState.NONE; // programado para una noche que ya pasó sin jugar
        }
        if (night) {
            if (s.scheduledDay != day && day - s.lastChallengeDay >= config.challenge.intervalNights) {
                s.scheduledDay = day;
                s.forced = true;
            }
            if (s.scheduledDay == day) {
                s.active = true;
                s.nightKills = 0;
                s.nightCarins = 0;
                return new ChallengeEvent(ChallengeEvent.Kind.STARTED, s.forced, level(party), 0, 0, 0);
            }
        }
        return null;
    }

    private ChallengeEvent end(Party party, boolean survived) {
        ChallengeState s = party.challenge();
        long day = s.scheduledDay;
        int kills = s.nightKills;
        s.active = false;
        s.lastChallengeDay = day;
        s.scheduledDay = ChallengeState.NONE;
        s.forced = false;
        long bonus = 0;
        if (survived) {
            s.nightsCompleted++;
            if (config.challenge.nightReward > 0
                    && economy.credit(party.wallet(), config.challenge.nightReward, "challenge_night", String.valueOf(day),
                    "challenge:" + party.id() + ":" + day) == EconomyService.Result.OK) {
                bonus = config.challenge.nightReward;
                s.nightCarins += bonus;
                s.totalCarins += bonus;
            }
            chronicle.add(party, ChronicleEntry.CHALLENGE_SURVIVED, null, "", String.valueOf(s.nightsCompleted),
                    String.valueOf(kills), String.valueOf(s.nightCarins));
        }
        return new ChallengeEvent(survived ? ChallengeEvent.Kind.SURVIVED : ChallengeEvent.Kind.ENDED, false,
                level(party), kills, s.nightCarins, bonus);
    }

    /** El dúo adelanta el desafío a la noche actual (o a la de hoy si aún es de día). */
    public ActionResult bringForward(Party party, long day, boolean night) {
        if (!enabled()) {
            return ActionResult.fail("challenge_disabled");
        }
        ChallengeState s = party.challenge();
        if (s.active) {
            return ActionResult.fail("challenge_active");
        }
        if (s.scheduledDay == day) {
            return ActionResult.fail("challenge_already_scheduled");
        }
        s.scheduledDay = day;
        s.forced = false;
        return ActionResult.ok(night ? "challenge_starting" : "challenge_scheduled");
    }

    /** Fuerza el final (administración). */
    public ChallengeEvent forceEnd(Party party) {
        return party.challenge().active ? end(party, false) : null;
    }

    /** Un miembro ha derrotado un mob del desafío. Devuelve los Carins ganados. */
    public long onKill(Party party) {
        ChallengeState s = party.challenge();
        if (!s.active) {
            return 0;
        }
        s.nightKills++;
        s.totalKills++;
        party.incrementStat(STAT_KILLS, 1);
        long reward = config.challenge.killReward == 0 ? 0 : config.challenge.killReward + level(party) - 1;
        if (reward > 0 && economy.credit(party.wallet(), reward, "challenge_kill", "", null) == EconomyService.Result.OK) {
            s.nightCarins += reward;
            s.totalCarins += reward;
            return reward;
        }
        return 0;
    }
}
