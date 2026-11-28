package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.cleaner.CleanerSchedule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanerScheduleTest {
    private static final long MIN = 60_000L;

    @Test
    void cleansEveryIntervalAndWarnsBefore() {
        CleanerSchedule schedule = new CleanerSchedule();
        schedule.schedule(0, 15 * MIN);
        assertFalse(schedule.due(14 * MIN));
        assertEquals(60, CleanerSchedule.warningFor(14 * MIN, schedule.nextAt(), 0));
        assertEquals(0, CleanerSchedule.warningFor(14 * MIN, schedule.nextAt(), 60), "no se repite");
        assertEquals(0, CleanerSchedule.warningFor(14 * MIN + 10_000, schedule.nextAt(), 60), "a 50 s no hay aviso");
        assertEquals(10, CleanerSchedule.warningFor(15 * MIN - 10_000, schedule.nextAt(), 30));
        assertEquals(1, CleanerSchedule.warningFor(15 * MIN - 1_000, schedule.nextAt(), 2));
        assertTrue(schedule.due(15 * MIN));
        schedule.schedule(15 * MIN, 10 * MIN);
        assertEquals(25 * MIN, schedule.nextAt());
    }

    @Test
    void deathZonesProtectItemsForAWhile() {
        CleanerSchedule schedule = new CleanerSchedule();
        schedule.protectDeath("minecraft:overworld", 100, 64, -20, 15 * MIN);
        assertTrue(schedule.protectedAt("minecraft:overworld", 103, 64, -18, 8, 5 * MIN));
        assertFalse(schedule.protectedAt("minecraft:overworld", 120, 64, -20, 8, 5 * MIN), "lejos");
        assertFalse(schedule.protectedAt("minecraft:the_nether", 100, 64, -20, 8, 5 * MIN), "otra dimensión");
        assertFalse(schedule.protectedAt("minecraft:overworld", 100, 64, -20, 8, 16 * MIN), "caducada");
        schedule.purge(16 * MIN);
        assertTrue(schedule.zones().isEmpty());
    }
}
