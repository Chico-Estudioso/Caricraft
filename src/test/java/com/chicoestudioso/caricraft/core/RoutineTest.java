package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.challenge.ChallengeService;
import com.chicoestudioso.caricraft.core.mission.MissionCategory;
import com.chicoestudioso.caricraft.core.mission.MissionDefinition;
import com.chicoestudioso.caricraft.core.mission.MissionScope;
import com.chicoestudioso.caricraft.core.mission.Objective;
import com.chicoestudioso.caricraft.core.mission.ObjectiveType;
import com.chicoestudioso.caricraft.core.mission.ProgressEvent;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.routine.MedalService;
import com.chicoestudioso.caricraft.core.routine.StreakService;
import com.chicoestudioso.caricraft.core.routine.WeeklyEventService;
import com.chicoestudioso.caricraft.server.StateCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Racha diaria, eventos semanales, medallas y jefes del desafío. */
class RoutineTest {
    private static final long DAY = 86_400_000L;

    private static MissionDefinition daily(String id, ObjectiveType type) {
        return new MissionDefinition(id, MissionScope.INDIVIDUAL, MissionCategory.DAILY, 40, List.of(new Objective(type, "", 1)),
                List.of(), null, null, false, "minecraft:stone", null, null, null);
    }

    /** Acepta, completa y reclama la misión diaria de ese jugador. */
    private static CaricraftCore.ClaimOutcome doDaily(TestFixture f, UUID player) {
        f.core.missions().accept(f.state, player, "chop"); // el día 1 ya puede estar aceptada
        f.core.progress(player, ProgressEvent.of(ObjectiveType.MINE_BLOCK, 1, "minecraft:oak_log"));
        return f.core.claimMission(player, "chop");
    }

    @Test
    void streakGrowsDailyPaysMoreAndDuoBonusNeedsBoth() {
        TestFixture f = TestFixture.withMissions(List.of(daily("chop", ObjectiveType.MINE_BLOCK)));
        Party party = f.formDuo();
        long before = party.wallet().balance();
        CaricraftCore.ClaimOutcome first = doDaily(f, f.alice);
        assertTrue(first.result().success());
        assertEquals(1, first.streak().days());
        assertEquals(15, first.streak().reward());
        assertEquals(0, first.streak().duoBonus(), "Bob aún no ha jugado hoy");
        assertEquals(before + 40 + 15, party.wallet().balance());
        // Bob entra el mismo día: cobra el bonus de dúo (la mitad del día)
        StreakService.Result bob = f.core.streak().onActive(party, f.bob, f.core.day());
        assertEquals(7, bob.duoBonus());
        assertEquals(0, f.core.streak().onActive(party, f.bob, f.core.day()).duoBonus(), "una vez al día");
        // Otra misión el mismo día no cuenta otra vez
        f.core.missions().abandon(f.state, f.alice, "chop");
        // Días 2..7
        for (int day = 2; day <= 7; day++) {
            f.clock.advance(DAY);
            CaricraftCore.ClaimOutcome next = doDaily(f, f.alice);
            assertEquals(day, next.streak().days());
            assertEquals(StreakService.REWARDS.get(day - 1), next.streak().reward());
        }
        assertEquals(100, f.core.streak().rewardFor(7));
        assertEquals(15, f.core.streak().rewardFor(8), "vuelve a empezar el ciclo semanal");
        assertEquals(7, party.routine().streakBest());
    }

    @Test
    void shieldCoversOneMissedDayPerWeekAndTwoBreakIt() {
        TestFixture f = TestFixture.withMissions(List.of(daily("chop", ObjectiveType.MINE_BLOCK)));
        Party party = f.state.partyOf(f.alice);
        // 2026-01-05 es lunes
        f.clock.advance(4 * DAY);
        assertEquals(1, doDaily(f, f.alice).streak().days());
        f.clock.advance(DAY);
        assertEquals(2, doDaily(f, f.alice).streak().days());
        f.clock.advance(2 * DAY); // se salta el miércoles
        assertTrue(f.core.streak().alive(party.routine(), f.core.day()));
        StreakService.Result shielded = doDaily(f, f.alice).streak();
        assertEquals(3, shielded.days());
        assertTrue(shielded.shieldUsed());
        f.clock.advance(2 * DAY); // otro día perdido la misma semana: ya no hay protector
        assertFalse(f.core.streak().alive(party.routine(), f.core.day()));
        StreakService.Result broken = doDaily(f, f.alice).streak();
        assertEquals(1, broken.days());
        assertTrue(broken.broken());
        assertEquals(3, party.routine().streakBest());
    }

