package com.chicoestudioso.caricraft.core.routine;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.mission.MissionDefinition;
import com.chicoestudioso.caricraft.core.mission.ObjectiveType;
import com.chicoestudioso.caricraft.core.party.Party;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Eventos semanales: cada semana (de lunes a domingo, en la zona horaria del servidor) hay un evento con una
 * ventaja (más Carins en ciertas misiones, más experiencia del desafío…) y una meta del dúo con dos premios: a la
 * mitad y al completarla. El evento es el mismo para todos y se elige en rotación.
 */
public final class WeeklyEventService {
    /** Ventaja del evento. */
    public enum Effect { MISSIONS, CHALLENGE_XP, BIOMES, HUNT }

    /**
     * @param stat       estadística del grupo que mide la meta
     * @param objectives misiones con estos objetivos dan más Carins (efecto {@code MISSIONS})
     * @param requires   {@code null}, {@code "challenge"} o {@code "cobblemon"}
     */
    public record Event(String id, String icon, String stat, long goal, Effect effect, Set<ObjectiveType> objectives, String requires) {
    }

    public static final List<Event> EVENTS = List.of(
            new Event("miners", "minecraft:diamond_pickaxe", "blocks_mined", 1_500, Effect.MISSIONS, Set.of(ObjectiveType.MINE_BLOCK), null),
            new Event("challenge", "minecraft:skeleton_skull", "challenge_kills", 150, Effect.CHALLENGE_XP, Set.of(), "challenge"),
            new Event("explorers", "minecraft:compass", "distance_m", 25_000, Effect.BIOMES,
                    Set.of(ObjectiveType.TRAVEL, ObjectiveType.VISIT_BIOME, ObjectiveType.DISCOVER_BIOME), null),
            new Event("trainers", "cobblemon:poke_ball", "pokemon_caught", 40, Effect.HUNT,
                    Set.of(ObjectiveType.CATCH_POKEMON, ObjectiveType.EVOLVE_POKEMON, ObjectiveType.WIN_BATTLE), "cobblemon"),
            new Event("hunters", "minecraft:iron_sword", "mobs_defeated", 250, Effect.MISSIONS, Set.of(ObjectiveType.KILL_ENTITY), null),
            new Event("crafters", "minecraft:crafting_table", "items_crafted", 600, Effect.MISSIONS, Set.of(ObjectiveType.CRAFT_ITEM), null));

    /** Carins por llegar a la mitad y por completar la meta. */
    public static final long HALF_REWARD = 100;
    public static final long FULL_REWARD = 300;
    public static final String STAT_COMPLETED = "weekly_events";

    private final CaricraftConfig config;
    private final EconomyService economy;
    private Predicate<String> available = feature -> false;

    public WeeklyEventService(CaricraftConfig config, EconomyService economy) {
        this.config = config;
        this.economy = economy;
    }

    /** Qué partes opcionales hay en el servidor ({@code "cobblemon"}); la del desafío sale de la configuración. */
    public void setAvailable(Predicate<String> available) {
        this.available = available == null ? feature -> false : available;
    }

    public boolean enabled() {
        return config.routine.weeklyEvents;
    }

    private boolean usable(Event event) {
        if (event.requires() == null) {
            return true;
        }
        if ("challenge".equals(event.requires())) {
            return config.challenge.enabled;
        }
        return available.test(event.requires());
    }

    public List<Event> rotation() {
        List<Event> list = new ArrayList<>();
        for (Event event : EVENTS) {
            if (usable(event)) {
                list.add(event);
            }
        }
        return list;
    }

    /** Evento de esa semana, o {@code null} si están desactivados. */
    public Event eventOf(long week) {
        if (!enabled()) {
            return null;
        }
        List<Event> list = rotation();
        return list.isEmpty() ? null : list.get((int) Math.floorMod(week, list.size()));
    }

    public long goal(Event event) {
        return event.goal();
    }

    public long halfReward() {
        return Math.max(0, Math.round(HALF_REWARD * config.routine.weeklyRewardMultiplier));
    }

    public long fullReward() {
        return Math.max(0, Math.round(FULL_REWARD * config.routine.weeklyRewardMultiplier));
    }

    /** Pone al día el evento del grupo (nueva semana: se toma la estadística de partida) y devuelve el progreso. */
    public long progress(Party party, long week) {
        Event event = eventOf(week);
        if (event == null) {
            return 0;
        }
        RoutineState s = party.routine();
        long value = party.stats().getOrDefault(event.stat(), 0L);
        if (s.eventWeek != week) {
            s.eventWeek = week;
            s.eventBaseline = value;
            s.eventPaid = 0;
        }
        return Math.max(0, value - s.eventBaseline);
    }

    /**
     * Cobra los tramos alcanzados. Devuelve 0 si no hay nada nuevo, 1 si se acaba de llegar a la mitad, 2 si se
     * acaba de completar (paga también la mitad si no se había cobrado).
     */
    public int check(Party party, long week) {
        Event event = eventOf(week);
        if (event == null) {
            return 0;
        }
        long progress = progress(party, week);
        RoutineState s = party.routine();
        int reached = 0;
        if (progress * 2 >= event.goal() && (s.eventPaid & 1) == 0) {
            s.eventPaid |= 1;
            economy.credit(party.wallet(), halfReward(), "weekly_event", event.id() + ":half", "weekly:" + party.id() + ":" + week + ":1");
            reached = 1;
        }
        if (progress >= event.goal() && (s.eventPaid & 2) == 0) {
            s.eventPaid |= 2;
            economy.credit(party.wallet(), fullReward(), "weekly_event", event.id(), "weekly:" + party.id() + ":" + week + ":2");
            party.incrementStat(STAT_COMPLETED, 1);
            reached = 2;
        }
        return reached;
    }

    // ------------------------------------------------------------------ ventajas

    private double bonus() {
        return config.routine.weeklyBonus;
    }

    /** Multiplicador de los Carins de una misión esta semana. */
    public double missionMultiplier(MissionDefinition def, long week) {
        Event event = eventOf(week);
        if (event == null || event.objectives().isEmpty()
                || def.objectives().stream().noneMatch(o -> event.objectives().contains(o.type()))) {
            return 1.0;
        }
        return bonus();
    }

    public double challengeXpMultiplier(long week) {
        Event event = eventOf(week);
        return event != null && event.effect() == Effect.CHALLENGE_XP ? bonus() : 1.0;
    }

    public double biomeMultiplier(long week) {
        Event event = eventOf(week);
        return event != null && event.effect() == Effect.BIOMES ? 2.0 : 1.0;
    }

    public double huntMultiplier(long week) {
        Event event = eventOf(week);
        return event != null && event.effect() == Effect.HUNT ? bonus() : 1.0;
    }
}
