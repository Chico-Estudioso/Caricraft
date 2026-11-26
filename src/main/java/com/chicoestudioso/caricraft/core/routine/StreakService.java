package com.chicoestudioso.caricraft.core.routine;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.party.Party;

import java.util.List;
import java.util.UUID;

/**
 * Racha diaria del dúo: cada día en que el grupo reclama al menos una misión, la racha sube y paga Carins (más cada
 * día de la semana de racha, con un premio grande el séptimo). Si los dos miembros juegan ese día, hay un bonus de
 * dúo del 50 %. Un día perdido por semana lo cubre el protector de racha; dos días seguidos la rompen.
 */
public final class StreakService {
    /** Carins del día 1 al 7 de cada semana de racha. */
    public static final List<Long> REWARDS = List.of(15L, 20L, 25L, 30L, 40L, 50L, 100L);

    /** Resultado de apuntar actividad. */
    public record Result(boolean counted, int days, long reward, long duoBonus, boolean shieldUsed, boolean broken) {
        public static final Result NONE = new Result(false, 0, 0, 0, false, false);
    }

    private final CaricraftConfig config;
    private final EconomyService economy;

    public StreakService(CaricraftConfig config, EconomyService economy) {
        this.config = config;
        this.economy = economy;
    }

    public boolean enabled() {
        return config.routine.streak;
    }

    static long week(long day) {
        return Math.floorDiv(day + 3, 7);
    }

    /** Carins del día {@code days} de racha (1, 2…), ya con el multiplicador. */
    public long rewardFor(int days) {
        long base = REWARDS.get(Math.floorMod(Math.max(1, days) - 1, REWARDS.size()));
        return Math.max(0, Math.round(base * config.routine.streakRewardMultiplier));
    }

    public long duoBonusFor(int days) {
        return rewardFor(days) / 2;
    }

    /** ¿Seguiría viva la racha si se cumple hoy? (ayer contó, o anteayer y queda protector esta semana) */
    public boolean alive(RoutineState s, long day) {
        if (s.streakDays <= 0 || s.streakLastDay < 0) {
            return false;
        }
        long gap = day - s.streakLastDay;
        return gap <= 1 || (gap == 2 && s.shieldWeek != week(day));
    }

    /** Racha que se ve hoy (0 si ya se ha roto). */
    public int current(RoutineState s, long day) {
        return alive(s, day) ? s.streakDays : 0;
    }

    public boolean doneToday(RoutineState s, long day) {
        return s.streakLastDay == day;
    }

    public boolean shieldAvailable(RoutineState s, long day) {
        return s.shieldWeek != week(day);
    }

    /** ¿Han jugado hoy todos los miembros del dúo? */
    public boolean everyoneActive(Party party, long day) {
        RoutineState s = party.routine();
        return party.isDuo() && party.members().stream().allMatch(m -> s.activeDay.getOrDefault(m, -1L) == day);
    }

    /** Un miembro juega hoy (al entrar o al hacer algo). Puede desbloquear el bonus de dúo si la racha ya contó. */
    public Result onActive(Party party, UUID member, long day) {
        if (!enabled()) {
            return Result.NONE;
        }
        RoutineState s = party.routine();
        s.activeDay.put(member, day);
        long bonus = payDuoBonus(party, day);
        return bonus > 0 ? new Result(false, s.streakDays, 0, bonus, false, false) : Result.NONE;
    }

    /** El grupo ha reclamado una misión: cuenta el día de racha (una vez al día) y paga. */
    public Result onMissionClaimed(Party party, UUID member, long day) {
        if (!enabled()) {
            return Result.NONE;
        }
        RoutineState s = party.routine();
        s.activeDay.put(member, day);
        if (s.streakLastDay == day) {
            long bonus = payDuoBonus(party, day);
            return bonus > 0 ? new Result(false, s.streakDays, 0, bonus, false, false) : Result.NONE;
        }
        boolean shield = false;
        boolean broken = false;
        long gap = s.streakLastDay < 0 ? Long.MAX_VALUE : day - s.streakLastDay;
        if (gap == 1) {
            s.streakDays++;
        } else if (gap == 2 && s.streakDays > 0 && s.shieldWeek != week(day)) {
            s.streakDays++;
            s.shieldWeek = week(day);
            shield = true;
        } else {
            broken = s.streakDays > 0;
            s.streakDays = 1;
        }
        s.streakLastDay = day;
        s.streakBest = Math.max(s.streakBest, s.streakDays);
        long reward = rewardFor(s.streakDays);
        if (reward > 0 && economy.credit(party.wallet(), reward, "streak", String.valueOf(s.streakDays),
                "streak:" + party.id() + ":" + day) != EconomyService.Result.OK) {
            reward = 0;
        }
        long bonus = payDuoBonus(party, day);
        return new Result(true, s.streakDays, reward, bonus, shield, broken);
    }

    private long payDuoBonus(Party party, long day) {
        RoutineState s = party.routine();
        if (s.streakLastDay != day || s.duoPaidDay == day || !everyoneActive(party, day)) {
            return 0;
        }
        s.duoPaidDay = day;
        long bonus = duoBonusFor(s.streakDays);
        if (bonus > 0 && economy.credit(party.wallet(), bonus, "streak_duo", String.valueOf(s.streakDays),
                "streak_duo:" + party.id() + ":" + day) != EconomyService.Result.OK) {
            return 0;
        }
        return bonus;
    }
}
