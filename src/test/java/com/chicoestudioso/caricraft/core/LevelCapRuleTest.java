package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.cobbleverse.LevelCapRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelCapRuleTest {
    @Test
    void underTheCapIsUntouched() {
        LevelCapRule.Decision d = LevelCapRule.decide(18, false, 20, 5, 60);
        assertTrue(d.spawn());
        assertEquals(18, d.level());
    }

    @Test
    void slightlyOverIsClampedAndFarOverIsCancelled() {
        LevelCapRule.Decision near = LevelCapRule.decide(25, false, 20, 5, 60);
        assertTrue(near.spawn());
        assertEquals(20, near.level());
        assertFalse(LevelCapRule.decide(26, false, 20, 5, 60).spawn());
        assertFalse(LevelCapRule.decide(55, false, 20, 5, 60).spawn());
    }

    @Test
    void legendariesWaitForTheMinimumCap() {
        assertFalse(LevelCapRule.decide(70, true, 59, 5, 60).spawn());
        LevelCapRule.Decision unlocked = LevelCapRule.decide(84, true, 60, 5, 60);
        assertTrue(unlocked.spawn());
        assertEquals(60, unlocked.level());
        assertEquals(70, LevelCapRule.decide(70, true, 100, 5, 60).level());
    }

    @Test
    void unknownCapChangesNothing() {
        LevelCapRule.Decision d = LevelCapRule.decide(90, true, 0, 5, 60);
        assertTrue(d.spawn());
        assertEquals(90, d.level());
    }
}
