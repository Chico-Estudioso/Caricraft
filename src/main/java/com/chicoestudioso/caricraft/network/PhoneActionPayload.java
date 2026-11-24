package com.chicoestudioso.caricraft.network;

import com.chicoestudioso.caricraft.CaricraftConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: acción tipada del CariPhone. {@code action} es {@code null} si el índice recibido no existe
 * (cliente manipulado o versión distinta); el servidor la ignora.
 */
public record PhoneActionPayload(PhoneAction action, String arg1, String arg2) implements CustomPacketPayload {
    public static final int MAX_ARG_LENGTH = 128;
    public static final Type<PhoneActionPayload> TYPE = new Type<>(CaricraftConstants.id("phone_action"));

    public static final StreamCodec<ByteBuf, PhoneActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, p -> p.action() == null ? -1 : p.action().ordinal(),
            ByteBufCodecs.stringUtf8(MAX_ARG_LENGTH), PhoneActionPayload::arg1,
            ByteBufCodecs.stringUtf8(MAX_ARG_LENGTH), PhoneActionPayload::arg2,
            (index, arg1, arg2) -> new PhoneActionPayload(PhoneAction.byIndex(index), arg1, arg2));

    public PhoneActionPayload {
        arg1 = arg1 == null ? "" : arg1;
        arg2 = arg2 == null ? "" : arg2;
    }

    public static PhoneActionPayload of(PhoneAction action) {
        return new PhoneActionPayload(action, "", "");
    }

    public static PhoneActionPayload of(PhoneAction action, String arg1) {
        return new PhoneActionPayload(action, arg1, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
