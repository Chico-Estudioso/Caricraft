package com.chicoestudioso.caricraft.server;

import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.CaricraftCore;
import com.chicoestudioso.caricraft.core.Clock;
import com.chicoestudioso.caricraft.core.market.MarketCatalog;
import com.chicoestudioso.caricraft.core.mission.MissionCatalog;
import com.chicoestudioso.caricraft.core.mission.MissionDefinition;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.network.PhoneNotifyPayload;
import com.chicoestudioso.caricraft.network.PhoneSnapshotPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Estado del mod mientras el servidor está en marcha: núcleo, datos guardados y envío de snapshots.
 * Todo se usa desde el hilo del servidor.
 */
public final class CaricraftServer {
    public static final String MISSIONS_FILE = "caricraft_missions.json";
    public static final String MARKET_FILE = "caricraft_market.json";
    private static CaricraftServer instance;

    private final MinecraftServer server;
    private final CaricraftSavedData savedData;
    private final com.chicoestudioso.caricraft.server.lens.PhotoStore photos;
    private CaricraftCore core;
    private ActionRateLimiter rateLimiter;

    private CaricraftServer(MinecraftServer server) {
        this.server = server;
        this.savedData = CaricraftSavedData.get(server);
        this.photos = new com.chicoestudioso.caricraft.server.lens.PhotoStore(server);
        reload();
    }

    public static void start(MinecraftServer server) {
        instance = new CaricraftServer(server);
        CaricraftMod.LOGGER.info("Caricraft Mod listo: {} grupos, {} perfiles, {} misiones, {} ofertas de mercado.",
                instance.core.state().parties().size(), instance.core.state().profiles().size(),
                instance.core.missions().catalog().size(), instance.core.market().catalog().pool().size());
    }

    public static void stop() {
        instance = null;
        com.chicoestudioso.caricraft.item.PhoneBinding.setPersonal(true);
    }

    /** {@code null} si el servidor no está en marcha. */
    public static CaricraftServer get() {
        return instance;
    }

    /** Relee configuración y misiones sin perder el estado. */
    public void reload() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        CaricraftConfig config = CaricraftConfig.loadOrCreate(configDir);
        MissionCatalog catalog = loadMissions(configDir, config);
        MarketCatalog market = loadMarket(configDir, config);
        this.core = new CaricraftCore(savedData.state(), config, catalog, market, Clock.SYSTEM);
        this.rateLimiter = new ActionRateLimiter(config.actionsPerSecond, System::currentTimeMillis);
        com.chicoestudioso.caricraft.server.phone.PhoneGuard.configure(config);
    }

    private static MissionCatalog loadMissions(Path configDir, CaricraftConfig config) {
        Path custom = configDir.resolve(MISSIONS_FILE);
        java.util.function.Predicate<String> features = feature -> !"challenge".equals(feature) || config.challenge.enabled;
        if (Files.exists(custom)) {
            try (Reader reader = Files.newBufferedReader(custom, StandardCharsets.UTF_8)) {
                return MissionCatalog.fromJson(reader, FabricLoader.getInstance()::isModLoaded, features);
            } catch (IOException | IllegalArgumentException e) {
                CaricraftMod.LOGGER.error("{} no es válido; se usan las misiones por defecto.", custom, e);
            }
        }
        return defaultMissions(features);
    }

    public static MissionCatalog defaultMissions(java.util.function.Predicate<String> features) {
        try (InputStream in = Objects.requireNonNull(CaricraftServer.class.getResourceAsStream("/caricraft/default_missions.json"));
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return MissionCatalog.fromJson(reader, FabricLoader.getInstance()::isModLoaded, features);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudieron leer las misiones por defecto", e);
        }
    }

    private static MarketCatalog loadMarket(Path configDir, CaricraftConfig config) {
        java.util.function.Predicate<String> sources = source -> "vanilla".equals(source)
                || ("cobblemon".equals(source) && config.market.includeCobblemonItems
                && FabricLoader.getInstance().isModLoaded("cobblemon"));
        java.util.function.Predicate<String> exists = item -> {
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(item);
            return id != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id);
        };
        Path custom = configDir.resolve(MARKET_FILE);
        if (Files.exists(custom)) {
            try (Reader reader = Files.newBufferedReader(custom, StandardCharsets.UTF_8)) {
                return MarketCatalog.fromJson(reader, sources, exists);
            } catch (IOException | IllegalArgumentException e) {
                CaricraftMod.LOGGER.error("{} no es válido; se usa el mercado por defecto.", custom, e);
            }
        }
        try (InputStream in = Objects.requireNonNull(CaricraftServer.class.getResourceAsStream("/caricraft/default_market.json"));
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return MarketCatalog.fromJson(reader, sources, exists);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el mercado por defecto", e);
        }
    }

    public CaricraftSavedData savedData() {
        return savedData;
    }

    public com.chicoestudioso.caricraft.server.lens.PhotoStore photos() {
        return photos;
    }

    public MinecraftServer server() {
        return server;
    }

    public CaricraftCore core() {
        return core;
    }

    public ActionRateLimiter rateLimiter() {
        return rateLimiter;
    }

    public void markDirty() {
        savedData.setDirty();
    }

    public void sendSnapshot(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, PhoneSnapshotPayload.TYPE)) {
            String json = SnapshotBuilder.build(this, player).toJson();
            ServerPlayNetworking.send(player, new PhoneSnapshotPayload(json));
        }
    }

    /** Envía snapshot a los miembros conectados del grupo del jugador. */
    public void sendPartySnapshots(UUID player) {
        Party party = core.partyOf(player);
        if (party == null) {
            return;
        }
        for (UUID member : List.copyOf(party.members())) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                sendSnapshot(online);
            }
        }
    }

    public void notify(ServerPlayer player, boolean success, String key, List<String> args) {
        if (ServerPlayNetworking.canSend(player, PhoneNotifyPayload.TYPE)) {
            List<String> safeArgs = args.size() > 8 ? args.subList(0, 8) : args;
            ServerPlayNetworking.send(player, new PhoneNotifyPayload(success, key,
                    safeArgs.stream().map(a -> a.length() > 200 ? a.substring(0, 200) : a).toList()));
        }
    }

    /** Avisa a los miembros conectados del grupo. */
    public void notifyParty(UUID player, String key, List<String> args) {
        Party party = core.partyOf(player);
        if (party == null) {
            return;
        }
        for (UUID member : List.copyOf(party.members())) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                notify(online, true, key, args);
            }
        }
    }

    /** Aviso y snapshot tras completar misiones. */
    public void announceCompleted(UUID player, Collection<MissionDefinition> completed) {
        if (completed.isEmpty()) {
            return;
        }
        for (MissionDefinition def : completed) {
            notifyParty(player, "caricraft.notify.mission_completed", List.of(def.id()));
        }
        sendPartySnapshots(player);
    }
}
