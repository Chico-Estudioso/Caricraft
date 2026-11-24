package com.chicoestudioso.caricraft.core.mission;

import java.util.List;

/**
 * Definición declarativa de una misión (JSON).
 *
 * @param requires          misiones que deben haberse reclamado antes
 * @param requiresApp       app del catálogo necesaria (o {@code null})
 * @param requiresMod       mod que debe estar cargado (p. ej. {@code cobblemon}) o {@code null}
 * @param requireAllMembers en cooperativas, todos los miembros deben contribuir
 * @param title             texto literal si no hay traducción (misiones personalizadas)
 */
public record MissionDefinition(String id, MissionScope scope, MissionCategory category, long reward,
                                List<Objective> objectives, List<String> requires, String requiresApp,
                                String requiresMod, boolean requireAllMembers, String icon, String title,
                                String description) {
    public MissionDefinition {
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
        requires = requires == null ? List.of() : List.copyOf(requires);
        icon = icon == null || icon.isBlank() ? "minecraft:paper" : icon;
    }

    public String stateKey(java.util.UUID player) {
        return scope.shared() ? id : id + "@" + player;
    }
}
