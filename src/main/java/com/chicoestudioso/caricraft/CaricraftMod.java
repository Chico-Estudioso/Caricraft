package com.chicoestudioso.caricraft;

import com.chicoestudioso.caricraft.integration.cobblemon.CobblemonCompat;
import com.chicoestudioso.caricraft.item.ModItems;
import com.chicoestudioso.caricraft.network.CaricraftNetworking;
import com.chicoestudioso.caricraft.server.CaricraftCommands;
import com.chicoestudioso.caricraft.server.PhoneActionHandler;
import com.chicoestudioso.caricraft.server.ServerEvents;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Punto de entrada común (cliente y servidor). */
public final class CaricraftMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(CaricraftConstants.MOD_NAME);

    @Override
    public void onInitialize() {
        ModItems.register();
        CaricraftNetworking.registerPayloadTypes();
        PhoneActionHandler.register();
        ServerEvents.register();
        CaricraftCommands.register();
        CobblemonCompat.init();
        LOGGER.info("{} inicializado (protocolo {}).", CaricraftConstants.MOD_NAME, CaricraftConstants.PROTOCOL_VERSION);
    }
}
