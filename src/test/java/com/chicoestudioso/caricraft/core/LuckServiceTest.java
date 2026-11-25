package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Crate;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Fusion;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Slots;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Upgrade;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseService;
import com.chicoestudioso.caricraft.core.cobbleverse.LuckService;
import com.chicoestudioso.caricraft.core.cobbleverse.LuckService.MonState;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuckServiceTest {
    private static CobbleverseCatalog defaults() {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                LuckServiceTest.class.getResourceAsStream("/caricraft/default_cobbleverse.json")), StandardCharsets.UTF_8)) {
            return CobbleverseCatalog.fromJson(reader, item -> true, species -> true);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static LuckService luck(TestFixture f) {
        f.config.cobbleverse.enabled = true;
        return new LuckService(new CobbleverseService(f.config, defaults(), f.clock));
    }

    @Test
    void defaultCatalogHasCratesSlotsUpgradesAndFusions() {
        CobbleverseCatalog all = defaults();
        assertEquals(3, all.crates().size());
        assertEquals(1000, all.crate("crate_poke").totalWeight());
        assertEquals("legendary", all.crate("crate_master").rewards().getLast().rarityOrDefault());
        assertEquals("cobblemon:cherish_ball", all.crate("crate_poke").rewards().getLast().iconOrDefault(), "Pokémon variocolor");
        assertEquals(6, all.slots().symbols().size());
        assertEquals(3, all.upgrades().size());
        assertEquals(5, all.fusions().size());
    }

    @Test
    void oldCatalogFilesGetTheNewSectionsFromTheMod() {
        String old = "{\"offers\":[],\"gift\":[]}";
        CobbleverseCatalog custom = CobbleverseCatalog.fromJson(new StringReader(old), i -> true, s -> true);
        assertTrue(custom.crates().isEmpty());
        CobbleverseCatalog merged = custom.withMissingFrom(defaults());
        assertEquals(3, merged.crates().size());
        assertEquals(6, merged.slots().symbols().size());
        assertTrue(merged.offers().isEmpty(), "lo que sí trae el archivo se respeta");

        String noCrates = "{\"offers\":[],\"gift\":[],\"crates\":[]}";
        assertTrue(CobbleverseCatalog.fromJson(new StringReader(noCrates), i -> true, s -> true)
                .withMissingFrom(defaults()).crates().isEmpty(), "una lista vacía a propósito no se rellena");
    }

    @Test
    void missingItemsAreFilteredFromCratesAndFusions() {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                LuckServiceTest.class.getResourceAsStream("/caricraft/default_cobbleverse.json")), StandardCharsets.UTF_8)) {
            CobbleverseCatalog noMaster = CobbleverseCatalog.fromJson(reader, item -> !item.contains("master_ball"), s -> true);
            assertTrue(noMaster.crate("crate_ultra").rewards().stream().noneMatch(r -> r.id().equals("master_ball")));
            assertNull(noMaster.fusion("master_ball"));
            assertEquals("minecraft:chest", noMaster.crate("crate_master").icon(), "icono que no existe");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void cratesRespectBalanceDailyLimitAndWeights() {
        TestFixture f = TestFixture.withDefaultMissions();
        LuckService luck = luck(f);
        PlayerProfile profile = f.state.profiles().get(f.alice);
        long day = Math.floorDiv(f.clock.now(), CobbleverseService.DAY_MS);
        Crate crate = defaults().crate("crate_master");
        assertEquals("caricraft.result.cv_insufficient", luck.canOpenCrate(profile, crate.id(), day, crate.price() - 1).key());
        assertTrue(luck.canOpenCrate(profile, crate.id(), day, crate.price()).success());
        for (int i = 0; i < crate.dailyLimitOrDefault(); i++) {
            luck.recordCrate(profile, crate.id(), day);
        }
        assertEquals("caricraft.result.cv_crate_limit", luck.canOpenCrate(profile, crate.id(), day, Long.MAX_VALUE).key());
        assertTrue(luck.canOpenCrate(profile, crate.id(), day + 1, Long.MAX_VALUE).success(), "otro día");
        assertTrue(luck.canOpenCrate(profile, "crate_poke", day, Long.MAX_VALUE).success(), "cada caja tiene su límite");
        assertEquals("caricraft.result.cv_offer_gone", luck.canOpenCrate(profile, "nope", day, Long.MAX_VALUE).key());

        // Los premios salen con sus pesos.
        Random random = new Random(42);
        int[] hits = new int[crate.rewards().size()];
        for (int i = 0; i < 100_000; i++) {
            hits[LuckService.pickReward(crate, random)]++;
        }
        for (int i = 0; i < hits.length; i++) {
            double expected = LuckService.chance(crate, crate.rewards().get(i));
            assertEquals(expected, hits[i] / 100_000.0, 0.01, crate.rewards().get(i).id());
        }

        f.config.cobbleverse.crates = false;
        assertFalse(luck.cratesEnabled());
        assertEquals("caricraft.result.cv_offer_gone", luck.canOpenCrate(profile, "crate_poke", day + 2, Long.MAX_VALUE).key());
    }

    @Test
    void slotMachinePaysAsDocumentedAndTheHouseWins() {
        Slots slots = defaults().slots();
        double rtp = LuckService.returnToPlayer(slots);
        assertTrue(rtp > 0.85 && rtp < 0.95, "devuelve entre el 85 y el 95 %: " + rtp);
        assertEquals(200, LuckService.multiplier(slots, new int[]{5, 5, 5}));
        assertEquals(1.5, LuckService.multiplier(slots, new int[]{2, 4, 2}), "dos iguales, separados");
        assertEquals(3, LuckService.multiplier(slots, new int[]{0, 4, 4}));
        assertEquals(0, LuckService.multiplier(slots, new int[]{0, 1, 2}));
        assertEquals(500, LuckService.payout(slots, new int[]{0, 0, 1}, 1000));

        // La simulación coincide con el cálculo exacto.
        Random random = new Random(7);
        double paid = 0;
        int spins = 400_000;
        for (int i = 0; i < spins; i++) {
            paid += LuckService.multiplier(slots, LuckService.spin(slots, random));
        }
        assertEquals(rtp, paid / spins, 0.03);
    }

    @Test
    void spinsAreLimitedPerDayAndBetsComeFromTheCatalog() {
        TestFixture f = TestFixture.withDefaultMissions();
        LuckService luck = luck(f);
        PlayerProfile profile = f.state.profiles().get(f.alice);
        long day = 100;
        assertEquals(1000, luck.bet(0));
        assertEquals(-1, luck.bet(3));
        assertEquals("caricraft.result.cv_offer_gone", luck.canSpin(profile, 9, day, Long.MAX_VALUE).key());
        assertEquals("caricraft.result.cv_insufficient", luck.canSpin(profile, 2, day, 19_999).key());
        for (int i = 0; i < 30; i++) {
            assertTrue(luck.canSpin(profile, 0, day, 1000).success());
            luck.recordSpin(profile, day);
        }
        assertEquals(0, luck.spinsLeft(profile, day));
        assertEquals("caricraft.result.cv_spins_done", luck.canSpin(profile, 0, day, 1000).key());
        assertEquals(30, luck.spinsLeft(profile, day + 1));
    }

    @Test
    void upgradesKnowWhenAPokemonIsMaxed() {
        Upgrade iv = new Upgrade("iv", "iv", 100, null, null);
        Upgrade level = new Upgrade("lv", "level", 100, 5, null);
        Upgrade shiny = new Upgrade("sh", "shiny", 100, null, 0.08);
        MonState mon = new MonState(98, false, new int[]{31, 12, 31, 4, 4, 31}, false);
        assertNull(LuckService.upgradeBlocked(iv, mon, false));
        assertEquals(3, LuckService.lowestIv(mon.ivs()), "el más bajo; si empatan, el primero");
        assertEquals(100, LuckService.levelAfter(level, 98), "no pasa de 100");
        assertEquals("in_battle", LuckService.upgradeBlocked(level, mon, true));
        MonState perfect = new MonState(100, true, new int[]{31, 31, 31, 31, 31, 31}, false);
        assertEquals(-1, LuckService.lowestIv(perfect.ivs()));
        assertEquals("maxed", LuckService.upgradeBlocked(iv, perfect, false));
        assertEquals("maxed", LuckService.upgradeBlocked(level, perfect, false));
        assertEquals("maxed", LuckService.upgradeBlocked(shiny, perfect, false));

        Random random = new Random(3);
        int wins = 0;
        for (int i = 0; i < 100_000; i++) {
            wins += LuckService.roll(shiny.chanceOrOne(), random) ? 1 : 0;
        }
        assertEquals(0.08, wins / 100_000.0, 0.005);
        assertTrue(LuckService.roll(iv.chanceOrOne(), random), "sin probabilidad, siempre sale");
    }

    @Test
    void fusionsNeedTheIngredientsAndTheMoney() {
        TestFixture f = TestFixture.withDefaultMissions();
        LuckService luck = luck(f);
        Fusion master = defaults().fusion("master_ball");
        assertEquals(0.1, master.chanceOrOne());
        var missing = luck.canFuse(master, Map.of("cobblemon:ultra_ball", 5), Long.MAX_VALUE);
        assertEquals("caricraft.result.cv_fuse_missing", missing.key());
        assertEquals("3", missing.args().getFirst());
        assertEquals("caricraft.result.cv_insufficient", luck.canFuse(master, Map.of("cobblemon:ultra_ball", 8), 19_999).key());
        assertTrue(luck.canFuse(master, Map.of("cobblemon:ultra_ball", 64), 20_000).success());
    }
}
