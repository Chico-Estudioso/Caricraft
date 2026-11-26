package com.chicoestudioso.caricraft.core.challenge;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleService;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.party.Party;

import java.util.UUID;

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
    /** Oleadas por noche (anochecer, medianoche y madrugada). */
    public static final int WAVES = 3;
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

    /** Oleada de la noche: 0 al anochecer, 1 a medianoche y 2 de madrugada (fuera de la noche, 0). */
    public static int wave(long dayTime) {
        if (!isNight(dayTime)) {
            return 0;
        }
        long t = Math.floorMod(dayTime, TICKS_PER_DAY);
        return t < 16_000 ? 0 : t < 19_500 ? 1 : 2;
    }

    public static boolean isNight(long dayTime) {
        long t = Math.floorMod(dayTime, TICKS_PER_DAY);
        return t >= NIGHT_START && t < NIGHT_END;
    }

    public boolean enabled() {
        return config.challenge.enabled;
    }

    // ------------------------------------------------------------------ nivel por experiencia

    /** Experiencia para pasar del nivel {@code level} al siguiente: {@code xpPerLevel × (1 + xpGrowth × (level − 1))}. */
    public long xpStep(int level) {
        CaricraftConfig.ChallengeConfig c = config.challenge;
        return Math.max(1, Math.round(c.xpPerLevel * (1 + c.xpGrowth * (Math.max(1, level) - 1))));
    }

    /** Experiencia total necesaria para llegar a un nivel (el 1 empieza en 0). */
    public long xpForLevel(int level) {
        long total = 0;
        for (int i = 1; i < Math.min(level, config.challenge.maxLevel); i++) {
            total += xpStep(i);
        }
        return total;
    }

    public int levelFromXp(long xp) {
        int level = 1;
        while (level < config.challenge.maxLevel && xp >= xpForLevel(level + 1)) {
            level++;
        }
        return level;
    }

    /** Grupos de antes de la experiencia: empiezan con la necesaria para su nivel por noches superadas. */
    private void migrate(Party party) {
        ChallengeState s = party.challenge();
        if (s.legacyLevel) {
            s.legacyLevel = false;
            int legacy = Math.min(config.challenge.maxLevel, 1 + s.nightsCompleted / config.challenge.nightsPerLevel);
            s.xp = Math.max(s.xp, xpForLevel(legacy));
        }
    }

    public int level(Party party) {
        migrate(party);
        return levelFromXp(party.challenge().xp);
    }

    public long xp(Party party) {
        migrate(party);
        return party.challenge().xp;
    }

    /** Suma experiencia (kills, escuadrones, noches, cofres). Devuelve el nivel que tenía antes. */
    public int gainXp(Party party, long amount) {
        int before = level(party);
        if (amount > 0) {
            party.challenge().xp += amount;
        }
        return before;
    }

    /** Experiencia de un mob derrotado: más cuanto más fuerte; triple si es élite. */
    public long killXp(int mobLevel, boolean elite) {
        long base = config.challenge.xpPerKill + Math.max(1, mobLevel) / 5;
        return elite ? base * 3 : base;
    }

    /** Nivel con el que aparecen ahora los mobs: nivel del grupo más la oleada. */
    public int effectiveLevel(Party party, int wave) {
        return level(party) + (config.challenge.waves ? Math.max(0, Math.min(2, wave)) : 0);
    }

    /** Probabilidad de que un mob sea élite a este nivel. */
    public double eliteChance(int effectiveLevel) {
        return config.challenge.elites ? Math.min(0.25, 0.04 + 0.01 * effectiveLevel) : 0;
    }

    /** Probabilidad de que un mob aparezca acompañado (horda) a este nivel. */
    public double hordeChance(int effectiveLevel) {
        return config.challenge.hordes ? Math.min(0.5, 0.03 * effectiveLevel) : 0;
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
        int previous = level(party);
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
            gainXp(party, config.challenge.xpPerNight);
        }
        return new ChallengeEvent(survived ? ChallengeEvent.Kind.SURVIVED : ChallengeEvent.Kind.ENDED, false,
                level(party), kills, s.nightCarins, bonus, previous);
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

    // ------------------------------------------------------------------ escuadrones

    /** Escuadrones por noche: 1, y uno más cada 8 niveles, hasta {@code maxSquadsPerNight}. */
    public int squadsPerNight(int level) {
        if (!config.challenge.squads) {
            return 0;
        }
        return Math.min(config.challenge.maxSquadsPerNight, 1 + (Math.max(1, level) - 1) / 8);
    }

    /** Mobs por escuadrón, capitán incluido: 3, y uno más cada 5 niveles (máx. 7). */
    public static int squadSize(int level) {
        return Math.min(7, 3 + Math.max(1, level) / 5);
    }

    /** Tabla de botín: 1 (niveles 1–9), 2 (10–19) o 3 (20 o más). */
    public static int lootTier(int level) {
        return level >= 20 ? 3 : level >= 10 ? 2 : 1;
    }

    /** Hora (dentro del día) a la que aparece el escuadrón {@code index} de {@code count}, repartidos por la noche. */
    public static long squadTime(int index, int count) {
        long span = NIGHT_END - NIGHT_START - 2_000L;
        return NIGHT_START + 600L + span * index / Math.max(1, count);
    }

    /** Carins por derrotar un escuadrón: {@code squadReward + 5 × nivel}. */
    public long squadReward(int level) {
        return config.challenge.squadReward == 0 ? 0 : config.challenge.squadReward + 5L * Math.max(1, level);
    }

    /**
     * El dúo ha derrotado un escuadrón: recompensa, estadística y crónica.
     *
     * @return Carins ganados (0 si el desafío ya no está activo)
     */
    public long onSquadDefeated(Party party, int level, UUID player, String playerName) {
        ChallengeState s = party.challenge();
        if (!s.active) {
            return 0;
        }
        party.incrementStat("challenge_squads", 1);
        chronicle.add(party, ChronicleEntry.SQUAD_DEFEATED, player, playerName, String.valueOf(level));
        gainXp(party, config.challenge.xpPerSquad);
        long reward = squadReward(level);
        if (reward > 0 && economy.credit(party.wallet(), reward, "challenge_squad", "", null) == EconomyService.Result.OK) {
            s.nightCarins += reward;
            s.totalCarins += reward;
            return reward;
        }
        return 0;
    }

    /** Un miembro ha derrotado un mob del desafío (nivel 1, no élite). */
    public long onKill(Party party) {
        return onKill(party, level(party), false);
    }

    /**
     * Un miembro ha derrotado un mob del desafío. Recompensa: {@code killReward + nivel/2}, triple si es élite.
     *
     * @return Carins ganados
     */
    public long onKill(Party party, int mobLevel, boolean elite) {
        ChallengeState s = party.challenge();
        if (!s.active) {
            return 0;
        }
        s.nightKills++;
        s.totalKills++;
        party.incrementStat(STAT_KILLS, 1);
        gainXp(party, killXp(mobLevel, elite));
        if (elite) {
            party.incrementStat("challenge_elites", 1);
        }
        long base = config.challenge.killReward == 0 ? 0 : config.challenge.killReward + Math.max(1, mobLevel) / 2;
        long reward = elite ? base * 3 : base;
        if (reward > 0 && economy.credit(party.wallet(), reward, "challenge_kill", "", null) == EconomyService.Result.OK) {
            s.nightCarins += reward;
            s.totalCarins += reward;
            return reward;
        }
        return 0;
    }

    // ------------------------------------------------------------------ cofres del desafío por el mundo

    /** Oleadas de la emboscada: 2 (niveles 1–9), 3 (10–19) o 4 (20 o más). */
    public static int chestWaves(int level) {
        return level >= 20 ? 4 : level >= 10 ? 3 : 2;
    }

    /** Mobs por oleada: 3, y uno más cada 4 niveles (máx. 8). La última oleada trae uno más (el capitán). */
    public static int chestWaveSize(int level, int wave, int waves) {
        int base = Math.min(8, 3 + Math.max(1, level) / 4);
        return wave >= waves ? base + 1 : base;
    }

    /** Nivel del Pokémon guardián: 12 + 2 por nivel de desafío, entre 10 y 80. */
    public static int guardianLevel(int level) {
        return Math.max(10, Math.min(80, 12 + 2 * Math.max(1, level)));
    }

    /** Carins por conquistar un cofre: {@code chestReward + 5 × nivel}. */
    public long chestReward(int level) {
        return config.challenge.chestReward == 0 ? 0 : config.challenge.chestReward + 5L * Math.max(1, level);
    }

    /**
     * El dúo conquista un cofre del desafío (de día o de noche): Carins, experiencia, estadística y crónica.
     *
     * @return Carins ganados
     */
    public long onChestConquered(Party party, int level, boolean guardian, UUID player, String playerName) {
        party.incrementStat("challenge_chests", 1);
        chronicle.add(party, guardian ? ChronicleEntry.GUARDIAN_DEFEATED : ChronicleEntry.CHEST_CONQUERED, player, playerName,
                String.valueOf(level));
        gainXp(party, config.challenge.xpPerChest);
        long reward = chestReward(level);
        if (reward > 0 && economy.credit(party.wallet(), reward, "challenge_chest", "", null) == EconomyService.Result.OK) {
            party.challenge().totalCarins += reward;
            return reward;
        }
        return 0;
    }

    /** Experiencia por un mob de la emboscada de un cofre (cuenta igual que en la noche, sin Carins). */
    public void onAmbushKill(Party party, int mobLevel, boolean elite) {
        party.challenge().totalKills++;
        party.incrementStat(STAT_KILLS, 1);
        gainXp(party, killXp(mobLevel, elite));
    }
}
