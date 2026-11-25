package com.chicoestudioso.caricraft.mixin;

import com.chicoestudioso.caricraft.server.phone.PhoneGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Al morir, el CariPhone no cae al suelo: se aparta y vuelve al reaparecer. */
@Mixin(Player.class)
public abstract class PlayerPhoneDeathMixin {
    @Inject(method = "dropEquipment", at = @At("HEAD"))
    private void caricraft$keepPhoneOnDeath(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            PhoneGuard.onDeathDrops(player);
        }
    }
}
