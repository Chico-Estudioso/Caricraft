package com.chicoestudioso.caricraft;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.item.ModItems;
import com.chicoestudioso.caricraft.network.CaricraftNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Punto de entrada común (cliente y servidor). */
public final class CaricraftMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(CaricraftConstants.MOD_NAME);

    private static CaricraftConfig config = new CaricraftConfig();

    @Override
    public void onInitialize() {
        config = CaricraftConfig.loadOrCreate(FabricLoader.getInstance().getConfigDir());
        ModItems.register();
        CaricraftNetworking.registerPayloadTypes();
        LOGGER.info("{} inicializado (protocolo {}).", CaricraftConstants.MOD_NAME, CaricraftConstants.PROTOCOL_VERSION);
    }

    public static CaricraftConfig config() {
        return config;
    }
}
