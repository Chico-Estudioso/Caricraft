package com.chicoestudioso.caricraft.server;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionRateLimiterTest {
    @Test
    void limitsActionsPerSecondAndPlayer() {
        AtomicLong now = new AtomicLong(10_000);
        ActionRateLimiter limiter = new ActionRateLimiter(3, now::get);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(a));
        assertTrue(limiter.tryAcquire(a));
        assertTrue(limiter.tryAcquire(a));
        assertFalse(limiter.tryAcquire(a), "la cuarta acción en el mismo segundo se descarta");
        assertTrue(limiter.tryAcquire(b), "otro jugador tiene su propio cupo");
        now.addAndGet(1000);
        assertTrue(limiter.tryAcquire(a), "el cupo se renueva cada segundo");
    }
}
