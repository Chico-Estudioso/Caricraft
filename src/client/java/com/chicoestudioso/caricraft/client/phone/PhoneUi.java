package com.chicoestudioso.caricraft.client.phone;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Primitivas de dibujo en modo inmediato. Cada frame se registran las zonas clicables; al hacer clic se
 * busca la última zona que contiene el ratón.
 */
public final class PhoneUi {
    public enum Style { PRIMARY, SECONDARY, DANGER, GOOD, DISABLED }

    private record Hit(int x0, int y0, int x1, int y1, Runnable action) {
    }

    private final List<Hit> hits = new ArrayList<>();
    public GuiGraphics g;
    public Font font;
    public PhoneTheme theme;
    public int mouseX;
    public int mouseY;
    /** Zona visible del contenido desplazable: los clics fuera no cuentan. */
    private int clipX0, clipY0, clipX1, clipY1;
    private boolean clipping;

    public void begin(GuiGraphics graphics, Font font, PhoneTheme theme, int mouseX, int mouseY) {
        this.g = graphics;
        this.font = font;
        this.theme = theme;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        hits.clear();
        clipping = false;
    }

    public void clip(int x0, int y0, int x1, int y1) {
        clipX0 = x0;
        clipY0 = y0;
        clipX1 = x1;
        clipY1 = y1;
        clipping = true;
        g.enableScissor(x0, y0, x1, y1);
    }

    public void unclip() {
        if (clipping) {
            g.disableScissor();
            clipping = false;
        }
    }

    public boolean hovered(int x0, int y0, int x1, int y1) {
        boolean inside = mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
        if (inside && clipping) {
            inside = mouseX >= clipX0 && mouseX < clipX1 && mouseY >= clipY0 && mouseY < clipY1;
        }
        return inside;
    }

    public void hit(int x0, int y0, int x1, int y1, Runnable action) {
        if (clipping) {
            x0 = Math.max(x0, clipX0);
            y0 = Math.max(y0, clipY0);
            x1 = Math.min(x1, clipX1);
            y1 = Math.min(y1, clipY1);
            if (x0 >= x1 || y0 >= y1) {
                return;
            }
        }
        hits.add(new Hit(x0, y0, x1, y1, action));
    }

    public boolean click(double x, double y) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (x >= hit.x0 && x < hit.x1 && y >= hit.y0 && y < hit.y1) {
                hit.action.run();
                return true;
            }
        }
        return false;
    }

    /** Rectángulo con esquinas redondeadas de radio 2. */
    public void round(int x0, int y0, int x1, int y1, int color) {
        if (x1 - x0 < 4 || y1 - y0 < 4) {
            g.fill(x0, y0, x1, y1, color);
            return;
        }
        g.fill(x0 + 2, y0, x1 - 2, y1, color);
        g.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, color);
        g.fill(x1 - 2, y0 + 1, x1 - 1, y1 - 1, color);
        g.fill(x0, y0 + 2, x0 + 1, y1 - 2, color);
        g.fill(x1 - 1, y0 + 2, x1, y1 - 2, color);
    }

    public void text(Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    public void text(String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    public void textRight(Component text, int right, int y, int color) {
        g.drawString(font, text, right - font.width(text), y, color, false);
    }

    public void textCentered(Component text, int centerX, int y, int color) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    /** Texto recortado con "…" si no cabe. */
    public void textClipped(Component text, int x, int y, int maxWidth, int color) {
        if (font.width(text) <= maxWidth) {
            text(text, x, y, color);
            return;
        }
        String plain = font.plainSubstrByWidth(text.getString(), Math.max(0, maxWidth - font.width("…")));
        text(plain + "…", x, y, color);
    }

    /** Texto centrado que se encoge (hasta un 65 %) si no cabe en {@code maxWidth}. */
    public void textFit(Component text, int centerX, int y, int maxWidth, int color) {
        int width = font.width(text);
        if (width <= maxWidth) {
            textCentered(text, centerX, y, color);
            return;
        }
        float scale = Math.max(0.65f, maxWidth / (float) width);
        g.pose().pushPose();
        g.pose().translate(centerX, y + (1 - scale) * font.lineHeight / 2f, 0);
        g.pose().scale(scale, scale, 1f);
        if (width * scale <= maxWidth) {
            g.drawString(font, text, -width / 2, 0, color, false);
        } else {
            int scaledMax = (int) (maxWidth / scale);
            String plain = font.plainSubstrByWidth(text.getString(), scaledMax - font.width("…")) + "…";
            g.drawString(font, plain, -font.width(plain) / 2, 0, color, false);
        }
        g.pose().popPose();
    }

    /** Dibuja texto ajustado y devuelve la altura usada. */
    public int wrapped(FormattedText text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(10, width));
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x, y + i * (font.lineHeight + 1), color, false);
        }
        return lines.size() * (font.lineHeight + 1);
    }

    public int wrappedHeight(FormattedText text, int width) {
        return font.split(text, Math.max(10, width)).size() * (font.lineHeight + 1);
    }

    /** Botón; devuelve su ancho. */
    public int button(int x, int y, int width, int height, Component label, Style style, Runnable action) {
        boolean hover = style != Style.DISABLED && hovered(x, y, x + width, y + height);
        int bg = switch (style) {
            case PRIMARY -> theme.accent();
            case SECONDARY -> theme.track();
            case DANGER -> theme.bad();
            case GOOD -> theme.good();
            case DISABLED -> PhoneTheme.mix(theme.track(), theme.background(), 0.5f);
        };
        int fg = switch (style) {
            case PRIMARY -> theme.accentText();
            case SECONDARY -> theme.text();
            case DISABLED -> theme.textDim();
            default -> 0xFFFFFFFF;
        };
        if (hover) {
            bg = PhoneTheme.mix(bg, 0xFFFFFFFF, 0.18f);
        }
        round(x, y, x + width, y + height, bg);
        int textY = y + (height - font.lineHeight) / 2 + 1;
        int maxText = width - 6;
        if (font.width(label) > maxText) {
            textClipped(label, x + 3, textY, maxText, fg);
        } else {
            textCentered(label, x + width / 2, textY, fg);
        }
        if (style != Style.DISABLED) {
            hit(x, y, x + width, y + height, action);
        }
        return width;
    }

    /** Ancho natural de un botón para una etiqueta. */
    public int buttonWidth(Component label) {
        return font.width(label) + 10;
    }

    public void progressBar(int x, int y, int width, long value, long max, int color) {
        round(x, y, x + width, y + 4, theme.track());
        int filled = max <= 0 ? 0 : (int) Math.round(width * Math.min(1.0, (double) value / max));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + 4, color);
        }
    }

    public void item(String id, int x, int y) {
        g.renderItem(stack(id), x, y);
    }

    public static ItemStack stack(String id) {
        ResourceLocation rl = id == null ? null : ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return new ItemStack(Items.PAPER);
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(rl));
    }

    /** Chapa pequeña con texto (p. ej. estado). Devuelve su ancho. */
    public int chip(Component label, int x, int y, int bg, int fg) {
        int width = font.width(label) + 6;
        round(x, y, x + width, y + font.lineHeight + 3, bg);
        text(label, x + 3, y + 2, fg);
        return width;
    }
}
