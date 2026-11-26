package com.chicoestudioso.caricraft.core.cobbleverse;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.party.Party;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Caza del día: tres objetivos de captura iguales para todo el servidor que cambian a las 00:00. Cada objetivo
 * paga al completarse; completar los tres da un premio extra, y si los dos del dúo han aportado capturas, el
 * bonus de dúo. Con suficientes cazas completas en la semana llega un premio semanal. Solo decide; el servidor
 * paga (CobbleDollars a cada miembro y Carins a la cartera del dúo).
 */
public final class HuntService {
    public static final int TARGETS = 3;

    public enum Kind { SPECIES, TYPE, ANY }

    /** Objetivo: capturar {@code count} Pokémon de la especie o el tipo {@code value} (o cualesquiera). */
    public record Target(Kind kind, String value, int count) {
    }

    /** CobbleDollars para cada miembro y Carins para el dúo. */
    public record Payout(long dollars, long carins) {
        public static final Payout ZERO = new Payout(0, 0);

        Payout plus(Payout other) {
            return new Payout(dollars + other.dollars, carins + other.carins);
        }

        Payout times(double factor) {
            return new Payout(Math.round(dollars * factor), Math.round(carins * factor));
        }
    }

    /** Lo que ha cambiado con una captura. */
    public record Result(boolean counted, List<Integer> completed, boolean cleared, boolean duoBonus, boolean weekly, Payout payout) {
    }

    /** Especies comunes y fáciles de ver por el mundo (se filtran por las que existan en el servidor). */
    public static final List<String> SPECIES_POOL = List.of("pidgey", "rattata", "caterpie", "weedle", "spearow", "ekans", "pikachu",
            "sandshrew", "clefairy", "vulpix", "jigglypuff", "zubat", "oddish", "paras", "venonat", "diglett", "meowth", "psyduck",
            "mankey", "growlithe", "poliwag", "abra", "machop", "bellsprout", "tentacool", "geodude", "ponyta", "slowpoke",
            "magnemite", "doduo", "seel", "grimer", "shellder", "gastly", "onix", "drowzee", "krabby", "voltorb", "exeggcute",
            "cubone", "koffing", "rhyhorn", "horsea", "goldeen", "staryu", "magikarp", "eevee", "sentret", "hoothoot", "ledyba",
            "spinarak", "mareep", "hoppip", "sunkern", "wooper", "murkrow", "snubbull", "teddiursa", "slugma", "swinub",
            "houndour", "phanpy", "zigzagoon", "wurmple", "lotad", "seedot", "taillow", "wingull", "ralts", "surskit",
            "shroomish", "slakoth", "makuhita", "skitty", "aron", "electrike", "roselia", "gulpin", "numel", "spoink", "swablu",
            "barboach", "corphish", "baltoy", "shuppet", "duskull", "snorunt", "starly", "bidoof", "kricketot", "shinx", "budew",
            "buizel", "combee", "pachirisu", "buneary", "glameow", "stunky", "bronzor", "hippopotas", "skorupi", "croagunk");
    /** Tipos para el objetivo de tipo (los más fáciles de encontrar). */
    public static final List<String> TYPES = List.of("normal", "water", "grass", "bug", "flying", "rock", "ground", "poison",
            "electric", "fire", "fighting", "psychic");

    static final Payout SPECIES_REWARD = new Payout(1200, 15);
    static final Payout TYPE_REWARD = new Payout(1000, 12);
    static final Payout ANY_REWARD = new Payout(800, 10);
    public static final Payout CLEAR_REWARD = new Payout(1500, 20);
    public static final Payout DUO_REWARD = new Payout(2500, 30);
    public static final Payout WEEKLY_REWARD = new Payout(6000, 80);

    private final CaricraftConfig config;

    public HuntService(CaricraftConfig config) {
        this.config = config;
    }

    public boolean enabled() {
        return config.cobbleverse.enabled && config.cobbleverse.dailyHunt;
    }

    public int duoMinEach() {
        return config.cobbleverse.huntDuoMinEach;
    }

    public int weeklyClears() {
        return config.cobbleverse.huntWeeklyClears;
    }

