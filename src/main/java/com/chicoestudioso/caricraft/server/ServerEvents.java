package com.chicoestudioso.caricraft.server;

import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.core.CaricraftCore;
import com.chicoestudioso.caricraft.core.mission.MissionDefinition;
import com.chicoestudioso.caricraft.core.mission.ObjectiveType;
import com.chicoestudioso.caricraft.core.mission.ProgressEvent;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;
import com.chicoestudioso.caricraft.item.ModItems;
import com.chicoestudioso.caricraft.server.challenge.ChallengeManager;
import com.chicoestudioso.caricraft.server.phone.PhoneGuard;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Enganches del mod a eventos de Fabric API en el servidor. */
public final class ServerEvents {
    private static final PlayerTracker TRACKER = new PlayerTracker();

    private ServerEvents() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CaricraftServer.start(server);
            ChallengeManager.onServerStarted(CaricraftServer.get());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(com.chicoestudioso.caricraft.server.challenge.ChallengeSquads::clearAll);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            com.chicoestudioso.caricraft.server.challenge.ChallengeBossBars.clear();
            CaricraftServer.stop();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUUID();
            TRACKER.forget(uuid);
            PhoneGuard.forget(uuid);
            com.chicoestudioso.caricraft.server.tips.TipManager.forget(uuid);
            com.chicoestudioso.caricraft.server.challenge.ChallengeBossBars.forget(handler.getPlayer());
            com.chicoestudioso.caricraft.server.lens.PhotoUploads.forget(uuid);
            com.chicoestudioso.caricraft.server.camp.TravelManager.forget(uuid);
            com.chicoestudioso.caricraft.server.cobbleverse.CobbleverseManager.forget(uuid);
            CaricraftServer mod = CaricraftServer.get();
            if (mod != null) {
                mod.rateLimiter().forget(uuid);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            CaricraftServer mod = CaricraftServer.get();
            if (mod != null) {
                TRACKER.tick(server, mod);
                ChallengeManager.tick(server, mod);
                com.chicoestudioso.caricraft.server.camp.TravelManager.tick(mod);
                PhoneGuard.tick(server, mod);
                com.chicoestudioso.caricraft.server.tips.TipManager.tick(server, mod);
            }
        });

        // CariPhone personal: vuelve al reaparecer y no se coloca en marcos, soportes, allays ni vasijas.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> PhoneGuard.onRespawn(newPlayer));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> PhoneGuard.onUseEntity(player, hand, entity));
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                PhoneGuard.onUseBlock(player, hand, level.getBlockState(hit.getBlockPos())));
        // Cofres de botín del desafío: cerrados hasta derrotar al escuadrón.
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                com.chicoestudioso.caricraft.server.challenge.ChallengeSquads.blocksOpening(player, level, hit.getBlockPos())
                        ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !com.chicoestudioso.caricraft.server.challenge.ChallengeSquads.blocksBreaking(player, level, pos));

        ServerEntityEvents.ENTITY_LOAD.register(ChallengeManager::onEntityLoad);

        EntitySleepEvents.ALLOW_SLEEPING.register((player, sleepingPos) -> {
            CaricraftServer mod = CaricraftServer.get();
            if (mod != null && mod.core().config().challenge.blockSleep && player instanceof ServerPlayer serverPlayer
                    && ChallengeManager.challengeTonight(serverPlayer)) {
                serverPlayer.displayClientMessage(Component.translatable("caricraft.challenge.no_sleep")
                        .withStyle(ChatFormatting.RED), true);
                return Player.BedSleepingProblem.OTHER_PROBLEM;
            }
            return null;
        });

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                progress(serverPlayer, ObjectiveType.MINE_BLOCK, ProgressKeys.of(state.getBlockHolder()), 1);
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.getTags().contains(com.chicoestudioso.caricraft.server.challenge.ChallengeSquads.SQUAD_TAG)) {
                com.chicoestudioso.caricraft.server.challenge.ChallengeSquads.onGuardDeath(entity,
                        source.getEntity() instanceof ServerPlayer killer ? killer : null);
            }
            if (source.getEntity() instanceof ServerPlayer killer && !(entity instanceof ServerPlayer)) {
                Set<String> keys = new HashSet<>(ProgressKeys.of(entity.getType().builtInRegistryHolder()));
                if (entity.getTags().contains(ChallengeManager.TAG)) {
                    keys.add("challenge");
                    ChallengeManager.onKill(killer, entity);
                }
                progress(killer, ObjectiveType.KILL_ENTITY, keys, 1);
            }
        });
    }

    private static void onJoin(ServerPlayer player) {
        CaricraftServer mod = CaricraftServer.get();
        if (mod == null) {
            return;
        }
        CaricraftCore core = mod.core();
        PlayerProfile profile = core.onPlayerJoin(player.getUUID(), player.getGameProfile().getName());
        if (core.config().phone.personal) {
            // Vincula o entrega su CariPhone nada más entrar (y da la bienvenida la primera vez).
            PhoneGuard.scan(mod, player);
        } else if (core.config().giveStarterPhone && !profile.starterPhoneGiven()) {
            ItemStack phone = new ItemStack(ModItems.CARIPHONE);
            if (!player.getInventory().add(phone)) {
                player.drop(phone, false);
            }
            profile.setStarterPhoneGiven(true);
            mod.notify(player, true, "caricraft.notify.welcome", List.of());
        }
        com.chicoestudioso.caricraft.server.tips.TipManager.onJoin(mod, player);
        mod.markDirty();
        mod.sendPartySnapshots(player.getUUID());
        core.parties().incomingInvites(core.state(), player.getUUID()).forEach(invite ->
                mod.notify(player, true, "caricraft.notify.invite_received", List.of(core.state().nameOf(invite.from()))));
    }

    /** Llamado por el mixin de fabricación. */
    public static void onCrafted(ServerPlayer player, ItemStack stack, int amount) {
        if (amount > 0 && !stack.isEmpty()) {
            progress(player, ObjectiveType.CRAFT_ITEM, ProgressKeys.of(stack.getItem().builtInRegistryHolder()), amount);
        }
    }

    /** Punto de entrada común para el progreso (también lo usa el adaptador de Cobblemon). */
    public static void progress(ServerPlayer player, ObjectiveType type, Set<String> keys, long amount) {
        CaricraftServer mod = CaricraftServer.get();
        if (mod == null || amount <= 0) {
            return;
        }
        try {
            List<MissionDefinition> completed = mod.core().progress(player.getUUID(), new ProgressEvent(type, keys, amount));
            mod.markDirty();
            mod.announceCompleted(player.getUUID(), completed);
        } catch (RuntimeException e) {
            CaricraftMod.LOGGER.error("Error aplicando progreso {} de {}", type, player.getGameProfile().getName(), e);
        }
    }
}
