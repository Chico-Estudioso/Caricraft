package com.chicoestudioso.caricraft.server.challenge;

import com.chicoestudioso.caricraft.CaricraftConstants;
import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.core.challenge.ChallengeService;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Escuadrones del Desafío Dúo: de vez en cuando, durante la noche, aparece cerca del dúo un cofre de botín
 * protegido por un grupo de mobs con un capitán. El cofre no se abre hasta derrotarlos a todos. Al amanecer,
 * el botín que no se ha ganado desaparece.
 */
public final class ChallengeSquads {
    public static final String SQUAD_TAG = "caricraft_squad";
    private static final String SQUAD_ID_PREFIX = "caricraft_squad_";
    private static final List<String> GUARD_TYPES = List.of("minecraft:zombie", "minecraft:husk", "minecraft:skeleton",
            "minecraft:stray", "minecraft:spider");
    private static final int GUARD_RANGE = 64;

    /** Un escuadrón activo: su cofre y cuántos guardias le quedan. */
    private static final class Squad {
        final int id;
        final UUID partyId;
        final ServerLevel level;
        final BlockPos chest;
        final int challengeLevel;
        int guardsLeft;
        boolean open;

        Squad(int id, UUID partyId, ServerLevel level, BlockPos chest, int challengeLevel, int guards) {
            this.id = id;
            this.partyId = partyId;
            this.level = level;
            this.chest = chest;
            this.challengeLevel = challengeLevel;
            this.guardsLeft = guards;
        }

        String tag() {
            return SQUAD_ID_PREFIX + id;
        }
    }

    private static final Map<Integer, Squad> SQUADS = new HashMap<>();
    /** Por grupo: {día, escuadrones que ya han salido esa noche}. */
    private static final Map<UUID, long[]> SPAWNED = new HashMap<>();
    private static int nextId = 1;
    private static boolean spawning;

    private ChallengeSquads() {
    }

    /** ¿Es guardia de un escuadrón que sigue cerrado? (Esos no se retiran en el barrido.) */
    static boolean isLiveGuard(Entity entity) {
        if (!entity.getTags().contains(SQUAD_TAG)) {
            return false;
        }
        for (Squad squad : SQUADS.values()) {
            if (!squad.open && entity.getTags().contains(squad.tag())) {
                return true;
            }
        }
        return false;
    }

    /** Mientras el propio escuadrón aparece, el filtro del desafío no lo toca. */
    public static boolean spawning() {
        return spawning;
    }

    // ------------------------------------------------------------------ ciclo

    /** Una vez por segundo, desde {@link ChallengeManager#tick}. */
    static void tick(CaricraftServer mod, Map<UUID, Party> parties, long dayTime, long day) {
        if (!mod.core().config().challenge.enabled) {
            return;
        }
        ChallengeService service = mod.core().challenge();
        long t = Math.floorMod(dayTime, ChallengeService.TICKS_PER_DAY);
        if (ChallengeService.isNight(dayTime)) {
            for (Party party : parties.values()) {
                if (!party.challenge().active()) {
                    continue;
                }
                int count = service.squadsPerNight(service.level(party));
                long[] done = SPAWNED.computeIfAbsent(party.id(), id -> new long[]{day, 0});
                if (done[0] != day) {
                    done[0] = day;
                    done[1] = 0;
                }
                if (done[1] < count && t >= ChallengeService.squadTime((int) done[1], count)) {
                    done[1]++;
                    spawn(mod, party, service.effectiveLevel(party, ChallengeService.wave(dayTime)));
                }
            }
        }
        for (Squad squad : List.copyOf(SQUADS.values())) {
            if (squad.open) {
                continue;
            }
            beacon(mod, squad);
            recount(mod, squad);
        }
    }

    /** Columna de partículas visible de lejos para los miembros del dúo. */
    private static void beacon(CaricraftServer mod, Squad squad) {
        Party party = mod.core().state().parties().get(squad.partyId);
        if (party == null) {
            return;
        }
        double x = squad.chest.getX() + 0.5;
        double z = squad.chest.getZ() + 0.5;
        for (UUID member : party.members()) {
            ServerPlayer player = mod.server().getPlayerList().getPlayer(member);
            if (player == null || player.level() != squad.level) {
                continue;
            }
            for (int i = 0; i < 12; i++) {
                squad.level.sendParticles(player, ParticleTypes.SOUL_FIRE_FLAME, true, x, squad.chest.getY() + 1 + i * 1.5, z,
                        2, 0.08, 0.3, 0.08, 0.0);
            }
        }
    }

