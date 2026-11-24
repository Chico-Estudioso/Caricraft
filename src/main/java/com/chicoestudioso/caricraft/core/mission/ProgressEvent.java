package com.chicoestudioso.caricraft.core.mission;

import java.util.Set;

/**
 * Evento de progreso neutral (sin clases de Minecraft). {@code keys} contiene todo lo que identifica el
 * evento: el id del ítem/bloque/entidad/especie, sus tags con prefijo {@code #} y marcas como {@code shiny}.
 */
public record ProgressEvent(ObjectiveType type, Set<String> keys, long amount) {
    public ProgressEvent {
        keys = Set.copyOf(keys);
    }

    public static ProgressEvent of(ObjectiveType type, long amount, String... keys) {
        return new ProgressEvent(type, Set.of(keys), amount);
    }
}
