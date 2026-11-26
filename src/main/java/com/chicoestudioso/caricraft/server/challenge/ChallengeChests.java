package com.chicoestudioso.caricraft.server.challenge;

import com.chicoestudioso.caricraft.CaricraftConstants;
import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.challenge.ChallengeService;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.integration.cobblemon.CobblemonCompat;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import com.chicoestudioso.caricraft.server.cobbleverse.PokemonBackend;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cofres del desafío por el mundo. De vez en cuando (de día o de noche, fuera de la noche de desafío) aparece cerca
 * del dúo un cofre cerrado, en un sitio que no pisa estructuras ni construcciones. Al acercarse hay que ganarlo:
 * <ul>
 *   <li><b>Emboscada:</b> oleadas de mobs equipados según el nivel de desafío (con casco y resistencia al fuego, para
 *   que el sol no los queme).</li>
 *   <li><b>Pokémon guardián</b> (con Cobblemon): un Pokémon salvaje fuerte que hay que derrotar o capturar.</li>
 * </ul>
 * Al conquistarlo: botín con equipo encantado (y objetos de Cobblemon), Carins y experiencia de desafío. Si nadie lo
 * conquista, se desvanece. Todo vive en memoria: al parar el servidor no queda ningún cofre cerrado.
 */
public final class ChallengeChests {
    public static final String AMBUSH_TAG = "caricraft_ambush";
    private static final String CHEST_TAG_PREFIX = "caricraft_chest_";
    private static final int TRIGGER_RANGE = 10;
    private static final int GUARDIAN_TRIGGER_RANGE = 16;
    private static final int LEASH_RANGE = 40;
    private static final long LEAVE_RESET_MS = 20_000;
    private static final long WAVE_PAUSE_MS = 3_000;
    private static final long FIRST_ATTEMPT_MS = 5 * 60_000L;
    private static final List<String> AMBUSH_TYPES = List.of("minecraft:zombie", "minecraft:husk", "minecraft:skeleton",
            "minecraft:stray", "minecraft:spider");
    private static final List<String> AMBUSH_HIGH_TYPES = List.of("minecraft:vindicator", "minecraft:pillager", "minecraft:witch");
    private static final List<List<String>> GUARDIANS = List.of(
            List.of("machoke", "graveler", "haunter", "nidorino", "golbat", "pupitar", "shelgon", "lairon", "kadabra", "ivysaur",
                    "charmeleon", "wartortle"),
            List.of("machamp", "golem", "gengar", "nidoking", "aggron", "scizor", "lucario", "gyarados", "arcanine", "alakazam",
                    "houndoom"),
            List.of("tyranitar", "salamence", "garchomp", "metagross", "dragonite", "hydreigon", "goodra", "volcarona", "haxorus"));
    /** Objetos de Cobblemon que se añaden al botín (por tabla 1–3): id, mínimo, máximo. */
    private static final List<List<Object[]>> COBBLEMON_LOOT = List.of(
            List.of(new Object[]{"cobblemon:great_ball", 3, 6}, new Object[]{"cobblemon:exp_candy_m", 2, 4},
                    new Object[]{"cobblemon:fire_stone", 1, 1}, new Object[]{"cobblemon:water_stone", 1, 1},
                    new Object[]{"cobblemon:thunder_stone", 1, 1}, new Object[]{"cobblemon:leaf_stone", 1, 1},
                    new Object[]{"cobblemon:relic_coin", 2, 5}, new Object[]{"cobblemon:oran_berry", 3, 6}),
            List.of(new Object[]{"cobblemon:ultra_ball", 3, 6}, new Object[]{"cobblemon:exp_candy_l", 2, 4},
                    new Object[]{"cobblemon:rare_candy", 1, 2}, new Object[]{"cobblemon:hp_up", 1, 2},
                    new Object[]{"cobblemon:protein", 1, 2}, new Object[]{"cobblemon:moon_stone", 1, 1},
                    new Object[]{"cobblemon:dusk_stone", 1, 1}, new Object[]{"cobblemon:ability_capsule", 1, 1}),
            List.of(new Object[]{"cobblemon:ultra_ball", 6, 10}, new Object[]{"cobblemon:rare_candy", 2, 4},
                    new Object[]{"cobblemon:exp_candy_xl", 2, 3}, new Object[]{"cobblemon:pp_max", 1, 1},
                    new Object[]{"cobblemon:lucky_egg", 1, 1}, new Object[]{"cobblemon:ability_patch", 1, 1},
                    new Object[]{"cobblemon:master_ball", 1, 1}));
    /** Probabilidad de que el cofre de emboscada traiga además un Pokémon (tabla 1–3). */
    private static final double[] POKEMON_REWARD_CHANCE = {0.10, 0.20, 0.30};