    /** Por si algún guardia ha desaparecido sin morir: se cuenta de nuevo si hay alguien cerca para cargar la zona. */
    private static void recount(CaricraftServer mod, Squad squad) {
        if (!squad.level.isLoaded(squad.chest)
                || squad.level.getNearestPlayer(squad.chest.getX(), squad.chest.getY(), squad.chest.getZ(), 40, false) == null) {
            return;
        }
        int alive = guards(squad).size();
        if (alive < squad.guardsLeft) {
            squad.guardsLeft = alive;
        }
        if (squad.guardsLeft <= 0) {
            Player nearest = squad.level.getNearestPlayer(squad.chest.getX(), squad.chest.getY(), squad.chest.getZ(), 40, false);
            unlock(mod, squad, nearest instanceof ServerPlayer sp ? sp : null);
        }
    }

    private static List<Mob> guards(Squad squad) {
        return squad.level.getEntitiesOfClass(Mob.class, new AABB(squad.chest).inflate(GUARD_RANGE),
                mob -> mob.isAlive() && mob.getTags().contains(squad.tag()));
    }

    // ------------------------------------------------------------------ aparición

    private static void spawn(CaricraftServer mod, Party party, int level) {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (UUID member : party.members()) {
            ServerPlayer player = mod.server().getPlayerList().getPlayer(member);
            if (player != null && player.level().dimension() == Level.OVERWORLD && !player.isSpectator()) {
                candidates.add(player);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        ServerLevel world = candidates.getFirst().serverLevel();
        RandomSource random = world.getRandom();
        ServerPlayer anchor = candidates.get(random.nextInt(candidates.size()));
        BlockPos pos = findSpot(world, anchor, random);
        if (pos == null) {
            CaricraftMod.LOGGER.debug("No se encontró sitio para un escuadrón cerca de {}", anchor.getGameProfile().getName());
            return;
        }
        try {
            world.setBlock(pos, Blocks.CHEST.defaultBlockState()
                    .setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)), 3);
            BlockEntity entity = world.getBlockEntity(pos);
            if (entity instanceof ChestBlockEntity chest) {
                ResourceKey<LootTable> table = ResourceKey.create(Registries.LOOT_TABLE,
                        CaricraftConstants.id("challenge/squad_cache_" + ChallengeService.lootTier(level)));
                chest.setLootTable(table, random.nextLong());
            }
            Squad squad = new Squad(nextId++, party.id(), world, pos, level, 0);
            squad.guardsLeft = spawnGuards(mod, squad, random);
            SQUADS.put(squad.id, squad);
            announce(mod, party, squad);
        } catch (RuntimeException e) {
            CaricraftMod.LOGGER.error("No se pudo crear un escuadrón del desafío", e);
        }
    }

