package com.chicoestudioso.caricraft.mixin;

import com.chicoestudioso.caricraft.item.PhoneBinding;
import com.chicoestudioso.caricraft.server.phone.PhoneGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** El CariPhone personal no se puede tirar (tecla Q, arrastrarlo fuera del inventario, modo creativo…). */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerPhoneMixin {
    /** Tecla de tirar con el teléfono en la mano: ni siquiera se saca del inventario. */
    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void caricraft$keepSelectedPhone(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!self.isDeadOrDying() && PhoneBinding.isProtected(self.getInventory().getSelected())) {
            PhoneGuard.refuseDrop(self);
            cir.setReturnValue(false);
        }
    }

    /** Cualquier otro intento de tirarlo: vuelve al inventario si cabe. */
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void caricraft$keepThrownPhone(ItemStack stack, boolean throwRandomly, boolean retainOwnership,
                                           CallbackInfoReturnable<ItemEntity> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!self.isDeadOrDying() && PhoneBinding.isProtected(stack) && self.getInventory().add(stack)) {
            PhoneGuard.refuseDrop(self);
            cir.setReturnValue(null);
        }
    }

    /** Si no cabía y ha caído al suelo, que no se destruya ni lo recoja otro. */
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("RETURN"))
    private void caricraft$protectDroppedPhone(ItemStack stack, boolean throwRandomly, boolean retainOwnership,
                                               CallbackInfoReturnable<ItemEntity> cir) {
        ItemEntity entity = cir.getReturnValue();
        if (entity != null && PhoneBinding.isProtected(entity.getItem())) {
            PhoneGuard.protect(entity, (ServerPlayer) (Object) this);
        }
    }
}
