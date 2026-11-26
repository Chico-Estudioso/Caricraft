package com.chicoestudioso.caricraft.core.cobbleverse;

/**
 * Apariciones según el level cap (el de los entrenadores de RCT en Cobbleverse): un Pokémon salvaje que se pasa
 * poco del cap baja al cap; si se pasa mucho, no aparece. Los legendarios, singulares y ultraentes no aparecen hasta
 * que el cap llega a un mínimo.
 */
public final class LevelCapRule {
    /** Qué hacer con una aparición: {@code level} es el nivel final si {@code spawn}. */
    public record Decision(boolean spawn, int level) {
        public static Decision cancel() {
            return new Decision(false, 0);
        }
    }

    private LevelCapRule() {
    }

    /**
     * @param level           nivel con el que iba a aparecer
     * @param legendary       legendario, singular o ultraente
     * @param cap             level cap más bajo de los jugadores cercanos
     * @param margin          niveles por encima del cap que todavía se corrigen bajándolo (si se pasa más, se cancela)
     * @param legendaryMinCap cap mínimo para que aparezcan legendarios
     */
    public static Decision decide(int level, boolean legendary, int cap, int margin, int legendaryMinCap) {
        if (cap <= 0) {
            return new Decision(true, level);
        }
        if (legendary && cap < legendaryMinCap) {
            return Decision.cancel();
        }
        if (level <= cap) {
            return new Decision(true, level);
        }
        // Los legendarios ya desbloqueados nunca se cancelan por nivel: salen al nivel del cap.
        if (legendary || level - cap <= margin) {
            return new Decision(true, cap);
        }
        return Decision.cancel();
    }
}