    /** Un hueco en la superficie a 24–40 bloques del jugador: suelo firme, sin líquidos y con aire encima. */
    private static BlockPos findSpot(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 24 + random.nextDouble() * 16;
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            BlockPos column = new BlockPos(x, 0, z);
            if (!level.isLoaded(column)) {
                continue;
            }
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            BlockPos below = top.below();
            if (Math.abs(top.getY() - player.getY()) > 24
                    || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                    || !level.getFluidState(below).isEmpty() || !level.getFluidState(top).isEmpty()
                    || !level.getBlockState(top).canBeReplaced() || !level.isEmptyBlock(top.above())) {
                continue;
            }
            return top;
        }
        return null;
    }

    private static int spawnGuards(CaricraftServer mod, Squad squad, RandomSource random) {
        List<EntityType<?>> pool = new ArrayList<>();
        for (String id : GUARD_TYPES) {
            EntityType.byString(id).filter(ChallengeManager::isConfiguredType).ifPresent(pool::add);
        }
        if (pool.isEmpty()) {
            pool.add(EntityType.ZOMBIE);
        }
        int size = ChallengeService.squadSize(squad.challengeLevel);
        int spawned = 0;
        for (int i = 0; i < size; i++) {
            EntityType<?> type = pool.get(random.nextInt(pool.size()));
            Entity created = type.create(squad.level);
            if (!(created instanceof Mob mob)) {
                continue;
            }
            double angle = Math.PI * 2 * i / size;
            double radius = 2.5 + random.nextDouble() * 1.5;
            BlockPos ground = squad.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(
                    squad.chest.getX() + 0.5 + Math.cos(angle) * radius, 0, squad.chest.getZ() + 0.5 + Math.sin(angle) * radius));
            mob.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, random.nextFloat() * 360f, 0f);
            mob.finalizeSpawn(squad.level, squad.level.getCurrentDifficultyAt(ground), MobSpawnType.EVENT, null);
            boolean captain = i == 0;
            int mobLevel = squad.challengeLevel + (captain ? 3 : 1);
            mob.addTag(ChallengeManager.TAG);
            mob.addTag(SQUAD_TAG);
            mob.addTag(squad.tag());
            mob.addTag(ChallengeManager.LEVEL_TAG_PREFIX + mobLevel);
            ChallengeGear.apply(mob, mobLevel, random);
            if (captain) {
                mob.addTag(ChallengeManager.ELITE_TAG);
                ChallengeGear.markElite(mob);
                mob.setCustomName(Component.translatable("caricraft.challenge.captain", mob.getType().getDescription())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
            mob.setPersistenceRequired();
            mob.restrictTo(squad.chest, 10);
            spawning = true;
            try {
                if (squad.level.addFreshEntity(mob)) {
                    spawned++;
                }
            } finally {
                spawning = false;
            }
        }
        return spawned;
    }

    private static void announce(CaricraftServer mod, Party party, Squad squad) {
        for (UUID member : party.members()) {
            ServerPlayer player = mod.server().getPlayerList().getPlayer(member);
            if (player == null) {
                continue;
            }
            if (player.level() == squad.level) {
                double dx = squad.chest.getX() + 0.5 - player.getX();
                double dz = squad.chest.getZ() + 0.5 - player.getZ();
                long distance = Math.round(Math.sqrt(dx * dx + dz * dz));
                int octant = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0), 8);
                mod.notify(player, false, "caricraft.notify.squad_spawned",
                        List.of(String.valueOf(distance), "caricraft.dir." + octant, String.valueOf(squad.guardsLeft)));
                player.playNotifySound(SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 0.8f, 0.9f);
            }
        }
        mod.sendPartySnapshots(party.members().iterator().next());
    }

    // ------------------------------------------------------------------ guardias y cofre

    /** Un guardia ha muerto (por cualquier causa). */
    public static void onGuardDeath(Entity entity, ServerPlayer killer) {
        CaricraftServer mod = CaricraftServer.get();
        if (mod == null || !entity.getTags().contains(SQUAD_TAG)) {
            return;
        }
        for (Squad squad : SQUADS.values()) {
            if (!squad.open && entity.getTags().contains(squad.tag())) {
                squad.guardsLeft = Math.max(0, squad.guardsLeft - 1);
                if (squad.guardsLeft == 0) {
                    unlock(mod, squad, killer);
                } else {
                    mod.sendPartySnapshots(squad.partyId.equals(partyOf(mod, killer)) && killer != null
                            ? killer.getUUID() : firstMember(mod, squad));
                }
                return;
            }
        }
    }

    private static UUID partyOf(CaricraftServer mod, ServerPlayer player) {
        Party party = player == null ? null : mod.core().partyOf(player.getUUID());
        return party == null ? null : party.id();
    }

    private static UUID firstMember(CaricraftServer mod, Squad squad) {
        Party party = mod.core().state().parties().get(squad.partyId);
        return party == null || party.members().isEmpty() ? null : party.members().iterator().next();
    }

    private static void unlock(CaricraftServer mod, Squad squad, ServerPlayer player) {
        squad.open = true;
        squad.guardsLeft = 0;
        Party party = mod.core().state().parties().get(squad.partyId);
        if (party == null) {
            return;
        }
        ServerPlayer actor = player != null && party.members().contains(player.getUUID()) ? player : null;
        UUID actorId = actor != null ? actor.getUUID() : party.members().iterator().next();
        String actorName = actor != null ? actor.getGameProfile().getName() : mod.core().state().nameOf(actorId);
        int before = mod.core().challenge().level(party);
        long reward = mod.core().challenge().onSquadDefeated(party, squad.challengeLevel, actorId, actorName);
        ChallengeManager.checkLevelUp(mod, party, before);
        double x = squad.chest.getX() + 0.5;
        double z = squad.chest.getZ() + 0.5;
        squad.level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, squad.chest.getY() + 1, z, 40, 0.4, 0.6, 0.4, 0.3);
        squad.level.playSound(null, squad.chest, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1f);
        for (UUID member : party.members()) {
            ServerPlayer online = mod.server().getPlayerList().getPlayer(member);
            if (online != null) {
                mod.notify(online, true, "caricraft.notify.squad_defeated", List.of(String.valueOf(reward)));
            }
        }
        mod.markDirty();
        mod.sendPartySnapshots(actorId);
    }

    private static Squad lockedAt(Level level, BlockPos pos) {
        for (Squad squad : SQUADS.values()) {
            if (!squad.open && squad.level == level && squad.chest.equals(pos)) {
                return squad;
            }
        }
        return null;
    }

    /** No se abre hasta derrotar al escuadrón. */
    public static boolean blocksOpening(Player player, Level level, BlockPos pos) {
        Squad squad = lockedAt(level, pos);
        if (squad == null) {
            return false;
        }
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.translatable("caricraft.challenge.squad_locked", squad.guardsLeft)
                    .withStyle(ChatFormatting.RED), true);
        }
        return true;
    }

    /** Ni se rompe. */
    public static boolean blocksBreaking(Player player, Level level, BlockPos pos) {
        boolean locked = blocksOpening(player, level, pos);
        if (!locked) {
            SQUADS.values().removeIf(squad -> squad.level == level && squad.chest.equals(pos));
        }
        return locked;
    }

    // ------------------------------------------------------------------ fin de la noche

    /** Al amanecer (o al terminar el desafío): el botín no ganado desaparece y los guardias se van. */
    public static void endNight(CaricraftServer mod, Party party) {
        SPAWNED.remove(party.id());
        for (Iterator<Squad> it = SQUADS.values().iterator(); it.hasNext(); ) {
            Squad squad = it.next();
            if (squad.partyId.equals(party.id())) {
                remove(squad);
                it.remove();
            }
        }
    }

    /** Al parar el servidor no se deja ningún cofre bloqueado (no se guarda en disco). */
    public static void clearAll(MinecraftServer server) {
        SQUADS.values().forEach(ChallengeSquads::remove);
        SQUADS.clear();
        SPAWNED.clear();
    }

    private static void remove(Squad squad) {
        for (Mob guard : guards(squad)) {
            squad.level.sendParticles(ParticleTypes.POOF, guard.getX(), guard.getY() + 0.5, guard.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
            guard.discard();
        }
        if (!squad.open && squad.level.getBlockEntity(squad.chest) instanceof ChestBlockEntity chest) {
            chest.clearContent();
            chest.setLootTable(null, 0L);
            squad.level.setBlock(squad.chest, Blocks.AIR.defaultBlockState(), 3);
            squad.level.sendParticles(ParticleTypes.LARGE_SMOKE, squad.chest.getX() + 0.5, squad.chest.getY() + 0.5,
                    squad.chest.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.02);
        }
    }

    // ------------------------------------------------------------------ teléfono

    public static List<PhoneSnapshot.CacheView> views(Party party) {
        List<PhoneSnapshot.CacheView> views = new ArrayList<>();
        for (Squad squad : SQUADS.values()) {
            if (squad.partyId.equals(party.id())) {
                views.add(new PhoneSnapshot.CacheView(squad.level.dimension().location().toString(),
                        squad.chest.getX() + 0.5, squad.chest.getY(), squad.chest.getZ() + 0.5, squad.guardsLeft, squad.open));
            }
        }
        return views;
    }

    /** Para comandos y QA: ¿hay escuadrones activos? */
    public static Set<Integer> ids() {
        return Set.copyOf(SQUADS.keySet());
    }
}
