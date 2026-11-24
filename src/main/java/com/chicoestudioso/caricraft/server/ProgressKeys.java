package com.chicoestudioso.caricraft.server;

import net.minecraft.core.Holder;

import java.util.HashSet;
import java.util.Set;

/** Construye las claves de un {@link com.chicoestudioso.caricraft.core.mission.ProgressEvent}: id + tags con {@code #}. */
public final class ProgressKeys {
    private ProgressKeys() {
    }

    public static <T> Set<String> of(Holder<T> holder) {
        Set<String> keys = new HashSet<>();
        holder.unwrapKey().ifPresent(key -> keys.add(key.location().toString()));
        holder.tags().forEach(tag -> keys.add("#" + tag.location()));
        return keys;
    }
}