    public enum Kind { AMBUSH, GUARDIAN }

    enum Phase { WAITING, FIGHTING }

    private static final class WorldChest {
        final int id;
        final UUID partyId;
        final ServerLevel level;
        final BlockPos pos;
        final int challengeLevel;
        Kind kind;
        final long expiresAt;
        final ServerBossEvent bar;
        Phase phase = Phase.WAITING;
        int wave;
        final int waves;
        long nextWaveAt;
        long nobodySince;
        UUID guardian;
        UUID guardianPokemon;
        String guardianSpecies;
        int guardianLevel;
        boolean guardianShiny;
        boolean guardianBattled;
        int guardianFailures;

        WorldChest(int id, UUID partyId, ServerLevel level, BlockPos pos, int challengeLevel, Kind kind, long expiresAt) {
            this.id = id;
            this.partyId = partyId;
            this.level = level;
            this.pos = pos;
            this.challengeLevel = challengeLevel;
            this.kind = kind;
            this.expiresAt = expiresAt;
            this.waves = ChallengeService.chestWaves(challengeLevel);
            this.bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        }

        String tag() {
            return CHEST_TAG_PREFIX + id;
        }
    }

    private static final Map<Integer, WorldChest> CHESTS = new HashMap<>();
    /** Por grupo: cuándo toca el próximo intento de sacar un cofre. */
    private static final Map<UUID, Long> NEXT_ATTEMPT = new HashMap<>();
    private static int nextId = 1;
    private static boolean spawning;

    private ChallengeChests() {
    }

    /** Mientras aparece una emboscada, el filtro del desafío no la toca. */
    public static boolean spawning() {
        return spawning;
    }

    private static CaricraftConfig.ChallengeConfig config(CaricraftServer mod) {
        return mod.core().config().challenge;
    }

    // ------------------------------------------------------------------ ciclo (una vez por segundo)

    static void tick(CaricraftServer mod, Map<UUID, Party> parties) {
        long now = System.currentTimeMillis();
        CaricraftConfig.ChallengeConfig config = config(mod);
        if (config.enabled && config.worldChests) {
            for (Party party : parties.values()) {
                if (party.challenge().active() || CHESTS.values().stream().anyMatch(c -> c.partyId.equals(party.id()))) {
                    continue;
                }
                long due = NEXT_ATTEMPT.computeIfAbsent(party.id(), id -> now + FIRST_ATTEMPT_MS);
                if (now < due) {
                    continue;
                }
                NEXT_ATTEMPT.put(party.id(), now + config.chestIntervalMinutes * 60_000L);
                if (mod.server().overworld().getRandom().nextDouble() < config.chestChance) {
                    List<ServerPlayer> members = members(mod, party, Level.OVERWORLD);
                    if (!members.isEmpty()) {
                        spawnNear(mod, members.get(mod.server().overworld().getRandom().nextInt(members.size())), null);
                    }
                }
            }
        }
        for (WorldChest chest : List.copyOf(CHESTS.values())) {
            try {
                update(mod, chest, now);
            } catch (RuntimeException e) {
                CaricraftMod.LOGGER.error("Error en el cofre del desafío {}", chest.id, e);
                remove(mod, chest, false);
            }
        }
    }

    private static List<ServerPlayer> members(CaricraftServer mod, Party party, ResourceKey<Level> dimension) {
        List<ServerPlayer> online = new ArrayList<>();
        for (UUID member : party.members()) {
            ServerPlayer player = mod.server().getPlayerList().getPlayer(member);
            if (player != null && !player.isSpectator() && (dimension == null || player.level().dimension() == dimension)) {
                online.add(player);
            }
        }
        return online;
    }

