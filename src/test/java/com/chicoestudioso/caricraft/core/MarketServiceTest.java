package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.market.MarketCatalog;
import com.chicoestudioso.caricraft.core.market.MarketOffer;
import com.chicoestudioso.caricraft.core.market.MarketService;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketServiceTest {
    private static final long DAY = 86_400_000L;

    private static MarketCatalog defaults(boolean cobblemon) {
        return MarketCatalog.fromJson(new InputStreamReader(Objects.requireNonNull(
                        MarketServiceTest.class.getResourceAsStream("/caricraft/default_market.json")), StandardCharsets.UTF_8),
                source -> "vanilla".equals(source) || cobblemon, item -> true);
    }

    private static TestFixture fixture(boolean cobblemon) {
        TestFixture f = new TestFixture();
        f.core = new CaricraftCore(f.state, f.config, TestFixture.defaultCatalog(true), defaults(cobblemon), f.clock);
        f.core.onPlayerJoin(f.alice, "Alice");
        f.core.onPlayerJoin(f.bob, "Bob");
        return f;
    }

    @Test
    void cobblemonItemsAreOffOnlyWhenDisabled() {
        assertTrue(defaults(false).pool().stream().allMatch(o -> "vanilla".equals(o.source())));
        assertTrue(defaults(true).pool().stream().anyMatch(o -> "cobblemon".equals(o.source())));
        assertTrue(defaults(false).pool().size() >= 30);
    }

    @Test
    void dailyOffersAreDeterministicUniqueAndRotate() {
        TestFixture f = fixture(false);
        MarketService market = f.core.market();
        List<MarketOffer> today = market.offers(100);
        assertEquals(f.config.market.offersPerDay, today.size());
        assertEquals(today, market.offers(100));
        assertEquals(today.size(), new HashSet<>(today.stream().map(MarketOffer::id).toList()).size());
        List<List<String>> week = new ArrayList<>();
        for (long d = 100; d < 107; d++) {
            week.add(market.offers(d).stream().map(MarketOffer::id).toList());
        }
        assertNotEquals(1, new HashSet<>(week).size(), "las ofertas deben cambiar de un día a otro");
    }

    @Test
    void buyingChargesDeliversAndRespectsStock() {
        TestFixture f = fixture(false);
        MarketService market = f.core.market();
        f.give(f.alice, 100_000);
        MarketOffer offer = market.offers(market.day()).getFirst();
        List<String> delivered = new ArrayList<>();
        for (int i = 0; i < offer.dailyStock(); i++) {
            ActionResult result = market.buy(f.state, f.alice, offer.id(), (item, count) -> delivered.add(item + "x" + count));
            assertTrue(result.success(), result.key());
        }
        assertEquals(offer.dailyStock(), delivered.size());
        assertEquals(offer.item() + "x" + offer.count(), delivered.getFirst());
        assertEquals(100_000 - offer.price() * offer.dailyStock(), f.balance(f.alice));
        assertEquals("caricraft.result.offer_sold_out",
                market.buy(f.state, f.alice, offer.id(), (item, count) -> delivered.add(item)).key());
        // Al día siguiente vuelve a haber stock si la oferta repite.
        f.clock.advance(DAY);
        assertEquals(0, market.bought(f.state.partyOf(f.alice), market.day(), offer.id()));
    }

    @Test
    void insufficientFundsAndExpiredOffersDoNotDeliver() {
        TestFixture f = fixture(false);
        MarketService market = f.core.market();
        MarketOffer offer = market.offers(market.day()).getFirst();
        List<String> delivered = new ArrayList<>();
        assertEquals("caricraft.result.insufficient_funds",
                market.buy(f.state, f.alice, offer.id(), (item, count) -> delivered.add(item)).key());
        String notToday = defaults(false).pool().stream().map(MarketOffer::id)
                .filter(id -> market.offers(market.day()).stream().noneMatch(o -> o.id().equals(id))).findFirst().orElseThrow();
        f.give(f.alice, 10_000);
        assertEquals("caricraft.result.offer_expired",
                market.buy(f.state, f.alice, notToday, (item, count) -> delivered.add(item)).key());
        assertTrue(delivered.isEmpty());
        assertEquals(10_000, f.balance(f.alice));
    }

    @Test
    void failedDeliveryIsRefunded() {
        TestFixture f = fixture(false);
        MarketService market = f.core.market();
        f.give(f.alice, 1000);
        MarketOffer offer = market.offers(market.day()).getFirst();
        ActionResult result = market.buy(f.state, f.alice, offer.id(), (item, count) -> {
            throw new IllegalStateException("inventario roto");
        });
        assertFalse(result.success());
        assertEquals(1000, f.balance(f.alice));
        assertEquals(0, market.bought(f.state.partyOf(f.alice), market.day(), offer.id()));
    }

    @Test
    void stockIsSharedByTheDuo() {
        TestFixture f = fixture(false);
        f.formDuo();
        f.give(f.alice, 100_000);
        MarketService market = f.core.market();
        MarketOffer offer = market.offers(market.day()).getFirst();
        for (int i = 0; i < offer.dailyStock(); i++) {
            market.buy(f.state, i % 2 == 0 ? f.alice : f.bob, offer.id(), (item, count) -> { });
        }
        assertEquals("caricraft.result.offer_sold_out", market.buy(f.state, f.bob, offer.id(), (item, count) -> { }).key());
    }

    @Test
    void invalidMarketFilesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MarketCatalog.of(
                List.of(new MarketOffer("bad id", "minecraft:dirt", 1, 1, 1, 1, null)), s -> true, i -> true));
        assertThrows(IllegalArgumentException.class, () -> MarketCatalog.of(
                List.of(new MarketOffer("free", "minecraft:dirt", 1, 0, 1, 1, null)), s -> true, i -> true));
        // Ítems que no existen en el servidor se descartan sin error.
        assertTrue(MarketCatalog.of(List.of(new MarketOffer("ghost", "mod:ghost", 1, 5, 1, 1, null)), s -> true, i -> false)
                .pool().isEmpty());
    }
}
