package com.chicoestudioso.caricraft.network;

import com.chicoestudioso.caricraft.CaricraftConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: un trozo de una foto de CariLens. Las fotos se suben troceadas porque los paquetes del cliente
 * tienen un límite de 32 KiB.
 */
public record PhotoUploadPayload(int uploadId, int index, int total, String caption, byte[] chunk) implements CustomPacketPayload {
    public static final int MAX_CHUNK = 30_000;
    public static final int MAX_CHUNKS = 6;
    public static final Type<PhotoUploadPayload> TYPE = new Type<>(CaricraftConstants.id("photo_upload"));
    public static final StreamCodec<ByteBuf, PhotoUploadPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PhotoUploadPayload::uploadId,
            ByteBufCodecs.VAR_INT, PhotoUploadPayload::index,
            ByteBufCodecs.VAR_INT, PhotoUploadPayload::total,
            ByteBufCodecs.stringUtf8(PhoneActionPayload.MAX_ARG_LENGTH), PhotoUploadPayload::caption,
            ByteBufCodecs.byteArray(MAX_CHUNK), PhotoUploadPayload::chunk,
            PhotoUploadPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
