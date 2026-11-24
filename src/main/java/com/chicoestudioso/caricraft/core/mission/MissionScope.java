package com.chicoestudioso.caricraft.core.mission;

public enum MissionScope {
    /** Cada miembro tiene su propio progreso. */
    INDIVIDUAL,
    /** Progreso compartido; requiere un dúo completo. */
    COOP,
    /** Progreso compartido del grupo, tenga uno o dos miembros (p. ej. hitos). */
    PARTY;

    public boolean shared() {
        return this != INDIVIDUAL;
    }
}