    private static void update(CaricraftServer mod, WorldChest chest, long now) {
        Party party = mod.core().state().parties().get(chest.partyId);
        if (party == null) {
            remove(mod, chest, false); // el grupo ya no existe
            return;
        }
        if (!chest.level.isLoaded(chest.pos)) {
            // Lejos de todos: no se carga el chunk cada segundo; solo se retira al caducar.
            if (now >= chest.expiresAt) {
                remove(mod, chest, false);
            }
            return;
        }
        if (!(chest.level.getBlockEntity(chest.pos) instanceof ChestBlockEntity)) {
            remove(mod, chest, false); // alguien lo ha quitado (p. ej. un administrador)
            return;
        }
        List<ServerPlayer> members = members(mod, party, chest.level.dimension());
        if (now >= chest.expiresAt && chest.phase == Phase.WAITING) {
            notifyParty(mod, party, false, "caricraft.notify.chest_vanished", List.of());
            remove(mod, chest, true);
            return;
        }
        beacon(chest, members);
        ServerPlayer near = nearest(chest, members, LEASH_RANGE);
        updateBar(chest, members);
        if (chest.phase == Phase.WAITING) {
            int trigger = chest.kind == Kind.GUARDIAN ? GUARDIAN_TRIGGER_RANGE : TRIGGER_RANGE;
            if (nearest(chest, members, trigger) != null) {
                start(mod, party, chest, now);
            }
            return;
        }
        // En combate: si el dúo se va, la emboscada (o el guardián) se retira y se puede volver a intentar.
        if (near == null) {
            if (chest.nobodySince == 0) {
                chest.nobodySince = now;
            } else if (now - chest.nobodySince > LEAVE_RESET_MS) {
                reset(chest);
            }
            return;
        }
        chest.nobodySince = 0;
        if (chest.kind == Kind.AMBUSH) {
            updateAmbush(mod, party, chest, now, near);
        } else {
            updateGuardian(mod, party, chest, near);
        }
    }

    private static ServerPlayer nearest(WorldChest chest, List<ServerPlayer> members, int range) {
        double best = (double) range * range;
        ServerPlayer found = null;
        for (ServerPlayer player : members) {
            double d = player.distanceToSqr(chest.pos.getX() + 0.5, chest.pos.getY(), chest.pos.getZ() + 0.5);
            if (d <= best) {
                best = d;
                found = player;
            }
        }
        return found;
    }

    /** Columna de luz morada sobre el cofre, visible de lejos para el dúo. */
    private static void beacon(WorldChest chest, List<ServerPlayer> members) {
        double x = chest.pos.getX() + 0.5;
        double z = chest.pos.getZ() + 0.5;
        for (ServerPlayer player : members) {
            for (int i = 0; i < 14; i++) {
                chest.level.sendParticles(player, ParticleTypes.END_ROD, true, x, chest.pos.getY() + 1.2 + i * 1.4, z,
                        1, 0.05, 0.25, 0.05, 0.0);
            }
            if (chest.phase == Phase.WAITING) {
                chest.level.sendParticles(player, ParticleTypes.WITCH, true, x, chest.pos.getY() + 1.0, z, 4, 0.4, 0.3, 0.4, 0.0);
            }
        }
    }

    private static void updateBar(WorldChest chest, List<ServerPlayer> members) {
        for (ServerPlayer player : members) {
            boolean close = player.distanceToSqr(chest.pos.getX(), chest.pos.getY(), chest.pos.getZ()) < 48 * 48;
            if (close && chest.phase == Phase.FIGHTING) {
                chest.bar.addPlayer(player);
            } else {
                chest.bar.removePlayer(player);
            }
        }
    }

    // ------------------------------------------------------------------ aparición

