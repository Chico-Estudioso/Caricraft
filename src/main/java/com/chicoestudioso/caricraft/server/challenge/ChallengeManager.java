package com.chicoestudioso.caricraft.server.challenge;

import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.CaricraftCore;
import com.chicoestudioso.caricraft.core.challenge.ChallengeEvent;
import com.chicoestudioso.caricraft.core.challenge.ChallengeService;
import com.chicoestudioso.caricraft.core.mission.ObjectiveType;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import com.chicoestudioso.caricraft.server.ServerEvents;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Parte de servidor del Desafío Dúo: ciclo de noches, filtro de aparición, refuerzo de mobs,
 * recompensas, sueño bloqueado y convivencia con MobsBeGone (COBBLEVERSE).
 */
public final class ChallengeManager {
    /** Etiqueta de los mobs que aparecen gracias al desafío. */
    public static final String TAG = "caricraft_challenge";
    public static final String ELITE_TAG = "caricraft_elite";
    public static final String HORDE_TAG = "caricraft_horde";
    private static final String LEVEL_TAG_PREFIX = "caricraft_lvl_";
    private static final int MAX_HORDE_SPAWNS_PER_TICK = 4;

    private record HordeSpawn(ServerLevel level, EntityType<?> type, double x, double y, double z) {
    }

    private static final java.util.ArrayDeque<HordeSpawn> HORDES = new java.util.ArrayDeque<>();
    private static final String MOBSBEGONE_CLASS = "moe.okaeri.mobsbegone.MobsBeGone";
    private static final String MOBSBEGONE_FILE = "mobsbegone-blacklist.json";

    private static volatile Set<EntityType<?>> types = Set.of();
    private static volatile List<String> blockedByModpack = List.of();
    private static long ticks;

    private ChallengeManager() {
    }

    // ------------------------------------------------------------------ arranque

