package com.chicoestudioso.caricraft.client.phone;

import com.chicoestudioso.caricraft.client.CaricraftClient;
import com.chicoestudioso.caricraft.client.ClientPhoneState;
import com.chicoestudioso.caricraft.client.cobblemon.PokemonModel;
import com.chicoestudioso.caricraft.client.phone.PhoneUi.Style;
import com.chicoestudioso.caricraft.network.PhoneAction;
import com.chicoestudioso.caricraft.network.PhoneActionPayload;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.CobbleverseView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.CvOfferView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.SellView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.WonderView;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CobbleCentro: la parte Cobbleverse en una pantalla grande, con los modelos 3D de los Pokémon (como el PC de
 * Cobblemon). Se abre desde el CariPhone. Igual que el teléfono, solo muestra el snapshot y pide acciones.
 */
public class CobbleCentroScreen extends Screen {
    /** Paleta oscura propia del CobbleCentro. */
    static final PhoneTheme THEME = new PhoneTheme(0xFF0E1420, 0xFF070A10, 0xFF141C2B, 0xFF1B998B, 0xFFFFFFFF,
            0xFFFFB703, 0xFF141C2B, 0xFFEDF2F7, 0xFF8A97AD, 0xFF1E293B, 0xFF26344A, 0xFF2EC27E, 0xFFE5484D,
            0xFFFFB703, 0xFF2B3A52);
    private static final int GOLD = 0xFFFFD166;
    private static final int SHINY = 0xFFFFE08A;
    private static final long REVEAL_SHAKE_MS = 1400;
    private static final long REVEAL_FLASH_MS = 350;
    /** Ancho de la barra lateral de pestañas. */
    private static final int SIDE_W = 106;

    enum Tab {
        HOME("home", "cobblemon:premier_ball"),
        SHOP("shop", "cobblemon:poke_ball"),
        WONDER("wonder", "cobblemon:link_cable"),
        SELL("sell", "cobblemon:relic_coin");

        final String key;
        final String icon;

        Tab(String key, String icon) {
            this.key = key;
            this.icon = icon;
        }
    }

    private static Tab lastTab = Tab.HOME;

    final PhoneUi ui = new PhoneUi();
    private final Screen parent;
    Tab tab = lastTab;
    private int scroll;
    private int maxScroll;
    private int ticks;
    private long lastActionAt;
    /** Pokémon elegido para el intercambio prodigioso. */
    String wonderPick;
    /** Pokémon tocado una vez para vender (hay que confirmar). */
    String sellArmed;
    long sellArmedAt;
    private long seenWonderTime = -1;
    private long revealStart;
    private boolean waitingWonder;
    private long waitingSince;

    private int x0, y0, x1, y1;

