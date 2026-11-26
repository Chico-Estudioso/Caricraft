package com.chicoestudioso.caricraft.core.cobbleverse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lo Pokémon del dúo que guarda Caricraft: la caza del día (progreso y quién aporta) y la Pokédex del dúo
 * (variocolores, capturas raras, legendarios, especies distintas y metas cumplidas). No sustituye a la Pokédex de
 * Cobblemon: solo lleva lo que se presume en el dúo.
 */
public final class DuoPokemonState {
    static final int MAX_SHINIES = 60;
    static final int MAX_RARES = 12;
    static final int MAX_LEGENDS = 30;

    /** Una captura destacada. {@code catchRate}: 3 (rarísimo) a 255 (muy común). */
    public record DexEntry(String species, String player, int level, boolean shiny, int catchRate, long time) {
    }

    // caza del día
    long huntDay = -1;
    final int[] huntProgress = new int[HuntService.TARGETS];
    final boolean[] huntPaid = new boolean[HuntService.TARGETS];
    boolean huntCleared;
    boolean huntDuoBonus;
    final Map<UUID, Integer> huntContrib = new LinkedHashMap<>();
    long huntWeek = -1;
    int weekClears;
    boolean weekPaid;

    // Pokédex del dúo
    final List<DexEntry> shinies = new ArrayList<>();
    final List<DexEntry> rares = new ArrayList<>();
    final List<DexEntry> legends = new ArrayList<>();
    final Set<String> species = new LinkedHashSet<>();
    final Map<String, Integer> typeCounts = new LinkedHashMap<>();
    long captures;
    final Set<String> goalsDone = new LinkedHashSet<>();

    public long huntDay() {
        return huntDay;
    }

    public int huntProgress(int target) {
        return huntProgress[target];
    }

    public boolean huntPaid(int target) {
        return huntPaid[target];
    }

    public boolean huntCleared() {
        return huntCleared;
    }

    public boolean huntDuoBonus() {
        return huntDuoBonus;
    }

    public Map<UUID, Integer> huntContrib() {
        return huntContrib;
    }

    public long huntWeek() {
        return huntWeek;
    }

    public int weekClears() {
        return weekClears;
    }

    public boolean weekPaid() {
        return weekPaid;
    }

    public List<DexEntry> shinies() {
        return shinies;
    }

    public List<DexEntry> rares() {
        return rares;
    }

    public List<DexEntry> legends() {
        return legends;
    }

    public Set<String> species() {
        return species;
    }

    public Map<String, Integer> typeCounts() {
        return typeCounts;
    }

    public long captures() {
        return captures;
    }

    public Set<String> goalsDone() {
        return goalsDone;
    }

    // ------------------------------------------------------------------ disco

    /** Solo para cargar desde disco. */
    public void restoreHunt(long day, int[] progress, boolean[] paid, boolean cleared, boolean duoBonus, Map<UUID, Integer> contrib,
                            long week, int clears, boolean weekPaid) {
        this.huntDay = day;
        for (int i = 0; i < HuntService.TARGETS; i++) {
            this.huntProgress[i] = i < progress.length ? Math.max(0, progress[i]) : 0;
            this.huntPaid[i] = i < paid.length && paid[i];
        }
        this.huntCleared = cleared;
        this.huntDuoBonus = duoBonus;
        this.huntContrib.clear();
        this.huntContrib.putAll(contrib);
        this.huntWeek = week;
        this.weekClears = Math.max(0, clears);
        this.weekPaid = weekPaid;
    }

    /** Solo para cargar desde disco. */
    public void restoreDex(List<DexEntry> shinies, List<DexEntry> rares, List<DexEntry> legends, Set<String> species,
                           Map<String, Integer> typeCounts, long captures, Set<String> goalsDone) {
        this.shinies.clear();
        this.shinies.addAll(shinies);
        this.rares.clear();
        this.rares.addAll(rares);
        this.legends.clear();
        this.legends.addAll(legends);
        this.species.clear();
        this.species.addAll(species);
        this.typeCounts.clear();
        this.typeCounts.putAll(typeCounts);
        this.captures = Math.max(0, captures);
        this.goalsDone.clear();
        this.goalsDone.addAll(goalsDone);
    }

    // ------------------------------------------------------------------ dúos

    /** Al formar un dúo: se suma la Pokédex del otro (la caza del día sigue siendo la del grupo que recibe). */
    public void mergeFrom(DuoPokemonState other) {
        addAllNewestFirst(shinies, other.shinies, MAX_SHINIES);
        addAllNewestFirst(legends, other.legends, MAX_LEGENDS);
        for (DexEntry rare : other.rares) {
            addRare(rare);
        }
        species.addAll(other.species);
        other.typeCounts.forEach((type, count) -> typeCounts.merge(type, count, Integer::sum));
        captures += other.captures;
        goalsDone.addAll(other.goalsDone);
    }

    /** Al deshacer un dúo, quien se va se lleva una copia de la Pokédex del dúo (no la caza del día). */
    public void copyDexFrom(DuoPokemonState other) {
        restoreDex(other.shinies, other.rares, other.legends, other.species, other.typeCounts, other.captures, other.goalsDone);
    }

    private static void addAllNewestFirst(List<DexEntry> into, List<DexEntry> from, int max) {
        into.addAll(from);
        into.sort(Comparator.comparingLong(DexEntry::time).reversed());
        while (into.size() > max) {
            into.removeLast();
        }
    }

    /** Las más raras primero (menor ratio de captura); a igualdad, la más antigua. */
    void addRare(DexEntry entry) {
        rares.add(entry);
        rares.sort(Comparator.comparingInt(DexEntry::catchRate).thenComparingLong(DexEntry::time));
        while (rares.size() > MAX_RARES) {
            rares.removeLast();
        }
    }
}
