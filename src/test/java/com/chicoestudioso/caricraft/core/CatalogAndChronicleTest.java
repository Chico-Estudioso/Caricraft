package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleService;
import com.chicoestudioso.caricraft.core.mission.MissionCatalog;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogAndChronicleTest {
    @Test
    void defaultMissionsAreValid() {
        MissionCatalog withCobblemon = TestFixture.defaultCatalog(true);
        MissionCatalog withoutCobblemon = TestFixture.defaultCatalog(false);
        assertTrue(withCobblemon.size() >= 20);
        assertTrue(withoutCobblemon.size() < withCobblemon.size());
        assertTrue(withoutCobblemon.all().stream().allMatch(m -> m.requiresMod() == null));
        assertTrue(withoutCobblemon.all().stream()
                .flatMap(m -> m.objectives().stream()).noneMatch(o -> o.type().requiresCobblemon()));
    }

    @Test
    void invalidMissionFilesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MissionCatalog.fromJson(new StringReader("{}"), m -> true));
        assertThrows(IllegalArgumentException.class, () -> MissionCatalog.fromJson(new StringReader(
                "{\"missions\":[{\"id\":\"Bad Id\",\"scope\":\"COOP\",\"category\":\"STORY\",\"objectives\":[{\"type\":\"TRAVEL\",\"amount\":1}]}]}"),
                m -> true));
        assertThrows(IllegalArgumentException.class, () -> MissionCatalog.fromJson(new StringReader(
                "{\"missions\":[{\"id\":\"a\",\"scope\":\"COOP\",\"category\":\"STORY\",\"objectives\":[{\"type\":\"TRAVEL\",\"amount\":0}]}]}"),
                m -> true));
        assertThrows(IllegalArgumentException.class, () -> MissionCatalog.fromJson(new StringReader(
                "{\"missions\":[{\"id\":\"a\",\"scope\":\"COOP\",\"category\":\"STORY\",\"requires\":[\"ghost\"],\"objectives\":[{\"type\":\"TRAVEL\",\"amount\":1}]}]}"),
                m -> true));
    }

    @Test
    void missionsDependingOnMissingModsAreDroppedInCascade() {
        MissionCatalog catalog = MissionCatalog.fromJson(new StringReader("""
                {"missions":[
                  {"id":"a","scope":"INDIVIDUAL","category":"STORY","requiresMod":"cobblemon","objectives":[{"type":"CATCH_POKEMON","amount":1}]},
                  {"id":"b","scope":"INDIVIDUAL","category":"STORY","requires":["a"],"objectives":[{"type":"TRAVEL","amount":1}]},
                  {"id":"c","scope":"INDIVIDUAL","category":"STORY","objectives":[{"type":"TRAVEL","amount":1}]}
                ]}"""), mod -> false);
        assertEquals(1, catalog.size());
        assertTrue(catalog.get("c").isPresent());
    }

    @Test
    void notesAreSanitized() {
        assertEquals("Hola mundo", ChronicleService.sanitizeNote("  Hola §cmundo\n\t "));
        assertEquals("", ChronicleService.sanitizeNote("§k"));
        assertEquals(ChronicleService.MAX_NOTE_LENGTH, ChronicleService.sanitizeNote("x".repeat(500)).length());
    }

    @Test
    void notesRequireChroniclesApp() {
        TestFixture f = TestFixture.withDefaultMissions();
        assertEquals("caricraft.result.app_locked", f.core.addNote(f.alice, "hola").key());
        f.give(f.alice, 250);
        f.core.shop().buy(f.state, f.alice, AppCatalog.CHRONICLES);
        assertEquals("caricraft.result.note_empty", f.core.addNote(f.alice, "   ").key());
        assertTrue(f.core.addNote(f.alice, "Primera base junto al río").success());
        ChronicleEntry entry = f.state.partyOf(f.alice).chronicle().getFirst();
        assertEquals(ChronicleEntry.NOTE, entry.type());
        assertEquals("Primera base junto al río", entry.args().getFirst());
    }

    @Test
    void chronicleIsBounded() {
        TestFixture f = TestFixture.withDefaultMissions();
        Party party = f.state.partyOf(f.alice);
        for (int i = 0; i < f.config.chronicleMaxEntries + 50; i++) {
            f.core.chronicle().add(party, ChronicleEntry.NOTE, f.alice, "Alice", "n" + i);
        }
        assertEquals(f.config.chronicleMaxEntries, party.chronicle().size());
        assertEquals("n" + (f.config.chronicleMaxEntries + 49), party.chronicle().getFirst().args().getFirst());
    }

    @Test
    void biomeDiscoveryPaysOnlyWithExplorerAndOnlyOnce() {
        TestFixture f = TestFixture.withDefaultMissions();
        assertTrue(f.core.onBiomeSampled(f.alice, "minecraft:plains", true).discovered());
        assertEquals(0, f.balance(f.alice));
        f.give(f.alice, 400);
        f.core.shop().buy(f.state, f.alice, AppCatalog.EXPLORER);
        CaricraftCore.BiomeOutcome outcome = f.core.onBiomeSampled(f.alice, "minecraft:forest", true);
        assertEquals(f.config.biomeDiscoveryReward, outcome.reward());
        assertFalse(f.core.onBiomeSampled(f.alice, "minecraft:forest", true).discovered());
        assertEquals(f.config.biomeDiscoveryReward, f.balance(f.alice));
        assertEquals(2, f.state.partyOf(f.alice).discoveredBiomes().size());
    }
}
