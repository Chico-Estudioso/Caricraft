package com.chicoestudioso.caricraft.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaricraftConfigTest {
    @Test
    void oldFilesMigrateOnlyUntouchedDefaults(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(CaricraftConfig.FILE_NAME),
                "{\"challenge\": {\"maxLevel\": 5, \"nightsPerLevel\": 2, \"killReward\": 7}}");
        CaricraftConfig config = CaricraftConfig.loadOrCreate(dir);
        assertEquals(30, config.challenge.maxLevel);
        assertEquals(1, config.challenge.nightsPerLevel);
        assertEquals(7, config.challenge.killReward, "lo ajustado a mano se respeta");
        assertTrue(Files.readString(dir.resolve(CaricraftConfig.FILE_NAME)).contains("\"configVersion\": 2"));
    }

    @Test
    void customValuesInOldFilesAreKept(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(CaricraftConfig.FILE_NAME), "{\"challenge\": {\"maxLevel\": 8, \"nightsPerLevel\": 3}}");
        CaricraftConfig config = CaricraftConfig.loadOrCreate(dir);
        assertEquals(8, config.challenge.maxLevel);
        assertEquals(3, config.challenge.nightsPerLevel);
    }

    @Test
    void currentFilesAreNotTouched(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(CaricraftConfig.FILE_NAME),
                "{\"configVersion\": 2, \"challenge\": {\"maxLevel\": 5, \"nightsPerLevel\": 2}}");
        CaricraftConfig config = CaricraftConfig.loadOrCreate(dir);
        assertEquals(5, config.challenge.maxLevel, "con la versión actual, 5 es una elección del admin");
    }

    @Test
    void outOfRangeValuesAreClamped(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(CaricraftConfig.FILE_NAME),
                "{\"configVersion\": 2, \"maxPartySize\": 9, \"challenge\": {\"intervalNights\": 0, \"maxLevel\": 500}}");
        CaricraftConfig config = CaricraftConfig.loadOrCreate(dir);
        assertEquals(2, config.maxPartySize);
        assertEquals(1, config.challenge.intervalNights);
        assertEquals(100, config.challenge.maxLevel);
    }
}
