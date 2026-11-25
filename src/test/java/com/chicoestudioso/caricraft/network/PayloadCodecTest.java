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
    void snapshotJsonRoundTrip() {
        PhoneSnapshot snapshot = new PhoneSnapshot(1, 123L, "uuid", 500, "classic", List.of("theme_ocean"),
                new PhoneSnapshot.PartyView("p", true, 2, List.of(new PhoneSnapshot.MemberView("u", "Alice", true, true)), 0),
                List.of(new PhoneSnapshot.AppView("explorer", "APP", "PARTY", 400, "AVAILABLE", List.of(), "minecraft:compass", 0xFF3A86FF)),
                List.of(new PhoneSnapshot.MissionView("walk", "INDIVIDUAL", "DAILY", "ACCEPTED", 25, false,
                        List.of(new PhoneSnapshot.ObjectiveView("TRAVEL", "", 1000, 250)), Map.of(), 999L, null, null)),
                List.of(), List.of(), new PhoneSnapshot.ExplorationView(3, 64, List.of("minecraft:plains")), Map.of("distance_m", 250L),
                List.of(), List.of(), false, true,
                new PhoneSnapshot.ChallengeView(true, false, true, false, false, 0, 3, 1, 5, 0, 0, 0, 0, 0, 2, 50, true, List.of()),
                new PhoneSnapshot.MarketView(true, 1000L, List.of(new PhoneSnapshot.OfferView("iron", "minecraft:iron_ingot", 8, 30, 3, 3, "vanilla"))));
        PhoneSnapshot decoded = PhoneSnapshot.fromJson(snapshot.toJson());
        assertEquals(snapshot, decoded);
    }
}
