package com.chicoestudioso.caricraft.network;

import com.chicoestudioso.caricraft.CaricraftConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: snapshot completo del teléfono como JSON ({@link PhoneSnapshot}). */
public record PhoneSnapshotPayload(String json) implements CustomPacketPayload {
    public static final int MAX_JSON_LENGTH = 512 * 1024;
    public static final Type<PhoneSnapshotPayload> TYPE = new Type<>(CaricraftConstants.id("phone_snapshot"));
    public static final StreamCodec<ByteBuf, PhoneSnapshotPayload> CODEC = ByteBufCodecs.stringUtf8(MAX_JSON_LENGTH)
            .map(PhoneSnapshotPayload::new, PhoneSnapshotPayload::json);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
