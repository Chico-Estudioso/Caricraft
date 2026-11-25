package com.chicoestudioso.caricraft.core.camp;

import java.util.UUID;

/**
 * Petición de viaje entre miembros del dúo, al estilo de /tpa: no se viaja hasta que el otro acepta.
 *
 * @param kind {@link Kind#GO}: {@code from} quiere ir hacia {@code to}; {@link Kind#BRING}: {@code from} quiere
 *             traer a {@code to} hasta él
 */
public record TravelRequest(UUID from, UUID to, Kind kind, long expiresAt) {
    public enum Kind { GO, BRING }

    /** Quién se teletransporta. */
    public UUID traveler() {
        return kind == Kind.GO ? from : to;
    }

    /** Junto a quién aparece. */
    public UUID destination() {
        return kind == Kind.GO ? to : from;
    }

    public boolean expired(long now) {
        return now >= expiresAt;
    }
}
