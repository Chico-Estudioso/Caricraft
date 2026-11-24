package com.chicoestudioso.caricraft.core.mission;

/**
 * Objetivo de una misión.
 *
 * @param target vacío = cualquiera; si no, debe aparecer en las claves del {@link ProgressEvent}
 * @param amount cantidad necesaria (&gt; 0)
 */
public record Objective(ObjectiveType type, String target, long amount) {
    public Objective {
        target = target == null ? "" : target.strip();
    }

    public boolean matches(ProgressEvent event) {
        return event.type() == type && (target.isEmpty() || event.keys().contains(target));
    }
}
