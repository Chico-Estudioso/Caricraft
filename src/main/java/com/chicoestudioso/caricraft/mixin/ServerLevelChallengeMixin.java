package com.chicoestudioso.caricraft.mixin;

import com.chicoestudioso.caricraft.server.challenge.ChallengeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Filtro del Desafío Dúo: los mobs de la lista solo entran al mundo cerca de un dúo en noche de desafío,
 * y entonces se equipan. Se engancha en el mismo punto que MobsBeGone, por lo que cubre aparición
 * natural, spawners, conversiones y comandos.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelChallengeMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void caricraft$challengeGate(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!ChallengeManager.onAddFreshEntity((ServerLevel) (Object) this, entity)) {
            cir.setReturnValue(false);
        }
    }
}
