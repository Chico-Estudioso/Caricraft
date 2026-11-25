package com.chicoestudioso.caricraft.client.phone;

import com.chicoestudioso.caricraft.client.CaricraftClient;
import com.chicoestudioso.caricraft.client.ClientPhoneState;
import com.chicoestudioso.caricraft.network.PhoneAction;
import com.chicoestudioso.caricraft.network.PhoneActionPayload;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Pantalla del CariPhone. Solo presenta el último snapshot del servidor y envía acciones tipadas.
 */
public class PhoneScreen extends Screen {
    private static final int REFRESH_TICKS = 40;
    private static final long ACTION_COOLDOWN_MS = 250;
    private static final long BANNER_MS = 4000;

    /** Página que se recuerda entre aperturas. */
    private static PhonePage lastPage = PhonePage.HOME;

    final PhoneUi ui = new PhoneUi();
    PhonePage page;
    String lockedAppId;
    int missionTab;
    /** Foto abierta en CariLens, o {@code null} para ver la cuadrícula. */
    String openPhotoId;
    /** Momento de la primera pulsación de "Borrar" (confirmación). */
    long deleteArmedAt;
    private int scroll;
    private int maxScroll;
    private int ticks;
    private long lastActionAt;

    private EditBox input;
    private PhonePage inputPage;

    // Geometría calculada en cada render
    int px, py, pw, ph;
    int sx0, sy0, sx1, sy1;
    int contentTop;

    public PhoneScreen() {
        super(Component.translatable("screen.caricraft.phone"));
        this.page = lastPage;
    }

    @Override
    protected void init() {
        computeLayout();
        rebuildInput();
        requestSnapshot();
    }

    private void computeLayout() {
        ph = Math.min(height - 12, 300);
        pw = Mth.clamp(Math.round(ph * 0.68f), 188, 236);
        pw = Math.min(pw, width - 8);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        sx0 = px + 5;
        sy0 = py + 12;
        sx1 = px + pw - 5;
        sy1 = py + ph - 15;
    }

    // ---------------------------------------------------------------- navegación y red

    void navigate(PhonePage target) {
        if (page != target) {
            page = target;
            lastPage = target == PhonePage.LOCKED ? PhonePage.HOME : target;
            scroll = 0;
            openPhotoId = null;
            rebuildInput();
        }
    }

    /** Abre directamente una página (p. ej. al volver de hacer una foto). */
    public void openPage(PhonePage target) {
        this.page = target;
        lastPage = target;
    }

    void openPhoto(String photoId) {
        openPhotoId = photoId;
        deleteArmedAt = 0;
        scroll = 0;
    }

    void openApp(String appId, String state) {
        PhonePage target = PhonePage.forApp(appId);
        if ("COMING_SOON".equals(state)) {
            lockedAppId = appId;
            navigate(PhonePage.LOCKED);
        } else if (target == null || "LOCKED".equals(state) || "AVAILABLE".equals(state)) {
            lockedAppId = appId;
            navigate(PhonePage.LOCKED);
        } else {
            navigate(target);
        }
    }

    void send(PhoneAction action, String arg) {
        long now = System.currentTimeMillis();
        if (action != PhoneAction.REQUEST_SNAPSHOT && now - lastActionAt < ACTION_COOLDOWN_MS) {
            return;
        }
        lastActionAt = now;
        if (ClientPlayNetworking.canSend(PhoneActionPayload.TYPE)) {
            ClientPlayNetworking.send(PhoneActionPayload.of(action, arg));
        }
    }

    private void requestSnapshot() {
        if (ClientPlayNetworking.canSend(PhoneActionPayload.TYPE)) {
            ClientPlayNetworking.send(PhoneActionPayload.of(PhoneAction.REQUEST_SNAPSHOT));
        }
    }

    // ---------------------------------------------------------------- campo de texto

    private void rebuildInput() {
        if (input != null) {
            removeWidget(input);
            input = null;
        }
        inputPage = null;
        boolean lensCaption = page == PhonePage.CARILENS && com.chicoestudioso.caricraft.client.ClientPhotos.hasPending();
        if (page == PhonePage.DUO || page == PhonePage.CHRONICLES || lensCaption) {
            int maxLength = page == PhonePage.DUO ? 16 : lensCaption ? 80 : 120;
            input = new EditBox(font, 0, 0, 100, 14, Component.empty());
            input.setMaxLength(maxLength);
            input.setHint(Component.translatable(page == PhonePage.DUO ? "caricraft.phone.duo.hint"
                    : lensCaption ? "caricraft.phone.lens.caption_hint" : "caricraft.phone.chronicles.hint"));
            input.visible = false;
            addRenderableWidget(input);
            inputPage = page;
        }
    }