    /** Objetivos del día, iguales para todos. {@code pool}: especies del grupo que existen en el servidor. */
    public static List<Target> targets(long day, List<String> pool) {
        Random random = new Random(day * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L);
        String species = pool.isEmpty() ? "pikachu" : pool.get(random.nextInt(pool.size()));
        String type = TYPES.get(random.nextInt(TYPES.size()));
        return List.of(new Target(Kind.SPECIES, species, 1), new Target(Kind.TYPE, type, 3 + random.nextInt(3)),
                new Target(Kind.ANY, "", 6 + random.nextInt(5)));
    }

    public static Payout reward(Kind kind) {
        return switch (kind) {
            case SPECIES -> SPECIES_REWARD;
            case TYPE -> TYPE_REWARD;
            case ANY -> ANY_REWARD;
        };
    }

    /** Multiplicador externo (eventos semanales). */
    private java.util.function.DoubleSupplier bonus = () -> 1.0;

    public void setBonus(java.util.function.DoubleSupplier bonus) {
        this.bonus = bonus == null ? () -> 1.0 : bonus;
    }

    public Payout scaled(Payout payout) {
        return payout.times(config.cobbleverse.huntRewardMultiplier * bonus.getAsDouble());
    }

    static long week(long day) {
        return Math.floorDiv(day + 3, 7);
    }

    /** Estado de la caza del dúo puesto al día (nuevo día o nueva semana). */
    public DuoPokemonState state(Party party, long day) {
        DuoPokemonState s = party.pokemon();
        if (s.huntDay != day) {
            s.huntDay = day;
            Arrays.fill(s.huntProgress, 0);
            Arrays.fill(s.huntPaid, false);
            s.huntCleared = false;
            s.huntDuoBonus = false;
            s.huntContrib.clear();
        }
        long week = week(day);
        if (s.huntWeek != week) {
            s.huntWeek = week;
            s.weekClears = 0;
            s.weekPaid = false;
        }
        return s;
    }

    static boolean matches(Target target, CaptureInfo info) {
        return switch (target.kind()) {
            case SPECIES -> target.value().equals(info.species());
            case TYPE -> info.types().contains(target.value());
            case ANY -> true;
        };
    }

    /** ¿Cumple el dúo la condición del bonus? Los dos han aportado al menos {@code huntDuoMinEach} capturas hoy. */
    public boolean duoBonusEarned(Party party, DuoPokemonState s) {
        return party.isDuo() && party.members().stream().allMatch(m -> s.huntContrib.getOrDefault(m, 0) >= duoMinEach());
    }

    /** Una captura de un miembro: avanza los objetivos que cumple y devuelve lo que hay que pagar. */
    public Result onCapture(Party party, UUID player, CaptureInfo info, long day, List<String> pool) {
        DuoPokemonState s = state(party, day);
        List<Target> targets = targets(day, pool);
        boolean counted = false;
        List<Integer> completed = new ArrayList<>();
        Payout payout = Payout.ZERO;
        for (int i = 0; i < TARGETS; i++) {
            Target target = targets.get(i);
            if (s.huntPaid[i] || !matches(target, info)) {
                continue;
            }
            counted = true;
            s.huntProgress[i]++;
            if (s.huntProgress[i] >= target.count()) {
                s.huntPaid[i] = true;
                completed.add(i);
                payout = payout.plus(reward(target.kind()));
            }
        }
        if (counted) {
            s.huntContrib.merge(player, 1, Integer::sum);
        }
        boolean cleared = false;
        boolean duo = false;
        boolean weekly = false;
        if (!s.huntCleared && allPaid(s)) {
            s.huntCleared = true;
            cleared = true;
            payout = payout.plus(CLEAR_REWARD);
            duo = duoBonusEarned(party, s);
            s.huntDuoBonus = duo;
            if (duo) {
                payout = payout.plus(DUO_REWARD);
            }
            // En un dúo, para la semanal solo cuentan las cazas que se han hecho entre los dos.
            if (duo || !party.isDuo()) {
                s.weekClears++;
                if (!s.weekPaid && s.weekClears >= weeklyClears()) {
                    s.weekPaid = true;
                    weekly = true;
                    payout = payout.plus(WEEKLY_REWARD);
                }
            }
        }
        return new Result(counted, completed, cleared, duo, weekly, scaled(payout));
    }

    private static boolean allPaid(DuoPokemonState s) {
        for (boolean paid : s.huntPaid) {
            if (!paid) {
                return false;
            }
        }
        return true;
    }
}
