package com.chicoestudioso.caricraft.network;

import com.chicoestudioso.caricraft.CaricraftConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** S2C: aviso para el teléfono (clave de traducción y argumentos). */
public record PhoneNotifyPayload(boolean success, String key, List<String> args) implements CustomPacketPayload {
    public static final Type<PhoneNotifyPayload> TYPE = new Type<>(CaricraftConstants.id("phone_notify"));
    public static final StreamCodec<ByteBuf, PhoneNotifyPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PhoneNotifyPayload::success,
            ByteBufCodecs.stringUtf8(256), PhoneNotifyPayload::key,
            ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(8)), PhoneNotifyPayload::args,
            PhoneNotifyPayload::new);

    public PhoneNotifyPayload {
        args = List.copyOf(args);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
