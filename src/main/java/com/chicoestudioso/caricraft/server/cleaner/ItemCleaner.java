package com.chicoestudioso.caricraft.server.cleaner;

import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.cleaner.CleanerSchedule;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Limpieza periódica del suelo contra el lag: cada {@code intervalMinutes} se retiran los objetos tirados, los orbes
 * de experiencia y las flechas sueltas de las zonas cargadas. Antes se avisa a todos. No se toca:
 * <ul>
 *     <li>lo que hay cerca de donde ha muerto un jugador hace poco (Lenient Death lo guarda 15 min), ni cerca del
 *     último suelo que pisó (por si sus cosas se movieron al caer al vacío);</li>
 *     <li>lo tirado hace menos de {@code protectNewSeconds};</li>
 *     <li>los objetos con nombre y los de la lista protegida.</li>
 * </ul>
 * Nunca toca mobs, Pokémon, marcos, soportes ni vehículos.
 */
public final class ItemCleaner {
    private static final CleanerSchedule SCHEDULE = new CleanerSchedule();
    private static final Map<UUID, Vec3> LAST_GROUND = new HashMap<>();
    private static final Map<UUID, String> LAST_GROUND_DIM = new HashMap<>();
    private static int lastWarned;
    private static int scheduledInterval;
    private static long ticks;
    private static List<String> protectedSource = List.of();
    private static Predicate<ItemStack> protectedMatcher = stack -> false;

    private ItemCleaner() {
    }

    /** Resultado de una limpieza. */
    public record Result(int items, int orbs, int arrows, int kept) {
        public int total() {
            return items + orbs + arrows;
        }
    }

    // ------------------------------------------------------------------ ciclo

