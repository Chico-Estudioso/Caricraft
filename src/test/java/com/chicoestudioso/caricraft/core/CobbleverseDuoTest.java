package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.cobbleverse.CaptureInfo;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseService;
import com.chicoestudioso.caricraft.core.cobbleverse.DuoDexService;
import com.chicoestudioso.caricraft.core.cobbleverse.DuoPokemonState;
import com.chicoestudioso.caricraft.core.cobbleverse.ExpeditionService;
import com.chicoestudioso.caricraft.core.cobbleverse.HuntService;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CobbleverseDuoTest {
    private static final List<String> POOL = HuntService.SPECIES_POOL;

    private static CaptureInfo mon(String species, String... types) {
        return new CaptureInfo(species, List.of(types), 20, false, false, 190);
    }

    /** Un dúo de Alice y Bob. */
    private static TestFixture duo() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.config.cobbleverse.enabled = true;
        f.formDuo();
        return f;
    }

    @Test
    void huntTargetsAreTheSameForEveryoneAndChangeDaily() {
        var today = HuntService.targets(500, POOL);
        assertEquals(today, HuntService.targets(500, POOL));
        assertEquals(3, today.size());
        assertEquals(HuntService.Kind.SPECIES, today.get(0).kind());
        assertTrue(POOL.contains(today.get(0).value()));
        assertTrue(HuntService.TYPES.contains(today.get(1).value()));
        assertTrue(today.get(2).count() >= 6 && today.get(2).count() <= 10);
        boolean changes = false;
        for (long d = 501; d < 510; d++) {
            changes |= !HuntService.targets(d, POOL).equals(today);
        }
        assertTrue(changes);
    }

    /** Completa la caza del día con capturas de quien se indique; devuelve el último resultado. */
    private static HuntService.Result clear(TestFixture f, Party party, long day, java.util.UUID... who) {
        HuntService hunt = f.core.hunt();
        var targets = HuntService.targets(day, POOL);
        HuntService.Result last = null;
        int turn = 0;
        // especie objetivo (también cuenta para el tipo si coincide y para «cualesquiera»)
        last = hunt.onCapture(party, who[turn++ % who.length], mon(targets.get(0).value(), "normal"), day, POOL);
        for (int i = 0; i < 20 && !party.pokemon().huntCleared(); i++) {
            last = hunt.onCapture(party, who[turn++ % who.length], mon("rattata", targets.get(1).value()), day, POOL);
        }
        return last;
    }

    @Test
    void huntPaysTargetsClearAndDuoBonusOnlyWhenBothPlay() {
        TestFixture f = duo();
        Party party = f.state.partyOf(f.alice);
        long day = 600;
        // Solo Alice: completa, pero sin bonus de dúo.
        HuntService.Result solo = clear(f, party, day, f.alice);
        assertTrue(solo.cleared());
        assertFalse(solo.duoBonus(), "Bob no ha aportado nada");
        assertEquals(0, party.pokemon().weekClears(), "en dúo, la semanal solo cuenta cazas a medias");
        // Al día siguiente, los dos.
        HuntService.Result both = clear(f, party, day + 1, f.alice, f.bob);
        assertTrue(both.cleared());
        assertTrue(both.duoBonus());
        assertEquals(1, party.pokemon().weekClears());
        assertTrue(both.payout().dollars() >= 1500 + 2500, "premio de completar + bonus de dúo");
        // Una captura que no cumple ningún objetivo pendiente no cuenta.
        var after = f.core.hunt().onCapture(party, f.alice, mon("pidgey", "normal"), day + 1, POOL);
        assertFalse(after.counted());
    }

    @Test
    void weeklyPrizeAfterEnoughDuoClears() {
        TestFixture f = duo();
        Party party = f.state.partyOf(f.alice);
        long monday = java.time.LocalDate.of(2026, 1, 5).toEpochDay(); // lunes
        assertEquals(0, Math.floorMod(monday + 3, 7));
        boolean weekly = false;
        for (int d = 0; d < 4; d++) {
            weekly = clear(f, party, monday + d, f.alice, f.bob).weekly();
        }
        assertTrue(weekly, "4 cazas en dúo en la semana");
        assertTrue(party.pokemon().weekPaid());
        clear(f, party, monday + 4, f.alice, f.bob);
        assertEquals(5, party.pokemon().weekClears());
        assertFalse(clear(f, party, monday + 7, f.alice, f.bob).weekly(), "nueva semana: vuelve a contar desde cero");
        assertEquals(1, party.pokemon().weekClears());
    }

    @Test
    void soloPartiesCanHuntWithoutDuoBonus() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.config.cobbleverse.enabled = true;
        Party party = f.state.partyOf(f.carol);
        HuntService.Result r = clear(f, party, 700, f.carol);
        assertTrue(r.cleared());
        assertFalse(r.duoBonus());
        assertEquals(1, party.pokemon().weekClears(), "sin dúo, cuenta para la semanal");
    }

    @Test
    void duoDexKeepsShiniesRaresGoalsAndMerges() {
        TestFixture f = duo();
        Party party = f.state.partyOf(f.alice);
        DuoDexService dex = f.core.duoDex();
        var goals = dex.onCapture(party, "Alice", new CaptureInfo("gible", List.of("dragon", "ground"), 30, true, false, 45), 10L);
        assertTrue(goals.stream().anyMatch(g -> g.id().equals("shinies_1")), "primer variocolor del dúo");
        dex.onCapture(party, "Bob", new CaptureInfo("mewtwo", List.of("psychic"), 70, false, true, 3), 20L);
        dex.onCapture(party, "Bob", mon("pidgey", "normal", "flying"), 30L);
        DuoPokemonState s = party.pokemon();
        assertEquals(1, s.shinies().size());
        assertEquals("mewtwo", s.rares().getFirst().species(), "lo más raro primero");
        assertEquals(2, s.rares().size(), "pidgey (190) no es raro");
        assertEquals(1, s.legends().size());
        assertEquals(3, s.species().size());
        assertEquals(1, s.typeCounts().get("dragon"));
        assertTrue(s.goalsDone().contains("legends_1"));
        assertTrue(dex.onCapture(party, "Bob", mon("pidgey", "normal"), 40L).isEmpty(), "las metas se pagan una vez");

        // Al deshacer el dúo, quien se va se lleva una copia; al formarse, se suman.
        DuoPokemonState other = new DuoPokemonState();
        other.copyDexFrom(s);
        assertEquals(3, other.species().size());
        DuoPokemonState merged = new DuoPokemonState();
        merged.mergeFrom(s);
        merged.mergeFrom(other);
        assertEquals(3, merged.species().size(), "las especies no se duplican");
        assertEquals(2, merged.shinies().size());
        assertEquals(8, merged.captures());
    }

    @Test
    void duoStateSurvivesSaving() {
        TestFixture f = duo();
        Party party = f.state.partyOf(f.alice);
        clear(f, party, 800, f.alice, f.bob);
        f.core.duoDex().onCapture(party, "Alice", new CaptureInfo("dratini", List.of("dragon"), 15, true, false, 45), 5L);
        var copy = com.chicoestudioso.caricraft.server.StateCodec.read(com.chicoestudioso.caricraft.server.StateCodec.write(f.state));
        DuoPokemonState loaded = copy.partyOf(f.alice).pokemon();
        assertTrue(loaded.huntCleared());
        assertTrue(loaded.huntDuoBonus());
        assertEquals(party.pokemon().huntContrib(), loaded.huntContrib());
        assertEquals("dratini", loaded.shinies().getFirst().species());
        assertEquals(party.pokemon().goalsDone(), loaded.goalsDone());
        PlayerProfile profile = copy.profiles().get(f.alice);
        assertTrue(profile.shinyCompass());
    }

    @Test
    void expeditionsArePassiveAndModest() {
        TestFixture f = duo();
        ExpeditionService exp = f.core.expeditions();
        Random random = new Random(1);
        for (ExpeditionService.Destination destination : ExpeditionService.DESTINATIONS) {
            for (int hours : ExpeditionService.HOURS) {
                long total = 0;
                for (int i = 0; i < 2000; i++) {
                    total += exp.roll(destination, hours, 100, true, random).dollars();
                }
                double perHour = total / 2000.0 / hours;
                // Lo mejor posible (nivel 100 y tipo que encaja) sigue por debajo de ₽600 la hora.
                assertTrue(perHour < 600, destination.id() + " " + hours + "h: " + perHour);
            }
        }
        assertEquals(1, ExpeditionService.rolls(1));
        assertEquals(5, ExpeditionService.rolls(8));
        assertTrue(ExpeditionService.factor(100, true) > ExpeditionService.factor(5, false));
        var forest = ExpeditionService.destination("forest");
        assertTrue(ExpeditionService.typeMatch(forest, List.of("bug")));
        assertFalse(ExpeditionService.typeMatch(forest, List.of("water")));

        PlayerProfile profile = f.state.profiles().get(f.alice);
        long day = 900;
        assertEquals("caricraft.result.cv_exp_busy", exp.canStart(profile, day, true, "forest", 4).key());
        assertEquals("caricraft.result.invalid_request", exp.canStart(profile, day, false, "luna", 4).key());
        assertEquals("caricraft.result.invalid_request", exp.canStart(profile, day, false, "forest", 3).key());
        for (int i = 0; i < f.config.cobbleverse.expeditionsPerDay; i++) {
            assertTrue(exp.canStart(profile, day, false, "cave", 1).success());
            exp.recordStart(profile, day);
        }
        assertEquals("caricraft.result.cv_exp_limit", exp.canStart(profile, day, false, "cave", 1).key());
        assertTrue(exp.canStart(profile, day + 1, false, "cave", 1).success());
    }

    @Test
    void pocketHealCostsAndHasACooldown() {
        TestFixture f = duo();
        CobbleverseService cv = new CobbleverseService(f.config, CobbleverseCatalog.empty(), f.clock);
        PlayerProfile profile = f.state.profiles().get(f.alice);
        long price = f.config.cobbleverse.pocketHealPrice;
        assertEquals("caricraft.result.cv_heal_not_needed", cv.canHeal(profile, false, false, price).key());
        assertEquals("caricraft.result.cv_heal_battle", cv.canHeal(profile, true, true, price).key());
        assertEquals("caricraft.result.cv_insufficient", cv.canHeal(profile, true, false, price - 1).key());
        assertTrue(cv.canHeal(profile, true, false, price).success());
        cv.recordHeal(profile);
        var wait = cv.canHeal(profile, true, false, price);
        assertEquals("caricraft.result.cv_heal_wait", wait.key());
        assertEquals(String.valueOf(f.config.cobbleverse.pocketHealCooldownMinutes), wait.args().getFirst());
        f.clock.advance(f.config.cobbleverse.pocketHealCooldownMinutes * 60_000L);
        assertTrue(cv.canHeal(profile, true, false, price).success());
        f.config.cobbleverse.pocketHeal = false;
        assertNotEquals("ok", cv.canHeal(profile, true, false, price).key());
    }
}
