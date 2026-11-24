package com.chicoestudioso.caricraft.server;

import com.chicoestudioso.caricraft.core.CaricraftCore;
import com.chicoestudioso.caricraft.core.mission.MissionDefinition;
import com.chicoestudioso.caricraft.core.mission.ObjectiveType;
import com.chicoestudioso.caricraft.core.mission.ProgressEvent;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Muestreo ligero de cada jugador (cada {@code progressSampleTicks}, repartido entre ticks):
 * bioma actual y distancia horizontal recorrida. No escanea el mundo.
 */
public final class PlayerTracker {
    /** Saltos mayores entre muestras se consideran teletransportes y no cuentan. */
    private static final double MAX_JUMP = 120.0;

    private final Map<UUID, Sample> samples = new HashMap<>();
    private long tick;

    private static final class Sample {
        ResourceKey<Level> dimension;
        double x;
        double z;
        String biome;
        double pendingMeters;
    }

    public void forget(UUID player) {
        samples.remove(player);
    }

    public void tick(MinecraftServer server, CaricraftServer mod) {
        tick++;
        int interval = mod.core().config().progressSampleTicks;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (Math.floorMod(tick + player.getUUID().hashCode(), interval) != 0) {
                continue;
            }
            sample(mod, player);
        }
    }

    private void sample(CaricraftServer mod, ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }
        CaricraftCore core = mod.core();
        UUID uuid = player.getUUID();
        Sample sample = samples.computeIfAbsent(uuid, id -> new Sample());
        ResourceKey<Level> dimension = player.level().dimension();
        List<MissionDefinition> completed = new ArrayList<>();

        if (dimension.equals(sample.dimension)) {
            double dx = player.getX() - sample.x;
            double dz = player.getZ() - sample.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance <= MAX_JUMP) {
                sample.pendingMeters += distance;
            }
        }
        sample.dimension = dimension;
        sample.x = player.getX();
        sample.z = player.getZ();
        long meters = (long) sample.pendingMeters;
        if (meters > 0) {
            sample.pendingMeters -= meters;
            completed.addAll(core.progress(uuid, ProgressEvent.of(ObjectiveType.TRAVEL, meters)));
        }

        Optional<ResourceKey<Biome>> biomeKey = player.serverLevel().getBiome(player.blockPosition()).unwrapKey();
        if (biomeKey.isPresent()) {
            String biome = biomeKey.get().location().toString();
            boolean changed = !biome.equals(sample.biome);
            sample.biome = biome;
            CaricraftCore.BiomeOutcome outcome = core.onBiomeSampled(uuid, biome, changed);
            completed.addAll(outcome.completedMissions());
            if (outcome.discovered() && core.shop().hasApp(core.partyOf(uuid), AppCatalog.EXPLORER)) {
                mod.notifyParty(uuid, "caricraft.notify.biome_discovered",
                        List.of(biome, String.valueOf(outcome.reward()), player.getGameProfile().getName()));
            }
        }
        mod.markDirty();
        mod.announceCompleted(uuid, completed);
    }
}
