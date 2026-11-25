package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.tips.TipRotation;
import com.chicoestudioso.caricraft.core.tips.TipRotation.TipContext;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TipRotationTest {
    private static final TipContext SOLO = new TipContext(false, Set.of(), true, false);

    @Test
    void noRepeatsUntilEveryTipWasShown() {
        TipRotation rotation = new TipRotation(new Random(7));
        long eligible = TipRotation.ALL.stream().filter(t -> t.condition().test(SOLO)).count();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < eligible; i++) {
            assertTrue(seen.add(rotation.next(SOLO).orElseThrow()), "no se repite dentro de una vuelta");
        }
        assertEquals(eligible, seen.size());
        assertTrue(rotation.next(SOLO).isPresent(), "empieza otra vuelta");
    }

    @Test
    void onlyRelevantTipsAreGiven() {
        TipRotation rotation = new TipRotation(new Random(3));
        TipContext duoWithTpDuo = new TipContext(true, Set.of("tpduo"), false, false);
        for (int i = 0; i < 60; i++) {
            String tip = rotation.next(duoWithTpDuo).orElseThrow();
            assertFalse(tip.equals("tpduo_buy") || tip.equals("duo_invite"), tip);
            assertFalse(tip.startsWith("challenge"), "desafío desactivado: " + tip);
            assertFalse(tip.equals("cobblemon"), "sin Cobblemon");
        }
    }

    @Test
    void emptyWhenNothingApplies() {
        TipRotation rotation = new TipRotation(List.of(new TipRotation.Tip("x", c -> false)), new Random(1));
        assertTrue(rotation.next(SOLO).isEmpty());
    }
}
