package com.chicoestudioso.caricraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.ByteBufCodecs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayloadCodecTest {
    @Test
    void actionRoundTrip() {
        ByteBuf buf = Unpooled.buffer();
        PhoneActionPayload.CODEC.encode(buf, new PhoneActionPayload(PhoneAction.BUY_ITEM, "chronicles", ""));
        PhoneActionPayload decoded = PhoneActionPayload.CODEC.decode(buf);
        assertEquals(PhoneAction.BUY_ITEM, decoded.action());
        assertEquals("chronicles", decoded.arg1());
    }

    @Test
    void unknownActionIndexDecodesAsNull() {
        ByteBuf buf = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buf, 9999);
        ByteBufCodecs.STRING_UTF8.encode(buf, "x");
        ByteBufCodecs.STRING_UTF8.encode(buf, "");
        assertNull(PhoneActionPayload.CODEC.decode(buf).action());
    }

    @Test
    void oversizedArgumentsAreRejected() {
        ByteBuf buf = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buf, PhoneAction.ADD_NOTE.ordinal());
        ByteBufCodecs.STRING_UTF8.encode(buf, "x".repeat(PhoneActionPayload.MAX_ARG_LENGTH + 1));
        ByteBufCodecs.STRING_UTF8.encode(buf, "");
        assertThrows(RuntimeException.class, () -> PhoneActionPayload.CODEC.decode(buf));
    }

    @Test
    void notifyRoundTrip() {
        ByteBuf buf = Unpooled.buffer();
        PhoneNotifyPayload.CODEC.encode(buf, new PhoneNotifyPayload(true, "caricraft.result.purchased", List.of("explorer", "400")));
        PhoneNotifyPayload decoded = PhoneNotifyPayload.CODEC.decode(buf);
        assertEquals(List.of("explorer", "400"), decoded.args());
    }

    @Test
    void photoUploadChunksRespectTheLimit() {
        ByteBuf buf = Unpooled.buffer();
        byte[] chunk = new byte[PhotoUploadPayload.MAX_CHUNK];
        PhotoUploadPayload.CODEC.encode(buf, new PhotoUploadPayload(7, 0, 2, "Hola", chunk));
        assertEquals(true, buf.readableBytes() < 32_767, "debe caber en un paquete del cliente");
        PhotoUploadPayload decoded = PhotoUploadPayload.CODEC.decode(buf);
        assertEquals(7, decoded.uploadId());
        assertEquals(PhotoUploadPayload.MAX_CHUNK, decoded.chunk().length);
    }

    @Test
    void snapshotJsonRoundTrip() {
        PhoneSnapshot snapshot = new PhoneSnapshot(1, 123L, "uuid", 500, "classic", List.of("theme_ocean"),
                new PhoneSnapshot.PartyView("p", true, 2, List.of(new PhoneSnapshot.MemberView("u", "Alice", true, true)), 0),
                List.of(new PhoneSnapshot.AppView("explorer", "APP", "PARTY", 400, "AVAILABLE", List.of(), "minecraft:compass", 0xFF3A86FF, 0, 1)),
                List.of(new PhoneSnapshot.MissionView("walk", "INDIVIDUAL", "DAILY", "ACCEPTED", 25, false,
                        List.of(new PhoneSnapshot.ObjectiveView("TRAVEL", "", 1000, 250)), Map.of(), 999L, null, null)),
                List.of(), List.of(), new PhoneSnapshot.ExplorationView(3, 64, List.of("minecraft:plains")), Map.of("distance_m", 250L),
                List.of(), List.of(), false, true, false,
                new PhoneSnapshot.ChallengeView(true, false, true, false, false, 0, 3, 1, 30, 0, 0, 0, 0, 0, 2, 50, true, List.of(), 0, 1, true,
                        List.of(new PhoneSnapshot.CacheView("minecraft:overworld", 10.5, 64, -3.5, 2, false)), 230, 150, 315,
                        List.of(new PhoneSnapshot.ChestView("minecraft:overworld", 100.5, 70, -40.5, "GUARDIAN", true, 0, 2,
                                "cobblemon:machoke", 18, 3, 99L)),
                        new PhoneSnapshot.BossesView(5, 5, "zombie_king", 10, "spider_queen",
                                List.of(new PhoneSnapshot.BossRecordView(5, "zombie_king", 7L, "Alice & Bob")),
                                new PhoneSnapshot.BossFightView("zombie_king", 5, 80.5f, 144f, 1.5, 64, -2.5), 250)),
                new PhoneSnapshot.MarketView(true, 1000L, List.of(new PhoneSnapshot.OfferView("iron", "minecraft:iron_ingot", 8, 30, 3, 3, "vanilla"))),
                new PhoneSnapshot.CobbleverseView(true, 12_300, true, true, 86_400_000L, true,
                        List.of(new PhoneSnapshot.CvOfferView("eevee_extremespeed", "cobblemon:premier_ball", null, 1, List.of("eevee"), 15,
                                false, 2, List.of("extremespeed"), 30_000, 0, 1)), true,
                        List.of(new PhoneSnapshot.SellView("u", "cobblemon:pidgey", "", 20, false, 93, 750, null, List.of("male"))), 1, 10,
                        true, 1, 1, 4, new PhoneSnapshot.WonderView("cobblemon:eevee", List.of("shiny"), 12, true, "Bob", "cobblemon:pidgey", 5L),
                        new PhoneSnapshot.LuckView(
                                List.of(new PhoneSnapshot.CrateView("crate_poke", 8000, "cobblemon:poke_ball", 1, 10,
                                        List.of(new PhoneSnapshot.RewardView("pokemon", "POKEMON", "cobblemon:poke_ball", null, 1, 1,
                                                List.of("eevee"), 15, false, 2, "rare", 0.07)))),
                                new PhoneSnapshot.SlotsView(List.of(1000L, 5000L), 29, 30,
                                        List.of(new PhoneSnapshot.SymbolView("poke", "cobblemon:poke_ball", 4, 0.5)), 0.915),
                                List.of(new PhoneSnapshot.UpgradeView("shiny_charm", "shiny", 25_000, 0, 0.08)),
                                List.of(new PhoneSnapshot.FusionView("master_ball", List.of(new PhoneSnapshot.IngredientView("cobblemon:ultra_ball", 8)),
                                        20_000, "cobblemon:master_ball", 1, 0.1)),
                                new PhoneSnapshot.LuckResultView("spin", "", 9L, 0, null, List.of(), false, 0, List.of(5, 5, 5), 200_000, true)),
                        1, 2, new PhoneSnapshot.HealView(1500, 9L, true, false),
                        new PhoneSnapshot.HuntView(List.of(new PhoneSnapshot.HuntTargetView("TYPE", "water", 4, 2, false, 1000, 12)),
                                java.util.Map.of("Alice", 2, "Bob", 1), 3, true, false, false, 1, 4, false, 1500, 20, 2500, 30, 6000, 80),
                        new PhoneSnapshot.ExpeditionView(List.of(new PhoneSnapshot.DestinationView("cave", "minecraft:iron_pickaxe",
                                List.of("rock"), List.of("minecraft:coal"))), List.of(1, 4, 8), 3, 1,
                                List.of(new PhoneSnapshot.AwayView("cobblemon:geodude", List.of("male"), false, 20, "cave", 4, 77L, false)), 2),
                        new PhoneSnapshot.DexView(12, 40, 1, 0, List.of(new PhoneSnapshot.DexEntryView("cobblemon:magikarp", "Bob", 7, true, 255, 3L)),
                                List.of(), List.of(), List.of(new PhoneSnapshot.GoalView("shinies_1", "SHINIES", null, 1, 1, true, 50, 2000)),
                                java.util.Map.of("water", 9)),
                        List.of(new PhoneSnapshot.ShinyAlertView("cobblemon:gible", 22, "minecraft:overworld", 1.5, 70, -8.25, 99L, "Alice")),
                        true, 25, true, 60, new PhoneSnapshot.TrainerView(10, 4, 5, 25)),
                new PhoneSnapshot.CampView(new PhoneSnapshot.PositionView("", "", "minecraft:overworld", "", 1.5, 64, 2.5), 0, 30, false, 0),
                new PhoneSnapshot.DuoTravelView(List.of(new PhoneSnapshot.PositionView("b0b", "Bob", "minecraft:the_nether",
                        "minecraft:crimson_forest", 10, 70, -3)), 0, 50, false,
                        new PhoneSnapshot.TravelView("Bob", "GO", 99L), null, 2, 60, 3),
                new PhoneSnapshot.LensView(true, 12, List.of(new PhoneSnapshot.PhotoView("0123456789abcdef", "Alice", 5L, "Hola", 256, 144))),
                new PhoneSnapshot.BoxView(true, 3, 2),
                new PhoneSnapshot.RoutineView(
                        new PhoneSnapshot.StreakView(4, 9, true, true, false, true, true, false, 30, 15, 5, List.of(15L, 20L, 25L, 30L, 40L, 50L, 100L), 86_400_000L),
                        new PhoneSnapshot.WeeklyView("miners", "minecraft:diamond_pickaxe", "blocks_mined", 1500, 820, 1, 100, 300, 604_800_000L, 1.5, "MISSIONS"),
                        List.of(new PhoneSnapshot.MedalView("miner", "minecraft:iron_pickaxe", 2, 12_000, List.of(1_000L, 10_000L, 50_000L), List.of(25L, 75L, 200L))),
                        List.of(new PhoneSnapshot.BossRecordView(5, "zombie_king", 7L, "Alice & Bob"))),
                new PhoneSnapshot.SavingsView(1_020, 0.02, 40, 20, 30, 20, 100_000));
        PhoneSnapshot decoded = PhoneSnapshot.fromJson(snapshot.toJson());
        assertEquals(snapshot, decoded);
    }
}