    /**
     * Saca un cofre cerca del jugador (lo usa también {@code /caricraft desafio cofre}).
     *
     * @param kind tipo forzado, o {@code null} para decidirlo al azar
     */
    public static boolean spawnNear(CaricraftServer mod, ServerPlayer anchor, Kind kind) {
        Party party = mod.core().partyOf(anchor.getUUID());
        if (party == null || anchor.level().dimension() != Level.OVERWORLD) {
            return false;
        }
        ServerLevel world = anchor.serverLevel();
        RandomSource random = world.getRandom();
        BlockPos pos = findSpot(world, anchor, random);
        if (pos == null) {
            CaricraftMod.LOGGER.debug("No se encontró sitio para un cofre del desafío cerca de {}", anchor.getGameProfile().getName());
            return false;
        }
        int level = mod.core().challenge().level(party);
        boolean cobblemon = CobblemonCompat.backend() != null;
        Kind chosen = kind != null ? kind
                : cobblemon && random.nextDouble() < config(mod).chestGuardianChance ? Kind.GUARDIAN : Kind.AMBUSH;
        if (chosen == Kind.GUARDIAN && !cobblemon) {
            chosen = Kind.AMBUSH;
        }
        world.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)), 3);
        if (world.getBlockEntity(pos) instanceof ChestBlockEntity chestEntity) {
            chestEntity.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                    CaricraftConstants.id("challenge/world_chest_" + ChallengeService.lootTier(level))), random.nextLong());
        }
        long expires = System.currentTimeMillis() + config(mod).chestLifetimeMinutes * 60_000L;
        WorldChest chest = new WorldChest(nextId++, party.id(), world, pos, level, chosen, expires);
        if (chosen == Kind.GUARDIAN) {
            List<String> pool = GUARDIANS.get(ChallengeService.lootTier(level) - 1);
            PokemonBackend backend = CobblemonCompat.backend();
            List<String> existing = pool.stream().filter(backend::speciesExists).toList();
            chest.guardianSpecies = existing.isEmpty() ? "snorlax" : existing.get(random.nextInt(existing.size()));
            chest.guardianLevel = ChallengeService.guardianLevel(level);
            chest.guardianShiny = random.nextFloat() < 0.04f;
        }
        CHESTS.put(chest.id, chest);
        world.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, 0.4, 0.6, 0.4, 0.05);
        announce(mod, party, chest);
        return true;
    }

    /**
     * Un hueco en la superficie a 60–140 bloques: suelo firme, sin líquidos, a cielo abierto, sin ninguna estructura
     * en 32 bloques (aldeas, templos, estructuras de Cobbleverse…), sin bloques con datos de jugador cerca (cofres,
     * hornos, camas…) y lejos de otros cofres del desafío.
     */
    private static BlockPos findSpot(ServerLevel level, ServerPlayer player, RandomSource random) {
        int[] rejected = new int[5]; // sin cargar, terreno, estructura, construcción, otro cofre
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 60 + random.nextDouble() * 80;
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            BlockPos column = new BlockPos(x, 0, z);
            if (!level.isLoaded(column)) {
                rejected[0]++;
                continue;
            }
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            BlockPos below = top.below();
            if (Math.abs(top.getY() - player.getY()) > 40
                    || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                    || !level.getFluidState(below).isEmpty() || !level.getFluidState(top).isEmpty()
                    || !level.getBlockState(top).canBeReplaced() || !level.isEmptyBlock(top.above())
                    || !level.canSeeSky(top.above())) {
                rejected[1]++;
                continue;
            }
            if (nearStructure(level, top)) {
                rejected[2]++;
                continue;
            }
            if (nearPlayerBuild(level, top)) {
                rejected[3]++;
                continue;
            }
            if (nearOtherChest(level, top)) {
                rejected[4]++;
                continue;
            }
            return top;
        }
        CaricraftMod.LOGGER.info("Cofre del desafío: sin sitio cerca de {} (sin cargar {}, terreno {}, estructura {}, construcción {}, "
                + "otro cofre {})", player.getGameProfile().getName(), rejected[0], rejected[1], rejected[2], rejected[3], rejected[4]);
        return null;
    }

    private static boolean nearStructure(ServerLevel level, BlockPos pos) {
        for (int dx = -32; dx <= 32; dx += 16) {
            for (int dz = -32; dz <= 32; dz += 16) {
                BlockPos probe = pos.offset(dx, 0, dz);
                if (level.isLoaded(probe) && level.structureManager().hasAnyStructureAt(probe)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Bloques con datos (cofres, hornos, carteles, camas…) en los chunks de alrededor: huele a construcción. */
    private static boolean nearPlayerBuild(ServerLevel level, BlockPos pos) {
        ChunkPos center = new ChunkPos(pos);
        for (int cx = center.x - 2; cx <= center.x + 2; cx++) {
            for (int cz = center.z - 2; cz <= center.z + 2; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof BeehiveBlockEntity) && !(entity.getBlockState().getBlock() instanceof BeehiveBlock)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean nearOtherChest(ServerLevel level, BlockPos pos) {
        for (WorldChest other : CHESTS.values()) {
            if (other.level == level && other.pos.distSqr(pos) < 64 * 64) {
                return true;
            }
        }
        return false;
    }

    private static void announce(CaricraftServer mod, Party party, WorldChest chest) {
        for (ServerPlayer player : members(mod, party, null)) {
            if (player.level() != chest.level) {
                continue;
            }
            double dx = chest.pos.getX() + 0.5 - player.getX();
            double dz = chest.pos.getZ() + 0.5 - player.getZ();
            long distance = Math.round(Math.sqrt(dx * dx + dz * dz));
            int octant = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0), 8);
            if (chest.kind == Kind.GUARDIAN) {
                mod.notify(player, false, "caricraft.notify.chest_guardian_spawned", List.of(String.valueOf(distance),
                        "caricraft.dir." + octant, "cobblemon:" + chest.guardianSpecies, String.valueOf(chest.guardianLevel)));
            } else {
                mod.notify(player, false, "caricraft.notify.chest_spawned", List.of(String.valueOf(distance), "caricraft.dir." + octant,
                        String.valueOf(chest.waves)));
            }
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.8f);
        }
        mod.sendPartySnapshots(party.members().iterator().next());
    }

    // ------------------------------------------------------------------ combate

    private static void start(CaricraftServer mod, Party party, WorldChest chest, long now) {
        chest.phase = Phase.FIGHTING;
        chest.nobodySince = 0;
        if (chest.kind == Kind.AMBUSH) {
            chest.wave = 0;
            chest.nextWaveAt = now;
            notifyParty(mod, party, false, "caricraft.notify.chest_ambush", List.of(String.valueOf(chest.waves)));
            chest.level.playSound(null, chest.pos, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.5f, 1.2f);
        } else {
            spawnGuardian(mod, party, chest);
        }
        mod.sendPartySnapshots(party.members().iterator().next());
    }

    private static void updateAmbush(CaricraftServer mod, Party party, WorldChest chest, long now, ServerPlayer near) {
        List<Mob> alive = ambushers(chest);
        if (chest.wave > 0) {
            int size = ChallengeService.chestWaveSize(chest.challengeLevel, chest.wave, chest.waves);
            chest.bar.setName(Component.translatable("caricraft.challenge.chest_bar", chest.wave, chest.waves, alive.size())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            chest.bar.setProgress(Math.max(0f, Math.min(1f, alive.size() / (float) Math.max(1, size))));
        }
        if (!alive.isEmpty()) {
            return;
        }
        if (chest.wave >= chest.waves) {
            conquer(mod, party, chest, near);
            return;
        }
        if (chest.wave > 0) {
            if (chest.nextWaveAt == Long.MAX_VALUE) {
                chest.nextWaveAt = now + WAVE_PAUSE_MS; // respiro entre oleadas
                chest.bar.setName(Component.translatable("caricraft.challenge.chest_bar_next", chest.wave + 1, chest.waves)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                chest.bar.setProgress(0f);
                return;
            }
            if (now < chest.nextWaveAt) {
                return;
            }
        }
        chest.wave++;
        int spawned = spawnWave(chest, chest.level.getRandom());
        chest.nextWaveAt = Long.MAX_VALUE;
        for (ServerPlayer player : members(mod, party, chest.level.dimension())) {
            player.displayClientMessage(Component.translatable("caricraft.challenge.chest_wave", chest.wave, chest.waves, spawned)
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
        }
        mod.sendPartySnapshots(party.members().iterator().next());
    }

    private static List<Mob> ambushers(WorldChest chest) {
        return chest.level.getEntitiesOfClass(Mob.class, new AABB(chest.pos).inflate(96),
                mob -> mob.isAlive() && mob.getTags().contains(chest.tag()));
    }

    private static int spawnWave(WorldChest chest, RandomSource random) {
        List<EntityType<?>> pool = new ArrayList<>();
        for (String id : AMBUSH_TYPES) {
            EntityType.byString(id).ifPresent(pool::add);
        }
        if (chest.challengeLevel >= 8) {
            for (String id : AMBUSH_HIGH_TYPES) {
                EntityType.byString(id).ifPresent(pool::add);
            }
        }
        int size = ChallengeService.chestWaveSize(chest.challengeLevel, chest.wave, chest.waves);
        int spawned = 0;
        for (int i = 0; i < size; i++) {
            boolean captain = chest.wave == chest.waves && i == 0;
            EntityType<?> type = pool.get(random.nextInt(pool.size()));
            Entity created = type.create(chest.level);
            if (!(created instanceof Mob mob)) {
                continue;
            }
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 6 + random.nextDouble() * 5;
            BlockPos ground = chest.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(
                    chest.pos.getX() + 0.5 + Math.cos(angle) * radius, 0, chest.pos.getZ() + 0.5 + Math.sin(angle) * radius));
            mob.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, random.nextFloat() * 360f, 0f);
            mob.finalizeSpawn(chest.level, chest.level.getCurrentDifficultyAt(ground), MobSpawnType.EVENT, null);
            int mobLevel = chest.challengeLevel + (captain ? 3 : chest.wave - 1);
            mob.addTag(AMBUSH_TAG);
            mob.addTag(chest.tag());
            mob.addTag(ChallengeManager.LEVEL_TAG_PREFIX + mobLevel);
            ChallengeGear.apply(mob, mobLevel, random);
            if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
                mob.setDropChance(EquipmentSlot.HEAD, 0f);
            }
            // De día: que el sol no los queme (también con el casco roto).
            mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
            if (captain) {
                mob.addTag(ChallengeManager.ELITE_TAG);
                ChallengeGear.markElite(mob);
                mob.setCustomName(Component.translatable("caricraft.challenge.captain", mob.getType().getDescription())
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            }
            mob.setPersistenceRequired();
            mob.restrictTo(chest.pos, 16);
            spawning = true;
            try {
                if (chest.level.addFreshEntity(mob)) {
                    spawned++;
                    chest.level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.5, mob.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                }
            } finally {
                spawning = false;
            }
        }
        return spawned;
    }

    private static void spawnGuardian(CaricraftServer mod, Party party, WorldChest chest) {
        PokemonBackend backend = CobblemonCompat.backend();
        if (backend == null) {
            return;
        }
        BlockPos ground = chest.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, chest.pos.offset(3, 0, 0));
        Entity entity = backend.spawnWild(chest.level, chest.guardianSpecies, chest.guardianLevel, chest.guardianShiny,
                ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
        if (entity == null) {
            CaricraftMod.LOGGER.warn("No se pudo crear el Pokémon guardián {}", chest.guardianSpecies);
            if (++chest.guardianFailures >= 3) {
                chest.kind = Kind.AMBUSH; // sin guardián: emboscada de mobs
                chest.wave = 0;
                chest.nextWaveAt = 0;
            }
            return;
        }
        entity.addTag(AMBUSH_TAG);
        entity.addTag(chest.tag());
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        }
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.restrictTo(chest.pos, 8);
        }
        chest.guardian = entity.getUUID();
        chest.guardianPokemon = backend.pokemonId(entity);
        chest.guardianBattled = false;
        chest.level.sendParticles(ParticleTypes.FLASH, entity.getX(), entity.getY() + 1, entity.getZ(), 1, 0, 0, 0, 0);
        chest.bar.setName(Component.translatable("caricraft.challenge.guardian_bar",
                        Component.translatable("cobblemon.species." + chest.guardianSpecies + ".name"), chest.guardianLevel)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        chest.bar.setProgress(1f);
        notifyParty(mod, party, false, "caricraft.notify.chest_guardian", List.of("cobblemon:" + chest.guardianSpecies,
                String.valueOf(chest.guardianLevel)));
    }

    private static void updateGuardian(CaricraftServer mod, Party party, WorldChest chest, ServerPlayer near) {
        PokemonBackend backend = CobblemonCompat.backend();
        Entity guardian = chest.guardian == null ? null : chest.level.getEntity(chest.guardian);
        if (guardian != null && guardian.isAlive()) {
            if (backend != null && backend.isBattling(guardian)) {
                chest.guardianBattled = true;
                chest.bar.setColor(BossEvent.BossBarColor.RED);
            }
            return;
        }
        // Ya no está: ¿lo han derrotado en combate o capturado? Si no (p. ej. ha desaparecido), vuelve a salir.
        boolean captured = backend != null && chest.guardianPokemon != null
                && members(mod, party, null).stream().anyMatch(p -> backend.owns(p, chest.guardianPokemon));
        if (chest.guardianBattled || captured) {
            if (captured) {
                notifyParty(mod, party, true, "caricraft.notify.guardian_captured", List.of("cobblemon:" + chest.guardianSpecies));
            }
            conquer(mod, party, chest, near);
        } else {
            spawnGuardian(mod, party, chest);
        }
    }

    /** El dúo se ha ido a mitad del combate: se retiran los atacantes y el cofre espera otro intento. */
    private static void reset(WorldChest chest) {
        for (Mob mob : ambushers(chest)) {
            chest.level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
            mob.discard();
        }
        discardGuardian(chest);
        chest.phase = Phase.WAITING;
        chest.wave = 0;
        chest.nobodySince = 0;
        chest.guardianBattled = false;
        chest.bar.removeAllPlayers();
    }

    private static void discardGuardian(WorldChest chest) {
        Entity guardian = chest.guardian == null ? null : chest.level.getEntity(chest.guardian);
        if (guardian != null && guardian.isAlive()) {
            chest.level.sendParticles(ParticleTypes.POOF, guardian.getX(), guardian.getY() + 0.5, guardian.getZ(), 12, 0.4, 0.5, 0.4, 0.02);
            guardian.discard();
        }
        chest.guardian = null;
    }

    // ------------------------------------------------------------------ conquista

    private static void conquer(CaricraftServer mod, Party party, WorldChest chest, ServerPlayer player) {
        CHESTS.remove(chest.id);
        chest.bar.removeAllPlayers();
        UUID actorId = player != null ? player.getUUID() : party.members().iterator().next();
        String actorName = player != null ? player.getGameProfile().getName() : mod.core().state().nameOf(actorId);
        int before = mod.core().challenge().level(party);
        long reward = mod.core().challenge().onChestConquered(party, chest.challengeLevel, chest.kind == Kind.GUARDIAN, actorId, actorName);
        ChallengeManager.checkLevelUp(mod, party, before);
        int tier = ChallengeService.lootTier(chest.challengeLevel);
        if (chest.level.getBlockEntity(chest.pos) instanceof ChestBlockEntity box) {
            box.unpackLootTable(player);
            addCobblemonLoot(box, tier, chest.level.getRandom());
        }
        String bonus = maybeGivePokemon(mod, chest, player, tier);
        double x = chest.pos.getX() + 0.5;
        double z = chest.pos.getZ() + 0.5;
        chest.level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, chest.pos.getY() + 1, z, 50, 0.5, 0.8, 0.5, 0.35);
        chest.level.playSound(null, chest.pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1.2f);
        notifyParty(mod, party, true, "caricraft.notify.chest_conquered", List.of(String.valueOf(reward)));
        if (bonus != null && player != null) {
            mod.notify(player, true, "caricraft.notify.chest_pokemon", List.of(bonus));
        }
        mod.markDirty();
        mod.sendPartySnapshots(actorId);
    }

    /** Con Cobblemon, el botín trae además unos objetos de Cobblemon de su tabla. */
    private static void addCobblemonLoot(ChestBlockEntity box, int tier, RandomSource random) {
        if (CobblemonCompat.backend() == null) {
            return;
        }
        List<Object[]> pool = COBBLEMON_LOOT.get(Math.max(1, Math.min(3, tier)) - 1);
        int picks = 2 + random.nextInt(2);
        for (int i = 0; i < picks; i++) {
            Object[] entry = pool.get(random.nextInt(pool.size()));
            ResourceLocation id = ResourceLocation.tryParse((String) entry[0]);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }
            int min = (int) entry[1];
            int max = (int) entry[2];
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id), min + random.nextInt(max - min + 1));
            for (int slot = 0; slot < box.getContainerSize(); slot++) {
                int candidate = random.nextInt(box.getContainerSize());
                if (box.getItem(candidate).isEmpty()) {
                    box.setItem(candidate, stack);
                    break;
                }
            }
        }
    }

    /** A veces el cofre de una emboscada trae un Pokémon (con IV perfectos según la tabla). Devuelve su especie. */
    private static String maybeGivePokemon(CaricraftServer mod, WorldChest chest, ServerPlayer player, int tier) {
        PokemonBackend backend = CobblemonCompat.backend();
        if (backend == null || player == null || chest.kind != Kind.AMBUSH
                || chest.level.getRandom().nextDouble() >= POKEMON_REWARD_CHANCE[Math.max(1, Math.min(3, tier)) - 1]) {
            return null;
        }
        List<String> pool = GUARDIANS.get(0).stream().filter(backend::speciesExists).toList();
        if (pool.isEmpty()) {
            return null;
        }
        try {
            String species = pool.get(chest.level.getRandom().nextInt(pool.size()));
            int level = Math.max(5, Math.min(60, 6 + chest.challengeLevel * 2));
            return backend.give(player, species, level, chest.level.getRandom().nextFloat() < 0.05f, tier + 1, List.of());
        } catch (RuntimeException e) {
            CaricraftMod.LOGGER.warn("No se pudo dar el Pokémon del cofre del desafío", e);
            return null;
        }
    }

    private static void notifyParty(CaricraftServer mod, Party party, boolean success, String key, List<String> args) {
        for (ServerPlayer player : members(mod, party, null)) {
            mod.notify(player, success, key, args);
        }
    }

    // ------------------------------------------------------------------ bloqueo del cofre y limpieza

    private static WorldChest lockedAt(Level level, BlockPos pos) {
        for (WorldChest chest : CHESTS.values()) {
            if (chest.level == level && chest.pos.equals(pos)) {
                return chest;
            }
        }
        return null;
    }

    /** Cerrado hasta conquistarlo. */
    public static boolean blocksOpening(Player player, Level level, BlockPos pos) {
        WorldChest chest = lockedAt(level, pos);
        if (chest == null) {
            return false;
        }
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.translatable(chest.kind == Kind.GUARDIAN ? "caricraft.challenge.chest_locked_guardian"
                    : "caricraft.challenge.chest_locked").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        return true;
    }

    /** Ni se rompe. */
    public static boolean blocksBreaking(Player player, Level level, BlockPos pos) {
        return blocksOpening(player, level, pos);
    }

    /** ¿Es un atacante de un cofre que sigue en pie? (Los de cofres que ya no existen se retiran al cargar.) */
    public static boolean isLiveAmbusher(Entity entity) {
        if (!entity.getTags().contains(AMBUSH_TAG)) {
            return false;
        }
        for (WorldChest chest : CHESTS.values()) {
            if (entity.getTags().contains(chest.tag())) {
                return true;
            }
        }
        return false;
    }

    /** Al cargar un chunk: fuera los atacantes y guardianes de cofres que ya no existen. */
    public static void onEntityLoad(Entity entity) {
        if (entity.getTags().contains(AMBUSH_TAG) && !isLiveAmbusher(entity)) {
            entity.discard();
        }
    }

    /** Un atacante de la emboscada ha muerto a manos del dúo: experiencia de desafío. */
    public static void onAmbusherDeath(CaricraftServer mod, ServerPlayer killer, Entity victim) {
        Party party = mod.core().partyOf(killer.getUUID());
        if (party == null || !victim.getTags().contains(AMBUSH_TAG)) {
            return;
        }
        int before = mod.core().challenge().level(party);
        mod.core().challenge().onAmbushKill(party, ChallengeManager.levelOf(victim), victim.getTags().contains(ChallengeManager.ELITE_TAG));
        ChallengeManager.checkLevelUp(mod, party, before);
        mod.markDirty();
    }

    private static void remove(CaricraftServer mod, WorldChest chest, boolean effects) {
        CHESTS.remove(chest.id);
        chest.bar.removeAllPlayers();
        for (Mob mob : ambushers(chest)) {
            mob.discard();
        }
        discardGuardian(chest);
        if (chest.level.getBlockEntity(chest.pos) instanceof ChestBlockEntity box) {
            box.clearContent();
            box.setLootTable(null, 0L);
            chest.level.setBlock(chest.pos, Blocks.AIR.defaultBlockState(), 3);
            if (effects) {
                chest.level.sendParticles(ParticleTypes.LARGE_SMOKE, chest.pos.getX() + 0.5, chest.pos.getY() + 0.5,
                        chest.pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    /** Al parar el servidor no se deja ningún cofre cerrado ni atacantes. */
    public static void clearAll(MinecraftServer server) {
        CaricraftServer mod = CaricraftServer.get();
        for (WorldChest chest : List.copyOf(CHESTS.values())) {
            if (mod != null) {
                remove(mod, chest, false);
            }
        }
        CHESTS.clear();
        NEXT_ATTEMPT.clear();
    }

    /** El grupo se ha ido del todo: sus cofres desaparecen. */
    public static void forgetParty(CaricraftServer mod, UUID partyId) {
        for (WorldChest chest : List.copyOf(CHESTS.values())) {
            if (chest.partyId.equals(partyId)) {
                remove(mod, chest, true);
            }
        }
    }

    // ------------------------------------------------------------------ teléfono

    public static List<PhoneSnapshot.ChestView> views(Party party) {
        List<PhoneSnapshot.ChestView> views = new ArrayList<>();
        for (WorldChest chest : CHESTS.values()) {
            if (chest.partyId.equals(party.id())) {
                views.add(new PhoneSnapshot.ChestView(chest.level.dimension().location().toString(), chest.pos.getX() + 0.5,
                        chest.pos.getY(), chest.pos.getZ() + 0.5, chest.kind.name(), chest.phase == Phase.FIGHTING, chest.wave,
                        chest.waves, chest.guardianSpecies == null ? "" : "cobblemon:" + chest.guardianSpecies, chest.guardianLevel,
                        chest.challengeLevel, chest.expiresAt));
            }
        }
        return views;
    }
}
