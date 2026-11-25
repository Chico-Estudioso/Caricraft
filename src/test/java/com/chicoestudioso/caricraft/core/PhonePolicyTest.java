package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.party.PlayerProfile;
import com.chicoestudioso.caricraft.core.phone.PhonePolicy;
import com.chicoestudioso.caricraft.core.phone.PhonePolicy.Action;
import com.chicoestudioso.caricraft.core.phone.PhonePolicy.PhoneTag;
import com.chicoestudioso.caricraft.core.phone.PhonePolicy.Plan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhonePolicyTest {
    private final UUID alice = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final UUID bob = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private PlayerProfile profile(int serial) {
        PlayerProfile profile = new PlayerProfile(alice, "Alice");
        profile.setPhoneSerial(serial);
        return profile;
    }

    private PhoneTag mine(int serial) {
        return new PhoneTag(alice, "Alice", serial);
    }

    @Test
    void firstPhoneIsIssuedRightAway() {
        PlayerProfile profile = profile(0);
        Plan plan = PhonePolicy.plan(profile, List.of(), true);
        assertTrue(plan.issueNew());
        assertEquals(1, plan.serial());
        assertEquals(1, profile.phoneSerial());
    }

    @Test
    void validPhoneIsKeptAndNothingChanges() {
        PlayerProfile profile = profile(3);
        Plan plan = PhonePolicy.plan(profile, List.of(mine(3)), true);
        assertEquals(List.of(Action.KEEP), plan.actions());
        assertFalse(plan.issueNew());
        assertFalse(plan.missing());
        assertFalse(plan.changesAnything());
        assertEquals(3, profile.phoneSerial());
    }

    @Test
    void onlyOneValidPhoneSurvives() {
        PlayerProfile profile = profile(2);
        List<PhoneTag> carried = List.of(mine(1), mine(2), mine(2), new PhoneTag(bob, "Bob", 7), PhoneTag.unbound());
        Plan plan = PhonePolicy.plan(profile, carried, true);
        assertEquals(List.of(Action.REMOVE, Action.KEEP, Action.REMOVE, Action.REMOVE, Action.REMOVE), plan.actions(),
                "serie antigua, duplicado, ajeno y sin dueño se retiran");
        assertFalse(plan.issueNew());
        assertEquals(2, profile.phoneSerial());
    }

    @Test
    void unboundPhoneIsBoundWithANewSerial() {
        PlayerProfile profile = profile(1);
        Plan plan = PhonePolicy.plan(profile, List.of(new PhoneTag(bob, "Bob", 1), PhoneTag.unbound(), PhoneTag.unbound()), true);
        assertEquals(List.of(Action.REMOVE, Action.BIND, Action.REMOVE), plan.actions());
        assertEquals(2, plan.serial(), "el teléfono perdido con la serie 1 deja de valer");
        assertFalse(plan.issueNew());
    }

    @Test
    void missingPhoneWaitsUntilAskedThenInvalidatesTheOldOne() {
        PlayerProfile profile = profile(4);
        Plan waiting = PhonePolicy.plan(profile, List.of(), false);
        assertTrue(waiting.missing());
        assertFalse(waiting.issueNew());
        assertEquals(4, profile.phoneSerial(), "mientras espera no cambia nada");

        Plan replaced = PhonePolicy.plan(profile, List.of(), true);
        assertTrue(replaced.issueNew());
        assertEquals(5, replaced.serial());

        // El viejo (serie 4) aparece más tarde: se retira.
        Plan later = PhonePolicy.plan(profile, List.of(mine(4), mine(5)), true);
        assertEquals(List.of(Action.REMOVE, Action.KEEP), later.actions());
    }

    @Test
    void foreignPhonesNeverCount() {
        PlayerProfile profile = profile(1);
        Plan plan = PhonePolicy.plan(profile, List.of(new PhoneTag(bob, "Bob", 1)), true);
        assertEquals(List.of(Action.REMOVE), plan.actions());
        assertTrue(plan.issueNew());
        assertEquals(2, plan.serial());
    }

    @Test
    void lostWorldDataAdoptsTheNewerPhone() {
        PlayerProfile profile = profile(0);
        Plan plan = PhonePolicy.plan(profile, List.of(mine(6)), true);
        assertEquals(List.of(Action.KEEP), plan.actions());
        assertEquals(6, profile.phoneSerial());
    }
}
