package com.chicoestudioso.caricraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuración del servidor ({@code config/caricraft.json}). Los valores se validan al cargar.
 * Los precios y recompensas del cliente siempre vienen del servidor.
 */
public final class CaricraftConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Caricraft Mod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final String FILE_NAME = "caricraft.json";

    /** Entregar un CariPhone la primera vez que un jugador entra al servidor. */
    public boolean giveStarterPhone = true;
    /** Saldo inicial de un grupo nuevo. */
    public long startingBalance = 0;
    /** Tamaño máximo de grupo. El MVP está pensado para dúos (2). */
    public int maxPartySize = 2;
    /** Segundos que dura una invitación. */
    public int inviteExpirySeconds = 300;
    /** Invitaciones salientes pendientes por jugador. */
    public int maxPendingInvites = 3;
    /** Horas de espera tras desvincularse antes de poder unirse a otro grupo. */
    public int unlinkCooldownHours = 24;
    /** Asientos del ledger que se conservan por grupo. */
    public int ledgerSize = 100;
    /** Entradas de crónica que se conservan por grupo. */
    public int chronicleMaxEntries = 300;
    /** Guardar coordenadas exactas en las crónicas. */
    public boolean chronicleRecordCoordinates = false;
    /** Carins por bioma nuevo cuando el grupo tiene la app Exploración. */
    public long biomeDiscoveryReward = 5;
    /** Multiplicador global de recompensas de misiones. */
    public double missionRewardMultiplier = 1.0;
    /** Acciones del teléfono permitidas por segundo y jugador. */
    public int actionsPerSecond = 20;
    /** Cada cuántos ticks se muestrea bioma y distancia de cada jugador. */
    public int progressSampleTicks = 40;
    /** Sobrescritura de precios por id de artículo (p. ej. "chronicles": 300). */
    public Map<String, Long> prices = new LinkedHashMap<>();
    /** Artículos de la tienda desactivados por id. */
    public List<String> disabledItems = new ArrayList<>();

    public static CaricraftConfig loadOrCreate(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        CaricraftConfig config = new CaricraftConfig();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                CaricraftConfig loaded = GSON.fromJson(reader, CaricraftConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (IOException | JsonParseException e) {
                LOGGER.error("No se pudo leer {}; se usan valores por defecto.", file, e);
                return config.validated();
            }
        }
        config = config.validated();
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("No se pudo escribir {}.", file, e);
        }
        return config;
    }

    /** Corrige valores fuera de rango y devuelve la misma instancia. */
    public CaricraftConfig validated() {
        startingBalance = Math.max(0, startingBalance);
        maxPartySize = clamp(maxPartySize, 1, 2);
        inviteExpirySeconds = clamp(inviteExpirySeconds, 30, 3600);
        maxPendingInvites = clamp(maxPendingInvites, 1, 10);
        unlinkCooldownHours = clamp(unlinkCooldownHours, 0, 24 * 30);
        ledgerSize = clamp(ledgerSize, 10, 1000);
        chronicleMaxEntries = clamp(chronicleMaxEntries, 20, 5000);
        biomeDiscoveryReward = Math.max(0, biomeDiscoveryReward);
        if (!(missionRewardMultiplier >= 0) || missionRewardMultiplier > 100) {
            missionRewardMultiplier = 1.0;
        }
        actionsPerSecond = clamp(actionsPerSecond, 1, 100);
        progressSampleTicks = clamp(progressSampleTicks, 10, 200);
        if (prices == null) {
            prices = new LinkedHashMap<>();
        }
        prices.replaceAll((id, price) -> price == null ? 0L : Math.max(0L, price));
        if (disabledItems == null) {
            disabledItems = new ArrayList<>();
        }
        return this;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
