package com.chicoestudioso.caricraft.core.challenge;

/** Cambio de estado del Desafío Dúo que el servidor debe anunciar. */
public record ChallengeEvent(Kind kind, boolean forced, int level, int kills, long carins, long nightBonus, int previousLevel) {
    public ChallengeEvent(Kind kind, boolean forced, int level, int kills, long carins, long nightBonus) {
        this(kind, forced, level, kills, carins, nightBonus, level);
    }

    /** ¿Ha subido el nivel con este evento? */
    public boolean leveledUp() {
        return level > previousLevel;
    }

    public enum Kind {
        /** Empieza la noche de desafío. */
        STARTED,
        /** Amanece y el dúo ha sobrevivido. */
        SURVIVED,
        /** Terminó sin contar como superada (desactivado, forzado por un admin o noche perdida sin jugar). */
        ENDED
    }
}