    public CobbleCentroScreen(Screen parent) {
        super(Component.translatable("caricraft.cc.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        request();
        PhoneSnapshot snap = ClientPhoneState.snapshot();
        if (snap != null && snap.cobbleverse() != null && snap.cobbleverse().lastWonder() != null) {
            seenWonderTime = snap.cobbleverse().lastWonder().time();
        }
    }

    // ---------------------------------------------------------------- red

    private void request() {
        if (ClientPlayNetworking.canSend(PhoneActionPayload.TYPE)) {
            ClientPlayNetworking.send(PhoneActionPayload.of(PhoneAction.REQUEST_SNAPSHOT));
        }
    }

    void send(PhoneAction action, String arg) {
        long now = System.currentTimeMillis();
        if (now - lastActionAt < 300) {
            return;
        }
        lastActionAt = now;
        if (ClientPlayNetworking.canSend(PhoneActionPayload.TYPE)) {
            ClientPlayNetworking.send(PhoneActionPayload.of(action, arg));
        }
    }

    @Override
    public void tick() {
        if (++ticks % 40 == 0) {
            request();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        lastTab = tab;
        PokemonModel.forget();
        minecraft.setScreen(parent);
    }

    // ---------------------------------------------------------------- entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && ui.click(mouseX, mouseY)) {
            if (minecraft != null) {
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.6f));
            }
            return true;
        }
        if (button == 3) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.round(scrollY * 16), 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (CaricraftClient.openPhoneKey().matches(keyCode, scanCode)) {
            lastTab = tab;
            minecraft.setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    void select(Tab target) {
        if (tab != target) {
            tab = target;
            scroll = 0;
        }
    }

    // ---------------------------------------------------------------- dibujo

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int w = Math.min(width - 16, 460);
        int h = Math.min(height - 16, 290);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        x1 = x0 + w;
        y1 = y0 + h;
        PhoneTheme t = THEME;
        ui.begin(graphics, font, t, mouseX, mouseY);

        // Marco
        ui.round(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0xFF05070C);
        ui.round(x0, y0, x1, y1, t.background());
        PhoneSnapshot snap = ClientPhoneState.snapshot();
        CobbleverseView cv = snap == null ? null : snap.cobbleverse();

        // Cabecera
        ui.round(x0, y0, x1, y0 + 24, t.header());
        boolean backHover = ui.hovered(x0, y0, x0 + 22, y0 + 24);
        if (backHover) {
            ui.g.fill(x0 + 2, y0 + 2, x0 + 22, y0 + 22, PhoneTheme.mix(t.header(), 0xFFFFFFFF, 0.2f));
        }
        ui.text("◀", x0 + 8, y0 + 8, 0xFFFFFFFF);
        ui.hit(x0, y0, x0 + 22, y0 + 24, this::onClose);
        ui.item("cobblemon:poke_ball", x0 + 26, y0 + 4);
        ui.text(Component.translatable("caricraft.cc.title").copy().withStyle(ChatFormatting.BOLD), x0 + 46, y0 + 8, 0xFFFFFFFF);
        if (cv != null && cv.cobbleDollars() >= 0) {
            String balance = "₽" + PhoneText.number(cv.cobbleDollars());
            int bw = font.width(balance) + 14;
            ui.round(x1 - bw - 6, y0 + 4, x1 - 6, y0 + 20, PhoneTheme.mix(t.header(), 0xFF000000, 0.35f));
            ui.text(balance, x1 - bw + 1, y0 + 8, GOLD);
        }

        // Barra lateral
        int sideW = SIDE_W;
        ui.g.fill(x0, y0 + 24, x0 + sideW, y1, PhoneTheme.mix(t.background(), 0xFF000000, 0.35f));
        int ty = y0 + 30;
        for (Tab each : Tab.values()) {
            boolean active = each == tab;
            boolean hover = ui.hovered(x0 + 4, ty, x0 + sideW - 4, ty + 22);
            if (active || hover) {
                ui.round(x0 + 4, ty, x0 + sideW - 4, ty + 22, active ? PhoneTheme.mix(t.header(), t.background(), 0.35f) : t.cardHover());
            }
            if (active) {
                ui.g.fill(x0 + 4, ty + 3, x0 + 6, ty + 19, t.accent());
            }
            ui.item(each.icon, x0 + 9, ty + 3);
            String badge = badge(cv, each);
            int badgeW = badge.isEmpty() ? 0 : Math.max(9, font.width(badge) + 4);
            ui.textClipped(Component.translatable("caricraft.cc.tab." + each.key), x0 + 28, ty + 7,
                    sideW - 34 - (badgeW == 0 ? 0 : badgeW + 3), active ? 0xFFFFFFFF : t.textDim());
            if (badgeW > 0) {
                ui.round(x0 + sideW - 8 - badgeW, ty + 6, x0 + sideW - 8, ty + 16, t.bad());
                ui.text(badge, x0 + sideW - 8 - badgeW + (badgeW - font.width(badge)) / 2, ty + 7, 0xFFFFFFFF);
            }
            Tab target = each;
            ui.hit(x0 + 4, ty, x0 + sideW - 4, ty + 22, () -> select(target));
            ty += 25;
        }

        // Contenido
        int cx = x0 + sideW + 8;
        int cy = y0 + 30;
        int cw = x1 - 8 - cx;
        if (cv == null) {
            ui.wrapped(Component.translatable(snap == null ? "caricraft.phone.loading" : "caricraft.cc.unavailable"), cx, cy, cw, t.textDim());
        } else {
            ui.clip(cx - 2, y0 + 26, x1 - 2, y1 - 2);
            int used = switch (tab) {
                case HOME -> home(cv, cx, cy - scroll, cw, partialTick);
                case SHOP -> shop(cv, cx, cy - scroll, cw, partialTick);
                case WONDER -> wonder(cv, cx, cy - scroll, cw, partialTick);
                case SELL -> sell(cv, cx, cy - scroll, cw, partialTick);
            };
            ui.unclip();
            maxScroll = Math.max(0, used + 8 - (y1 - cy));
            scroll = Mth.clamp(scroll, 0, maxScroll);
            if (maxScroll > 0) {
                int track = y1 - y0 - 34;
                int thumb = Math.max(14, track * track / (track + maxScroll));
                int sy = y0 + 28 + (int) ((track - thumb) * (scroll / (double) maxScroll));
                ui.g.fill(x1 - 4, sy, x1 - 2, sy + thumb, t.track());
            }
            revealOverlay(cv, cx, y0 + 26, cw, y1 - y0 - 28, partialTick);
        }
        banner(snap, t);
    }

