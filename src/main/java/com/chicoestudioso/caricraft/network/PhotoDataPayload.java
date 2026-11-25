package com.chicoestudioso.caricraft.network;

import com.chicoestudioso.caricraft.CaricraftConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: imagen PNG de una foto del álbum, enviada bajo demanda. */
public record PhotoDataPayload(String photoId, byte[] png) implements CustomPacketPayload {
    public static final Type<PhotoDataPayload> TYPE = new Type<>(CaricraftConstants.id("photo_data"));
    public static final StreamCodec<ByteBuf, PhotoDataPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), PhotoDataPayload::photoId,
            ByteBufCodecs.byteArray(512 * 1024), PhotoDataPayload::png,
            PhotoDataPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
