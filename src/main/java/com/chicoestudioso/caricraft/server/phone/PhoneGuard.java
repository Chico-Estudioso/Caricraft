package com.chicoestudioso.caricraft.server.phone;

import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;
import com.chicoestudioso.caricraft.core.phone.PhonePolicy;
import com.chicoestudioso.caricraft.core.phone.PhonePolicy.Action;
import com.chicoestudioso.caricraft.core.phone.PhonePolicy.PhoneTag;
import com.chicoestudioso.caricraft.item.PhoneBinding;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CariPhone personal en el servidor. Cada segundo revisa lo que lleva cada jugador (inventario, cursor y
 * contenedor abierto) y aplica {@link PhonePolicy}: un único teléfono válido, ligado a su dueño. Además:
 * <ul>
 *     <li>No se puede tirar (mixin en {@code ServerPlayer.drop}).</li>
 *     <li>No se puede guardar en contenedores (mixin en {@code Slot.mayPlace}, más esta revisión para huecos de
 *     otros mods), marcos, soportes de armadura, allays ni vasijas.</li>
 *     <li>Al morir no cae: se guarda y vuelve al reaparecer.</li>
 * </ul>
 */
public final class PhoneGuard {
    private static final int SCAN_TICKS = 20;
    /** Revisiones seguidas sin teléfono, por jugador. */
    private static final Map<UUID, Integer> MISSING_SCANS = new HashMap<>();
    /** Teléfono apartado al morir, hasta que el jugador reaparece. */
    private static final Map<UUID, ItemStack> KEPT_ON_DEATH = new HashMap<>();

    /** Dónde está un teléfono encontrado y cómo quitarlo de ahí. */
    private record Found(PhoneTag tag, ItemStack stack, Runnable remove, boolean inInventory) {
    }

    private PhoneGuard() {
    }

    /** Aplica la configuración del servidor (también la usa el cliente integrado). */
    public static void configure(CaricraftConfig config) {
        PhoneBinding.setPersonal(config.phone.personal);
    }

    public static void forget(UUID player) {
        MISSING_SCANS.remove(player);
    }

    // ------------------------------------------------------------------ revisión periódica

