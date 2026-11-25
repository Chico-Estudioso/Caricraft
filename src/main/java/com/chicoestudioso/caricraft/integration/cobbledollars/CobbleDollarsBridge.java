package com.chicoestudioso.caricraft.integration.cobbledollars;

import com.chicoestudioso.caricraft.CaricraftMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.OptionalLong;

/**
 * Acceso al saldo de CobbleDollars (mod de COBBLEVERSE, «All rights reserved»). No se compila contra él ni se
 * incluye nada suyo: CobbleDollars añade a cada jugador la interfaz {@code CobbleDollarsPlayer} con un getter y
 * un setter de {@link BigInteger}, y se usan por reflexión. El saldo va en los datos sincronizados del jugador,
 * así que el cliente lo ve al momento. Verificado con CobbleDollars 2.0.0+Beta-5.1.
 */
public final class CobbleDollarsBridge {
    private static final String INTERFACE = "fr.harmex.cobbledollars.common.utils.CobbleDollarsPlayer";

    private static boolean resolved;
    private static Class<?> playerInterface;
    private static Method getter;
    private static Method setter;

    private CobbleDollarsBridge() {
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!FabricLoader.getInstance().isModLoaded("cobbledollars")) {
            return;
        }
        try {
            playerInterface = Class.forName(INTERFACE);
            getter = playerInterface.getMethod("cobbleDollars$getCobbleDollars");
            setter = playerInterface.getMethod("cobbleDollars$setCobbleDollars", BigInteger.class);
            CaricraftMod.LOGGER.info("CobbleDollars detectado: la tienda Cobbleverse cobra y paga en CobbleDollars.");
        } catch (ReflectiveOperationException | RuntimeException e) {
            getter = null;
            setter = null;
            CaricraftMod.LOGGER.warn("CobbleDollars está instalado pero su API ha cambiado; la tienda en CobbleDollars queda desactivada.", e);
        }
    }

    public static boolean available() {
        resolve();
        return getter != null && setter != null;
    }

    public static OptionalLong balance(ServerPlayer player) {
        if (!available() || !playerInterface.isInstance(player)) {
            return OptionalLong.empty();
        }
        try {
            BigInteger value = (BigInteger) getter.invoke(player);
            if (value == null) {
                return OptionalLong.of(0);
            }
            return OptionalLong.of(value.min(BigInteger.valueOf(Long.MAX_VALUE)).max(BigInteger.ZERO).longValue());
        } catch (ReflectiveOperationException | RuntimeException e) {
            CaricraftMod.LOGGER.warn("No se pudo leer el saldo de CobbleDollars", e);
            return OptionalLong.empty();
        }
    }

    private static boolean set(ServerPlayer player, BigInteger value) {
        try {
            setter.invoke(player, value);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            CaricraftMod.LOGGER.warn("No se pudo cambiar el saldo de CobbleDollars", e);
            return false;
        }
    }

    public static boolean add(ServerPlayer player, long amount) {
        OptionalLong balance = balance(player);
        return amount >= 0 && balance.isPresent()
                && set(player, BigInteger.valueOf(balance.getAsLong()).add(BigInteger.valueOf(amount)));
    }

    /** Quita {@code amount} solo si hay saldo suficiente. */
    public static boolean remove(ServerPlayer player, long amount) {
        OptionalLong balance = balance(player);
        return amount >= 0 && balance.isPresent() && balance.getAsLong() >= amount
                && set(player, BigInteger.valueOf(balance.getAsLong() - amount));
    }
}
