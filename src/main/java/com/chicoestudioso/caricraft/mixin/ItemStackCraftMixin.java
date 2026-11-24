package com.chicoestudioso.caricraft.mixin;

import com.chicoestudioso.caricraft.server.ServerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Detecta ítems fabricados (mesa de crafteo, hornos, etc.) para el objetivo {@code CRAFT_ITEM}.
 * Fabric API 0.116 no ofrece un evento de fabricación.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackCraftMixin {
    @Inject(method = "onCraftedBy", at = @At("HEAD"))
    private void caricraft$onCraftedBy(Level level, Player player, int amount, CallbackInfo ci) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ServerEvents.onCrafted(serverPlayer, (ItemStack) (Object) this, amount);
        }
    }
}
