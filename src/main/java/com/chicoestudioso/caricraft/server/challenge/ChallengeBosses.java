package com.chicoestudioso.caricraft.server.challenge;

import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.core.challenge.ChallengeService;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.item.ModItems;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Jefes del desafío: cada {@code bossEvery} niveles, en la siguiente noche de desafío aparece cerca del dúo un mob
 * gigante y reforzado con barra de vida. Al derrotarlo, cada miembro conectado recibe un trofeo con su nombre y el
 * dúo, Carins, experiencia y botín. Si sale el sol antes, se retira con el resto del desafío y vuelve otra noche.
 */
public final class ChallengeBosses {
    public static final String BOSS_TAG = "caricraft_boss";
    private static final String PARTY_TAG = "caricraft_boss_party_";
    private static final String MILESTONE_TAG = "caricraft_boss_level_";
    /** Hora de la noche a la que aparece (unos 100 s después de anochecer). */
    private static final long SPAWN_AT = ChallengeService.NIGHT_START + 2_000L;
    private static final double BAR_RANGE = 64;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final class Fight {
        final UUID partyId;
        final UUID entity;
        final ServerLevel level;
        final int milestone;
        final ChallengeService.Boss boss;
        final ServerBossEvent bar;
        double x;
        double y;
        double z;
        float health;
        float maxHealth;

        Fight(UUID partyId, Mob mob, int milestone, ChallengeService.Boss boss) {
            this.partyId = partyId;
            this.entity = mob.getUUID();
            this.level = (ServerLevel) mob.level();
            this.milestone = milestone;
            this.boss = boss;
            this.bar = new ServerBossEvent(name(boss, milestone), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
            this.bar.setDarkenScreen(true);
            this.x = mob.getX();
            this.y = mob.getY();
            this.z = mob.getZ();
            this.health = mob.getHealth();
            this.maxHealth = mob.getMaxHealth();
        }
    }

    private static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    /** Noche (índice de día) en la que ya salió el jefe de cada grupo. */
    private static final Map<UUID, Long> SPAWNED = new HashMap<>();
    private static boolean spawning;

    private ChallengeBosses() {
    }

    public static boolean spawning() {
        return spawning;
    }

    static Component name(ChallengeService.Boss boss, int milestone) {
        return Component.translatable("caricraft.boss.bar", Component.translatable("caricraft.boss." + boss.id()), milestone)
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
    }

    // ------------------------------------------------------------------ ciclo

    /** Una vez por segundo, desde {@link ChallengeManager#tick}. */
    static void tick(CaricraftServer mod, Map<UUID, Party> parties, long dayTime, long day) {
        ChallengeService service = mod.core().challenge();
        long t = Math.floorMod(dayTime, ChallengeService.TICKS_PER_DAY);
        if (service.bossesEnabled() && ChallengeService.isNight(dayTime) && t >= SPAWN_AT) {
            for (Party party : parties.values()) {
                if (!party.challenge().active() || FIGHTS.containsKey(party.id()) || Long.valueOf(day).equals(SPAWNED.get(party.id()))) {
                    continue;
                }
                int milestone = service.pendingBossLevel(party);
                if (milestone > 0) {
                    SPAWNED.put(party.id(), day);
                    spawn(mod, party, milestone);
                }
            }
        }
        for (Fight fight : List.copyOf(FIGHTS.values())) {
            Party party = mod.core().state().parties().get(fight.partyId);
            if (party == null || !party.challenge().active()) {
                end(fight); // amanece: el desafío retira al jefe y volverá otra noche
                continue;
            }
            Entity entity = fight.level.getEntity(fight.entity);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                fight.x = living.getX();
                fight.y = living.getY();
                fight.z = living.getZ();
                fight.health = living.getHealth();
                fight.maxHealth = living.getMaxHealth();
                fight.bar.setProgress(Math.max(0f, Math.min(1f, living.getHealth() / living.getMaxHealth())));
            }
            for (ServerPlayer player : mod.server().getPlayerList().getPlayers()) {
                boolean near = player.level() == fight.level && player.distanceToSqr(fight.x, fight.y, fight.z) <= BAR_RANGE * BAR_RANGE;
                if (near && !fight.bar.getPlayers().contains(player)) {
                    fight.bar.addPlayer(player);
                } else if (!near && fight.bar.getPlayers().contains(player)) {
                    fight.bar.removePlayer(player);
                }
            }
        }
    }

    private static void end(Fight fight) {
        fight.bar.removeAllPlayers();
        FIGHTS.remove(fight.partyId);
    }

    /** Administración/QA: saca ya el jefe pendiente del grupo del jugador (el desafío debe estar en marcha). */
    public static String spawnNow(CaricraftServer mod, ServerPlayer player) {
        Party party = mod.core().partyOf(player.getUUID());
        if (party == null || !party.challenge().active()) {
            return "no_challenge";
        }
        if (FIGHTS.containsKey(party.id())) {
            return "already";
        }
        int milestone = mod.core().challenge().pendingBossLevel(party);
        if (milestone <= 0) {
            milestone = mod.core().challenge().nextBossLevel(party); // para probar un jefe aún no alcanzado
        }
        if (milestone <= 0) {
            return "none";
        }
        SPAWNED.put(party.id(), ChallengeManager.day(mod.server()));
        return spawn(mod, party, milestone) ? "ok" : "no_spot";
    }

