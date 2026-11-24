package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyServiceTest {
    @Test
    void newPlayersGetASoloParty() {
        TestFixture f = TestFixture.withDefaultMissions();
        Party alice = f.state.partyOf(f.alice);
        assertEquals(1, alice.members().size());
        assertFalse(alice.isDuo());
        assertEquals(ChronicleEntry.WELCOME, alice.chronicle().getFirst().type());
        assertNotEquals(alice.id(), f.state.partyOf(f.bob).id());
    }

    @Test
    void acceptingMergesBalanceAppsAndChronicles() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.give(f.alice, 100);
        f.give(f.bob, 300);
        f.core.shop().buy(f.state, f.bob, AppCatalog.CHRONICLES);
        f.core.onBiomeSampled(f.bob, "minecraft:plains", true);
        Party bobSolo = f.state.partyOf(f.bob);

        assertTrue(f.core.parties().invite(f.state, f.alice, f.bob).success());
        assertEquals(1, f.core.parties().incomingInvites(f.state, f.bob).size());
        ActionResult accept = f.core.parties().accept(f.state, f.bob, f.alice);
        assertTrue(accept.success(), accept.key());

        Party duo = f.state.partyOf(f.alice);
        assertEquals(duo.id(), f.state.partyOf(f.bob).id());
        assertTrue(duo.isDuo());
        assertEquals(150, duo.wallet().balance()); // 100 + (300 - 250)
        assertTrue(duo.ownedApps().contains(AppCatalog.CHRONICLES));
        assertTrue(duo.discoveredBiomes().contains("minecraft:plains"));
        assertFalse(f.state.parties().containsKey(bobSolo.id()));
        assertEquals(ChronicleEntry.PARTY_FORMED, duo.chronicle().getFirst().type());
        assertTrue(f.state.invites().isEmpty());
    }

    @Test
    void invitesValidateTargetsAndLimits() {
        TestFixture f = TestFixture.withDefaultMissions();
        assertEquals("caricraft.result.invite_self", f.core.parties().invite(f.state, f.alice, f.alice).key());
        assertTrue(f.core.parties().invite(f.state, f.alice, f.bob).success());
        assertEquals("caricraft.result.invite_duplicate", f.core.parties().invite(f.state, f.alice, f.bob).key());
        f.core.parties().accept(f.state, f.bob, f.alice);
        // Dúo lleno: no se puede invitar a un tercero ni invitar a alguien que ya está en dúo.
        assertEquals("caricraft.result.party_full", f.core.parties().invite(f.state, f.alice, f.carol).key());
        assertEquals("caricraft.result.target_in_party", f.core.parties().invite(f.state, f.carol, f.bob).key());
    }

    @Test
    void invitesExpire() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.core.parties().invite(f.state, f.alice, f.bob);
        f.clock.advance(f.config.inviteExpirySeconds * 1000L + 1);
        assertEquals("caricraft.result.invite_missing", f.core.parties().accept(f.state, f.bob, f.alice).key());
        assertFalse(f.state.partyOf(f.alice).isDuo());
    }

    @Test
    void declineAndCancelRemoveInvites() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.core.parties().invite(f.state, f.alice, f.bob);
        assertTrue(f.core.parties().decline(f.state, f.bob, f.alice).success());
        assertTrue(f.state.invites().isEmpty());
        f.core.parties().invite(f.state, f.alice, f.carol);
        assertTrue(f.core.parties().cancel(f.state, f.alice, f.carol).success());
        assertEquals("caricraft.result.invite_missing", f.core.parties().accept(f.state, f.carol, f.alice).key());
    }

    @Test
    void leavingRequiresConfirmationAndSplitsHalf() {
        TestFixture f = TestFixture.withDefaultMissions();
        Party duo = f.formDuo();
        f.give(f.alice, 1001);
        f.core.shop().buy(f.state, f.alice, AppCatalog.EXPLORER);
        long before = duo.wallet().balance(); // 601

        assertEquals("caricraft.result.leave_not_requested", f.core.parties().confirmLeave(f.state, f.bob).key());
        assertTrue(f.core.parties().requestLeave(f.state, f.bob).success());
        assertTrue(f.core.parties().leavePending(f.state, f.bob));
        ActionResult left = f.core.parties().confirmLeave(f.state, f.bob);
        assertTrue(left.success(), left.key());

        Party bobSolo = f.state.partyOf(f.bob);
        assertNotEquals(duo.id(), bobSolo.id());
        assertEquals(before / 2, bobSolo.wallet().balance());
        assertEquals(before - before / 2, duo.wallet().balance());
        assertEquals(before, duo.wallet().balance() + bobSolo.wallet().balance());
        assertTrue(bobSolo.ownedApps().contains(AppCatalog.EXPLORER));
        assertTrue(duo.ownedApps().contains(AppCatalog.EXPLORER));
        assertFalse(duo.isDuo());
        assertEquals(ChronicleEntry.PARTY_SPLIT, bobSolo.chronicle().getFirst().type());
    }

    @Test
    void confirmationWindowExpires() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.formDuo();
        f.core.parties().requestLeave(f.state, f.bob);
        f.clock.advance(11_000);
        assertEquals("caricraft.result.leave_not_requested", f.core.parties().confirmLeave(f.state, f.bob).key());
        assertTrue(f.state.partyOf(f.alice).isDuo());
    }

    @Test
    void cooldownAfterLeaving() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.formDuo();
        f.core.parties().requestLeave(f.state, f.bob);
        f.core.parties().confirmLeave(f.state, f.bob);
        assertEquals("caricraft.result.cooldown_self", f.core.parties().invite(f.state, f.bob, f.carol).key());
        assertEquals("caricraft.result.cooldown_target", f.core.parties().invite(f.state, f.carol, f.alice).key());
        f.clock.advance(f.config.unlinkCooldownHours * 3_600_000L + 1);
        assertTrue(f.core.parties().invite(f.state, f.bob, f.carol).success());
    }

    @Test
    void namesUpdateOnJoin() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.core.onPlayerJoin(f.alice, "AliceRenamed");
        assertEquals("AliceRenamed", f.state.nameOf(f.alice));
        assertEquals(1, f.state.partyOf(f.alice).members().size());
    }
}
