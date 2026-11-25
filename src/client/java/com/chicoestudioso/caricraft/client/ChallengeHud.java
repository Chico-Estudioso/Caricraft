package com.chicoestudioso.caricraft.client;

import com.chicoestudioso.caricraft.client.phone.PhoneScreen;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Indicador en pantalla durante una noche del Desafío Dúo. */
public final class ChallengeHud {
    private ChallengeHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        PhoneSnapshot snapshot = ClientPhoneState.snapshot();
        if (snapshot == null || snapshot.challenge() == null || !snapshot.challenge().active()
                || minecraft.options.hideGui || minecraft.screen instanceof PhoneScreen) {
            return;
        }
        PhoneSnapshot.ChallengeView c = snapshot.challenge();
        // La oleada depende de la hora: se calcula en el cliente para que el indicador esté siempre al día.
        int wave = minecraft.level == null ? c.wave()
                : com.chicoestudioso.caricraft.core.challenge.ChallengeService.wave(minecraft.level.getDayTime());
        int level = c.level() + (c.waves() ? wave : 0);
        Component text = Component.translatable("caricraft.hud.challenge", level, wave + 1, c.nightKills());
        int width = minecraft.font.width(text) + 10;
        int x = (graphics.guiWidth() - width) / 2;
        int y = 4;
        graphics.fill(x, y, x + width, y + 13, 0xB09D0208);
        graphics.fill(x, y + 13, x + width, y + 14, 0xFFFF5A5F);
        graphics.drawString(minecraft.font, text, x + 5, y + 3, 0xFFFFFFFF, true);
    }
}
