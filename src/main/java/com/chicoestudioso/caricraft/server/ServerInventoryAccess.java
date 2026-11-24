package com.chicoestudioso.caricraft.server;

import com.chicoestudioso.caricraft.core.mission.InventoryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Retira ítems del inventario principal y la mano secundaria (nunca de la armadura). */
public final class ServerInventoryAccess implements InventoryAccess {
    private static final int MAIN_SLOTS = 36;
    private static final int OFFHAND_SLOT = 40;

    private final ServerPlayer player;

    public ServerInventoryAccess(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public long remove(String target, long max) {
        Predicate<ItemStack> matcher = matcher(target);
        if (matcher == null || max <= 0) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        long removed = 0;
        for (int slot = 0; slot < inventory.getContainerSize() && removed < max; slot++) {
            if (slot >= MAIN_SLOTS && slot != OFFHAND_SLOT) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !matcher.test(stack)) {
                continue;
            }
            int take = (int) Math.min(stack.getCount(), max - removed);
            inventory.removeItem(slot, take);
            removed += take;
        }
        if (removed > 0) {
            inventory.setChanged();
            player.containerMenu.broadcastChanges();
        }
        return removed;
    }

    /** Devuelve un predicado para un id de ítem o {@code #tag}, o {@code null} si no es válido. */
    static Predicate<ItemStack> matcher(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        if (target.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(target.substring(1));
            if (id == null) {
                return null;
            }
            TagKey<net.minecraft.world.item.Item> tag = TagKey.create(Registries.ITEM, id);
            return stack -> stack.is(tag);
        }
        ResourceLocation id = ResourceLocation.tryParse(target);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(id);
        return stack -> stack.is(item);
    }
}