    private String badge(CobbleverseView cv, Tab each) {
        if (cv == null) {
            return "";
        }
        return switch (each) {
            case HOME -> cv.giftEnabled() && cv.giftAvailable() ? "!" : "";
            case WONDER -> cv.wonderEnabled() && cv.wondersLeft() > 0 ? String.valueOf(cv.wondersLeft()) : "";
            default -> "";
        };
    }

    private void banner(PhoneSnapshot snap, PhoneTheme t) {
        ClientPhoneState.Notification latest = ClientPhoneState.notifications().peekFirst();
        if (latest == null || System.currentTimeMillis() - latest.createdAt() > 4000) {
            return;
        }
        if (latest.key().startsWith("caricraft.result.cv_wonder") && (waitingWonder || revealStart != 0)) {
            return; // la animación ya lo cuenta; el aviso destriparía la sorpresa
        }
        Component text = PhoneText.format(snap, latest.key(), latest.args());
        int bw = Math.min(x1 - x0 - 120, font.width(text) + 16);
        int bx = (x0 + SIDE_W + x1) / 2 - bw / 2;
        int bh = ui.wrappedHeight(text, bw - 12) + 8;
        int by = y1 - bh - 6;
        ui.g.pose().pushPose();
        ui.g.pose().translate(0, 0, 300);
        ui.round(bx, by, bx + bw, by + bh, latest.success() ? t.good() : t.bad());
        ui.wrapped(text, bx + 6, by + 4, bw - 12, 0xFFFFFFFF);
        ui.g.pose().popPose();
    }

    // ---------------------------------------------------------------- ayudas

    private static Set<String> aspects(List<String> aspects, boolean shiny) {
        Set<String> set = new HashSet<>(aspects == null ? List.of() : aspects);
        if (shiny) {
            set.add("shiny");
        }
        return set;
    }

    /**
     * Dibuja dentro de un recorte anidado. {@code GuiGraphics} apila los recortes y los cruza con el anterior, así que
     * no se sale del área de contenido; no toca el recorte de {@link PhoneUi} (que se usa para los clics).
     */
    private void stage(int x0, int y0, int x1, int y1, Runnable draw) {
        ui.g.enableScissor(x0, y0, x1, y1);
        try {
            draw.run();
        } finally {
            ui.g.disableScissor();
        }
    }

    /** Modelo 3D del Pokémon en un hueco cuadrado; sin Cobblemon, una Poké Ball. */
    private void model(String key, String species, Set<String> aspects, int cx, int top, int size, float yaw, float partialTick) {
        if (!PokemonModel.draw(ui.g, key, species, aspects, cx, top, size, yaw, partialTick)) {
            ui.g.pose().pushPose();
            ui.g.pose().translate(cx - size / 4f, top + size / 4f, 0);
            ui.g.pose().scale(size / 32f, size / 32f, 1f);
            ui.item("cobblemon:poke_ball", 0, 0);
            ui.g.pose().popPose();
        }
    }

    private static float spin() {
        return 35f + (System.currentTimeMillis() % 7200L) / 20f;
    }

    private static Component species(String id) {
        return PhoneText.idName(id.contains(":") ? id : "cobblemon:" + id);
    }

    private static Component move(String id) {
        String key = "cobblemon.move." + id;
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(id);
    }

    private static String money(long amount) {
        return "₽" + PhoneText.number(amount);
    }

    private int sectionTitle(Component title, Component right, int x, int y, int w) {
        ui.text(title.copy().withStyle(ChatFormatting.BOLD), x, y, THEME.text());
        if (right != null) {
            ui.textRight(right, x + w, y, THEME.textDim());
        }
        ui.g.fill(x, y + 11, x + w, y + 12, THEME.track());
        return 16;
    }

    // ---------------------------------------------------------------- inicio

