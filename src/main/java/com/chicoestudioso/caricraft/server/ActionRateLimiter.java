package com.chicoestudioso.caricraft.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Límite simple de acciones por segundo y jugador (ventana fija de 1 s). */
public final class ActionRateLimiter {
    private final Map<UUID, long[]> windows = new HashMap<>();
    private final LongSupplier clock;
    private final int perSecond;

    public ActionRateLimiter(int perSecond, LongSupplier clock) {
        this.perSecond = perSecond;
        this.clock = clock;
    }

    /** @return {@code true} si la acción se permite */
    public boolean tryAcquire(UUID player) {
        long second = clock.getAsLong() / 1000L;
        long[] window = windows.computeIfAbsent(player, id -> new long[]{second, 0});
        if (window[0] != second) {
            window[0] = second;
            window[1] = 0;
        }
        if (window[1] >= perSecond) {
            return false;
        }
        window[1]++;
        return true;
    }

    public void forget(UUID player) {
        windows.remove(player);
    }
}
