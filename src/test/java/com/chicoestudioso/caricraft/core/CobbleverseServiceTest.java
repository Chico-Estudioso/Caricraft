package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseService;
import com.chicoestudioso.caricraft.core.cobbleverse.PokemonPricing;
import com.chicoestudioso.caricraft.core.cobbleverse.PokemonPricing.PokemonInfo;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CobbleverseServiceTest {
    private static CobbleverseCatalog defaults() {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                CobbleverseServiceTest.class.getResourceAsStream("/caricraft/default_cobbleverse.json")), StandardCharsets.UTF_8)) {
            return CobbleverseCatalog.fromJson(reader, item -> true, species -> true);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static CobbleverseService service(TestFixture f) {
        f.config.cobbleverse.enabled = true;
        return new CobbleverseService(f.config, defaults(), f.clock);
    }

    @Test
    void defaultCatalogLoadsAndFiltersWhatDoesNotExist() {
        CobbleverseCatalog all = defaults();
        assertEquals(24, all.offers().size());
        assertEquals(16, all.gifts().size());
        assertEquals(6, all.dailyOffers());
        assertTrue(all.offer("eevee_extremespeed").isPokemon());
        assertEquals(List.of("extremespeed", "quickattack", "bite", "protect"),
                all.offer("eevee_extremespeed").pokemon().movesList());

        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                CobbleverseServiceTest.class.getResourceAsStream("/caricraft/default_cobbleverse.json")), StandardCharsets.UTF_8)) {
            CobbleverseCatalog noBalls = CobbleverseCatalog.fromJson(reader, item -> !item.contains("master_ball"),
                    species -> !species.equals("ditto"));
            assertNull(noBalls.offer("master_ball"), "objeto que no existe en el servidor");
            assertNull(noBalls.offer("ditto_6iv"), "especie que no existe");
            assertTrue(noBalls.gifts().stream().noneMatch(g -> g.id().equals("master_ball")));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void invalidCatalogIsRejected() {
        String json = "{\"offers\":[{\"id\":\"x\",\"price\":10},{\"id\":\"x\",\"item\":\"a\",\"price\":0}],\"gift\":[]}";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CobbleverseCatalog.fromJson(new StringReader(json), i -> true, s -> true));
        assertTrue(e.getMessage().contains("debe tener"));
        assertTrue(e.getMessage().contains("duplicado"));
    }

    @Test
    void offersChangeDailyAndAreTheSameForEveryone() {
        TestFixture f = TestFixture.withDefaultMissions();
        CobbleverseService service = service(f);
        assertEquals(service.offersOfDay(10), service.offersOfDay(10));
        assertEquals(6, service.offersOfDay(10).size());
        assertNotEquals(service.offersOfDay(10), service.offersOfDay(11));
    }

    @Test
    void purchasesRespectBalanceAndDailyLimit() {
        TestFixture f = TestFixture.withDefaultMissions();
        CobbleverseService service = service(f);
        PlayerProfile profile = f.state.profiles().get(f.alice);
        long day = service.day();
        CobbleverseCatalog.Offer offer = service.offersOfDay(day).getFirst();
        assertEquals("caricraft.result.cv_insufficient", service.canBuy(profile, offer.id(), day, offer.price() - 1).key());
        assertTrue(service.canBuy(profile, offer.id(), day, offer.price()).success());
        for (int i = 0; i < offer.dailyLimitOrDefault(); i++) {
            service.recordPurchase(profile, offer.id(), day);
        }
        assertEquals("caricraft.result.cv_sold_out", service.canBuy(profile, offer.id(), day, Long.MAX_VALUE).key());
        assertTrue(service.canBuy(profile, offer.id(), day + 7, Long.MAX_VALUE).success()
                || service.offersOfDay(day + 7).stream().noneMatch(o -> o.id().equals(offer.id())), "otro día, contador nuevo");
        String notToday = defaults().offers().stream().map(CobbleverseCatalog.Offer::id)
                .filter(id -> service.offersOfDay(day).stream().noneMatch(o -> o.id().equals(id))).findFirst().orElseThrow();
        assertEquals("caricraft.result.cv_offer_gone", service.canBuy(profile, notToday, day, Long.MAX_VALUE).key());
    }

    @Test
    void salesAreLimitedPerDay() {
        TestFixture f = TestFixture.withDefaultMissions();
        CobbleverseService service = service(f);
        f.config.cobbleverse.maxSalesPerDay = 2;
        PlayerProfile profile = f.state.profiles().get(f.alice);
        long day = service.day();
        service.recordSale(profile, day);
        service.recordSale(profile, day);
        assertEquals("caricraft.result.cv_sell_limit", service.canSell(profile, day).key());
        assertTrue(service.canSell(profile, day + 1).success());
        service.recordSale(profile, day + 1);
        assertEquals(1, service.salesToday(profile, day + 1));
    }

    @Test
    void mysteryGiftTwicePerDayAndWeighted() {
        TestFixture f = TestFixture.withDefaultMissions();
        CobbleverseService service = service(f);
        PlayerProfile profile = f.state.profiles().get(f.alice);
        long day = service.day();
        assertEquals(2, service.giftsLeft(profile, day));
        assertTrue(service.giftAvailable(profile, day));
        service.recordGift(profile, day);
        assertEquals(1, service.giftsLeft(profile, day));
        assertTrue(service.giftAvailable(profile, day), "el segundo del día");
        service.recordGift(profile, day);
        assertFalse(service.giftAvailable(profile, day));
        assertEquals(0, service.giftsLeft(profile, day));
        assertTrue(service.giftAvailable(profile, day + 1));
        assertEquals(2, service.giftsLeft(profile, day + 1));

        // Datos guardados antes de contar los regalos: el de ese día cuenta como uno.
        PlayerProfile old = f.state.profiles().get(f.bob);
        old.setGiftDay(day);
        assertEquals(1, service.giftsLeft(old, day));
        f.config.cobbleverse.mysteryGift = false;
        assertFalse(service.giftAvailable(profile, day + 1));

        Map<String, Integer> counts = new HashMap<>();
        Random random = new Random(42);
        for (int i = 0; i < 20_000; i++) {
            counts.merge(service.pickGift(random).id(), 1, Integer::sum);
        }
        assertTrue(counts.get("rare_candy") > counts.get("shiny_pokemon") * 5, "lo común sale mucho más que lo raro");
        assertTrue(counts.getOrDefault("master_ball", 0) < 600, "la Master Ball es muy rara");
        assertEquals(16, counts.size(), "todos los premios pueden salir");
    }

    @Test
    void pricesRewardLevelRarityIvsAndShinies() {
        PokemonInfo pidgey = new PokemonInfo("pidgey", 20, 255, 93, false, false);
        PokemonInfo gible = new PokemonInfo("gible", 20, 45, 93, false, false);
        assertEquals(750, PokemonPricing.sellPrice(pidgey, 1.0));
        assertTrue(PokemonPricing.sellPrice(gible, 1.0) > 3 * PokemonPricing.sellPrice(pidgey, 1.0), "rareza");
        assertTrue(PokemonPricing.sellPrice(new PokemonInfo("pidgey", 60, 255, 93, false, false), 1.0)
                > PokemonPricing.sellPrice(pidgey, 1.0), "nivel");
        assertEquals(1130, PokemonPricing.sellPrice(new PokemonInfo("pidgey", 20, 255, 186, false, false), 1.0),
                "IV perfectos: ×1,5 (1125, redondeado a decenas)");
        assertEquals(10 * 750, PokemonPricing.sellPrice(new PokemonInfo("pidgey", 20, 255, 93, true, false), 1.0), "variocolor ×10");
        assertEquals(380, PokemonPricing.sellPrice(pidgey, 0.5), "375 redondeado a decenas");
    }

    @Test
    void sellingIsBlockedForLegendariesLastPokemonAndBattles() {
        TestFixture f = TestFixture.withDefaultMissions();
        CobbleverseService service = service(f);
        PokemonInfo mewtwo = new PokemonInfo("mewtwo", 70, 3, 100, false, true);
        PokemonInfo pidgey = new PokemonInfo("pidgey", 20, 255, 93, false, false);
        assertEquals("legendary", service.sellBlocked(mewtwo, false, false));
        assertEquals("last", service.sellBlocked(pidgey, true, false));
        assertEquals("in_battle", service.sellBlocked(pidgey, false, true));
        assertNull(service.sellBlocked(pidgey, false, false));
        f.config.cobbleverse.sellLegendaries = true;
        assertNull(service.sellBlocked(mewtwo, false, false));
    }

    @Test
    void dailyThingsResetAtLocalMidnight() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.config.timeZone = "Europe/Madrid";
        CobbleverseService service = service(f);
        PlayerProfile profile = f.state.profiles().get(f.alice);
        // 2026-01-01 23:30 en Madrid (22:30 UTC): todavía es día 1 en España.
        f.clock.advance(22 * 3_600_000L + 30 * 60_000L);
        long day = service.day();
        assertEquals(java.time.LocalDate.of(2026, 1, 1).toEpochDay(), day);
        service.recordGift(profile, day);
        service.recordGift(profile, day);
        assertFalse(service.giftAvailable(profile, service.day()));
        assertEquals(TestFixture.START + 23 * 3_600_000L, service.nextDayAt(), "00:00 en Madrid = 23:00 UTC en invierno");
        f.clock.advance(31 * 60_000L); // 00:01 en Madrid
        assertEquals(day + 1, service.day());
        assertTrue(service.giftAvailable(profile, service.day()));
        assertEquals(2, service.giftsLeft(profile, service.day()));
    }

    @Test
    void calendarFollowsSummerTimeAndWeeksStartOnMonday() {
        var madrid = com.chicoestudioso.caricraft.core.GameCalendar.of("Europe/Madrid");
        long summer = java.time.ZonedDateTime.of(2026, 7, 15, 23, 59, 0, 0, java.time.ZoneId.of("Europe/Madrid")).toInstant().toEpochMilli();
        assertEquals(java.time.LocalDate.of(2026, 7, 15).toEpochDay(), madrid.day(summer));
        assertEquals(summer + 60_000L, madrid.nextDayAt(summer), "en verano, 00:00 en Madrid = 22:00 UTC");
        long thursday = java.time.ZonedDateTime.of(2026, 7, 16, 12, 0, 0, 0, java.time.ZoneId.of("Europe/Madrid")).toInstant().toEpochMilli();
        long monday = java.time.ZonedDateTime.of(2026, 7, 20, 0, 0, 0, 0, java.time.ZoneId.of("Europe/Madrid")).toInstant().toEpochMilli();
        assertEquals(monday, madrid.nextWeekAt(thursday));
        assertEquals(madrid.week(thursday) + 1, madrid.week(monday));
        assertEquals(com.chicoestudioso.caricraft.core.GameCalendar.UTC, com.chicoestudioso.caricraft.core.GameCalendar.of("No/Existe"));
    }

    @Test
    void profileCountersSurviveSaving() {
        TestFixture f = TestFixture.withDefaultMissions();
        CobbleverseService service = service(f);
        PlayerProfile profile = f.state.profiles().get(f.alice);
        long day = service.day();
        service.recordGift(profile, day);
        service.recordSale(profile, day);
        service.recordPurchase(profile, "master_ball", day);
        var loaded = com.chicoestudioso.caricraft.server.StateCodec.read(com.chicoestudioso.caricraft.server.StateCodec.write(f.state));
        PlayerProfile copy = loaded.profiles().get(f.alice);
        assertEquals(day, copy.giftDay());
        assertEquals(1, copy.giftsToday());
        assertEquals(1, service.salesToday(copy, day));
        assertEquals(1, service.boughtToday(copy, "master_ball", day));
        assertEquals(-1, loaded.profiles().get(f.bob).giftDay());
        assertFalse(UUID.randomUUID().equals(f.alice));
    }

    @Test
    void wonderTradeOncePerDayFromOtherPlayers() {
        TestFixture f = TestFixture.withDefaultMissions();
        CobbleverseService service = service(f);
        PlayerProfile profile = f.state.profiles().get(f.alice);
        long day = service.day();
        assertEquals(1, service.wondersLeft(profile, day));
        assertTrue(service.canWonderTrade(profile, day).success());
        service.recordWonderTrade(profile, day);
        assertEquals("caricraft.result.cv_wonder_done", service.canWonderTrade(profile, day).key());
        assertTrue(service.canWonderTrade(profile, day + 1).success(), "al día siguiente vuelve");
        f.config.cobbleverse.wonderTradesPerDay = 3;
        assertEquals(2, service.wondersLeft(profile, day));

        Random random = new Random(5);
        assertEquals(-1, CobbleverseService.pickFromPool(List.of(f.alice, f.alice), f.alice, random), "solo propios: se genera");
        for (int i = 0; i < 50; i++) {
            int pick = CobbleverseService.pickFromPool(List.of(f.alice, f.bob, f.alice, f.carol), f.alice, random);
            assertTrue(pick == 1 || pick == 3, "nunca el propio");
        }
        for (int i = 0; i < 50; i++) {
            int level = CobbleverseService.wonderLevel(2, random);
            assertTrue(level >= 1 && level <= 5);
        }
    }
}