    private int home(CobbleverseView cv, int x, int y, int w, float partialTick) {
        PhoneTheme t = THEME;
        int top = y;
        long left = cv.nextDayAt() - (ClientPhoneState.snapshot() == null ? 0 : ClientPhoneState.snapshot().serverTime());
        if (cv.giftEnabled()) {
            int h = 64;
            ui.round(x, y, x + w, y + h, PhoneTheme.mix(cv.giftAvailable() ? 0xFFB8860B : t.card(), t.background(), 0.25f));
            float bounce = cv.giftAvailable() ? (float) Math.abs(Math.sin(System.currentTimeMillis() / 300.0)) * 4f : 0f;
            ui.g.pose().pushPose();
            ui.g.pose().translate(x + 10, y + 12 - bounce, 0);
            ui.g.pose().scale(2.5f, 2.5f, 1f);
            ui.item("cobblemon:premier_ball", 0, 0);
            ui.g.pose().popPose();
            ui.text(Component.translatable("caricraft.phone.cv.gift").copy().withStyle(ChatFormatting.BOLD), x + 56, y + 8, 0xFFFFFFFF);
            Component text = cv.giftAvailable() ? Component.translatable("caricraft.phone.cv.gift_ready")
                    : Component.translatable("caricraft.phone.cv.gift_wait", PhoneText.remaining(left));
            ui.wrapped(text, x + 56, y + 20, w - 62 - (cv.giftAvailable() ? 86 : 0), 0xFFE8E2D0);
            if (cv.giftAvailable()) {
                Component open = Component.translatable("caricraft.phone.cv.gift_open");
                int bw = Math.max(80, ui.buttonWidth(open));
                ui.button(x + w - bw - 8, y + h - 24, bw, 16, open, Style.GOOD, () -> send(PhoneAction.CV_GIFT, ""));
            }
            y += h + 10;
        }

        y += sectionTitle(Component.translatable("caricraft.cc.team"), null, x, y, w);
        List<SellView> party = cv.party();
        if (party.isEmpty()) {
            y += ui.wrapped(Component.translatable("caricraft.cc.team_empty"), x, y, w, t.textDim()) + 6;
        } else {
            int cell = Math.min(64, w / Math.max(3, party.size()));
            int rowW = cell * party.size();
            int sx = x + (w - rowW) / 2;
            for (int i = 0; i < party.size(); i++) {
                SellView mon = party.get(i);
                int cx = sx + i * cell;
                boolean hover = ui.hovered(cx + 2, y, cx + cell - 2, y + cell + 22);
                ui.round(cx + 2, y, cx + cell - 2, y + cell + 22, hover ? t.cardHover() : t.card());
                if (mon.shiny()) {
                    ui.g.fill(cx + 2, y, cx + cell - 2, y + 2, SHINY);
                }
                int rowY = y;
                stage(cx + 2, rowY, cx + cell - 2, rowY + cell, () -> model("home:" + mon.uuid(), mon.species(),
                        aspects(mon.aspects(), mon.shiny()), cx + cell / 2, rowY + 2, cell - 8, hover ? spin() : 35f, partialTick));
                Component name = mon.nickname() == null || mon.nickname().isEmpty() ? species(mon.species()) : Component.literal(mon.nickname());
                ui.textFit(name, cx + cell / 2, y + cell + 2, cell - 6, t.text());
                ui.textFit(Component.translatable("caricraft.phone.cv.level", mon.level()), cx + cell / 2, y + cell + 12, cell - 6,
                        mon.shiny() ? SHINY : t.textDim());
            }
            y += cell + 28;
        }

        // Accesos
        y += sectionTitle(Component.translatable("caricraft.cc.today"), null, x, y, w);
        int half = (w - 8) / 2;
        tile(x, y, half, "cobblemon:link_cable", Component.translatable("caricraft.cc.tab.wonder"),
                Component.translatable("caricraft.cc.wonder_left", cv.wondersLeft(), cv.wondersPerDay()), () -> select(Tab.WONDER));
        tile(x + half + 8, y, half, "cobblemon:relic_coin", Component.translatable("caricraft.cc.tab.sell"),
                Component.translatable("caricraft.phone.cv.sales_today", cv.salesToday(), cv.maxSales()), () -> select(Tab.SELL));
        y += 40;
        tile(x, y, half, "cobblemon:poke_ball", Component.translatable("caricraft.cc.tab.shop"),
                Component.translatable("caricraft.phone.cv.changes_in", PhoneText.remaining(left)), () -> select(Tab.SHOP));
        y += 40;
        return y - top;
    }