    @Test
    void weeklyEventRotatesPaysTiersAndBoostsMissions() {
        TestFixture f = TestFixture.withMissions(List.of(daily("chop", ObjectiveType.MINE_BLOCK)));
        f.config.routine.weeklyEvents = true;
        WeeklyEventService weekly = f.core.weekly();
        // Sin Cobblemon, el evento de entrenadores no entra en la rotación
        assertTrue(weekly.rotation().stream().noneMatch(e -> e.id().equals("trainers")));
        f.core.setAvailableFeatures("cobblemon"::equals);
        assertTrue(weekly.rotation().stream().anyMatch(e -> e.id().equals("trainers")));
        // Busca una semana con el evento minero
        long week = f.core.week();
        while (!weekly.eventOf(week).id().equals("miners")) {
            week++;
        }
        long target = week;
        while (f.core.week() < target) {
            f.clock.advance(DAY);
        }
        Party party = f.state.partyOf(f.alice);
        party.incrementStat("blocks_mined", 5_000); // lo de antes no cuenta
        assertEquals(0, weekly.progress(party, target));
        long before = party.wallet().balance();
        party.incrementStat("blocks_mined", 750);
        assertEquals(1, weekly.check(party, target));
        assertEquals(before + 100, party.wallet().balance());
        party.incrementStat("blocks_mined", 750);
        assertEquals(2, weekly.check(party, target));
        assertEquals(0, weekly.check(party, target), "cada tramo se cobra una vez");
        assertEquals(before + 400, party.wallet().balance());
        assertEquals(1, party.stats().get(WeeklyEventService.STAT_COMPLETED));
        // Ventaja: las misiones de picar pagan ×1,5
        assertEquals(60, f.core.missions().rewardOf(f.core.missions().catalog().get("chop").orElseThrow()));
        // La semana siguiente el evento cambia y empieza de cero
        assertEquals(0, weekly.progress(party, target + 1));
    }

    @Test
    void medalsUnlockTiersOnceAndCountExistingStats() {
        TestFixture f = TestFixture.withMissions(List.of());
        Party party = f.state.partyOf(f.alice);
        MedalService medals = f.core.medals();
        assertTrue(medals.medals().stream().noneMatch(m -> m.id().equals("trainer")), "sin Cobblemon no sale");
        long before = party.wallet().balance();
        party.incrementStat("blocks_mined", 12_000); // bronce y plata a la vez
        List<MedalService.Unlock> unlocked = medals.check(party);
        assertEquals(2, unlocked.size());
        assertEquals(before + 25 + 75, party.wallet().balance());
        assertEquals(2, party.routine().medals().get("miner"));
        assertTrue(medals.check(party).isEmpty());
        party.discoveredBiomes().addAll(List.of("a:1", "a:2", "a:3", "a:4", "a:5", "a:6", "a:7", "a:8", "a:9", "a:10"));
        assertEquals("explorer", medals.check(party).getFirst().medal().id());
    }

    @Test
    void bossesComeEveryFiveLevelsAndPayOnce() {
        TestFixture f = TestFixture.withMissions(List.of());
        Party party = f.state.partyOf(f.alice);
        ChallengeService challenge = f.core.challenge();
        assertEquals(0, challenge.pendingBossLevel(party));
        assertEquals(5, challenge.nextBossLevel(party));
        challenge.gainXp(party, challenge.xpForLevel(11));
        assertEquals(11, challenge.level(party));
        assertEquals(5, challenge.pendingBossLevel(party));
        ChallengeService.Boss first = challenge.boss(5);
        assertEquals("zombie_king", first.id());
        assertEquals("spider_queen", challenge.boss(10).id());
        assertTrue(challenge.boss(35).health() > ChallengeService.BOSSES.getFirst().health() * 1.5, "en el segundo ciclo es más fuerte");
        long before = party.wallet().balance();
        long reward = challenge.onBossDefeated(party, 5, f.alice, "Alice", "Alice, Bob", f.clock.now());
        assertEquals(150 + 100, reward);
        assertEquals(before + reward, party.wallet().balance());
        assertEquals(-1, challenge.onBossDefeated(party, 5, f.alice, "Alice", "Alice", f.clock.now()));
        assertEquals(10, challenge.pendingBossLevel(party));
        assertEquals(1, party.stats().get(ChallengeService.STAT_BOSSES));
    }

    @Test
    void routineAndBossesSurviveSaveAndDuoChanges() throws Exception {
        TestFixture f = TestFixture.withMissions(List.of(daily("chop", ObjectiveType.MINE_BLOCK)));
        doDaily(f, f.alice);
        Party alice = f.state.partyOf(f.alice);
        alice.routine().medals().put("miner", 2);
        f.core.challenge().onBossDefeated(alice, 5, f.alice, "Alice", "Alice", 123L);
        CaricraftState loaded = StateCodec.read(StateCodec.write(f.state));
        Party reloaded = loaded.partyOf(f.alice);
        assertEquals(1, reloaded.routine().streakDays());
        assertEquals(f.core.day(), reloaded.routine().streakLastDay());
        assertEquals(2, reloaded.routine().medals().get("miner"));
        assertEquals(1, reloaded.challenge().bosses().size());
        assertEquals("zombie_king", reloaded.challenge().bosses().getFirst().boss());
        // Al formar dúo se conservan la racha y las medallas; al separarse, cada uno se lleva una copia
        Party duo = f.formDuo();
        assertEquals(1, duo.routine().streakDays());
        assertEquals(2, duo.routine().medals().get("miner"));
        assertNotNull(duo);
    }
}