    public static void tick(MinecraftServer server, CaricraftServer mod) {
        if (!PhoneBinding.personal() || server.getTickCount() % SCAN_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                scan(mod, player);
            } catch (RuntimeException e) {
                CaricraftMod.LOGGER.error("Error revisando el CariPhone de {}", player.getGameProfile().getName(), e);
            }
        }
    }

    /** Revisa los teléfonos de un jugador y aplica las reglas. */
    public static void scan(CaricraftServer mod, ServerPlayer player) {
        if (!PhoneBinding.personal() || player.isDeadOrDying() || player.isRemoved()) {
            return;
        }
        UUID uuid = player.getUUID();
        String name = player.getGameProfile().getName();
        ItemStack kept = KEPT_ON_DEATH.remove(uuid);
        if (kept != null) {
            give(player, kept);
        }

        List<Found> found = collect(player);
        PlayerProfile profile = mod.core().onPlayerJoin(uuid, name);
        boolean firstTime = !profile.starterPhoneGiven();
        int missingScans = MISSING_SCANS.getOrDefault(uuid, 0);
        boolean replace = firstTime || missingScans + 1 >= mod.core().config().phone.replaceAfterSeconds;
        int serialBefore = profile.phoneSerial();
        PhonePolicy.Plan plan = PhonePolicy.plan(profile, found.stream().map(Found::tag).toList(), replace);

        if (plan.missing() && !plan.issueNew() && plan.actions().stream().noneMatch(a -> a == Action.BIND)) {
            MISSING_SCANS.put(uuid, missingScans + 1);
        } else {
            MISSING_SCANS.remove(uuid);
        }

        boolean removedSomething = false;
        for (int i = 0; i < found.size(); i++) {
            Found phone = found.get(i);
            switch (plan.actions().get(i)) {
                case KEEP -> {
                    if (!name.equals(phone.tag().ownerName())) {
                        PhoneBinding.bind(phone.stack(), uuid, name, plan.serial());
                    }
                    pullIntoInventory(player, phone);
                }
                case BIND -> {
                    PhoneBinding.bind(phone.stack(), uuid, name, plan.serial());
                    pullIntoInventory(player, phone);
                }
                case REMOVE -> {
                    phone.remove().run();
                    removedSomething = true;
                }
            }
        }
        if (plan.issueNew()) {
            give(player, PhoneBinding.create(uuid, name, plan.serial()));
            if (firstTime) {
                mod.notify(player, true, "caricraft.notify.welcome", List.of());
            } else {
                mod.notify(player, true, "caricraft.notify.phone_replaced", List.of());
            }
        } else if (removedSomething) {
            player.displayClientMessage(Component.translatable("caricraft.phone.removed").withStyle(ChatFormatting.GOLD), true);
        }
        if (firstTime) {
            // Con replace activo, tras el plan el jugador ya tiene su teléfono (conservado, vinculado o nuevo).
            profile.setStarterPhoneGiven(true);
        }
        if (profile.phoneSerial() != serialBefore || plan.changesAnything()) {
            player.getInventory().setChanged();
            mod.markDirty();
        }
    }

    /** Inventario, objeto en el cursor y huecos del contenedor abierto que no son del inventario del jugador. */
    private static List<Found> collect(ServerPlayer player) {
        List<Found> found = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (PhoneBinding.isPhone(stack)) {
                int slot = i;
                found.add(new Found(PhoneBinding.read(stack), stack, () -> inventory.setItem(slot, ItemStack.EMPTY), true));
            }
        }
        AbstractContainerMenu menu = player.containerMenu;
        ItemStack carried = menu.getCarried();
        if (PhoneBinding.isPhone(carried)) {
            // En la mano del ratón cuenta como llevado: no se mueve, solo se retira si no vale.
            found.add(new Found(PhoneBinding.read(carried), carried, () -> menu.setCarried(ItemStack.EMPTY), true));
        }
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (PhoneBinding.isPhone(stack)) {
                found.add(new Found(PhoneBinding.read(stack), stack, () -> slot.set(ItemStack.EMPTY), false));
            }
        }
        return found;
    }

    /** Devuelve al inventario el teléfono válido que se ha quedado en un contenedor. */
    private static void pullIntoInventory(ServerPlayer player, Found phone) {
        if (phone.inInventory()) {
            return;
        }
        ItemStack copy = phone.stack().copy();
        if (player.getInventory().add(copy)) {
            phone.remove().run();
            player.displayClientMessage(Component.translatable("caricraft.phone.no_store").withStyle(ChatFormatting.GOLD), true);
        }
    }

    private static void give(ServerPlayer player, ItemStack phone) {
        if (!player.getInventory().add(phone)) {
            ItemEntity entity = player.drop(phone, false, true);
            if (entity != null) {
                protect(entity, player);
            }
            player.displayClientMessage(Component.translatable("caricraft.phone.at_feet").withStyle(ChatFormatting.GOLD), false);
        }
    }

    // ------------------------------------------------------------------ tirar, morir y reaparecer

    /** El jugador intenta tirar el teléfono: se le avisa y se sincroniza su inventario. */
    public static void refuseDrop(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("caricraft.phone.no_drop").withStyle(ChatFormatting.GOLD), true);
        player.containerMenu.sendAllDataToRemote();
        if (player.containerMenu != player.inventoryMenu) {
            player.inventoryMenu.sendAllDataToRemote();
        }
    }

    /** Si aun así acaba en el suelo (inventario lleno), que no se rompa ni lo coja otro. */
    public static void protect(ItemEntity entity, Player owner) {
        entity.setInvulnerable(true);
        entity.setTarget(owner.getUUID());
        entity.setNoPickUpDelay();
    }

    /** Antes de soltar el inventario al morir: se apartan los teléfonos y vuelve el del jugador al reaparecer. */
    public static void onDeathDrops(ServerPlayer player) {
        if (!PhoneBinding.personal()) {
            return;
        }
        Inventory inventory = player.getInventory();
        ItemStack kept = ItemStack.EMPTY;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (PhoneBinding.isPhone(stack)) {
                if (kept.isEmpty() && PhoneBinding.usableBy(stack, player.getUUID())) {
                    kept = stack.copy();
                }
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (PhoneBinding.isPhone(carried)) {
            if (kept.isEmpty() && PhoneBinding.usableBy(carried, player.getUUID())) {
                kept = carried.copy();
            }
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        if (!kept.isEmpty()) {
            KEPT_ON_DEATH.put(player.getUUID(), kept);
        }
    }

    public static void onRespawn(ServerPlayer player) {
        ItemStack kept = KEPT_ON_DEATH.remove(player.getUUID());
        if (kept != null) {
            give(player, kept);
            player.displayClientMessage(Component.translatable("caricraft.phone.kept").withStyle(ChatFormatting.AQUA), true);
        }
    }

    // ------------------------------------------------------------------ marcos, soportes y vasijas

    public static InteractionResult onUseEntity(Player player, InteractionHand hand, Entity entity) {
        if (PhoneBinding.isProtected(player.getItemInHand(hand))
                && (entity instanceof ItemFrame || entity instanceof ArmorStand || entity instanceof Allay)) {
            refuseStore(player);
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    public static InteractionResult onUseBlock(Player player, InteractionHand hand, BlockState state) {
        if (PhoneBinding.isProtected(player.getItemInHand(hand)) && (state.getBlock() instanceof DecoratedPotBlock
                || "cobblemon:display_case".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()))) {
            refuseStore(player);
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    private static void refuseStore(Player player) {
        if (!player.level().isClientSide()) {
            player.displayClientMessage(Component.translatable("caricraft.phone.no_store").withStyle(ChatFormatting.GOLD), true);
        }
    }
}