    private void tile(int x, int y, int w, String icon, Component title, Component sub, Runnable action) {
        PhoneTheme t = THEME;
        boolean hover = ui.hovered(x, y, x + w, y + 34);
        ui.round(x, y, x + w, y + 34, hover ? t.cardHover() : t.card());
        ui.item(icon, x + 6, y + 9);
        ui.textClipped(title.copy().withStyle(ChatFormatting.BOLD), x + 28, y + 7, w - 34, t.text());
        ui.textClipped(sub, x + 28, y + 19, w - 34, t.textDim());
        ui.hit(x, y, x + w, y + 34, action);
    }

    // ---------------------------------------------------------------- tienda

    private int shop(CobbleverseView cv, int x, int y, int w, float partialTick) {
        PhoneTheme t = THEME;
        int top = y;
        long left = cv.nextDayAt() - ClientPhoneState.snapshot().serverTime();
        y += sectionTitle(Component.translatable("caricraft.phone.cv.shop"),
                Component.translatable("caricraft.phone.cv.changes_in", PhoneText.remaining(left)), x, y, w);
        if (!cv.shopEnabled() || cv.offers().isEmpty()) {
            return y - top + ui.wrapped(Component.translatable("caricraft.cc.shop_closed"), x, y, w, t.textDim());
        }
        int columns = w >= 360 ? 2 : 1;
        int cardW = (w - (columns - 1) * 8) / columns;
        int rowH = 0;
        for (int i = 0; i < cv.offers().size(); i++) {
            int col = i % columns;
            if (col == 0 && i > 0) {
                y += rowH + 8;
                rowH = 0;
            }
            rowH = Math.max(rowH, offerCard(cv, cv.offers().get(i), x + col * (cardW + 8), y, cardW, partialTick));
        }
        y += rowH + 8;
        return y - top;
    }

    private int offerCard(CobbleverseView cv, CvOfferView offer, int x, int y, int w, float partialTick) {
        PhoneTheme t = THEME;
        boolean pokemon = !offer.species().isEmpty();
        // Con una sola columna, la tarjeta va en horizontal: escaparate a la izquierda y datos a la derecha.
        boolean wide = w >= 240;
        int stage = wide ? 70 : 58;
        int h = wide ? stage + 8 : 128;
        boolean hover = ui.hovered(x, y, x + w, y + h);
        ui.round(x, y, x + w, y + h, hover ? t.cardHover() : t.card());
        int sx0 = x + 4;
        int sy0 = y + 4;
        int sx1 = wide ? x + 4 + stage : x + w - 4;
        int sy1 = y + 4 + stage;
        ui.round(sx0, sy0, sx1, sy1, PhoneTheme.mix(offer.shiny() ? 0xFFB8860B : 0xFF1B998B, t.card(), 0.7f));
        int mid = (sx0 + sx1) / 2;
        if (pokemon) {
            int index = offer.species().size() == 1 ? 0 : (int) ((System.currentTimeMillis() / 1500) % offer.species().size());
            String shown = offer.species().get(index);
            stage(sx0, sy0, sx1, sy1, () -> model("shop:" + offer.id() + ":" + shown, shown,
                    aspects(List.of(), offer.shiny()), mid, sy0, stage - 4, hover ? spin() : 35f, partialTick));
            if (offer.species().size() > 1) {
                ui.text("?", sx1 - 10, sy0 + 4, 0xFFFFFFFF);
            }
        } else {
            ui.g.pose().pushPose();
            ui.g.pose().translate(mid - 16, sy0 + stage / 2f - 16, 0);
            ui.g.pose().scale(2f, 2f, 1f);
            ui.item(offer.icon(), 0, 0);
            ui.g.pose().popPose();
        }
        if (offer.shiny()) {
            ui.chip(Component.translatable("caricraft.phone.cv.shiny"), sx0 + 3, sy0 + 3, PhoneTheme.mix(0xFFB8860B, 0xFF000000, 0.2f), SHINY);
        }
        int tx = wide ? sx1 + 8 : x + 6;
        int tw = x + w - 6 - tx;
        int cy = wide ? y + 7 : y + stage + 8;
        Component name;
        if (!pokemon) {
            name = Component.translatable("caricraft.phone.market.offer", offer.count(), PhoneText.idName(offer.item()));
        } else if (offer.species().size() == 1) {
            name = species(offer.species().getFirst());
        } else {
            name = Component.translatable("caricraft.phone.cv.mystery_pokemon");
        }
        ui.textClipped(name.copy().withStyle(ChatFormatting.BOLD), tx, cy, tw, t.text());
        cy += 11;
        MutableComponent detail = Component.empty();
        if (pokemon) {
            detail.append(Component.translatable("caricraft.phone.cv.level", offer.level()));
            if (offer.perfectIvs() > 0) {
                detail.append(" · ").append(Component.translatable("caricraft.phone.cv.ivs", offer.perfectIvs()));
            }
            if (!offer.moves().isEmpty()) {
                detail.append(" · ");
                for (int i = 0; i < offer.moves().size(); i++) {
                    if (i > 0) {
                        detail.append(", ");
                    }
                    detail.append(move(offer.moves().get(i)));
                }
            }
        } else if (offer.dailyLimit() > 1) {
            detail.append(Component.translatable("caricraft.phone.cv.left_today", offer.dailyLimit() - offer.boughtToday(), offer.dailyLimit()));
        }
        if (wide) {
            var lines = font.split(detail, Math.max(10, tw));
            int room = Math.max(1, (y + h - 18 - cy) / (font.lineHeight + 1));
            for (int i = 0; i < Math.min(room, lines.size()); i++) {
                ui.g.drawString(font, lines.get(i), tx, cy + i * (font.lineHeight + 1), t.textDim(), false);
            }
        } else {
            ui.textClipped(detail, tx, cy, tw, t.textDim());
        }
        ui.text(money(offer.price()), tx, y + h - 16, GOLD);
        Component label;
        Style style;
        if (offer.boughtToday() >= offer.dailyLimit()) {
            label = Component.translatable("caricraft.phone.cv.sold_out");
            style = Style.DISABLED;
        } else if (cv.cobbleDollars() < offer.price()) {
            label = Component.translatable("caricraft.phone.cv.missing", money(Math.max(0, offer.price() - cv.cobbleDollars())));
            style = Style.DISABLED;
        } else {
            label = Component.translatable("caricraft.phone.shop.buy");
            style = Style.PRIMARY;
        }
        int bw = ui.buttonWidth(label);
        ui.button(x + w - bw - 6, y + h - 20, bw, 15, label, style, () -> send(PhoneAction.CV_BUY, offer.id()));
        return h;
    }

