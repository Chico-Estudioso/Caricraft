package com.chicoestudioso.caricraft.integration.cobblemon;

import com.chicoestudioso.caricraft.CaricraftMod;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Punto de entrada del adaptador opcional. No referencia clases de Cobblemon: solo carga
 * {@link CobblemonHooks} si el mod está presente.
 */
public final class CobblemonCompat {
    private static boolean active;
    private static com.chicoestudioso.caricraft.server.cobbleverse.PokemonBackend backend;

    private CobblemonCompat() {
    }

    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded("cobblemon")) {
            CaricraftMod.LOGGER.info("Cobblemon no está instalado: misiones de Cobblemon desactivadas.");
            return;
        }
        try {
            CobblemonHooks.register();
            backend = new CobblemonBackend();
            active = true;
            String version = FabricLoader.getInstance().getModContainer("cobblemon")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
            CaricraftMod.LOGGER.info("Integración con Cobblemon {} activa.", version);
        } catch (Throwable t) {
            // Una API incompatible no debe tumbar el servidor: el resto del mod sigue funcionando.
            CaricraftMod.LOGGER.error("No se pudo activar la integración con Cobblemon; se desactiva.", t);
        }
    }

    public static boolean active() {
        return active;
    }

    /** Equipo, PC y creación de Pokémon para la tienda Cobbleverse, o {@code null} sin Cobblemon. */
    public static com.chicoestudioso.caricraft.server.cobbleverse.PokemonBackend backend() {
        return backend;
    }
}
