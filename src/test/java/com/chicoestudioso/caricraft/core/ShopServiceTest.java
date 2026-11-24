package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopServiceTest {
    @Test
    void buyingAnAppChargesOnceAndSharesWithDuo() {
        TestFixture f = TestFixture.withDefaultMissions();
        Party duo = f.formDuo();
        f.give(f.alice, 1000);

        ActionResult first = f.core.shop().buy(f.state, f.alice, AppCatalog.CHRONICLES);
        assertTrue(first.success(), first.key());
        assertEquals(750, duo.wallet().balance());
        assertTrue(f.core.shop().hasApp(duo, AppCatalog.CHRONICLES));
        assertEquals(ChronicleEntry.APP_PURCHASED, duo.chronicle().getFirst().type());

        // El otro miembro no puede volver a comprarla: no se cobra dos veces.
        ActionResult second = f.core.shop().buy(f.state, f.bob, AppCatalog.CHRONICLES);
        assertFalse(second.success());
        assertEquals("caricraft.result.already_owned", second.key());
        assertEquals(750, duo.wallet().balance());
    }

    @Test
    void insufficientFundsLeaveEverythingUntouched() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.give(f.alice, 100);
        ActionResult result = f.core.shop().buy(f.state, f.alice, AppCatalog.EXPLORER);
        assertFalse(result.success());
        assertEquals("caricraft.result.insufficient_funds", result.key());
        assertEquals("300", result.args().getFirst());
        assertEquals(100, f.balance(f.alice));
        assertFalse(f.core.shop().hasApp(f.state.partyOf(f.alice), AppCatalog.EXPLORER));
    }

    @Test
    void comingSoonBuiltinAndUnknownAreNotPurchasable() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.give(f.alice, 5000);
        assertEquals("caricraft.result.coming_soon", f.core.shop().buy(f.state, f.alice, AppCatalog.CARILENS).key());
        assertEquals("caricraft.result.already_owned", f.core.shop().buy(f.state, f.alice, AppCatalog.MISSIONS).key());
        assertEquals("caricraft.result.unknown_item", f.core.shop().buy(f.state, f.alice, "hack").key());
        assertEquals(5000, f.balance(f.alice));
    }

    @Test
    void cosmeticsArePersonal() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.formDuo();
        f.give(f.alice, 1000);
        assertTrue(f.core.shop().buy(f.state, f.alice, "theme_ocean").success());
        assertTrue(f.state.profiles().get(f.alice).ownedCosmetics().contains("theme_ocean"));
        assertFalse(f.state.profiles().get(f.bob).ownedCosmetics().contains("theme_ocean"));

        assertTrue(f.core.shop().selectTheme(f.state, f.alice, "theme_ocean").success());
        assertEquals("theme_ocean", f.state.profiles().get(f.alice).theme());
        assertEquals("caricraft.result.not_owned", f.core.shop().selectTheme(f.state, f.bob, "theme_ocean").key());
        // Bob sí puede comprar el mismo tema para él.
        assertTrue(f.core.shop().buy(f.state, f.bob, "theme_ocean").success());
        assertEquals(700, f.balance(f.alice));
    }

    @Test
    void configOverridesPricesAndDisablesItems() {
        TestFixture f = new TestFixture();
        f.config.prices.put(AppCatalog.STATS, 10L);
        f.config.disabledItems.add("theme_forest");
        f.core = new CaricraftCore(f.state, f.config, TestFixture.defaultCatalog(false), f.clock);
        f.core.onPlayerJoin(f.alice, "Alice");
        assertEquals(10, f.core.shop().catalog().get(AppCatalog.STATS).orElseThrow().price());
        assertTrue(f.core.shop().catalog().get("theme_forest").isEmpty());
        f.give(f.alice, 10);
        assertTrue(f.core.shop().buy(f.state, f.alice, AppCatalog.STATS).success());
        assertEquals(0, f.balance(f.alice));
    }
}
