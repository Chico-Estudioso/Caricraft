package com.chicoestudioso.caricraft.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registro de los tipos de payload (común a cliente y servidor). */
public final class CaricraftNetworking {
    private CaricraftNetworking() {
    }

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playC2S().register(PhoneActionPayload.TYPE, PhoneActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PhoneSnapshotPayload.TYPE, PhoneSnapshotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PhoneNotifyPayload.TYPE, PhoneNotifyPayload.CODEC);
    }
}
