package com.chicoestudioso.caricraft.client;

import com.chicoestudioso.caricraft.client.phone.PhoneScreen;
import com.chicoestudioso.caricraft.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Notificaciones del CariPhone con el teléfono cerrado: una tarjeta que entra por la esquina superior derecha,
 * con sonido, y se va sola. No depende de los avisos emergentes del juego (que recortan el texto y algunos
 * modpacks ocultan).
 */
public final class NotificationHud {
    private static final int WIDTH = 190;
    private static final int MAX_LINES = 4;
    private static final long SLIDE_MS = 220;
    private static final int MAX_QUEUE = 6;

    private record Popup(Component text, boolean success) {
    }

    private static final Deque<Popup> QUEUE = new ArrayDeque<>();
    private static Popup current;
    private static long shownAt;
    private static ItemStack icon;

    private NotificationHud() {
    }

    public static void push(Component text, boolean success) {
        if (QUEUE.size() >= MAX_QUEUE) {
            QUEUE.removeFirst();
        }
        QUEUE.addLast(new Popup(text, success));
    }

    public static void clear() {
        QUEUE.clear();
        current = null;
    }

    /** Debajo de las barras de jefe, si hay alguna (cada una ocupa 19 píxeles desde y=12). */
    private static int topOffset(Minecraft minecraft) {
        int bars = ((com.chicoestudioso.caricraft.client.mixin.BossHealthOverlayAccessor) minecraft.gui.getBossOverlay())
                .caricraft$events().size();
        return bars == 0 ? 4 : Math.min(8 + 19 * bars, 8 + 19 * 3);
    }

    private static long duration(int lines) {
        return 4_500L + Math.max(0, lines - 2) * 900L;
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen instanceof PhoneScreen) {
            return;
        }
        long now = System.currentTimeMillis();
        int width = Math.min(WIDTH, graphics.guiWidth() - 8);
        List<FormattedCharSequence> lines = current == null ? List.of()
                : minecraft.font.split(current.text(), width - 12);
        if (current != null && now - shownAt > duration(lines.size()) + 2 * SLIDE_MS) {
            current = null;
        }
        if (current == null) {
            current = QUEUE.pollFirst();
            if (current == null) {
                return;
            }
            shownAt = now;
            lines = minecraft.font.split(current.text(), width - 12);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.7f));
        }
        if (minecraft.options.hideGui) {
            return;
        }
        if (lines.size() > MAX_LINES) {
            lines = lines.subList(0, MAX_LINES);
        }

        long age = now - shownAt;
        long total = duration(lines.size()) + 2 * SLIDE_MS;
        float slide;
        if (age < SLIDE_MS) {
            slide = 1f - age / (float) SLIDE_MS;
        } else if (age > total - SLIDE_MS) {
            slide = (age - (total - SLIDE_MS)) / (float) SLIDE_MS;
        } else {
            slide = 0f;
        }
        slide = slide * slide;
        int height = 22 + lines.size() * (minecraft.font.lineHeight + 1) + 4;
        int x = graphics.guiWidth() - width - 4 + Math.round(slide * (width + 8));
        int y = topOffset(minecraft);
        int accent = current.success() ? 0xFF2EC4B6 : 0xFFFF5A5F;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(x + 1, y, x + width - 1, y + height, 0xF01E2230);
        graphics.fill(x, y + 1, x + width, y + height - 1, 0xF01E2230);
        graphics.fill(x, y + 1, x + 2, y + height - 1, accent);
        if (icon == null) {
            icon = new ItemStack(ModItems.CARIPHONE);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x + 6, y + 4, 0);
        graphics.pose().scale(0.75f, 0.75f, 1f);
        graphics.renderItem(icon, 0, 0);
        graphics.pose().popPose();
        graphics.drawString(minecraft.font, Component.literal("CariPhone").withStyle(ChatFormatting.BOLD), x + 21, y + 6, 0xFFFFFFFF, false);
        Component ago = Component.translatable("caricraft.hud.notification_now");
        graphics.drawString(minecraft.font, ago, x + width - 6 - minecraft.font.width(ago), y + 6, 0xFF9AA0B4, false);
        int ty = y + 20;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(minecraft.font, line, x + 7, ty, 0xFFE8EAF2, false);
            ty += minecraft.font.lineHeight + 1;
        }
        // Barra de tiempo restante
        float left = 1f - Math.min(1f, age / (float) total);
        graphics.fill(x + 2, y + height - 2, x + 2 + Math.round((width - 4) * left), y + height - 1, accent);
        graphics.pose().popPose();
    }
}
