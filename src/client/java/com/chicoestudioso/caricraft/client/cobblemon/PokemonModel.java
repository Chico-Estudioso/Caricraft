package com.chicoestudioso.caricraft.client.cobblemon;

import com.chicoestudioso.caricraft.CaricraftMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Set;

/**
 * Modelos 3D de Pokémon en la interfaz. No referencia clases de Cobblemon: si no está instalado (o su API
 * falla), devuelve {@code false} y quien llama dibuja otra cosa.
 */
public final class PokemonModel {
    private static final boolean AVAILABLE = FabricLoader.getInstance().isModLoaded("cobblemon");
    private static boolean broken;

    private PokemonModel() {
    }

    public static boolean available() {
        return AVAILABLE && !broken;
    }

    /**
     * @param key  identifica la animación (p. ej. el UUID del Pokémon o la oferta)
     * @param cx   centro horizontal
     * @param top  borde superior del hueco
     * @param size lado del hueco en píxeles (como las ranuras del PC de Cobblemon, que miden 25)
     */
    public static boolean draw(GuiGraphics graphics, String key, String species, Set<String> aspects, float cx, float top,
                               float size, float yaw, float partialTicks) {
        if (!available() || species == null || species.isEmpty()) {
            return false;
        }
        try {
            return CobblemonModels.draw(graphics, key, species, aspects, cx, top, size, yaw, partialTicks);
        } catch (Throwable t) {
            broken = true;
            CaricraftMod.LOGGER.error("No se pudieron dibujar modelos de Cobblemon; se usan iconos.", t);
            return false;
        }
    }

    public static void forget() {
        if (AVAILABLE) {
            try {
                CobblemonModels.forget();
            } catch (Throwable ignored) {
                // sin Cobblemon no hay nada que olvidar
            }
        }
    }
}