    // ---------------------------------------------------------------- equipo en rejilla (vender e intercambiar)

    private interface CardAction {
        void draw(SellView mon, int x, int y, int w, int h);
    }

    private int partyGrid(CobbleverseView cv, int x, int y, int w, float partialTick, String prefix, java.util.function.Predicate<SellView> selected,
                          CardAction extra, int extraHeight, java.util.function.Consumer<SellView> onClick) {
        PhoneTheme t = THEME;
        List<SellView> party = cv.party();
        if (party.isEmpty()) {
            return ui.wrapped(Component.translatable("caricraft.cc.team_empty"), x, y, w, t.textDim()) + 6;
        }
        int columns = w >= 330 ? 3 : 2;
        int cardW = (w - (columns - 1) * 8) / columns;
        int stage = 54;
        int cardH = stage + 30 + extraHeight;
        int top = y;
        for (int i = 0; i < party.size(); i++) {
            SellView mon = party.get(i);
            int cx = x + (i % columns) * (cardW + 8);
            int cy = top + (i / columns) * (cardH + 8);
            boolean hover = ui.hovered(cx, cy, cx + cardW, cy + cardH);
            boolean picked = selected.test(mon);
            ui.round(cx - (picked ? 2 : 0), cy - (picked ? 2 : 0), cx + cardW + (picked ? 2 : 0), cy + cardH + (picked ? 2 : 0),
                    picked ? t.accent() : hover ? t.cardHover() : t.card());
            if (picked) {
                ui.round(cx, cy, cx + cardW, cy + cardH, t.cardHover());
            }
            ui.round(cx + 4, cy + 4, cx + cardW - 4, cy + 4 + stage,
                    PhoneTheme.mix(mon.shiny() ? 0xFFB8860B : 0xFF2B3A52, t.card(), 0.6f));
            stage(cx + 4, cy + 4, cx + cardW - 4, cy + 4 + stage, () -> model(prefix + mon.uuid(), mon.species(),
                    aspects(mon.aspects(), mon.shiny()), cx + cardW / 2, cy + 4, stage - 4, hover || picked ? spin() : 35f, partialTick));
            Component name = mon.nickname() == null || mon.nickname().isEmpty() ? species(mon.species()) : Component.literal(mon.nickname());
            if (mon.shiny()) {
                name = Component.literal("★ ").append(name);
            }
            ui.textClipped(name.copy().withStyle(ChatFormatting.BOLD), cx + 6, cy + stage + 8, cardW - 12, mon.shiny() ? SHINY : t.text());
            ui.textClipped(Component.translatable("caricraft.phone.cv.mon_detail", mon.level(), mon.ivTotal()), cx + 6, cy + stage + 19,
                    cardW - 12, t.textDim());
            extra.draw(mon, cx, cy + stage + 30, cardW, extraHeight);
            if (onClick != null) {
                ui.hit(cx, cy, cx + cardW, cy + stage + 28, () -> onClick.accept(mon));
            }
        }
        int rows = (party.size() + columns - 1) / columns;
        return rows * (cardH + 8);
    }