    // ------------------------------------------------------------------ aparición

    private static boolean spawn(CaricraftServer mod, Party party, int milestone) {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (UUID member : party.members()) {
            ServerPlayer player = mod.server().getPlayerList().getPlayer(member);
            if (player != null && player.level().dimension() == Level.OVERWORLD && !player.isSpectator()) {
                candidates.add(player);
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        ServerLevel level = candidates.getFirst().serverLevel();
        RandomSource random = level.getRandom();
        ServerPlayer anchor = candidates.get(random.nextInt(candidates.size()));
        BlockPos pos = findSpot(level, anchor, random);
        ChallengeService.Boss boss = mod.core().challenge().boss(milestone);
        EntityType<?> type = EntityType.byString(boss.entity()).orElse(EntityType.ZOMBIE);
        if (pos == null || !(type.create(level) instanceof Mob mob)) {
            CaricraftMod.LOGGER.debug("No se pudo sacar el jefe del desafío cerca de {}", anchor.getGameProfile().getName());
            return false;
        }
        try {
            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            int gearLevel = Math.min(mod.core().config().challenge.maxLevel, milestone + 5);
            ChallengeGear.apply(mob, gearLevel, random);
            if (mob.getType() == EntityType.DROWNED) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
            }
            base(mob, Attributes.MAX_HEALTH, boss.health());
            AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damage != null) {
                damage.setBaseValue(damage.getBaseValue() + boss.damage());
            }
            base(mob, Attributes.ARMOR, boss.armor());
            base(mob, Attributes.KNOCKBACK_RESISTANCE, 0.75);
            base(mob, Attributes.FOLLOW_RANGE, 48);
            base(mob, Attributes.SCALE, boss.scale());
            mob.setHealth(mob.getMaxHealth());
            mob.addTag(ChallengeManager.TAG);
            mob.addTag(ChallengeManager.ELITE_TAG);
            mob.addTag(ChallengeManager.LEVEL_TAG_PREFIX + gearLevel);
            mob.addTag(BOSS_TAG);
            mob.addTag(PARTY_TAG + party.id());
            mob.addTag(MILESTONE_TAG + milestone);
            mob.setCustomName(Component.translatable("caricraft.boss." + boss.id()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            mob.setCustomNameVisible(true);
            mob.setGlowingTag(true);
            mob.setPersistenceRequired();
            spawning = true;
            boolean added;
            try {
                added = level.addFreshEntity(mob);
            } finally {
                spawning = false;
            }
            if (!added) {
                return false;
            }
            FIGHTS.put(party.id(), new Fight(party.id(), mob, milestone, boss));
            announce(mod, party, boss, milestone, pos);
            return true;
        } catch (RuntimeException e) {
            CaricraftMod.LOGGER.error("No se pudo crear el jefe del desafío", e);
            return false;
        }
    }

    private static void base(Mob mob, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** Un hueco en la superficie a 20–32 bloques del jugador, con sitio para un mob grande. */
    private static BlockPos findSpot(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 20 + random.nextDouble() * 12;
            BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * distance, 0, player.getZ() + Math.sin(angle) * distance);
            if (!level.isLoaded(column)) {
                continue;
            }
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            BlockPos below = top.below();
            if (Math.abs(top.getY() - player.getY()) > 20
                    || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                    || !level.getFluidState(below).isEmpty() || !level.getFluidState(top).isEmpty()
                    || !level.isEmptyBlock(top) || !level.isEmptyBlock(top.above()) || !level.isEmptyBlock(top.above(2))
                    || !level.isEmptyBlock(top.above(3))) {
                continue;
            }
            return top;
        }
        return null;
    }

    private static void announce(CaricraftServer mod, Party party, ChallengeService.Boss boss, int milestone, BlockPos pos) {
        for (UUID member : party.members()) {
            ServerPlayer player = mod.server().getPlayerList().getPlayer(member);
            if (player == null) {
                continue;
            }
            double dx = pos.getX() + 0.5 - player.getX();
            double dz = pos.getZ() + 0.5 - player.getZ();
            int octant = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0), 8);
            mod.notify(player, false, "caricraft.notify.boss_spawned", List.of("caricraft.boss." + boss.id(), String.valueOf(milestone),
                    String.valueOf(Math.round(Math.sqrt(dx * dx + dz * dz))), "caricraft.dir." + octant));
            player.displayClientMessage(Component.translatable("caricraft.boss.actionbar", Component.translatable("caricraft.boss." + boss.id()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
            player.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 1.2f);
        }
        mod.sendPartySnapshots(party.members().iterator().next());
    }

    // ------------------------------------------------------------------ derrota

    /** Desde {@code AFTER_DEATH}: si era un jefe y lo ha derrotado alguien del dúo, premios y trofeo. */
    public static void onBossDeath(Entity entity, ServerPlayer killer) {
        CaricraftServer mod = CaricraftServer.get();
        if (mod == null) {
            return;
        }
        UUID partyId = null;
        int milestone = 0;
        for (String tag : entity.getTags()) {
            if (tag.startsWith(PARTY_TAG)) {
                try {
                    partyId = UUID.fromString(tag.substring(PARTY_TAG.length()));
                } catch (IllegalArgumentException ignored) {
                    // etiqueta rota: no se premia
                }
            } else if (tag.startsWith(MILESTONE_TAG)) {
                milestone = parse(tag.substring(MILESTONE_TAG.length()));
            }
        }
        Fight fight = partyId == null ? null : FIGHTS.get(partyId);
        if (fight != null && fight.entity.equals(entity.getUUID())) {
            end(fight);
        }
        Party party = partyId == null ? null : mod.core().state().parties().get(partyId);
        if (party == null || milestone <= 0 || killer == null || !party.members().contains(killer.getUUID())) {
            return; // sin mérito del dúo: volverá otra noche
        }
        ChallengeService service = mod.core().challenge();
        List<ServerPlayer> online = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (UUID member : party.members()) {
            ServerPlayer player = mod.server().getPlayerList().getPlayer(member);
            if (player != null) {
                online.add(player);
            }
            names.add(mod.core().state().nameOf(member));
        }
        String players = String.join(" & ", names);
        int before = service.level(party);
        long reward = service.onBossDefeated(party, milestone, killer.getUUID(), killer.getGameProfile().getName(), players,
                System.currentTimeMillis());
        if (reward < 0) {
            return;
        }
        ChallengeService.Boss boss = service.boss(milestone);
        ServerLevel level = (ServerLevel) entity.level();
        dropLoot(level, entity, milestone);
        for (ServerPlayer player : online) {
            ItemStack trophy = trophy(boss, milestone, players);
            if (!player.getInventory().add(trophy)) {
                player.drop(trophy, false);
            }
            mod.notify(player, true, "caricraft.notify.boss_defeated", List.of("caricraft.boss." + boss.id(), String.valueOf(milestone),
                    String.valueOf(reward)));
            player.displayClientMessage(Component.translatable("caricraft.boss.defeated_actionbar", Component.translatable("caricraft.boss." + boss.id()))
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        mod.markDirty();
        ChallengeManager.checkLevelUp(mod, party, before);
        if (!online.isEmpty()) {
            mod.sendPartySnapshots(online.getFirst().getUUID());
        }
    }

    private static int parse(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Trofeo con el nombre del jefe, el nivel, el dúo y la fecha. */
    static ItemStack trophy(ChallengeService.Boss boss, int milestone, String players) {
        ItemStack stack = new ItemStack(ModItems.CHALLENGE_TROPHY);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("caricraft.boss.trophy", Component.translatable("caricraft.boss." + boss.id()))
                .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD)));
        String date = DATE.format(Instant.now().atZone(ZoneId.systemDefault()));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("caricraft.boss.trophy_level", milestone).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.LIGHT_PURPLE)),
                Component.literal(players).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)),
                Component.literal(date).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.DARK_GRAY)))));
        return stack;
    }

    private static void dropLoot(ServerLevel level, Entity at, int milestone) {
        RandomSource random = level.getRandom();
        int tier = milestone / 5;
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Items.DIAMOND, 2 + tier + random.nextInt(2)));
        loot.add(new ItemStack(Items.EMERALD, 4 + 2 * tier));
        loot.add(new ItemStack(Items.GOLDEN_APPLE, 1 + tier / 2));
        loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 6 + 2 * tier));
        if (milestone >= 15) {
            loot.add(new ItemStack(Items.NETHERITE_SCRAP, 1 + (milestone >= 30 ? 1 : 0)));
        }
        if (milestone >= 25) {
            loot.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
        }
        for (ItemStack stack : loot) {
            ItemEntity item = new ItemEntity(level, at.getX(), at.getY() + 0.5, at.getZ(), stack);
            item.setDeltaMovement(random.nextGaussian() * 0.08, 0.3, random.nextGaussian() * 0.08);
            level.addFreshEntity(item);
        }
        ExperienceOrb.award(level, at.position(), 60 + 10 * milestone);
    }

    // ------------------------------------------------------------------ vistas

    public static PhoneSnapshot.BossFightView fightView(Party party) {
        Fight fight = FIGHTS.get(party.id());
        return fight == null ? null : new PhoneSnapshot.BossFightView(fight.boss.id(), fight.milestone, fight.health, fight.maxHealth,
                fight.x, fight.y, fight.z);
    }

    public static void clearAll(MinecraftServer server) {
        for (Fight fight : List.copyOf(FIGHTS.values())) {
            fight.bar.removeAllPlayers();
        }
        FIGHTS.clear();
        SPAWNED.clear();
    }
}
