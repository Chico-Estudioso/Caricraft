package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.box.BoxService;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoxServiceTest {
    @Test
    void boxGrowsWithUpgradesUpToSixRows() {
        TestFixture f = TestFixture.withDefaultMissions();
        BoxService box = f.core.box();
        Party party = f.state.partyOf(f.alice);
        assertFalse(box.hasApp(party));
        f.give(f.alice, 5000);
        assertEquals("caricraft.result.requirements_missing", f.core.shop().buy(f.state, f.alice, AppCatalog.CARIBOX_ROWS).key());
        assertTrue(f.core.shop().buy(f.state, f.alice, AppCatalog.CARIBOX).success());
        assertTrue(box.hasApp(party));
        assertEquals(27, box.slots(party));
        for (int i = 0; i < 3; i++) {
            assertTrue(f.core.shop().buy(f.state, f.alice, AppCatalog.CARIBOX_ROWS).success());
        }
        assertEquals(6, box.rows(party));
        assertEquals(54, box.slots(party));
        assertEquals("caricraft.result.already_owned", f.core.shop().buy(f.state, f.alice, AppCatalog.CARIBOX_ROWS).key());
        assertEquals(5000 - 400 - 3 * 250, f.balance(f.alice));
    }

    @Test
    void duoSharesTheBoxApp() {
        TestFixture f = TestFixture.withDefaultMissions();
        Party duo = f.formDuo();
        f.give(f.alice, 1000);
        f.core.shop().buy(f.state, f.bob, AppCatalog.CARIBOX);
        assertTrue(f.core.box().hasApp(f.state.partyOf(f.alice)));
        assertTrue(f.core.box().hasApp(duo));
    }
}
