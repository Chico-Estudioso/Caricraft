package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseService;
import com.chicoestudioso.caricraft.core.cobbleverse.ExpeditionService;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;
import com.chicoestudioso.caricraft.core.savings.SavingsService;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
import com.chicoestudioso.caricraft.server.StateCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hucha del dúo, entrenador personal y mejoras del CobbleCentro. */
class SavingsAndTrainerTest {
    private static final long DAY = 86_400_000L;

    private static TestFixture withSavings() {
        TestFixture f = TestFixture.withMissions(List.of());
        f.give(f.alice, 5_000);
        assertTrue(f.core.shop().buy(f.state, f.alice, AppCatalog.SAVINGS).success());
        return f;
    }

    @Test
    void savingsNeedTheAppAndMoveCarinsBothWays() {
        TestFixture f = TestFixture.withMissions(List.of());
        Party party = f.state.partyOf(f.alice);
        SavingsService savings = f.core.savings();
        assertEquals("caricraft.result.app_locked", savings.deposit(party, 10, f.core.day()).key());
        f = withSavings();
        party = f.state.partyOf(f.alice);
        long wallet = party.wallet().balance();
        assertTrue(f.core.savings().deposit(party, 1_000, f.core.day()).success());
        assertEquals(wallet - 1_000, party.wallet().balance());
        assertEquals(1_000, party.savings().balance());
        assertEquals("caricraft.result.savings_not_enough", f.core.savings().withdraw(party, 2_000, f.core.day()).key());
        assertTrue(f.core.savings().withdraw(party, 400, f.core.day()).success());
        assertEquals(600, party.savings().balance());
        assertEquals(wallet - 600, party.wallet().balance());
        assertTrue(f.core.savings().withdraw(party, -1, f.core.day()).success(), "sacar todo");
        assertEquals(0, party.savings().balance());
    }

    @Test
    void interestUsesTheLowestBalanceOfTheDayAndHasACap() {
        TestFixture f = withSavings();
        Party party = f.state.partyOf(f.alice);
        SavingsService savings = f.core.savings();
        savings.deposit(party, 1_000, f.core.day()); // hoy empezó en 0: el mínimo de hoy es 0
        f.clock.advance(DAY);
        assertEquals(0, savings.accrue(party, f.core.day()), "meter y esperar a medianoche no da nada");
        f.clock.advance(DAY);
        assertEquals(20, savings.accrue(party, f.core.day()), "2 % de 1.000");
        assertEquals(1_020, party.savings().balance());
        // Sacar a mitad del día baja el mínimo de ese día
        savings.withdraw(party, 520, f.core.day());
        f.clock.advance(DAY);
        assertEquals(10, savings.accrue(party, f.core.day()));
        // Tope diario: 40 Carins aunque haya mucho
        f.give(f.alice, 10_000);
        savings.deposit(party, 9_000, f.core.day());
        f.clock.advance(DAY);
        savings.accrue(party, f.core.day());
        f.clock.advance(DAY);
        assertEquals(40, savings.accrue(party, f.core.day()));
        assertEquals(40, savings.interestFor(1_000_000));
    }

    @Test
    void savingsMergeSplitAndPersist() throws Exception {
        TestFixture f = withSavings();
        Party alice = f.state.partyOf(f.alice);
        f.core.savings().deposit(alice, 1_001, f.core.day());
        CaricraftState loaded = StateCodec.read(StateCodec.write(f.state));
        assertEquals(1_001, loaded.partyOf(f.alice).savings().balance());
        Party duo = f.formDuo();
        assertEquals(1_001, duo.savings().balance());
        assertTrue(f.core.parties().requestLeave(f.state, f.bob).success());
        assertTrue(f.core.parties().confirmLeave(f.state, f.bob).success());
        assertEquals(500, f.state.partyOf(f.bob).savings().balance(), "quien se va se lleva la mitad hacia abajo");
        assertEquals(501, f.state.partyOf(f.alice).savings().balance());
    }

    @Test
    void trainerRespectsLevelCapDailyLimitAndPrice() {
        TestFixture f = TestFixture.withMissions(List.of());
        f.config.cobbleverse.enabled = true;
        CobbleverseService cv = f.core.cobbleverse();
        PlayerProfile profile = f.state.profiles().get(f.alice);
        long day = cv.day();
        assertEquals(30, cv.trainerPrice(20), "10 + nivel");
        assertTrue(cv.canTrain(profile, day, 20, 25, false, 100).success());
        assertEquals("caricraft.result.cv_train_cap", cv.canTrain(profile, day, 25, 25, false, 100).key());
        assertEquals("caricraft.result.cv_train_max", cv.canTrain(profile, day, 100, 0, false, 1_000).key());
        assertEquals("caricraft.result.insufficient_funds", cv.canTrain(profile, day, 20, 0, false, 10).key());
        assertEquals("caricraft.result.cv_train_battle", cv.canTrain(profile, day, 20, 0, true, 100).key());
        for (int i = 0; i < 5; i++) {
            cv.recordTrain(profile, day);
        }
        assertEquals(0, cv.trainerLevelsLeft(profile, day));
        assertEquals("caricraft.result.cv_train_limit", cv.canTrain(profile, day, 20, 0, false, 100).key());
    }

    @Test
    void cobbleCentroUpgradesChangeHealExpeditionsAndAreBought() {
        TestFixture f = TestFixture.withMissions(List.of());
        f.config.cobbleverse.enabled = true;
        // El catálogo se crea con la configuración: hace falta un núcleo con Cobbleverse activado desde el principio
        TestFixture g = new TestFixture();
        g.config.cobbleverse.enabled = true;
        g.core = new CaricraftCore(g.state, g.config, TestFixture.defaultCatalog(true), g.clock);
        g.core.onPlayerJoin(g.alice, "Alice");
        g.give(g.alice, 2_000);
        Party party = g.state.partyOf(g.alice);
        assertTrue(g.core.shop().buy(g.state, g.alice, AppCatalog.CC_HEAL).success());
        assertTrue(g.core.shop().buy(g.state, g.alice, AppCatalog.CC_HEAL).success());
        assertFalse(g.core.shop().buy(g.state, g.alice, AppCatalog.CC_HEAL).success(), "dos niveles como mucho");
        assertEquals(2, party.upgradeLevel(AppCatalog.CC_HEAL));
        CobbleverseService cv = g.core.cobbleverse();
        assertEquals(15, cv.healCooldownMinutes(0));
        assertEquals(10, cv.healCooldownMinutes(1));
        assertEquals(5, cv.healCooldownMinutes(2));
        assertEquals(1, ExpeditionService.slots(0));
        assertEquals(3, ExpeditionService.slots(2));
        PlayerProfile profile = g.state.profiles().get(g.alice);
        long day = cv.day();
        assertEquals("caricraft.result.cv_exp_busy", g.core.expeditions().canStart(profile, day, 1, 0, "cave", 1).key());
        assertTrue(g.core.expeditions().canStart(profile, day, 1, 1, "cave", 1).success(), "con la mejora caben dos a la vez");
        assertEquals(4, g.core.expeditions().perDay(1));
    }
}