    public static void tick(MinecraftServer server, CaricraftServer mod) {
        if (++ticks % 20 != 0) {
            return;
        }
        CaricraftConfig.CleanerConfig config = mod.core().config().cleaner;
        long now = System.currentTimeMillis();
        if (!config.enabled) {
            scheduledInterval = 0;
            return;
        }
        if (scheduledInterval != config.intervalMinutes || SCHEDULE.nextAt() == 0) {
            scheduledInterval = config.intervalMinutes;
            SCHEDULE.schedule(now, config.intervalMinutes * 60_000L);
            lastWarned = 0;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.onGround() && !player.isSpectator()) {
                LAST_GROUND.put(player.getUUID(), player.position());
                LAST_GROUND_DIM.put(player.getUUID(), player.level().dimension().location().toString());
            }
        }
        if (config.warnings) {
            int warning = CleanerSchedule.warningFor(now, SCHEDULE.nextAt(), lastWarned);
            if (warning > 0) {
                lastWarned = warning;
                warn(mod, server, warning);
            }
        }
        if (SCHEDULE.due(now)) {
            Result result = clean(server, mod);
            SCHEDULE.schedule(now, config.intervalMinutes * 60_000L);
            lastWarned = 0;
            announce(mod, server, result, config.intervalMinutes);
        }
    }

    private static void warn(CaricraftServer mod, MinecraftServer server, int seconds) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (seconds >= 30) {
                mod.notify(player, false, "caricraft.notify.cleaner_soon", List.of(String.valueOf(seconds)));
            }
            player.displayClientMessage(Component.translatable("caricraft.cleaner.countdown", seconds)
                    .withStyle(seconds <= 5 ? ChatFormatting.RED : ChatFormatting.GOLD), true);
            if (seconds <= 3) {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.MASTER, 0.6f, 1.2f);
            }
        }
    }

    private static void announce(CaricraftServer mod, MinecraftServer server, Result result, int minutes) {
        CaricraftMod.LOGGER.info("Limpieza del suelo: {} objetos, {} orbes y {} flechas retirados ({} protegidos)",
                result.items(), result.orbs(), result.arrows(), result.kept());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            mod.notify(player, true, "caricraft.notify.cleaner_done",
                    List.of(String.valueOf(result.total()), String.valueOf(minutes)));
            player.displayClientMessage(Component.translatable("caricraft.cleaner.done", result.total()).withStyle(ChatFormatting.GREEN), true);
        }
    }

    /** Segundos hasta la próxima limpieza, o -1 si está desactivada. */
    public static long secondsLeft(CaricraftServer mod) {
        if (!mod.core().config().cleaner.enabled || SCHEDULE.nextAt() == 0) {
            return -1;
        }
        return Math.max(0, (SCHEDULE.nextAt() - System.currentTimeMillis()) / 1000);
    }

    /** Limpia ya (comando) y reinicia la cuenta. */
    public static Result cleanNow(CaricraftServer mod) {
        Result result = clean(mod.server(), mod);
        CaricraftConfig.CleanerConfig config = mod.core().config().cleaner;
        SCHEDULE.schedule(System.currentTimeMillis(), config.intervalMinutes * 60_000L);
        lastWarned = 0;
        announce(mod, mod.server(), result, config.intervalMinutes);
        return result;
    }

    // ------------------------------------------------------------------ muertes

    /** Un jugador ha muerto: lo que soltó (y lo que pueda haberse movido a su último suelo) queda protegido un rato. */
    public static void onPlayerDeath(CaricraftServer mod, ServerPlayer player) {
        CaricraftConfig.CleanerConfig config = mod.core().config().cleaner;
        if (config.protectDeathMinutes <= 0) {
            return;
        }
        long until = System.currentTimeMillis() + config.protectDeathMinutes * 60_000L;
        SCHEDULE.protectDeath(player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ(), until);
        Vec3 ground = LAST_GROUND.get(player.getUUID());
        String dim = LAST_GROUND_DIM.get(player.getUUID());
        if (ground != null && dim != null) {
            SCHEDULE.protectDeath(dim, ground.x, ground.y, ground.z, until);
        }
    }

    public static void forget(UUID player) {
        LAST_GROUND.remove(player);
        LAST_GROUND_DIM.remove(player);
    }

    // ------------------------------------------------------------------ limpieza

    static Result clean(MinecraftServer server, CaricraftServer mod) {
        CaricraftConfig.CleanerConfig config = mod.core().config().cleaner;
        long now = System.currentTimeMillis();
        SCHEDULE.purge(now);
        Predicate<ItemStack> protectedItem = matcher(config.protectedItems);
        int items = 0;
        int orbs = 0;
        int arrows = 0;
        int kept = 0;
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().location().toString();
            List<Entity> doomed = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity == null || entity.isRemoved()) {
                    continue;
                }
                boolean candidate = entity instanceof ItemEntity
                        || (config.xpOrbs && entity instanceof ExperienceOrb)
                        || (config.arrows && (entity instanceof Arrow || entity instanceof SpectralArrow));
                if (!candidate) {
                    continue;
                }
                if (SCHEDULE.protectedAt(dimension, entity.getX(), entity.getY(), entity.getZ(), config.protectDeathRadius, now)) {
                    kept++;
                    continue;
                }
                if (entity instanceof ItemEntity item) {
                    ItemStack stack = item.getItem();
                    if (item.getAge() < config.protectNewSeconds * 20
                            || (config.protectNamed && stack.has(DataComponents.CUSTOM_NAME))
                            || protectedItem.test(stack)) {
                        kept++;
                        continue;
                    }
                }
                doomed.add(entity);
            }
            for (Entity entity : doomed) {
                if (entity instanceof ItemEntity) {
                    items++;
                } else if (entity instanceof ExperienceOrb) {
                    orbs++;
                } else {
                    arrows++;
                }
                entity.discard();
            }
        }
        return new Result(items, orbs, arrows, kept);
    }

    /** Ids ({@code minecraft:elytra}) o etiquetas ({@code #minecraft:shulker_boxes}). */
    static Predicate<ItemStack> matcher(List<String> entries) {
        if (entries.equals(protectedSource)) {
            return protectedMatcher;
        }
        Set<Item> items = new HashSet<>();
        List<TagKey<Item>> tags = new ArrayList<>();
        for (String raw : entries) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            if (raw.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(raw.substring(1));
                if (id != null) {
                    tags.add(TagKey.create(Registries.ITEM, id));
                }
            } else {
                ResourceLocation id = ResourceLocation.tryParse(raw);
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                    items.add(BuiltInRegistries.ITEM.get(id));
                }
            }
        }
        protectedSource = List.copyOf(entries);
        protectedMatcher = stack -> items.contains(stack.getItem()) || tags.stream().anyMatch(stack::is);
        return protectedMatcher;
    }
}
