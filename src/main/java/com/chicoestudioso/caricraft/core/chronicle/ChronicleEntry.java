package com.chicoestudioso.caricraft.core.chronicle;

import java.util.List;
import java.util.UUID;

/**
 * Entrada del diario compartido. {@code type} se traduce en cliente como {@code caricraft.chronicle.<type>}
 * con {@code args}. {@code actor} puede ser {@code null} para entradas del sistema.
 */
public record ChronicleEntry(long time, String type, UUID actor, String actorName, List<String> args) {
    public static final String PARTY_FORMED = "party_formed";
    public static final String PARTY_SPLIT = "party_split";
    public static final String MISSION_COMPLETED = "mission_completed";
    public static final String APP_PURCHASED = "app_purchased";
    public static final String BIOME_DISCOVERED = "biome_discovered";
    public static final String FIRST_CATCH = "first_catch";
    public static final String SHINY_CATCH = "shiny_catch";
    public static final String LEGENDARY_CATCH = "legendary_catch";
    public static final String EVOLUTION = "evolution";
    public static final String NOTE = "note";
    public static final String WELCOME = "welcome";
    public static final String CHALLENGE_SURVIVED = "challenge_survived";
    public static final String CAMP_SET = "camp_set";

    public ChronicleEntry {
        args = List.copyOf(args);
        actorName = actorName == null ? "" : actorName;
    }
}
