package com.chicoestudioso.caricraft.core.cobbleverse;

import com.chicoestudioso.caricraft.core.party.Party;

import java.util.ArrayList;
import java.util.List;

/**
 * Pokédex del dúo: no repite la Pokédex de Cobblemon. Guarda lo que se presume entre los dos (variocolores,
 * capturas más raras, legendarios, especies distintas entre ambos, capturas por tipo) y unas metas compartidas
 * con premio. Solo decide; el servidor paga.
 */
public final class DuoDexService {
    /** Ratio de captura a partir del cual una captura entra en «las más raras». */
    public static final int RARE_CATCH_RATE = 45;

    public enum GoalKind { SPECIES, CAPTURES, SHINIES, LEGENDS, TYPES, TYPE }

    /**
     * Meta compartida del dúo.
     *
     * @param type tipo para {@link GoalKind#TYPE}
     */
    public record Goal(String id, GoalKind kind, String type, int count, long carins, long dollars) {
    }

    public static final List<Goal> GOALS = List.of(
            new Goal("species_25", GoalKind.SPECIES, "", 25, 50, 2_000),
            new Goal("species_75", GoalKind.SPECIES, "", 75, 120, 5_000),
            new Goal("species_150", GoalKind.SPECIES, "", 150, 250, 10_000),
            new Goal("species_300", GoalKind.SPECIES, "", 300, 500, 25_000),
            new Goal("captures_100", GoalKind.CAPTURES, "", 100, 60, 3_000),
            new Goal("captures_500", GoalKind.CAPTURES, "", 500, 200, 12_000),
            new Goal("shinies_1", GoalKind.SHINIES, "", 1, 40, 3_000),
            new Goal("shinies_5", GoalKind.SHINIES, "", 5, 150, 12_000),
            new Goal("shinies_15", GoalKind.SHINIES, "", 15, 400, 30_000),
            new Goal("legends_1", GoalKind.LEGENDS, "", 1, 80, 6_000),
            new Goal("legends_5", GoalKind.LEGENDS, "", 5, 250, 20_000),
            new Goal("types_all", GoalKind.TYPES, "", 18, 200, 15_000),
            new Goal("type_water", GoalKind.TYPE, "water", 40, 60, 3_000),
            new Goal("type_fire", GoalKind.TYPE, "fire", 25, 60, 3_000),
            new Goal("type_grass", GoalKind.TYPE, "grass", 40, 60, 3_000),
            new Goal("type_electric", GoalKind.TYPE, "electric", 25, 60, 3_000),
            new Goal("type_ghost", GoalKind.TYPE, "ghost", 15, 80, 4_000),
            new Goal("type_dragon", GoalKind.TYPE, "dragon", 10, 120, 6_000));

    public static int progress(DuoPokemonState s, Goal goal) {
        long value = switch (goal.kind()) {
            case SPECIES -> s.species.size();
            case CAPTURES -> s.captures;
            case SHINIES -> s.shinies.size();
            case LEGENDS -> s.legends.size();
            case TYPES -> s.typeCounts.size();
            case TYPE -> s.typeCounts.getOrDefault(goal.type(), 0);
        };
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    /**
     * Apunta una captura y devuelve las metas que acaba de cumplir el dúo.
     *
     * @param playerName quien la captura (se guarda tal cual)
     */
    public List<Goal> onCapture(Party party, String playerName, CaptureInfo info, long now) {
        DuoPokemonState s = party.pokemon();
        s.captures++;
        s.species.add(info.species());
        for (String type : info.types()) {
            s.typeCounts.merge(type, 1, Integer::sum);
        }
        DuoPokemonState.DexEntry entry = new DuoPokemonState.DexEntry(info.species(), playerName, info.level(), info.shiny(),
                info.catchRate(), now);
        if (info.shiny()) {
            s.shinies.addFirst(entry);
            while (s.shinies.size() > DuoPokemonState.MAX_SHINIES) {
                s.shinies.removeLast();
            }
        }
        if (info.legendary()) {
            s.legends.addFirst(entry);
            while (s.legends.size() > DuoPokemonState.MAX_LEGENDS) {
                s.legends.removeLast();
            }
        }
        if (info.catchRate() <= RARE_CATCH_RATE) {
            s.addRare(entry);
        }
        List<Goal> done = new ArrayList<>();
        for (Goal goal : GOALS) {
            if (!s.goalsDone.contains(goal.id()) && progress(s, goal) >= goal.count()) {
                s.goalsDone.add(goal.id());
                done.add(goal);
            }
        }
        return done;
    }
}
