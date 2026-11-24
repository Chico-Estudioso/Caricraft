package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.mission.MissionCategory;
import com.chicoestudioso.caricraft.core.mission.MissionDefinition;
import com.chicoestudioso.caricraft.core.mission.MissionScope;
import com.chicoestudioso.caricraft.core.mission.MissionStatus;
import com.chicoestudioso.caricraft.core.mission.Objective;
import com.chicoestudioso.caricraft.core.mission.ObjectiveType;
import com.chicoestudioso.caricraft.core.mission.ProgressEvent;
import com.chicoestudioso.caricraft.core.party.Party;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionServiceTest {
    private static final long DAY = 86_400_000L;

    private static MissionDefinition def(String id, MissionScope scope, MissionCategory category, long reward,
                                         boolean allMembers, List<String> requires, Objective... objectives) {
        return new MissionDefinition(id, scope, category, reward, List.of(objectives), requires, null, null,
                allMembers, null, null, null);
    }

    private static MissionDefinition walk(String id, MissionScope scope, MissionCategory category, long meters) {
        return def(id, scope, category, 100, false, List.of(), new Objective(ObjectiveType.TRAVEL, "", meters));
    }

    private MissionStatus status(TestFixture f, UUID player, String id) {
        return f.core.missions().statusFor(f.state.partyOf(player), player,
                f.core.missions().catalog().get(id).orElseThrow());
    }

    @Test
    void fullLifecycleAndIdempotentClaim() {
        TestFixture f = TestFixture.withMissions(List.of(walk("walk", MissionScope.INDIVIDUAL, MissionCategory.STORY, 100)));
        assertEquals(MissionStatus.AVAILABLE, status(f, f.alice, "walk"));
        // El progreso antes de aceptar no cuenta.
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.TRAVEL, 50));
        assertTrue(f.core.missions().accept(f.state, f.alice, "walk").success());
        assertEquals(MissionStatus.ACCEPTED, status(f, f.alice, "walk"));
        assertEquals("caricraft.result.mission_not_completed", f.core.missions().claim(f.state, f.alice, "walk").key());

        List<MissionDefinition> done = f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.TRAVEL, 120));
        assertEquals(1, done.size());
        assertEquals(MissionStatus.COMPLETED, status(f, f.alice, "walk"));
        assertEquals(ChronicleEntry.MISSION_COMPLETED, f.state.partyOf(f.alice).chronicle().getFirst().type());

        assertTrue(f.core.missions().claim(f.state, f.alice, "walk").success());
        assertEquals(100, f.balance(f.alice));
        // Doble clic / reintento: no paga dos veces.
        assertEquals("caricraft.result.mission_already_claimed", f.core.missions().claim(f.state, f.alice, "walk").key());
        assertEquals("caricraft.result.mission_already_claimed", f.core.missions().accept(f.state, f.alice, "walk").key());
        assertEquals(100, f.balance(f.alice));
        assertEquals(MissionStatus.CLAIMED, status(f, f.alice, "walk"));
    }

    @Test
    void abandonResetsProgress() {
        TestFixture f = TestFixture.withMissions(List.of(walk("walk", MissionScope.INDIVIDUAL, MissionCategory.STORY, 100)));
        f.core.missions().accept(f.state, f.alice, "walk");
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.TRAVEL, 60));
        assertTrue(f.core.missions().abandon(f.state, f.alice, "walk").success());
        assertEquals(MissionStatus.AVAILABLE, status(f, f.alice, "walk"));
        f.core.missions().accept(f.state, f.alice, "walk");
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.TRAVEL, 60));
        assertEquals(MissionStatus.ACCEPTED, status(f, f.alice, "walk"));
    }

    @Test
    void individualMissionsAreTrackedPerMember() {
        TestFixture f = TestFixture.withMissions(List.of(walk("walk", MissionScope.INDIVIDUAL, MissionCategory.STORY, 100)));
        Party duo = f.formDuo();
        f.core.missions().accept(f.state, f.alice, "walk");
        f.core.missions().accept(f.state, f.bob, "walk");
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.TRAVEL, 100));
        assertEquals(MissionStatus.COMPLETED, status(f, f.alice, "walk"));
        assertEquals(MissionStatus.ACCEPTED, status(f, f.bob, "walk"));
        f.core.missions().claim(f.state, f.alice, "walk");
        // La recompensa individual va al saldo compartido.
        assertEquals(100, duo.wallet().balance());
    }

    @Test
    void coopMissionsNeedADuoAndBothContributions() {
        MissionDefinition coop = def("together", MissionScope.COOP, MissionCategory.STORY, 200, true, List.of(),
                new Objective(ObjectiveType.TRAVEL, "", 100));
        TestFixture f = TestFixture.withMissions(List.of(coop));
        assertEquals(MissionStatus.LOCKED, status(f, f.alice, "together"));
        assertEquals("caricraft.result.mission_needs_duo", f.core.missions().accept(f.state, f.alice, "together").key());

        f.formDuo();
        assertTrue(f.core.missions().accept(f.state, f.alice, "together").success());
        // Bob ve la misma misión aceptada.
        assertEquals(MissionStatus.ACCEPTED, status(f, f.bob, "together"));
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.TRAVEL, 500));
        // Progreso lleno pero Bob no ha aportado: aún no está completada.
        assertEquals(MissionStatus.ACCEPTED, status(f, f.alice, "together"));
        f.core.progress(f.bob, ProgressEvent.of(ObjectiveType.TRAVEL, 1));
        assertEquals(MissionStatus.COMPLETED, status(f, f.bob, "together"));
        assertTrue(f.core.missions().claim(f.state, f.bob, "together").success());
        assertEquals("caricraft.result.mission_already_claimed", f.core.missions().claim(f.state, f.alice, "together").key());
        assertEquals(200, f.balance(f.alice));
    }

    @Test
    void requirementsLockMissions() {
        MissionDefinition first = walk("first", MissionScope.INDIVIDUAL, MissionCategory.STORY, 10);
        MissionDefinition second = def("second", MissionScope.INDIVIDUAL, MissionCategory.STORY, 10, false,
                List.of("first"), new Objective(ObjectiveType.TRAVEL, "", 10));
        TestFixture f = TestFixture.withMissions(List.of(first, second));
        assertEquals(MissionStatus.LOCKED, status(f, f.alice, "second"));
        assertEquals("caricraft.result.mission_locked", f.core.missions().accept(f.state, f.alice, "second").key());
        f.core.missions().accept(f.state, f.alice, "first");
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.TRAVEL, 10));
        f.core.missions().claim(f.state, f.alice, "first");
        assertEquals(MissionStatus.AVAILABLE, status(f, f.alice, "second"));
        assertEquals(MissionStatus.LOCKED, status(f, f.bob, "second"));
    }

    @Test
    void targetsMatchIdsTagsAndFlags() {
        MissionDefinition logs = def("logs", MissionScope.INDIVIDUAL, MissionCategory.STORY, 10, false, List.of(),
                new Objective(ObjectiveType.MINE_BLOCK, "#minecraft:logs", 3));
        MissionDefinition shiny = def("shiny", MissionScope.INDIVIDUAL, MissionCategory.STORY, 10, false, List.of(),
                new Objective(ObjectiveType.CATCH_POKEMON, "shiny", 1));
        TestFixture f = TestFixture.withMissions(List.of(logs, shiny));
        f.core.missions().accept(f.state, f.alice, "logs");
        f.core.missions().accept(f.state, f.alice, "shiny");
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.MINE_BLOCK, 1, "minecraft:stone", "#minecraft:base_stone_overworld"));
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.MINE_BLOCK, 3, "minecraft:oak_log", "#minecraft:logs"));
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.CATCH_POKEMON, 1, "cobblemon:pidgey"));
        assertEquals(MissionStatus.COMPLETED, status(f, f.alice, "logs"));
        assertEquals(MissionStatus.ACCEPTED, status(f, f.alice, "shiny"));
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.CATCH_POKEMON, 1, "cobblemon:pidgey", "shiny"));
        assertEquals(MissionStatus.COMPLETED, status(f, f.alice, "shiny"));
        assertEquals(2, f.state.partyOf(f.alice).stat("pokemon_caught"));
        assertEquals(4, f.state.partyOf(f.alice).stat("blocks_mined"));
    }

    @Test
    void milestonesActivateThemselves() {
        MissionDefinition milestone = def("m", MissionScope.PARTY, MissionCategory.MILESTONE, 300, false, List.of(),
                new Objective(ObjectiveType.DISCOVER_BIOME, "", 2));
        TestFixture f = TestFixture.withMissions(List.of(milestone));
        assertEquals("caricraft.result.milestone_auto", f.core.missions().accept(f.state, f.alice, "m").key());
        f.core.onBiomeSampled(f.alice, "minecraft:plains", true);
        f.core.onBiomeSampled(f.alice, "minecraft:plains", false); // repetido: no cuenta
        assertEquals(MissionStatus.ACCEPTED, status(f, f.alice, "m"));
        f.core.onBiomeSampled(f.alice, "minecraft:forest", true);
        assertEquals(MissionStatus.COMPLETED, status(f, f.alice, "m"));
        assertTrue(f.core.missions().claim(f.state, f.alice, "m").success());
        f.core.onBiomeSampled(f.alice, "minecraft:desert", true);
        assertEquals(MissionStatus.CLAIMED, status(f, f.alice, "m"));
        assertEquals(300, f.balance(f.alice));
    }

    @Test
    void dailyMissionsExpireAndRepeat() {
        TestFixture f = TestFixture.withMissions(List.of(walk("daily", MissionScope.INDIVIDUAL, MissionCategory.DAILY, 10)));
        f.core.missions().accept(f.state, f.alice, "daily");
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.TRAVEL, 5));
        f.clock.advance(DAY);
        // Al cambiar de día lo no completado caduca.
        assertEquals(MissionStatus.AVAILABLE, status(f, f.alice, "daily"));

        f.core.missions().accept(f.state, f.alice, "daily");
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.TRAVEL, 10));
        f.core.missions().claim(f.state, f.alice, "daily");
        assertEquals(MissionStatus.CLAIMED, status(f, f.alice, "daily"));
        assertEquals("caricraft.result.mission_already_claimed", f.core.missions().accept(f.state, f.alice, "daily").key());
        f.clock.advance(DAY);
        assertEquals(MissionStatus.AVAILABLE, status(f, f.alice, "daily"));
        f.core.missions().accept(f.state, f.alice, "daily");
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.TRAVEL, 10));
        assertTrue(f.core.missions().claim(f.state, f.alice, "daily").success());
        assertEquals(200, f.balance(f.alice));
    }

    @Test
    void completedDailyStaysClaimableAfterCycleEnds() {
        TestFixture f = TestFixture.withMissions(List.of(walk("daily", MissionScope.INDIVIDUAL, MissionCategory.DAILY, 10)));
        f.core.missions().accept(f.state, f.alice, "daily");
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.TRAVEL, 10));
        f.clock.advance(DAY);
        assertEquals(MissionStatus.COMPLETED, status(f, f.alice, "daily"));
        assertTrue(f.core.missions().claim(f.state, f.alice, "daily").success());
        assertEquals(MissionStatus.AVAILABLE, status(f, f.alice, "daily"));
    }

    @Test
    void weeklyCycleStartsOnMonday() {
        // 2026-01-01 es jueves; el lunes siguiente es el 5 de enero.
        long thursday = TestFixture.START;
        long sunday = thursday + 3 * DAY + DAY - 1;
        long monday = thursday + 4 * DAY;
        assertEquals(MissionCategory.WEEKLY.cycle(thursday), MissionCategory.WEEKLY.cycle(sunday));
        assertEquals(MissionCategory.WEEKLY.cycle(thursday) + 1, MissionCategory.WEEKLY.cycle(monday));
        assertEquals(monday, MissionCategory.WEEKLY.cycleEnd(thursday));
        assertEquals(thursday + DAY, MissionCategory.DAILY.cycleEnd(thursday));
    }

    @Test
    void deliveriesTakeOnlyWhatIsNeeded() {
        MissionDefinition supplies = def("supplies", MissionScope.INDIVIDUAL, MissionCategory.STORY, 50, false, List.of(),
                new Objective(ObjectiveType.DELIVER_ITEM, "minecraft:iron_ingot", 10),
                new Objective(ObjectiveType.DELIVER_ITEM, "#minecraft:logs", 5));
        TestFixture f = TestFixture.withMissions(List.of(supplies));
        Map<String, Long> inventory = new HashMap<>(Map.of("minecraft:iron_ingot", 7L, "#minecraft:logs", 64L));
        com.chicoestudioso.caricraft.core.mission.InventoryAccess access = (target, max) -> {
            long available = inventory.getOrDefault(target, 0L);
            long taken = Math.min(available, max);
            inventory.put(target, available - taken);
            return taken;
        };
        assertEquals("caricraft.result.mission_not_active", f.core.missions().deliver(f.state, f.alice, "supplies", access).key());
        f.core.missions().accept(f.state, f.alice, "supplies");
        ActionResult partial = f.core.missions().deliver(f.state, f.alice, "supplies", access);
        assertEquals("caricraft.result.delivered", partial.key());
        assertEquals(0L, inventory.get("minecraft:iron_ingot"));
        assertEquals(59L, inventory.get("#minecraft:logs"));
        assertEquals("caricraft.result.nothing_to_deliver", f.core.missions().deliver(f.state, f.alice, "supplies", access).key());
        inventory.put("minecraft:iron_ingot", 5L);
        assertEquals("caricraft.result.mission_completed", f.core.missions().deliver(f.state, f.alice, "supplies", access).key());
        assertEquals(2L, inventory.get("minecraft:iron_ingot"));
    }

    @Test
    void rewardMultiplierApplies() {
        TestFixture f = TestFixture.withMissions(List.of(walk("walk", MissionScope.INDIVIDUAL, MissionCategory.STORY, 10)));
        f.config.missionRewardMultiplier = 1.5;
        f.core.missions().accept(f.state, f.alice, "walk");
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.TRAVEL, 10));
        f.core.missions().claim(f.state, f.alice, "walk");
        assertEquals(150, f.balance(f.alice));
    }

    @Test
    void leavingKeepsOwnIndividualMissions() {
        TestFixture f = TestFixture.withMissions(List.of(walk("walk", MissionScope.INDIVIDUAL, MissionCategory.STORY, 100)));
        f.formDuo();
        f.core.missions().accept(f.state, f.bob, "walk");
        f.core.progress(f.bob, ProgressEvent.of(ObjectiveType.TRAVEL, 40));
        f.core.parties().requestLeave(f.state, f.bob);
        f.core.parties().confirmLeave(f.state, f.bob);
        f.core.progress(f.bob, ProgressEvent.of(ObjectiveType.TRAVEL, 60));
        assertEquals(MissionStatus.COMPLETED, status(f, f.bob, "walk"));
        assertFalse(f.state.partyOf(f.alice).missions().containsKey("walk@" + f.bob));
    }
}
