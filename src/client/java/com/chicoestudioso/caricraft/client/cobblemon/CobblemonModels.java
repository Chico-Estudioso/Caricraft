package com.chicoestudioso.caricraft.client.cobblemon;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Dibuja modelos 3D de Pokémon con la API de Cobblemon (la misma que usa su PC). Solo se carga con Cobblemon. */
final class CobblemonModels {
    private static final Map<String, FloatingState> STATES = new HashMap<>();

    private CobblemonModels() {
    }

    /** Como en el PC de Cobblemon: centrado arriba en un hueco de {@code size} píxeles. */
    static boolean draw(GuiGraphics graphics, String key, String species, Set<String> aspects, float cx, float top, float size,
                        float yaw, float partialTicks) {
        String name = species.contains(":") ? species.substring(species.indexOf(':') + 1) : species;
        Species found = PokemonSpecies.getByName(name);
        if (found == null) {
            return false;
        }
        RenderablePokemon renderable = new RenderablePokemon(found, aspects, ItemStack.EMPTY);
        FloatingState state = STATES.computeIfAbsent(key, k -> new FloatingState());
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, top + size * 0.04f, 0);
        pose.scale(size / 10f, size / 10f, 1f);
        Quaternionf rotation = com.cobblemon.mod.common.util.math.QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(),
                new org.joml.Vector3f(13f, yaw, 0f));
        state.updatePartialTicks(partialTicks);
        PokemonGuiUtilsKt.drawProfilePokemon(renderable, pose, rotation, PoseType.PROFILE, state, partialTicks, 4.5f,
                ProfileTransformType.PROFILE, false, 1f, 1f, 1f, 1f, 0f, 0f, 13);
        pose.popPose();
        return true;
    }

    static void forget() {
        STATES.clear();
    }
}
