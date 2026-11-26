package com.chicoestudioso.caricraft.integration.rct;

import com.chicoestudioso.caricraft.CaricraftMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Level cap de los entrenadores de Radical Cobblemon Trainers (RCT, el mod de gimnasios de COBBLEVERSE). No se
 * compila contra RCT ni se incluye nada suyo: se llama por reflexión a
 * {@code RCTMod.getInstance().getTrainerManager().getData(player).getLevelCap()}. Verificado con rctmod 0.19.2-beta.
 * El valor se guarda unos segundos para no recalcularlo en cada aparición.
 */
public final class RctBridge {
    private static final long CACHE_MS = 5_000L;

    private static boolean resolved;
    private static Method getInstance;
    private static Method getTrainerManager;
    private static Method getData;
    private static Method getLevelCap;
    private static final Map<UUID, long[]> CACHE = new HashMap<>();

    private RctBridge() {
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!FabricLoader.getInstance().isModLoaded("rctmod")) {
            return;
        }
        try {
            Class<?> mod = Class.forName("com.gitlab.srcmc.rctmod.api.RCTMod");
            getInstance = mod.getMethod("getInstance");
            getTrainerManager = mod.getMethod("getTrainerManager");
            getData = getTrainerManager.getReturnType().getMethod("getData", Player.class);
            getLevelCap = getData.getReturnType().getMethod("getLevelCap");
            CaricraftMod.LOGGER.info("RCT detectado: las apariciones de Pokémon pueden seguir el level cap de los entrenadores.");
        } catch (ReflectiveOperationException | RuntimeException e) {
            getLevelCap = null;
            CaricraftMod.LOGGER.warn("RCT está instalado pero su API ha cambiado; las apariciones según el level cap quedan desactivadas.", e);
        }
    }

    public static boolean available() {
        resolve();
        return getLevelCap != null;
    }

    /** Level cap actual del jugador, o vacío si RCT no está o falla. */
    public static synchronized OptionalInt levelCap(Player player) {
        if (!available()) {
            return OptionalInt.empty();
        }
        long now = System.currentTimeMillis();
        long[] cached = CACHE.get(player.getUUID());
        if (cached != null && now - cached[1] < CACHE_MS) {
            return cached[0] > 0 ? OptionalInt.of((int) cached[0]) : OptionalInt.empty();
        }
        int cap;
        try {
            Object manager = getTrainerManager.invoke(getInstance.invoke(null));
            Object data = manager == null ? null : getData.invoke(manager, player);
            cap = data == null ? 0 : (int) getLevelCap.invoke(data);
        } catch (ReflectiveOperationException | RuntimeException e) {
            cap = 0;
        }
        CACHE.put(player.getUUID(), new long[]{cap, now});
        return cap > 0 ? OptionalInt.of(cap) : OptionalInt.empty();
    }

    public static synchronized void forget(UUID player) {
        CACHE.remove(player);
    }
}
