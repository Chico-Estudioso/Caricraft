package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.camp.CampService;
import com.chicoestudioso.caricraft.core.camp.TravelRequest;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelRequestTest {
    private TestFixture duoWithTpDuo() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.formDuo();
        f.give(f.alice, 2000);
        assertTrue(f.core.shop().buy(f.state, f.alice, AppCatalog.TPDUO).success());
        return f;
    }

    @Test
    void needsTpDuoNotCariCamp() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.formDuo();
        f.give(f.alice, 2000);
        f.core.shop().buy(f.state, f.alice, AppCatalog.CARICAMP);
        ActionResult result = f.core.camp().request(f.state, f.alice, f.bob, TravelRequest.Kind.GO, false);
        assertEquals("caricraft.result.app_locked", result.key());
        assertEquals(java.util.List.of(AppCatalog.TPDUO), result.args());
        assertTrue(f.state.travelRequests().isEmpty());
    }

    @Test
    void requestMustBeAcceptedByThePartner() {
        TestFixture f = duoWithTpDuo();
        CampService camp = f.core.camp();
        assertTrue(camp.request(f.state, f.alice, f.bob, TravelRequest.Kind.GO, false).success());
        assertTrue(camp.incoming(f.state, f.bob).isPresent());
        assertTrue(camp.outgoing(f.state, f.alice).isPresent());
        // Alice no puede aceptar su propia petición.
        assertEquals("caricraft.result.travel_request_missing", camp.accept(f.state, f.alice).result().key());
        CampService.Acceptance acceptance = camp.accept(f.state, f.bob);
        assertTrue(acceptance.result().success());
        assertEquals(f.alice, acceptance.request().traveler());
        assertEquals(f.bob, acceptance.request().destination());
        assertFalse(camp.incoming(f.state, f.bob).isPresent());
    }

    @Test
    void bringMakesThePartnerTravel() {
        TestFixture f = duoWithTpDuo();
        f.core.camp().request(f.state, f.alice, f.bob, TravelRequest.Kind.BRING, false);
        TravelRequest request = f.core.camp().accept(f.state, f.bob).request();
        assertEquals(f.bob, request.traveler());
        assertEquals(f.alice, request.destination());
    }

    @Test
    void requestsExpireAndCanBeDeclined() {
        TestFixture f = duoWithTpDuo();
        CampService camp = f.core.camp();
        camp.request(f.state, f.alice, f.bob, TravelRequest.Kind.GO, false);
        assertTrue(camp.decline(f.state, f.bob).success());
        assertNull(camp.accept(f.state, f.bob).request());
        camp.request(f.state, f.alice, f.bob, TravelRequest.Kind.GO, false);
        f.clock.advance(f.config.camp.requestSeconds * 1000L + 1);
        assertEquals("caricraft.result.travel_request_missing", camp.accept(f.state, f.bob).result().key());
    }

    @Test
    void rulesAreCheckedBeforeAsking() {
        TestFixture f = duoWithTpDuo();
        CampService camp = f.core.camp();
        assertEquals("caricraft.result.travel_challenge", camp.request(f.state, f.alice, f.bob, TravelRequest.Kind.GO, true).key());
        assertEquals("caricraft.result.not_in_duo", camp.request(f.state, f.alice, f.carol, TravelRequest.Kind.GO, false).key());
        Party duo = f.state.partyOf(f.alice);
        f.core.economy().set(duo.wallet(), 10, "test", "");
        assertEquals("caricraft.result.insufficient_funds", camp.request(f.state, f.alice, f.bob, TravelRequest.Kind.GO, false).key());
        f.core.economy().set(duo.wallet(), 1000, "test", "");
        // El viajero en espera no puede volver a viajar.
        camp.chargeTravel(duo, f.alice, CampService.Destination.PARTNER, false);
        assertEquals("caricraft.result.travel_cooldown", camp.request(f.state, f.alice, f.bob, TravelRequest.Kind.GO, false).key());
        assertTrue(camp.request(f.state, f.alice, f.bob, TravelRequest.Kind.BRING, false).success(), "trae a Bob, que no espera");
    }

    @Test
    void newRequestReplacesThePreviousOne() {
        TestFixture f = duoWithTpDuo();
        CampService camp = f.core.camp();
        camp.request(f.state, f.alice, f.bob, TravelRequest.Kind.GO, false);
        camp.request(f.state, f.alice, f.bob, TravelRequest.Kind.BRING, false);
        assertEquals(1, f.state.travelRequests().size());
        assertEquals(TravelRequest.Kind.BRING, camp.incoming(f.state, f.bob).orElseThrow().kind());
    }
}
