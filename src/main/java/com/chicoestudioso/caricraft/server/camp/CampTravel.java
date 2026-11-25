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

    public static ActionResult travel(CaricraftServer mod, ServerPlayer player, String target) {
        CaricraftCore core = mod.core();
        Party party = core.partyOf(player.getUUID());
        Destination destination = "partner".equals(target) ? Destination.PARTNER : Destination.CAMP;
        ServerLevel level;
        Vec3 position;
        if (destination == Destination.CAMP) {
            CampSite camp = party.camp();
            if (camp == null) {
                return ActionResult.fail("camp_missing");
            }
            ResourceLocation dimension = ResourceLocation.tryParse(camp.dimension());
            level = dimension == null ? null : mod.server().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null) {
                return ActionResult.fail("camp_missing");
            }
            BlockPos campfire = new BlockPos(camp.x(), camp.y(), camp.z());
            if (!isLitCampfire(level.getBlockState(campfire))) {
                // Sin la hoguera encendida no hay campamento (no se cobra).
                if (!level.getBlockState(campfire).is(BlockTags.CAMPFIRES)) {
                    core.camp().clearCamp(party);
                    mod.markDirty();
                }
                return ActionResult.fail("camp_campfire_gone");
            }
            position = safeSpotNear(level, campfire);
        } else {
            ServerPlayer partner = onlinePartner(mod, party, player.getUUID());
            if (partner == null) {
                return ActionResult.fail("partner_offline");
            }
            level = partner.serverLevel();
            position = partner.position();
        }
        boolean challengeNight = ChallengeManager.challengeTonight(player);
        ActionResult charged = core.camp().chargeTravel(party, player.getUUID(), destination, challengeNight);
        if (!charged.success()) {
            return charged;
        }
        try {
            effects(player.serverLevel(), player.position());
            player.stopRiding();
            player.teleportTo(level, position.x, position.y, position.z, player.getYRot(), player.getXRot());
            effects(level, position);
        } catch (RuntimeException e) {
            core.camp().refund(party, player.getUUID(), destination);
            return ActionResult.fail("internal_error");
        }
        return charged;
    }

    private static ServerPlayer onlinePartner(CaricraftServer mod, Party party, UUID self) {
        for (UUID member : party.members()) {
            if (!member.equals(self)) {
                ServerPlayer online = mod.server().getPlayerList().getPlayer(member);
                if (online != null) {
                    return online;
                }
            }
        }
        return null;
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
