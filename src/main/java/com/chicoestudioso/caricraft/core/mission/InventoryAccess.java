package com.chicoestudioso.caricraft.core.mission;

/** Acceso mínimo al inventario de un jugador para las entregas. Lo implementa el servidor. */
@FunctionalInterface
public interface InventoryAccess {
    /**
     * Quita hasta {@code max} unidades que coincidan con {@code target} (id de ítem o {@code #tag}).
     *
     * @return unidades quitadas realmente
     */
    long remove(String target, long max);
}