    // ---------------------------------------------------------------- vender

    private int sell(CobbleverseView cv, int x, int y, int w, float partialTick) {
        PhoneTheme t = THEME;
        int top = y;
        y += sectionTitle(Component.translatable("caricraft.phone.cv.sell"),
                Component.translatable("caricraft.phone.cv.sales_today", cv.salesToday(), cv.maxSales()), x, y, w);
        if (!cv.sellEnabled()) {
            return y - top + ui.wrapped(Component.translatable("caricraft.cc.sell_closed"), x, y, w, t.textDim());
        }
        boolean limit = cv.salesToday() >= cv.maxSales();
        y += partyGrid(cv, x, y, w, partialTick, "sell:", mon -> false, (mon, cx, cy, cw, ch) -> {
            boolean armed = mon.uuid().equals(sellArmed) && System.currentTimeMillis() - sellArmedAt < 3000;
            Component label;
            Style style;
            Runnable action;
            if (mon.blocked() != null) {
                label = Component.translatable("caricraft.phone.cv.blocked." + mon.blocked());
                style = Style.DISABLED;
                action = () -> { };
            } else if (limit || !cv.cobbleDollarsInstalled()) {
                label = Component.literal(money(mon.price()));
                style = Style.DISABLED;
                action = () -> { };
            } else if (armed) {
                label = Component.translatable("caricraft.cc.sell_confirm", money(mon.price()));
                style = Style.DANGER;
                action = () -> {
                    sellArmed = null;
                    send(PhoneAction.CV_SELL, mon.uuid());
                };
            } else {
                label = Component.translatable("caricraft.cc.sell_for", money(mon.price()));
                style = Style.SECONDARY;
                action = () -> {
                    sellArmed = mon.uuid();
                    sellArmedAt = System.currentTimeMillis();
                };
            }
            ui.button(cx + 4, cy, cw - 8, 15, label, style, action);
        }, 20, null);
        y += ui.wrapped(Component.translatable("caricraft.phone.cv.sell_help"), x, y, w, t.textDim());
        return y - top;
    }

    // ---------------------------------------------------------------- intercambio prodigioso

    private int wonder(CobbleverseView cv, int x, int y, int w, float partialTick) {
        PhoneTheme t = THEME;
        int top = y;
        y += sectionTitle(Component.translatable("caricraft.cc.tab.wonder"),
                Component.translatable("caricraft.cc.wonder_left", cv.wondersLeft(), cv.wondersPerDay()), x, y, w);
        if (!cv.wonderEnabled()) {
            return y - top + ui.wrapped(Component.translatable("caricraft.cc.wonder_closed"), x, y, w, t.textDim());
        }
        y += ui.wrapped(Component.translatable("caricraft.cc.wonder_help", cv.wonderPool()), x, y, w, t.textDim()) + 8;
        SellView picked = cv.party().stream().filter(m -> m.uuid().equals(wonderPick)).findFirst().orElse(null);
        if (picked != null && picked.blocked() != null) {
            picked = null;
        }
        // Botón de intercambiar
        Component label;
        Style style;
        if (cv.wondersLeft() <= 0) {
            label = Component.translatable("caricraft.cc.wonder_tomorrow");
            style = Style.DISABLED;
        } else if (picked == null) {
            label = Component.translatable("caricraft.cc.wonder_pick");
            style = Style.DISABLED;
        } else {
            Component name = picked.nickname() == null || picked.nickname().isEmpty() ? species(picked.species())
                    : Component.literal(picked.nickname());
            label = Component.translatable("caricraft.cc.wonder_go", name);
            style = Style.PRIMARY;
        }
        ui.button(x, y, w, 18, label, style, this::startWonder);
        y += 26;
        y += partyGrid(cv, x, y, w, partialTick, "wonder:", mon -> mon.uuid().equals(wonderPick), (mon, cx, cy, cw, ch) -> {
            if (mon.blocked() != null) {
                ui.textFit(Component.translatable("caricraft.phone.cv.blocked." + mon.blocked()), cx + cw / 2, cy + 2, cw - 8, t.bad());
            }
        }, 12, mon -> {
            if (mon.blocked() == null && cv.wondersLeft() > 0) {
                wonderPick = mon.uuid();
            }
        });
        return y - top;
    }

