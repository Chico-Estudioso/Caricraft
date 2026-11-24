package com.chicoestudioso.caricraft.core;

import java.util.Arrays;
import java.util.List;

/**
 * Resultado de una acción del dominio. {@code key} es una clave de traducción
 * ({@code caricraft.result.*}) y {@code args} sus argumentos.
 */
public record ActionResult(boolean success, String key, List<String> args) {
    public ActionResult {
        args = List.copyOf(args);
    }

    public static ActionResult ok(String key, Object... args) {
        return new ActionResult(true, "caricraft.result." + key, toStrings(args));
    }

    public static ActionResult fail(String key, Object... args) {
        return new ActionResult(false, "caricraft.result." + key, toStrings(args));
    }

    private static List<String> toStrings(Object[] args) {
        return Arrays.stream(args).map(String::valueOf).toList();
    }
}
