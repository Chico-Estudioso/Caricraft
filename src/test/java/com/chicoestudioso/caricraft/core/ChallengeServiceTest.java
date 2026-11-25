package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.challenge.ChallengeEvent;
import com.chicoestudioso.caricraft.core.challenge.ChallengeService;
import com.chicoestudioso.caricraft.core.challenge.ChallengeState;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.mission.MissionStatus;
import com.chicoestudioso.caricraft.core.mission.ObjectiveType;
import com.chicoestudioso.caricraft.core.mission.ProgressEvent;
import com.chicoestudioso.caricraft.core.party.Party;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChallengeServiceTest {
    private static final long DAY = ChallengeService.TICKS_PER_DAY;

    /** Simula el paso de una noche completa del día {@code d}: tarde, noche y amanecer. */
    private static ChallengeEvent[] night(ChallengeService service, Party party, long d) {
        ChallengeEvent dusk = service.update(party, d, false);
        ChallengeEvent start = service.update(party, d, true);
        ChallengeEvent mid = service.update(party, d, true);
        ChallengeEvent dawn = service.update(party, d + 1, false);
        return new ChallengeEvent[]{dusk, start, mid, dawn};
    }

    @Test
    void dayIndexChangesAtDawnNotAtMidnight() {
        assertEquals(0, ChallengeService.dayIndex(0));
        assertEquals(0, ChallengeService.dayIndex(13_000));
        assertEquals(0, ChallengeService.dayIndex(22_999));
        assertEquals(1, ChallengeService.dayIndex(23_000));
        assertEquals(1, ChallengeService.dayIndex(DAY + 5_000));
        assertTrue(ChallengeService.isNight(DAY * 7 + 15_000));
        assertFalse(ChallengeService.isNight(DAY * 7 + 23_500));
        assertFalse(ChallengeService.isNight(6_000));
    }

    @Test
    void forcedChallengeEveryThreeNights() {
        TestFixture f = TestFixture.withDefaultMissions();
        ChallengeService service = f.core.challenge();
        Party party = f.state.partyOf(f.alice);
        long start = 100;
        // Noches 100, 101 y 102: libres (la primera vez solo se apunta el día).
        for (long d = start; d < start + 3; d++) {
            ChallengeEvent[] events = night(service, party, d);
            assertNull(events[1], "noche " + d + " no debería ser de desafío");
        }
        ChallengeEvent[] forced = night(service, party, start + 3);
        assertEquals(ChallengeEvent.Kind.STARTED, forced[1].kind());
        assertTrue(forced[1].forced());
        assertNull(forced[2]);
        assertEquals(ChallengeEvent.Kind.SURVIVED, forced[3].kind());
        assertEquals(1, party.challenge().nightsCompleted());
        // La siguiente obligatoria es 3 noches después.
        assertNull(night(service, party, start + 4)[1]);
        assertNull(night(service, party, start + 5)[1]);
        assertEquals(ChallengeEvent.Kind.STARTED, night(service, party, start + 6)[1].kind());
    }

    @Test
    void bringingForwardResetsTheCount() {
        TestFixture f = TestFixture.withDefaultMissions();
        ChallengeService service = f.core.challenge();
        Party party = f.state.partyOf(f.alice);
        service.update(party, 10, false);
        assertEquals(3, service.nightsUntilForced(party, 10));
        assertTrue(service.bringForward(party, 10, false).success());
        assertEquals("caricraft.result.challenge_already_scheduled", service.bringForward(party, 10, false).key());
        assertEquals(0, service.nightsUntilForced(party, 10));
        assertTrue(service.isChallengeNight(party, 10, true));
        assertFalse(service.isChallengeNight(party, 10, false));
        ChallengeEvent[] events = night(service, party, 10);
        assertEquals(ChallengeEvent.Kind.STARTED, events[1].kind());
        assertFalse(events[1].forced());
        assertEquals(2, service.nightsUntilForced(party, 11)); // libres 11 y 12, obligatoria la 13
        assertNull(night(service, party, 11)[1]);
        assertNull(night(service, party, 12)[1]);
        assertEquals(ChallengeEvent.Kind.STARTED, night(service, party, 13)[1].kind());
    }

    @Test
    void bringingForwardAtNightStartsImmediately() {
        TestFixture f = TestFixture.withDefaultMissions();
        ChallengeService service = f.core.challenge();
        Party party = f.state.partyOf(f.alice);
        service.update(party, 5, true);
        assertEquals("caricraft.result.challenge_starting", service.bringForward(party, 5, true).key());
        assertEquals(ChallengeEvent.Kind.STARTED, service.update(party, 5, true).kind());
        assertEquals("caricraft.result.challenge_active", service.bringForward(party, 5, true).key());
    }

    @Test
    void killsPayMoreWithLevelAndOnlyDuringChallenge() {
        TestFixture f = TestFixture.withDefaultMissions();
        ChallengeService service = f.core.challenge();
        Party party = f.state.partyOf(f.alice);
        assertEquals(0, service.onKill(party), "fuera del desafío no se paga");
        service.update(party, 0, false);
        service.bringForward(party, 0, true);
        service.update(party, 0, true);
        assertEquals(1, service.level(party));
        assertEquals(2, service.onKill(party));
        assertEquals(2, service.onKill(party));
        ChallengeEvent end = service.update(party, 1, false);
        assertEquals(ChallengeEvent.Kind.SURVIVED, end.kind());
        assertEquals(2, end.kills());
        assertEquals(4 + f.config.challenge.nightReward, end.carins());
        assertEquals(4 + f.config.challenge.nightReward, f.balance(f.alice));
        assertEquals(ChronicleEntry.CHALLENGE_SURVIVED, party.chronicle().getFirst().type());

        // Cada noche superada sube un nivel: más fuertes y mejor pagados.
        assertEquals(2, service.level(party));
        service.bringForward(party, 1, true);
        service.update(party, 1, true);
        service.update(party, 2, false);
        assertEquals(3, service.level(party));
        service.bringForward(party, 2, true);
        service.update(party, 2, true);
        assertEquals(3, service.onKill(party));
        assertEquals(2 + 10 / 2, service.onKill(party, 10, false));
        assertEquals((2 + 10 / 2) * 3, service.onKill(party, 10, true), "los élite pagan el triple");
    }

    @Test
    void wavesMakeTheNightHarder() {
        TestFixture f = TestFixture.withDefaultMissions();
        ChallengeService service = f.core.challenge();
        Party party = f.state.partyOf(f.alice);
        assertEquals(0, ChallengeService.wave(DAY * 3 + 14_000));
        assertEquals(1, ChallengeService.wave(DAY * 3 + 17_000));
        assertEquals(2, ChallengeService.wave(DAY * 3 + 21_000));
        assertEquals(0, ChallengeService.wave(DAY * 3 + 6_000));
        assertEquals(service.level(party) + 2, service.effectiveLevel(party, 2));
        f.config.challenge.waves = false;
        assertEquals(service.level(party), service.effectiveLevel(party, 2));
        assertTrue(service.eliteChance(30) <= 0.25);
        assertTrue(service.hordeChance(5) < service.hordeChance(20));
        assertTrue(service.hordeChance(100) <= 0.5);
    }

    @Test
    void levelKeepsGrowingUpToTheConfiguredMaximum() {
        TestFixture f = TestFixture.withDefaultMissions();
        ChallengeService service = f.core.challenge();
        Party party = f.state.partyOf(f.alice);
        for (long d = 0; d < 12; d++) {
            service.bringForward(party, d, true);
            service.update(party, d, true);
            service.update(party, d + 1, false);
        }
        assertEquals(13, service.level(party), "un nivel por noche superada");
    }

    @Test
    void levelIsCapped() {
        TestFixture f = TestFixture.withDefaultMissions();
        Party party = f.state.partyOf(f.alice);
        party.challenge().restore(0, ChallengeState.NONE, false, false, 99, 0, 0, 0, 0);
        assertEquals(f.config.challenge.maxLevel, f.core.challenge().level(party));
    }

    @Test
    void nightRewardIsPaidOncePerNight() {
        TestFixture f = TestFixture.withDefaultMissions();
        ChallengeService service = f.core.challenge();
        Party party = f.state.partyOf(f.alice);
        service.bringForward(party, 3, true);
        service.update(party, 3, true);
        service.update(party, 4, false);
        long balance = f.balance(f.alice);
        // Repetir el mismo día no vuelve a pagar (clave idempotente) ni reactiva el desafío.
        assertNull(service.update(party, 4, false));
        assertEquals(balance, f.balance(f.alice));
    }

    @Test
    void longAbsenceEndsWithoutReward() {
        TestFixture f = TestFixture.withDefaultMissions();
        ChallengeService service = f.core.challenge();
        Party party = f.state.partyOf(f.alice);
        service.bringForward(party, 3, true);
        service.update(party, 3, true);
        ChallengeEvent end = service.update(party, 9, false);
        assertEquals(ChallengeEvent.Kind.ENDED, end.kind());
        assertEquals(0, party.challenge().nightsCompleted());
        assertEquals(0, f.balance(f.alice));
    }

    @Test
    void disablingTheFeatureStopsEverything() {
        TestFixture f = TestFixture.withDefaultMissions();
        ChallengeService service = f.core.challenge();
        Party party = f.state.partyOf(f.alice);
        service.bringForward(party, 1, true);
        service.update(party, 1, true);
        f.config.challenge.enabled = false;
        assertEquals(ChallengeEvent.Kind.ENDED, service.update(party, 1, true).kind());
        assertEquals("caricraft.result.challenge_disabled", service.bringForward(party, 2, true).key());
        for (long d = 2; d < 20; d++) {
            assertNull(night(service, party, d)[1]);
        }
    }

    @Test
    void huntingMissionsUseTheChallengeKey() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.core.missions().accept(f.state, f.alice, "night_hunter");
        for (int i = 0; i < 19; i++) {
            f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.KILL_ENTITY, 1, "minecraft:zombie", "challenge"));
        }
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.KILL_ENTITY, 1, "minecraft:zombie"));
        var def = f.core.missions().catalog().get("night_hunter").orElseThrow();
        assertEquals(MissionStatus.ACCEPTED, f.core.missions().statusFor(f.state.partyOf(f.alice), f.alice, def));
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.KILL_ENTITY, 1, "minecraft:skeleton", "challenge"));
        assertEquals(MissionStatus.COMPLETED, f.core.missions().statusFor(f.state.partyOf(f.alice), f.alice, def));
    }

    @Test
    void challengeMissionsDisappearWhenDisabled() {
        var withChallenge = TestFixture.defaultCatalog(true);
        assertTrue(withChallenge.get("night_hunter").isPresent());
        java.io.Reader reader = new java.io.InputStreamReader(TestFixture.class.getResourceAsStream("/caricraft/default_missions.json"));
        var without = com.chicoestudioso.caricraft.core.mission.MissionCatalog.fromJson(reader, mod -> true, feature -> false);
        assertTrue(without.get("night_hunter").isEmpty());
        assertTrue(without.get("elite_hunters").isEmpty());
        assertTrue(without.get("first_steps").isPresent());
    }
}