    /** Resuelve la lista de mobs y comprueba si MobsBeGone los sigue bloqueando. */
    public static void onServerStarted(CaricraftServer mod) {
        CaricraftConfig.ChallengeConfig config = mod.core().config().challenge;
        Set<EntityType<?>> resolved = new LinkedHashSet<>();
        for (String id : config.mobs) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
                resolved.add(BuiltInRegistries.ENTITY_TYPE.get(rl));
            } else {
                CaricraftMod.LOGGER.warn("Mob de desafío desconocido en la configuración: {}", id);
            }
        }
        types = Set.copyOf(resolved);
        blockedByModpack = detectBlocked(types);
        if (config.enabled && !blockedByModpack.isEmpty()) {
            CaricraftMod.LOGGER.warn("MobsBeGone sigue bloqueando {} mobs del Desafío Dúo ({}). Usa '/caricraft desafio preparar' y reinicia.",
                    blockedByModpack.size(), String.join(", ", blockedByModpack));
        }
    }

    private static List<String> detectBlocked(Set<EntityType<?>> candidates) {
        if (!FabricLoader.getInstance().isModLoaded("mobsbegone")) {
            return List.of();
        }
        try {
            Method check = Class.forName(MOBSBEGONE_CLASS).getMethod("isEntityBlacklisted", EntityType.class);
            List<String> blocked = new ArrayList<>();
            for (EntityType<?> type : candidates) {
                if (Boolean.TRUE.equals(check.invoke(null, type))) {
                    blocked.add(EntityType.getKey(type).toString());
                }
            }
            return List.copyOf(blocked);
        } catch (ReflectiveOperationException | RuntimeException e) {
            CaricraftMod.LOGGER.warn("No se pudo consultar MobsBeGone; se asume que no bloquea nada.", e);
            return List.of();
        }
    }

    public static List<String> blockedByModpack() {
        return blockedByModpack;
    }

    /**
     * Quita los mobs del desafío de la lista negra de MobsBeGone (con copia de seguridad).
     * Requiere reiniciar el servidor.
     *
     * @return número de entradas quitadas, o -1 si el archivo no existe
     */
    public static int prepareMobsBeGone(CaricraftConfig.ChallengeConfig config) throws IOException {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(MOBSBEGONE_FILE);
        if (!Files.exists(file)) {
            return -1;
        }
        JsonArray original;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            original = JsonParser.parseReader(reader).getAsJsonArray();
        }
        Set<String> remove = new HashSet<>(config.mobs);
        JsonArray kept = new JsonArray();
        int removed = 0;
        for (JsonElement element : original) {
            if (element.isJsonPrimitive() && remove.contains(element.getAsString())) {
                removed++;
            } else {
                kept.add(element);
            }
        }
        if (removed > 0) {
            Files.copy(file, file.resolveSibling(MOBSBEGONE_FILE + ".caricraft.bak"), StandardCopyOption.REPLACE_EXISTING);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                gson.toJson(kept, writer);
            }
        }
        return removed;
    }

    // ------------------------------------------------------------------ ciclo

    public static long day(MinecraftServer server) {
        return ChallengeService.dayIndex(server.overworld().getDayTime());
    }

    public static boolean night(MinecraftServer server) {
        return ChallengeService.isNight(server.overworld().getDayTime());
    }

    /** Llamado cada tick: hordas pendientes y, una vez por segundo, el ciclo de noches. */
    public static void tick(MinecraftServer server, CaricraftServer mod) {
        spawnHordes();
        if (++ticks % 20 != 0) {
            return;
        }
        CaricraftCore core = mod.core();
        long day = day(server);
        boolean night = night(server);
        java.util.Map<UUID, Party> online = new java.util.LinkedHashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Party party = core.partyOf(player.getUUID());
            if (party == null || online.putIfAbsent(party.id(), party) != null) {
                continue;
            }
            ChallengeEvent event = core.challenge().update(party, day, night);
            if (event != null) {
                mod.markDirty();
                announce(mod, party, event);
            }
        }
        ChallengeBossBars.update(mod, online, server.overworld().getDayTime(), day);
    }

    private static void announce(CaricraftServer mod, Party party, ChallengeEvent event) {
        List<ServerPlayer> online = onlineMembers(mod, party);
        switch (event.kind()) {
            case STARTED -> {
                for (ServerPlayer player : online) {
                    mod.notify(player, false, event.forced() ? "caricraft.notify.challenge_forced" : "caricraft.notify.challenge_started",
                            List.of(String.valueOf(event.level())));
                    player.displayClientMessage(Component.translatable("caricraft.challenge.actionbar_start", event.level())
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
                    player.playNotifySound(net.minecraft.sounds.SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.7f, 1.0f);
                }
            }
            case SURVIVED, ENDED -> {
                for (ServerPlayer player : online) {
                    despawnAround(player, mod.core().config().challenge.radius);
                    if (event.kind() == ChallengeEvent.Kind.SURVIVED) {
                        mod.notify(player, true, "caricraft.notify.challenge_survived",
                                List.of(String.valueOf(event.kills()), String.valueOf(event.carins())));
                        player.playNotifySound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.0f);
                        ServerEvents.progress(player, ObjectiveType.CHALLENGE_NIGHT, Set.of("challenge"), 1);
                    } else {
                        mod.notify(player, true, "caricraft.notify.challenge_ended", List.of());
                    }
                }
            }
        }
        if (!online.isEmpty()) {
            mod.sendPartySnapshots(online.getFirst().getUUID());
        }
    }

    private static List<ServerPlayer> onlineMembers(CaricraftServer mod, Party party) {
        List<ServerPlayer> online = new ArrayList<>();
        for (UUID member : List.copyOf(party.members())) {
            ServerPlayer player = mod.server().getPlayerList().getPlayer(member);
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }

    /** Hace desaparecer (con humo) los mobs del desafío cerca del jugador. */
    public static int despawnAround(ServerPlayer player, int radius) {
        ServerLevel level = player.serverLevel();
        AABB box = player.getBoundingBox().inflate(radius);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box, mob -> mob.getTags().contains(TAG));
        for (Mob mob : mobs) {
            level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
            mob.discard();
        }
        return mobs.size();
    }

    // ------------------------------------------------------------------ aparición

    public static boolean isChallengeType(Entity entity) {
        return entity instanceof Mob && types.contains(entity.getType());
    }

    /**
     * Decide si un mob de la lista puede entrar al mundo. Devuelve el nivel de desafío a aplicar (&gt; 0) o 0 si se
     * debe impedir. Para mobs que no son del desafío devuelve -1 (no se toca).
     */
    public static int spawnLevel(ServerLevel level, Entity entity) {
        CaricraftServer mod = CaricraftServer.get();
        if (mod == null || !isChallengeType(entity)) {
            return -1;
        }
        CaricraftConfig.ChallengeConfig config = mod.core().config().challenge;
        if (!config.enabled) {
            return -1;
        }
        double radiusSq = (double) config.radius * config.radius;
        int wave = ChallengeService.wave(mod.server().overworld().getDayTime());
        int best = 0;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(entity) > radiusSq) {
                continue;
            }
            Party party = mod.core().partyOf(player.getUUID());
            if (party != null && party.challenge().active()) {
                best = Math.max(best, mod.core().challenge().effectiveLevel(party, wave));
            }
        }
        if (best == 0 && !config.gateMobsOutsideChallenge) {
            return -1;
        }
        return best;
    }

    /** Lo llama el mixin de {@code ServerLevel.addFreshEntity}. Devuelve falso para impedir la aparición. */
    public static boolean onAddFreshEntity(ServerLevel level, Entity entity) {
        try {
            int challengeLevel = spawnLevel(level, entity);
            if (challengeLevel < 0) {
                return true;
            }
            if (challengeLevel == 0) {
                return false;
            }
            if (entity.addTag(TAG)) {
                Mob mob = (Mob) entity;
                ChallengeService service = CaricraftServer.get().core().challenge();
                int mobLevel = challengeLevel;
                if (level.getRandom().nextDouble() < service.eliteChance(challengeLevel)) {
                    mobLevel += 3;
                    mob.addTag(ELITE_TAG);
                    ChallengeGear.markElite(mob);
                }
                mob.addTag(LEVEL_TAG_PREFIX + mobLevel);
                ChallengeGear.apply(mob, mobLevel, level.getRandom());
                if (!mob.getTags().contains(HORDE_TAG) && level.getRandom().nextDouble() < service.hordeChance(challengeLevel)) {
                    HORDES.add(new HordeSpawn(level, mob.getType(), mob.getX(), mob.getY(), mob.getZ()));
                }
            }
            return true;
        } catch (RuntimeException e) {
            CaricraftMod.LOGGER.error("Error en el filtro del Desafío Dúo", e);
            return true;
        }
    }

    /** Crea los acompañantes de las hordas fuera del propio addFreshEntity. */
    private static void spawnHordes() {
        for (int i = 0; i < MAX_HORDE_SPAWNS_PER_TICK && !HORDES.isEmpty(); i++) {
            HordeSpawn spawn = HORDES.poll();
            try {
                Entity created = spawn.type().create(spawn.level());
                if (!(created instanceof Mob mob)) {
                    continue;
                }
                double x = spawn.x() + spawn.level().getRandom().nextInt(5) - 2;
                double z = spawn.z() + spawn.level().getRandom().nextInt(5) - 2;
                mob.moveTo(x, spawn.y(), z, spawn.level().getRandom().nextFloat() * 360f, 0f);
                mob.addTag(HORDE_TAG);
                mob.finalizeSpawn(spawn.level(), spawn.level().getCurrentDifficultyAt(mob.blockPosition()),
                        net.minecraft.world.entity.MobSpawnType.REINFORCEMENT, null);
                spawn.level().addFreshEntity(mob);
            } catch (RuntimeException e) {
                CaricraftMod.LOGGER.warn("No se pudo crear un mob de horda", e);
            }
        }
    }

    /** Nivel con el que apareció un mob del desafío (1 si no consta). */
    public static int levelOf(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith(LEVEL_TAG_PREFIX)) {
                try {
                    return Integer.parseInt(tag.substring(LEVEL_TAG_PREFIX.length()));
                } catch (NumberFormatException ignored) {
                    return 1;
                }
            }
        }
        return 1;
    }

    /** Al cargar chunks: retira mobs de desafíos pasados que quedaron guardados. */
    public static void onEntityLoad(Entity entity, ServerLevel level) {
        if (entity.getTags().contains(TAG) && spawnLevel(level, entity) == 0) {
            entity.discard();
        }
    }

    // ------------------------------------------------------------------ jugador

    /** ¿Tiene el jugador una noche de desafío en curso o a punto de empezar? */
    public static boolean challengeTonight(ServerPlayer player) {
        CaricraftServer mod = CaricraftServer.get();
        if (mod == null) {
            return false;
        }
        Party party = mod.core().partyOf(player.getUUID());
        MinecraftServer server = mod.server();
        return party != null && mod.core().challenge().isChallengeNight(party, day(server), night(server));
    }

    /** Recompensa por derrotar un mob del desafío. */
    public static void onKill(ServerPlayer killer, Entity victim) {
        CaricraftServer mod = CaricraftServer.get();
        if (mod == null) {
            return;
        }
        Party party = mod.core().partyOf(killer.getUUID());
        if (party == null) {
            return;
        }
        boolean elite = victim.getTags().contains(ELITE_TAG);
        long reward = mod.core().challenge().onKill(party, levelOf(victim), elite);
        if (reward > 0) {
            killer.displayClientMessage(Component.translatable(elite ? "caricraft.challenge.elite_reward" : "caricraft.challenge.kill_reward", reward)
                    .withStyle(ChatFormatting.GOLD), true);
        }
        mod.markDirty();
        mod.sendPartySnapshots(killer.getUUID()); // contador del HUD
    }
}
