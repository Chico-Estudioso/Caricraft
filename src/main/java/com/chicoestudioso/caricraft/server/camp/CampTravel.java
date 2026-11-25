package com.chicoestudioso.caricraft.server.camp;

import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.CaricraftCore;
import com.chicoestudioso.caricraft.core.camp.CampService.Destination;
import com.chicoestudioso.caricraft.core.camp.CampSite;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import com.chicoestudioso.caricraft.server.challenge.ChallengeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Parte de servidor de CariCamp: buscar la hoguera, validar destinos y teletransportar. */
public final class CampTravel {
    private CampTravel() {
    }

    private static boolean isLitCampfire(BlockState state) {
        return state.is(BlockTags.CAMPFIRES) && state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT);
    }

    public static ActionResult setCamp(CaricraftServer mod, ServerPlayer player) {
        CaricraftCore core = mod.core();
        Party party = core.partyOf(player.getUUID());
        if (!core.camp().hasApp(party)) {
            return ActionResult.fail("app_locked", "caricamp");
        }
        int r = core.config().camp.campfireRadius;
        BlockPos origin = player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            if (isLitCampfire(player.level().getBlockState(pos))) {
                double distance = pos.distSqr(origin);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
        }
        if (best == null) {
            return ActionResult.fail("camp_no_campfire", r);
        }
        return core.camp().setCamp(party, player.getUUID(), player.getGameProfile().getName(),
                player.level().dimension().location().toString(), best.getX(), best.getY(), best.getZ());
    }

    /** Comprueba que el campamento existe y su hoguera sigue encendida (borra el campamento si la rompieron). */
    static ActionResult checkCamp(CaricraftServer mod, Party party) {
        CampSite camp = party == null ? null : party.camp();
        if (camp == null) {
            return ActionResult.fail("camp_missing");
        }
        ServerLevel level = campLevel(mod, camp);
        if (level == null) {
            return ActionResult.fail("camp_missing");
        }
        BlockPos campfire = new BlockPos(camp.x(), camp.y(), camp.z());
        if (!isLitCampfire(level.getBlockState(campfire))) {
            if (!level.getBlockState(campfire).is(BlockTags.CAMPFIRES)) {
                mod.core().camp().clearCamp(party);
                mod.markDirty();
            }
            return ActionResult.fail("camp_campfire_gone");
        }
        return ActionResult.ok("ok");
    }

    private static ServerLevel campLevel(CaricraftServer mod, CampSite camp) {
        ResourceLocation dimension = ResourceLocation.tryParse(camp.dimension());
        return dimension == null ? null : mod.server().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }

    /** Al terminar la cuenta atrás: vuelve a comprobar, cobra y teletransporta al campamento. */
    static ActionResult travelToCamp(CaricraftServer mod, ServerPlayer player, Party party) {
        ActionResult check = checkCamp(mod, party);
        if (!check.success()) {
            return check;
        }
        CampSite camp = party.camp();
        ServerLevel level = campLevel(mod, camp);
        Vec3 position = safeSpotNear(level, new BlockPos(camp.x(), camp.y(), camp.z()));
        ActionResult charged = mod.core().camp().chargeTravel(party, player.getUUID(), Destination.CAMP,
                ChallengeManager.challengeTonight(player));
        if (!charged.success()) {
            return charged;
        }
        if (!teleport(player, level, position)) {
            mod.core().camp().refund(party, player.getUUID(), Destination.CAMP);
            return ActionResult.fail("internal_error");
        }
        return charged;
    }

    static boolean teleport(ServerPlayer player, ServerLevel level, Vec3 position) {
        try {
            effects(player.serverLevel(), player.position());
            player.stopRiding();
            player.teleportTo(level, position.x, position.y, position.z, player.getYRot(), player.getXRot());
            effects(level, position);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Busca junto a la hoguera un hueco con suelo firme y dos bloques de aire. */
    static Vec3 safeSpotNear(Level level, BlockPos campfire) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int dy = 0; dy >= -1; dy--) {
            for (int[] offset : offsets) {
                BlockPos feet = campfire.offset(offset[0], dy, offset[1]);
                if (standable(level, feet)) {
                    return Vec3.atBottomCenterOf(feet);
                }
            }
        }
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos feet = campfire.above(dy);
            if (standable(level, feet)) {
                return Vec3.atBottomCenterOf(feet);
            }
        }
        return Vec3.atBottomCenterOf(campfire.above());
    }

    private static boolean standable(Level level, BlockPos feet) {
        BlockState floor = level.getBlockState(feet.below());
        return floor.isFaceSturdy(level, feet.below(), Direction.UP)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty();
    }

    private static void effects(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 40, 0.4, 0.8, 0.4, 0.2);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.0f);
    }
}