    /** Envía el Pokémon elegido al intercambio prodigioso y arranca la animación de espera. */
    void startWonder() {
        if (wonderPick == null || waitingWonder) {
            return;
        }
        waitingWonder = true;
        waitingSince = System.currentTimeMillis();
        revealStart = 0;
        send(PhoneAction.CV_WONDER, wonderPick);
    }

    void dismissReveal() {
        revealStart = 0;
    }

    /** Animación al recibir el Pokémon del intercambio: Poké Ball que se agita, destello y el Pokémon girando. */
    private void revealOverlay(CobbleverseView cv, int x, int y, int w, int h, float partialTick) {
        WonderView last = cv.lastWonder();
        long now = System.currentTimeMillis();
        if (last != null && last.time() > seenWonderTime) {
            seenWonderTime = last.time();
            if (waitingWonder) {
                revealStart = now;
                waitingWonder = false;
                wonderPick = null;
            }
        }
        if (waitingWonder && now - waitingSince > 6000) {
            waitingWonder = false;
        }
        if (!waitingWonder && revealStart == 0) {
            return;
        }
        PhoneTheme t = THEME;
        ui.g.pose().pushPose();
        ui.g.pose().translate(0, 0, 200);
        ui.g.fill(x - 2, y, x + w + 4, y + h, 0xE0070A10);
        int cx = x + w / 2;
        int cy = y + h / 2;
        long age = waitingWonder ? 0 : now - revealStart;
        if (waitingWonder || age < REVEAL_SHAKE_MS) {
            float shake = (float) Math.sin(now / 60.0) * 6f;
            ui.g.pose().pushPose();
            ui.g.pose().translate(cx, cy - 10, 0);
            ui.g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(shake * 3));
            ui.g.pose().scale(3f, 3f, 1f);
            ui.item("cobblemon:poke_ball", -8, -8);
            ui.g.pose().popPose();
            ui.textCentered(Component.translatable("caricraft.cc.wonder_trading"), cx, cy + 22, t.text());
        } else {
            long after = age - REVEAL_SHAKE_MS;
            if (after < REVEAL_FLASH_MS) {
                int alpha = (int) (255 * (1 - after / (float) REVEAL_FLASH_MS));
                ui.g.fill(x - 2, y, x + w + 4, y + h, (alpha << 24) | 0xFFFFFF);
            }
            int size = Math.min(120, h - 70);
            if (last.shiny()) {
                for (int i = 0; i < 8; i++) {
                    double a = now / 600.0 + i * Math.PI / 4;
                    int sx = cx + (int) (Math.cos(a) * (size / 2 + 12));
                    int sy = y + 14 + size / 2 + (int) (Math.sin(a) * (size / 2 + 6));
                    ui.text("✦", sx, sy, SHINY);
                }
            }
            stage(x, y + 2, x + w, y + h, () -> model("reveal:" + last.time(), last.species(),
                    aspects(last.aspects(), last.shiny()), cx, y + 10, size, spin(), partialTick));
            int ty = y + 16 + size;
            Component title = Component.translatable(last.shiny() ? "caricraft.cc.wonder_got_shiny" : "caricraft.cc.wonder_got",
                    species(last.species()), last.level()).copy().withStyle(ChatFormatting.BOLD);
            ui.textCentered(title, cx, ty, last.shiny() ? SHINY : 0xFFFFFFFF);
            Component from = last.from() == null || last.from().isEmpty()
                    ? Component.translatable("caricraft.phone.cv.wonder_far")
                    : Component.literal(last.from());
            ui.textCentered(Component.translatable("caricraft.cc.wonder_from", from), cx, ty + 12, t.textDim());
            Component ok = Component.translatable("caricraft.cc.wonder_ok");
            int bw = Math.max(70, ui.buttonWidth(ok));
            ui.button(cx - bw / 2, ty + 26, bw, 16, ok, Style.GOOD, this::dismissReveal);
        }
        ui.g.pose().popPose();
    }
}