    /** Coloca el campo de texto (lo llama la página que lo usa). Devuelve su altura. */
    int placeInput(int x, int y, int width) {
        if (input == null || inputPage != page) {
            return 0;
        }
        input.setX(x);
        input.setY(y);
        input.setWidth(width);
        input.visible = true;
        return 14;
    }

    String takeInput() {
        if (input == null) {
            return "";
        }
        String value = input.getValue().strip();
        input.setValue("");
        return value;
    }

    // ---------------------------------------------------------------- ciclo de vida

    @Override
    public void tick() {
        super.tick();
        if (++ticks % REFRESH_TICKS == 0) {
            requestSnapshot();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        String value = input == null ? "" : input.getValue();
        super.resize(minecraft, width, height);
        if (input != null) {
            input.setValue(value);
        }
    }

    // ---------------------------------------------------------------- entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (input != null && input.isFocused() && !input.isMouseOver(mouseX, mouseY)) {
            input.setFocused(false);
            setFocused(null);
        }
        if (button == 0 && ui.click(mouseX, mouseY)) {
            playClick();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.round(scrollY * 14), 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean typing = input != null && input.isFocused();
        if (typing && (keyCode == 257 || keyCode == 335)) { // Intro
            submitInput();
            return true;
        }
        if (!typing && CaricraftClient.openPhoneKey().matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Guarda la foto pendiente con el pie escrito (puede ir vacío). */
    void savePhoto() {
        String caption = takeInput();
        com.chicoestudioso.caricraft.client.ClientPhotos.uploadPending(caption);
        rebuildInput();
    }

    void discardPhoto() {
        com.chicoestudioso.caricraft.client.ClientPhotos.clearPending();
        rebuildInput();
    }

    void takePhoto() {
        com.chicoestudioso.caricraft.client.ClientPhotos.beginCapture();
    }

    void submitInput() {
        if (page == PhonePage.CARILENS) {
            savePhoto();
            return;
        }
        String value = takeInput();
        if (value.isEmpty()) {
            return;
        }
        if (page == PhonePage.DUO) {
            send(PhoneAction.INVITE_PLAYER, value);
        } else if (page == PhonePage.CHRONICLES) {
            send(PhoneAction.ADD_NOTE, value);
        }
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.6f));
        }
    }

    // ---------------------------------------------------------------- dibujo

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        computeLayout();
        PhoneSnapshot snapshot = ClientPhoneState.snapshot();
        PhoneTheme theme = PhoneTheme.byId(snapshot == null ? null : snapshot.theme());
        renderBackground(graphics, mouseX, mouseY, partialTick);
        ui.begin(graphics, font, theme, mouseX, mouseY);
        if (input != null) {
            input.visible = false;
        }

        drawFrame(theme);
        drawStatusBar(snapshot, theme);
        contentTop = sy0 + 11;
        if (page != PhonePage.HOME) {
            drawHeader(theme);
            contentTop += 17;
        }

        int contentX = sx0 + 5;
        int contentWidth = sx1 - sx0 - 10;
        if (snapshot == null) {
            Component message = Component.translatable(ClientPhoneState.protocolMismatch()
                    ? "caricraft.phone.protocol_mismatch" : "caricraft.phone.loading");
            int y = (contentTop + sy1) / 2 - 10;
            ui.wrapped(message, contentX, y, contentWidth, theme.textDim());
        } else {
            // Zona fija superior (campo de texto) que no se desplaza.
            int fixedHeight = PhonePages.renderFixed(this, snapshot, contentX, contentTop + 4, contentWidth);
            int scrollTop = contentTop + (fixedHeight > 0 ? fixedHeight + 8 : 0);
            ui.clip(sx0, scrollTop, sx1, sy1);
            int height = PhonePages.render(this, snapshot, contentX, scrollTop + 4 - scroll, contentWidth);
            ui.unclip();
            maxScroll = Math.max(0, height + 8 - (sy1 - scrollTop));
            scroll = Mth.clamp(scroll, 0, maxScroll);
            drawScrollbar(theme, scrollTop);
        }
        drawBanner(snapshot, theme);

        // El único widget es el campo de texto. No se llama a super.render(): en 1.21 vuelve a pintar
        // el fondo desenfocado encima de todo lo dibujado.
        if (input != null && input.visible) {
            input.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawFrame(PhoneTheme theme) {
        ui.round(px - 1, py - 1, px + pw + 1, py + ph + 1, theme.bezelEdge());
        ui.round(px, py, px + pw, py + ph, theme.bezel());
        // Altavoz y cámara
        ui.round(px + pw / 2 - 14, py + 4, px + pw / 2 + 14, py + 7, PhoneTheme.mix(theme.bezel(), 0xFF000000, 0.5f));
        ui.g.fill(px + pw / 2 + 20, py + 4, px + pw / 2 + 23, py + 7, 0xFF3A86FF);
        ui.g.fill(sx0, sy0, sx1, sy1, theme.background());
        // Botón de inicio
        int cx = px + pw / 2;
        int by = py + ph - 12;
        boolean hover = ui.hovered(cx - 12, by, cx + 12, by + 9);
        ui.round(cx - 12, by, cx + 12, by + 9, hover ? PhoneTheme.mix(theme.bezel(), 0xFFFFFFFF, 0.35f)
                : PhoneTheme.mix(theme.bezel(), 0xFFFFFFFF, 0.15f));
        ui.hit(cx - 12, by, cx + 12, by + 9, () -> navigate(PhonePage.HOME));
    }

    private void drawStatusBar(PhoneSnapshot snapshot, PhoneTheme theme) {
        int y = sy0;
        ui.g.fill(sx0, y, sx1, y + 11, PhoneTheme.mix(theme.header(), 0xFF000000, 0.25f));
        ui.text(PhoneText.clock(System.currentTimeMillis()), sx0 + 4, y + 2, 0xFFFFFFFF);
        if (snapshot != null) {
            String balance = PhoneText.number(snapshot.balance());
            int textWidth = font.width(balance);
            int right = sx1 - 4;
            ui.text(balance, right - textWidth, y + 2, 0xFFFFE08A);
            drawCoin(right - textWidth - 9, y + 2);
            if (snapshot.party().duo()) {
                long online = snapshot.party().members().stream().filter(PhoneSnapshot.MemberView::online).count();
                Component duo = Component.translatable("caricraft.phone.status.duo", online, snapshot.party().members().size());
                ui.textCentered(duo, (sx0 + sx1) / 2, y + 2, 0xFFFFFFFF);
            }
        }
    }

    void drawCoin(int x, int y) {
        ui.round(x, y, x + 7, y + 7, 0xFFB8860B);
        ui.g.fill(x + 1, y + 1, x + 6, y + 6, 0xFFFFD34E);
        ui.g.fill(x + 3, y + 2, x + 4, y + 5, 0xFFB8860B);
    }

    private void drawHeader(PhoneTheme theme) {
        int y = sy0 + 11;
        ui.g.fill(sx0, y, sx1, y + 17, theme.header());
        boolean hover = ui.hovered(sx0, y, sx0 + 18, y + 17);
        if (hover) {
            ui.g.fill(sx0, y, sx0 + 18, y + 17, PhoneTheme.mix(theme.header(), 0xFFFFFFFF, 0.2f));
        }
        ui.text("◀", sx0 + 5, y + 5, theme.headerText());
        ui.hit(sx0, y, sx0 + 18, y + 17, () -> navigate(page.parent()));
        Component title = page == PhonePage.LOCKED && lockedAppId != null ? PhoneText.appName(lockedAppId) : page.title();
        ui.textCentered(title, (sx0 + sx1) / 2, y + 5, theme.headerText());
    }

    private void drawScrollbar(PhoneTheme theme, int top) {
        if (maxScroll <= 0) {
            return;
        }
        int trackHeight = sy1 - top - 4;
        int thumb = Math.max(12, trackHeight * trackHeight / (trackHeight + maxScroll));
        int y = top + 2 + (int) ((trackHeight - thumb) * (scroll / (double) maxScroll));
        ui.g.fill(sx1 - 3, y, sx1 - 1, y + thumb, PhoneTheme.mix(theme.textDim(), theme.background(), 0.4f));
    }

    private void drawBanner(PhoneSnapshot snapshot, PhoneTheme theme) {
        ClientPhoneState.Notification latest = ClientPhoneState.notifications().peekFirst();
        if (latest == null || System.currentTimeMillis() - latest.createdAt() > BANNER_MS) {
            return;
        }
        Component text = PhoneText.format(snapshot, latest.key(), latest.args());
        int x0 = sx0 + 4;
        int x1 = sx1 - 4;
        int textHeight = ui.wrappedHeight(text, x1 - x0 - 8);
        int y0 = sy1 - textHeight - 10;
        int bg = latest.success() ? theme.good() : theme.bad();
        ui.round(x0, y0, x1, y0 + textHeight + 6, PhoneTheme.mix(bg, 0xFF000000, 0.1f));
        ui.wrapped(text, x0 + 4, y0 + 4, x1 - x0 - 8, 0xFFFFFFFF);
    }
}
