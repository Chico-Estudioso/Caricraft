package com.chicoestudioso.caricraft.server.tips;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;
import com.chicoestudioso.caricraft.core.tips.TipRotation;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Envía de vez en cuando un consejo del CariPhone por el chat a cada jugador que no los haya desactivado. */
public final class TipManager {
    private static final Map<UUID, Long> NEXT_TIP = new HashMap<>();
    private static final Map<UUID, TipRotation> ROTATIONS = new HashMap<>();
    private static final Random RANDOM = new Random();

    private TipManager() {
    }

    public static void onJoin(CaricraftServer mod, ServerPlayer player) {
        CaricraftConfig.TipsConfig config = mod.core().config().tips;
        NEXT_TIP.put(player.getUUID(), (long) mod.server().getTickCount() + config.firstDelayMinutes * 1200L);
    }

    public static void forget(UUID player) {
        NEXT_TIP.remove(player);
        ROTATIONS.remove(player);
    }

    public static void tick(MinecraftServer server, CaricraftServer mod) {
        CaricraftConfig.TipsConfig config = mod.core().config().tips;
        if (!config.enabled || server.getTickCount() % 20 != 0) {
            return;
        }
        long now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Long next = NEXT_TIP.get(player.getUUID());
            if (next == null) {
                onJoin(mod, player);
                continue;
            }
            if (now < next) {
                continue;
            }
            NEXT_TIP.put(player.getUUID(), now + config.intervalMinutes * 1200L);
            send(mod, player, false);
        }
    }

    /**
     * Envía el siguiente consejo si el jugador los tiene activados (o si lo pide él con {@code force}).
     * Devuelve si se ha enviado.
     */
    public static boolean send(CaricraftServer mod, ServerPlayer player, boolean force) {
        PlayerProfile profile = mod.core().onPlayerJoin(player.getUUID(), player.getGameProfile().getName());
        if (!force && !profile.tipsEnabled()) {
            return false;
        }
        Party party = mod.core().partyOf(player.getUUID());
        TipRotation.TipContext context = new TipRotation.TipContext(party != null && party.isDuo(),
                party == null ? Set.of() : Set.copyOf(party.ownedApps()), mod.core().config().challenge.enabled,
                FabricLoader.getInstance().isModLoaded("cobblemon"));
        return ROTATIONS.computeIfAbsent(player.getUUID(), uuid -> new TipRotation(new Random(RANDOM.nextLong())))
                .next(context)
                .map(id -> {
                    player.sendSystemMessage(message(id));
                    return true;
                })
                .orElse(false);
    }

    private static MutableComponent message(String id) {
        MutableComponent prefix = Component.translatable("caricraft.tip.prefix").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        // La tecla se resuelve en el cliente con la que tenga asignada cada jugador.
        MutableComponent text = Component.translatable("caricraft.tip." + id, Component.keybind("key.caricraft.open_phone")
                .withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY);
        return prefix.append(" ").append(text);
    }
}
