package com.chicoestudioso.caricraft;

import net.minecraft.resources.ResourceLocation;

/** Constantes compartidas por servidor y cliente. */
public final class CaricraftConstants {
    public static final String MOD_ID = "caricraft";
    public static final String MOD_NAME = "Caricraft Mod";

    /** Versión del protocolo de red del CariPhone. Incrementar si cambia el formato de los payloads. */
    public static final int PROTOCOL_VERSION = 3;

    private CaricraftConstants() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
