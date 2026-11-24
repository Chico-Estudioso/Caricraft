package com.chicoestudioso.caricraft.client.phone;

import com.chicoestudioso.caricraft.client.ClientPhoneState;
import com.chicoestudioso.caricraft.network.PhoneAction;
import com.chicoestudioso.caricraft.network.PhoneActionPayload;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Pantalla provisional del CariPhone (M1). La interfaz completa llega en M2. */
public class PhoneScreen extends Screen {
    public PhoneScreen() {
        super(Component.translatable("screen.caricraft.phone"));
    }

    @Override
    protected void init() {
        if (ClientPlayNetworking.canSend(PhoneActionPayload.TYPE)) {
            ClientPlayNetworking.send(PhoneActionPayload.of(PhoneAction.REQUEST_SNAPSHOT));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int h = Math.min(height - 20, 240);
        int w = (int) (h * 0.56f);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        graphics.fill(x - 4, y - 4, x + w + 4, y + h + 4, 0xFF1B1B24);
        graphics.fill(x, y, x + w, y + h, 0xFF2EC4B6);
        PhoneSnapshot snapshot = ClientPhoneState.snapshot();
        Component text = snapshot == null
                ? Component.translatable("caricraft.phone.loading")
                : Component.translatable("caricraft.phone.balance", snapshot.balance());
        graphics.drawCenteredString(font, title, width / 2, y + 8, 0xFFFFFFFF);
        graphics.drawCenteredString(font, text, width / 2, y + h / 2, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
