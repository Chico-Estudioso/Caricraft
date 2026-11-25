package com.chicoestudioso.caricraft.core.mission;

import java.util.List;

/**
 * Definición declarativa de una misión (JSON).
 *
 * @param requires          misiones que deben haberse reclamado antes
 * @param requiresApp       app del catálogo necesaria (o {@code null})
 * @param requiresMod       mod que debe estar cargado (p. ej. {@code cobblemon}) o {@code null}
 * @param requiresFeature   función del servidor que debe estar activa (p. ej. {@code challenge}) o {@code null}
 * @param requireAllMembers en cooperativas, todos los miembros deben contribuir
 * @param title             texto literal si no hay traducción (misiones personalizadas)
 */
public record MissionDefinition(String id, MissionScope scope, MissionCategory category, long reward,
                                List<Objective> objectives, List<String> requires, String requiresApp,
                                String requiresMod, boolean requireAllMembers, String icon, String title,
                                String description, String requiresFeature) {
    public MissionDefinition {
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
        requires = requires == null ? List.of() : List.copyOf(requires);
        icon = icon == null || icon.isBlank() ? "minecraft:paper" : icon;
    }

    /** Constructor sin función requerida. */
    public MissionDefinition(String id, MissionScope scope, MissionCategory category, long reward,
                             List<Objective> objectives, List<String> requires, String requiresApp,
                             String requiresMod, boolean requireAllMembers, String icon, String title,
                             String description) {
        this(id, scope, category, reward, objectives, requires, requiresApp, requiresMod, requireAllMembers, icon,
                title, description, null);
    }

    public String stateKey(java.util.UUID player) {
        return scope.shared() ? id : id + "@" + player;
    }
}
