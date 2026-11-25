package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.camp.CampService;
import com.chicoestudioso.caricraft.core.camp.CampService.Destination;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampServiceTest {
    private TestFixture withCamp() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.give(f.alice, 2000);
        assertTrue(f.core.shop().buy(f.state, f.alice, AppCatalog.CARICAMP).success());
        return f;
    }

    @Test
    void needsTheApp() {
        TestFixture f = TestFixture.withDefaultMissions();
        Party party = f.state.partyOf(f.alice);
        assertEquals("caricraft.result.app_locked",
                f.core.camp().setCamp(party, f.alice, "Alice", "minecraft:overworld", 1, 64, 1).key());
        assertEquals("caricraft.result.app_locked",
                f.core.camp().chargeTravel(party, f.alice, Destination.CAMP, false).key());
    }

    @Test
    void setCampAndTravelChargesAndStartsCooldown() {
        TestFixture f = withCamp();
        CampService camp = f.core.camp();
        Party party = f.state.partyOf(f.alice);
        assertEquals("caricraft.result.camp_missing", camp.chargeTravel(party, f.alice, Destination.CAMP, false).key());
        assertTrue(camp.setCamp(party, f.alice, "Alice", "minecraft:overworld", 10, 70, -5).success());
        assertNotNull(party.camp());
        assertEquals(ChronicleEntry.CAMP_SET, party.chronicle().getFirst().type());
        long before = f.balance(f.alice);
        assertTrue(camp.chargeTravel(party, f.alice, Destination.CAMP, false).success());
        assertEquals(before - f.config.camp.campTravelCost, f.balance(f.alice));
        assertEquals("caricraft.result.travel_cooldown", camp.chargeTravel(party, f.alice, Destination.CAMP, false).key());
        f.clock.advance(f.config.camp.cooldownSeconds * 1000L);
        assertTrue(camp.chargeTravel(party, f.alice, Destination.CAMP, false).success());
    }

    @Test
    void blockedDuringChallengeAndWithoutFunds() {
        TestFixture f = withCamp();
        CampService camp = f.core.camp();
        Party party = f.state.partyOf(f.alice);
        camp.setCamp(party, f.alice, "Alice", "minecraft:overworld", 0, 64, 0);
        assertEquals("caricraft.result.travel_challenge", camp.chargeTravel(party, f.alice, Destination.CAMP, true).key());
        f.core.economy().set(party.wallet(), 5, "test", "");
        assertEquals("caricraft.result.insufficient_funds", camp.chargeTravel(party, f.alice, Destination.CAMP, false).key());
        assertEquals(5, f.balance(f.alice));
    }

    @Test
    void campAndPartnerTravelAreSeparateApps() {
        TestFixture f = withCamp();
        Party duo = f.formDuo();
        CampService camp = f.core.camp();
        ActionResult partner = camp.chargeTravel(duo, f.bob, Destination.PARTNER, false);
        assertEquals("caricraft.result.app_locked", partner.key());
        assertEquals(List.of(AppCatalog.TPDUO), partner.args(), "CariCamp ya no incluye los viajes al compañero");

        TestFixture g = TestFixture.withDefaultMissions();
        g.give(g.alice, 2000);
        assertTrue(g.core.shop().buy(g.state, g.alice, AppCatalog.TPDUO).success());
        Party solo = g.state.partyOf(g.alice);
        ActionResult setCamp = g.core.camp().setCamp(solo, g.alice, "Alice", "minecraft:overworld", 0, 64, 0);
        assertEquals("caricraft.result.app_locked", setCamp.key());
        assertEquals(List.of(AppCatalog.CARICAMP), setCamp.args(), "TP Dúo no da campamento");
    }

    @Test
    void partnerTravelNeedsADuoAndRefundWorks() {
        TestFixture f = withCamp();
        assertTrue(f.core.shop().buy(f.state, f.alice, AppCatalog.TPDUO).success());
        CampService camp = f.core.camp();
        assertEquals("caricraft.result.not_in_duo",
                camp.chargeTravel(f.state.partyOf(f.alice), f.alice, Destination.PARTNER, false).key());
        Party duo = f.formDuo();
        long before = duo.wallet().balance();
        assertTrue(camp.chargeTravel(duo, f.bob, Destination.PARTNER, false).success());
        assertEquals(before - f.config.camp.partnerTravelCost, duo.wallet().balance());
        camp.refund(duo, f.bob, Destination.PARTNER);
        assertEquals(before, duo.wallet().balance());
        assertTrue(camp.chargeTravel(duo, f.bob, Destination.PARTNER, false).success(), "la devolución quita la espera");
    }

    @Test
    void campSurvivesSplitForBoth() {
        TestFixture f = withCamp();
        Party duo = f.formDuo();
        f.core.camp().setCamp(duo, f.alice, "Alice", "minecraft:overworld", 3, 64, 3);
        f.core.parties().requestLeave(f.state, f.bob);
        f.core.parties().confirmLeave(f.state, f.bob);
        assertEquals(duo.camp(), f.state.partyOf(f.bob).camp());
    }
}
