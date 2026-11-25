package com.chicoestudioso.caricraft.mixin;

import com.chicoestudioso.caricraft.item.PhoneBinding;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * El CariPhone personal solo cabe en el inventario del jugador: no en cofres, cofres de ender, hornos, mesas…
 * Los huecos de otros mods que no pasen por aquí los cubre la revisión periódica de {@code PhoneGuard}.
 */
@Mixin(Slot.class)
public abstract class SlotPhoneMixin {
    @Shadow
    @Final
    public Container container;

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void caricraft$onlyInPlayerInventory(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!(container instanceof Inventory) && PhoneBinding.isProtected(stack)) {
            cir.setReturnValue(false);
        }
    }
}
