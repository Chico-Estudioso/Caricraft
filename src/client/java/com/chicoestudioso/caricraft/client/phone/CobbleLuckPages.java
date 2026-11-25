package com.chicoestudioso.caricraft.client.phone;

import com.chicoestudioso.caricraft.client.ClientPhoneState;
import com.chicoestudioso.caricraft.client.phone.PhoneUi.Style;
import com.chicoestudioso.caricraft.network.PhoneAction;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.CobbleverseView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.CrateView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.FusionView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.IngredientView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.LuckResultView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.LuckView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.RewardView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.SellView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.SlotsView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.SymbolView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.UpgradeView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Pestañas de suerte del CobbleCentro: cajas (con ruleta), tragaperras, mejoras de Pokémon y fusiones de objetos.
 * El servidor decide el resultado; aquí solo se anima lo que llega en el snapshot ({@link LuckResultView}).
 */
final class CobbleLuckPages {
    private static final int[] RARITY_COLORS = {0xFF5C6B80, 0xFF3A86FF, 0xFF9B5DE5, 0xFFFFB703};
    private static final int[] CRATE_COLORS = {0xFF2B6CB0, 0xFF6B46C1, 0xFFB7791F, 0xFF2F855A};
    private static final long REEL_MS = 3600;
    private static final long SLOT_FIRST_STOP_MS = 700;
    private static final long SLOT_STOP_GAP_MS = 450;
    private static final long SHINY_SUSPENSE_MS = 1300;
    private static final int TILE = 34;
    /** Casilla de la tira de la ruleta donde se coloca el premio. */
    private static final int WIN_TILE = 34;

    private final CobbleCentroScreen s;
    private final Random random = new Random();

    /** Pokémon elegido en Mejoras. */
    String upgradePick;
    /** Apuesta elegida en la tragaperras. */
    int betIndex;
    /** Lo que se ha pedido y aún no ha llegado: crate, spin, upgrade o fusion. */
    private String waiting;
    private long waitingSince;
    private long seenTime = -1;
    /** Animación en curso, o {@code null}. */
    private Anim anim;

    private record Anim(LuckResultView result, long start, CrateView crate, List<Integer> strip) {
    }

    CobbleLuckPages(CobbleCentroScreen screen) {
        this.s = screen;
    }

    void init(CobbleverseView cv) {
        if (cv != null && cv.luck() != null && cv.luck().last() != null) {
            seenTime = cv.luck().last().time();
        }
    }

    private PhoneUi ui() {
        return s.ui;
    }

    // ---------------------------------------------------------------- estado

    /** Pide una acción de suerte y espera su resultado para animarlo. */
    void start(String kind, PhoneAction action, String arg1, String arg2) {
        if (waiting != null || anim != null) {
            return;
        }
        if (s.send(action, arg1, arg2)) {
            waiting = kind;
            waitingSince = System.currentTimeMillis();
        }
    }

    /** Llega un resultado nuevo: empieza su animación. Un fallo (sin saldo, límite…) cancela la espera. */
    void update(CobbleverseView cv) {
        long now = System.currentTimeMillis();
        LuckView luck = cv.luck();
        LuckResultView last = luck == null ? null : luck.last();
        if (last != null && last.time() > seenTime) {
            seenTime = last.time();
            if (waiting != null && waiting.equals(last.kind())) {
                CrateView crate = "crate".equals(last.kind()) ? luck.crates().stream()
                        .filter(c -> c.id().equals(last.id())).findFirst().orElse(null) : null;
                anim = new Anim(last, now, crate, crate == null ? List.of() : strip(crate, last.reward()));
                waiting = null;
            }
        }
        if (waiting != null) {
            ClientPhoneState.Notification latest = ClientPhoneState.notifications().peekFirst();
            boolean refused = latest != null && !latest.success() && latest.createdAt() >= waitingSince
                    && !latest.key().endsWith("_fail");
            if (refused || now - waitingSince > 6000) {
                waiting = null;
            }
        }
    }

    /** Durante la animación no se enseña el aviso de texto (contaría el resultado antes de tiempo). */
    boolean hides(String key) {
        if (waiting == null && anim == null) {
            return false;
        }
        return key.startsWith("caricraft.result.cv_crate_") || key.startsWith("caricraft.result.cv_spin_")
                || key.startsWith("caricraft.result.cv_up_") || key.startsWith("caricraft.result.cv_fuse_");
    }

    /** Cierra la animación en curso (botón «¡Genial!»). */
    void dismiss() {
        anim = null;
    }

    private long age() {
        return anim == null ? 0 : System.currentTimeMillis() - anim.start();
    }

    private boolean animating(String kind) {
        return anim != null && anim.result().kind().equals(kind);
    }

    /** Tira de la ruleta: premios al azar según su probabilidad y el ganador en {@link #WIN_TILE}. */
    private List<Integer> strip(CrateView crate, int winner) {
        List<Integer> tiles = new ArrayList<>();
        for (int i = 0; i < WIN_TILE + 6; i++) {
            if (i == WIN_TILE) {
                tiles.add(Math.max(0, Math.min(crate.rewards().size() - 1, winner)));
                continue;
            }
            double roll = random.nextDouble();
            int pick = crate.rewards().size() - 1;
            for (int r = 0; r < crate.rewards().size(); r++) {
                roll -= crate.rewards().get(r).chance();
                if (roll < 0) {
                    pick = r;
                    break;
                }
            }
            tiles.add(pick);
        }
        return tiles;
    }

    // ---------------------------------------------------------------- textos

    static int rarityColor(String rarity) {
        return switch (rarity == null ? "" : rarity) {
            case "rare" -> RARITY_COLORS[1];
            case "epic" -> RARITY_COLORS[2];
            case "legendary" -> RARITY_COLORS[3];
            default -> RARITY_COLORS[0];
        };
    }

    private static Component named(String prefix, String id) {
        String key = prefix + id;
        if (I18n.exists(key)) {
            return Component.translatable(key);
        }
        String plain = id.replace('_', ' ');
        return Component.literal(plain.isEmpty() ? plain : plain.substring(0, 1).toUpperCase(Locale.ROOT) + plain.substring(1));
    }

    private static String range(int min, int max) {
        return min == max ? PhoneText.number(min) : PhoneText.number(min) + "–" + PhoneText.number(max);
    }

    /** «5–10 × Poké Ball», «Pokémon al azar (Nv. 15)», «₽3.000–7.000»… */
    static Component rewardName(RewardView reward) {
        return switch (reward.kind()) {
            case "POKEMON" -> {
                Component base = reward.species().size() == 1 ? CobbleCentroScreen.species(reward.species().getFirst())
                        : Component.translatable("caricraft.cc.reward.random_pokemon");
                yield Component.translatable(reward.shiny() ? "caricraft.cc.reward.pokemon_shiny" : "caricraft.cc.reward.pokemon",
                        base, reward.level());
            }
            case "COBBLEDOLLARS" -> Component.literal("₽" + range(reward.min(), reward.max()));
            case "CARINS" -> Component.translatable("caricraft.cc.reward.carins", range(reward.min(), reward.max()));
            default -> Component.translatable("caricraft.phone.market.offer", range(reward.min(), reward.max()), PhoneText.idName(reward.item()));
        };
    }

    /** «30 %», «2,5 %», «0,5 %». */
    static String percent(double chance) {
        double tenths = Math.round(chance * 1000) / 10.0;
        String text = tenths == Math.floor(tenths) ? String.valueOf((long) tenths)
                : String.format(Locale.ROOT, "%.1f", tenths).replace('.', ',');
        return text + " %";
    }

    private static String multiplier(double value) {
        return "×" + (value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value).replace('.', ','));
    }

    // ---------------------------------------------------------------- cajas

    int crates(CobbleverseView cv, int x, int y, int w) {
        PhoneTheme t = CobbleCentroScreen.THEME;
        int top = y;
        LuckView luck = cv.luck();
        y += s.sectionTitle(Component.translatable("caricraft.cc.tab.crates"), Component.translatable("caricraft.cc.paid_in_dollars"), x, y, w);
        if (luck == null || luck.crates().isEmpty()) {
            return y - top + ui().wrapped(Component.translatable("caricraft.cc.closed"), x, y, w, t.textDim());
        }
        for (int i = 0; i < luck.crates().size(); i++) {
            y += crateCard(cv, luck.crates().get(i), CRATE_COLORS[i % CRATE_COLORS.length], x, y, w) + 8;
        }
        y += ui().wrapped(Component.translatable("caricraft.cc.crates_help"), x, y, w, t.textDim());
        return y - top;
    }

    private int crateCard(CobbleverseView cv, CrateView crate, int color, int x, int y, int w) {
        PhoneUi ui = ui();
        PhoneTheme t = CobbleCentroScreen.THEME;
        int perRow = Math.max(1, (w - 64) / 24);
        int rows = (crate.rewards().size() + perRow - 1) / perRow;
        int h = Math.max(70, 40 + rows * 30);
        boolean hover = ui.hovered(x, y, x + w, y + h);
        ui.round(x, y, x + w, y + h, hover ? t.cardHover() : t.card());
        ui.round(x + 4, y + 4, x + 56, y + h - 4, PhoneTheme.mix(color, t.card(), 0.45f));
        float bob = hover ? (float) Math.sin(System.currentTimeMillis() / 180.0) * 2f : 0f;
        ui.g.pose().pushPose();
        ui.g.pose().translate(x + 14, y + h / 2f - 14 + bob, 0);
        ui.g.pose().scale(2f, 2f, 1f);
        ui.item(crate.icon(), 0, 0);
        ui.g.pose().popPose();
        int tx = x + 62;
        ui.textClipped(named("caricraft.cc.crate.", crate.id()).copy().withStyle(ChatFormatting.BOLD), tx, y + 6, w - 70 - 70, t.text());
        ui.text(CobbleCentroScreen.money(crate.price()), tx, y + 17, CobbleCentroScreen.GOLD);
        ui.textRight(Component.translatable("caricraft.cc.opened_today", crate.opened(), crate.dailyLimit()), x + w - 6, y + 6, t.textDim());
        // Contenido con la probabilidad de cada premio
        for (int i = 0; i < crate.rewards().size(); i++) {
            RewardView reward = crate.rewards().get(i);
            int rx = tx + (i % perRow) * 24;
            int ry = y + 30 + (i / perRow) * 30;
            ui.round(rx, ry, rx + 21, ry + 20, PhoneTheme.mix(rarityColor(reward.rarity()), t.card(), 0.55f));
            ui.g.fill(rx + 1, ry + 18, rx + 20, ry + 20, rarityColor(reward.rarity()));
            ui.item(reward.icon(), rx + 2, ry + 1);
            ui.g.pose().pushPose();
            ui.g.pose().translate(rx + 10.5f, ry + 22, 0);
            ui.g.pose().scale(0.6f, 0.6f, 1f);
            ui.textCentered(Component.literal(percent(reward.chance())), 0, 0, t.textDim());
            ui.g.pose().popPose();
            if (ui.hovered(rx, ry, rx + 21, ry + 20)) {
                tooltip(rewardName(reward), rarityColor(reward.rarity()));
            }
        }
        long balance = cv.cobbleDollars();
        Component label;
        Style style;
        if (crate.opened() >= crate.dailyLimit()) {
            label = Component.translatable("caricraft.cc.crate_tomorrow");
            style = Style.DISABLED;
        } else if (balance < crate.price()) {
            label = Component.translatable("caricraft.phone.cv.missing", CobbleCentroScreen.money(crate.price() - Math.max(0, balance)));
            style = Style.DISABLED;
        } else {
            label = Component.translatable("caricraft.cc.crate_open");
            style = Style.PRIMARY;
        }
        int bw = ui.buttonWidth(label);
        ui.button(x + w - bw - 6, y + 16, bw, 15, label, style, () -> start("crate", PhoneAction.CV_CRATE, crate.id(), ""));
        return h;
    }

    private Component tooltipText;
    private int tooltipColor;

    private void tooltip(Component text, int color) {
        tooltipText = text;
        tooltipColor = color;
    }

    // ---------------------------------------------------------------- tragaperras

    int slots(CobbleverseView cv, int x, int y, int w) {
        PhoneUi ui = ui();
        PhoneTheme t = CobbleCentroScreen.THEME;
        int top = y;
        SlotsView slots = cv.luck() == null ? null : cv.luck().slots();
        if (slots == null) {
            return ui.wrapped(Component.translatable("caricraft.cc.closed"), x, y, w, t.textDim());
        }
        y += s.sectionTitle(Component.translatable("caricraft.cc.slots_title"),
                Component.translatable("caricraft.cc.spins_left", slots.spinsLeft(), slots.spinsPerDay()), x, y, w);
        betIndex = Mth.clamp(betIndex, 0, slots.bets().size() - 1);

        // Máquina
        int mw = Math.min(w, 220);
        int mx = x + (w - mw) / 2;
        int mh = 74;
        long now = System.currentTimeMillis();
        LuckResultView result = animating("spin") ? anim.result() : null;
        boolean spinning = "spin".equals(waiting);
        long age = age();
        boolean settled = result != null && age > SLOT_FIRST_STOP_MS + 2 * SLOT_STOP_GAP_MS;
        boolean win = settled && result.payout() > 0;
        boolean jackpot = settled && result.reels().size() == 3 && result.reels().get(0).equals(result.reels().get(1))
                && result.reels().get(1).equals(result.reels().get(2)) && result.reels().get(0) == slots.symbols().size() - 1;
        int frame = jackpot && (now / 150) % 2 == 0 ? CobbleCentroScreen.GOLD : 0xFFE5484D;
        ui.round(mx - 3, y - 3, mx + mw + 3, y + mh + 3, frame);
        ui.round(mx, y, mx + mw, y + mh, 0xFF2A0F14);
        int reelW = (mw - 24) / 3;
        for (int r = 0; r < 3; r++) {
            int rx = mx + 6 + r * (reelW + 6);
            int ry = y + 8;
            int rh = mh - 16;
            ui.round(rx, ry, rx + reelW, ry + rh, 0xFFF7F3E8);
            int symbol;
            boolean stopped = result != null && age > SLOT_FIRST_STOP_MS + r * SLOT_STOP_GAP_MS;
            float offset = 0;
            if (stopped) {
                symbol = result.reels().get(r);
            } else if (spinning || result != null) {
                long tick = now / 60 + r * 3L;
                symbol = (int) (tick % slots.symbols().size());
                offset = (now % 60) / 60f * 14f;
            } else {
                List<Integer> previous = lastReels(cv);
                symbol = previous == null ? r : previous.get(r);
            }
            SymbolView view = slots.symbols().get(symbol);
            ui.g.enableScissor(rx, ry, rx + reelW, ry + rh);
            ui.g.pose().pushPose();
            ui.g.pose().translate(rx + reelW / 2f - 16, ry + rh / 2f - 16 + offset, 0);
            ui.g.pose().scale(2f, 2f, 1f);
            ui.item(view.icon(), 0, 0);
            ui.g.pose().popPose();
            ui.g.disableScissor();
        }
        ui.g.fill(mx + 3, y + mh / 2, mx + 6, y + mh / 2 + 1, CobbleCentroScreen.GOLD);
        ui.g.fill(mx + mw - 6, y + mh / 2, mx + mw - 3, y + mh / 2 + 1, CobbleCentroScreen.GOLD);
        y += mh + 10;

        // Resultado
        if (settled) {
            Component text;
            int color;
            if (jackpot) {
                text = Component.translatable("caricraft.cc.slots_jackpot", CobbleCentroScreen.money(result.payout()));
                color = CobbleCentroScreen.GOLD;
            } else if (win) {
                text = Component.translatable("caricraft.cc.slots_win", CobbleCentroScreen.money(result.payout()));
                color = result.success() ? t.good() : t.textDim();
            } else {
                text = Component.translatable("caricraft.cc.slots_lose");
                color = t.textDim();
            }
            ui.textCentered(text.copy().withStyle(ChatFormatting.BOLD), x + w / 2, y, color);
            if (age > SLOT_FIRST_STOP_MS + 2 * SLOT_STOP_GAP_MS + 2500) {
                anim = null;
            }
        } else if (spinning || result != null) {
            ui.textCentered(Component.translatable("caricraft.cc.slots_spinning"), x + w / 2, y, t.textDim());
        } else {
            ui.textCentered(Component.translatable("caricraft.cc.slots_pick"), x + w / 2, y, t.textDim());
        }
        y += 14;

        // Apuestas y botón
        int gap = 6;
        int bw = (Math.min(w, 220) - gap * (slots.bets().size() - 1)) / slots.bets().size();
        int bx = x + (w - Math.min(w, 220)) / 2;
        for (int i = 0; i < slots.bets().size(); i++) {
            int index = i;
            boolean selected = i == betIndex;
            ui.button(bx + i * (bw + gap), y, bw, 15, Component.literal(CobbleCentroScreen.money(slots.bets().get(i))),
                    selected ? Style.GOOD : Style.SECONDARY, () -> betIndex = index);
        }
        y += 20;
        long bet = slots.bets().get(betIndex);
        Component label;
        Style style;
        if (slots.spinsLeft() <= 0) {
            label = Component.translatable("caricraft.cc.spins_tomorrow");
            style = Style.DISABLED;
        } else if (cv.cobbleDollars() < bet) {
            label = Component.translatable("caricraft.phone.cv.missing", CobbleCentroScreen.money(bet - Math.max(0, cv.cobbleDollars())));
            style = Style.DISABLED;
        } else if (spinning || (result != null && !settled)) {
            label = Component.translatable("caricraft.cc.slots_spinning");
            style = Style.DISABLED;
        } else {
            label = Component.translatable("caricraft.cc.slots_spin", CobbleCentroScreen.money(bet));
            style = Style.PRIMARY;
        }
        ui.button(bx, y, Math.min(w, 220), 18, label, style, () -> {
            anim = null;
            start("spin", PhoneAction.CV_SPIN, String.valueOf(betIndex), "");
        });
        y += 26;

        // Tabla de premios
        y += s.sectionTitle(Component.translatable("caricraft.cc.paytable"), null, x, y, w);
        int col = (w - 8) / 2;
        for (int i = slots.symbols().size() - 1; i >= 0; i--) {
            SymbolView symbol = slots.symbols().get(i);
            int row = slots.symbols().size() - 1 - i;
            int px = x + (row % 2) * (col + 8);
            int py = y + (row / 2) * 20;
            for (int k = 0; k < 3; k++) {
                ui.item(symbol.icon(), px + k * 14, py);
            }
            ui.text(multiplier(symbol.pay3()), px + 46, py + 4, CobbleCentroScreen.GOLD);
            ui.text(Component.translatable("caricraft.cc.pair", multiplier(symbol.pay2())), px + 46 + 32, py + 4, t.textDim());
        }
        y += ((slots.symbols().size() + 1) / 2) * 20 + 4;
        y += ui.wrapped(Component.translatable("caricraft.cc.slots_help", Math.round(slots.returnToPlayer() * 100)), x, y, w, t.textDim());
        return y - top;
    }

    private List<Integer> lastReels(CobbleverseView cv) {
        LuckResultView last = cv.luck().last();
        return last != null && "spin".equals(last.kind()) && last.reels().size() == 3 ? last.reels() : null;
    }


    // ---------------------------------------------------------------- mejoras y fusiones

    int upgrades(CobbleverseView cv, int x, int y, int w, float partialTick) {
        PhoneUi ui = ui();
        PhoneTheme t = CobbleCentroScreen.THEME;
        int top = y;
        LuckView luck = cv.luck();
        if (luck == null) {
            return ui.wrapped(Component.translatable("caricraft.cc.closed"), x, y, w, t.textDim());
        }
        if (!luck.upgrades().isEmpty()) {
            y += s.sectionTitle(Component.translatable("caricraft.cc.upgrades_title"), null, x, y, w);
            List<SellView> party = cv.party();
            if (party.isEmpty()) {
                y += ui.wrapped(Component.translatable("caricraft.cc.team_empty"), x, y, w, t.textDim()) + 6;
            } else {
                if (party.stream().noneMatch(m -> m.uuid().equals(upgradePick))) {
                    upgradePick = party.getFirst().uuid();
                }
                int cell = Math.min(58, w / Math.max(3, party.size()));
                int sx = x + (w - cell * party.size()) / 2;
                for (int i = 0; i < party.size(); i++) {
                    SellView mon = party.get(i);
                    int cx = sx + i * cell;
                    boolean picked = mon.uuid().equals(upgradePick);
                    boolean hover = ui.hovered(cx + 2, y, cx + cell - 2, y + cell + 12);
                    ui.round(cx + 2, y, cx + cell - 2, y + cell + 12, picked ? t.accent() : hover ? t.cardHover() : t.card());
                    if (picked) {
                        ui.round(cx + 3, y + 1, cx + cell - 3, y + cell + 11, t.cardHover());
                    }
                    int rowY = y;
                    s.stage(cx + 3, rowY + 1, cx + cell - 3, rowY + cell, () -> s.model("up:" + mon.uuid(), mon.species(),
                            CobbleCentroScreen.aspects(mon.aspects(), mon.shiny()), cx + cell / 2, rowY + 2, cell - 8,
                            picked || hover ? CobbleCentroScreen.spin() : 35f, partialTick));
                    ui.textFit(Component.translatable("caricraft.phone.cv.level", mon.level()), cx + cell / 2, y + cell + 1, cell - 6,
                            mon.shiny() ? CobbleCentroScreen.SHINY : t.textDim());
                    ui.hit(cx + 2, y, cx + cell - 2, y + cell + 12, () -> upgradePick = mon.uuid());
                }
                y += cell + 18;
                SellView mon = party.stream().filter(m -> m.uuid().equals(upgradePick)).findFirst().orElse(party.getFirst());
                Component name = mon.nickname() == null || mon.nickname().isEmpty() ? CobbleCentroScreen.species(mon.species())
                        : Component.literal(mon.nickname());
                ui.text(Component.translatable("caricraft.cc.upgrading", name, mon.level(), mon.ivTotal()), x, y, t.text());
                y += 13;
                for (UpgradeView upgrade : luck.upgrades()) {
                    y += upgradeRow(cv, mon, upgrade, x, y, w) + 5;
                }
                y += ui.wrapped(Component.translatable("caricraft.cc.upgrades_help"), x, y, w, t.textDim());
            }
            y += 6;
        }
        if (!luck.fusions().isEmpty()) {
            y += s.sectionTitle(Component.translatable("caricraft.cc.fusions_title"), null, x, y, w);
            Map<String, Integer> inventory = inventory();
            for (FusionView fusion : luck.fusions()) {
                y += fusionRow(cv, fusion, inventory, x, y, w) + 5;
            }
            y += ui.wrapped(Component.translatable("caricraft.cc.fusions_help"), x, y, w, t.textDim());
        }
        return y - top;
    }

    private static String upgradeIcon(String kind) {
        return switch (kind) {
            case "iv" -> "cobblemon:hp_up";
            case "level" -> "cobblemon:rare_candy";
            default -> "cobblemon:cherish_ball";
        };
    }

    private int upgradeRow(CobbleverseView cv, SellView mon, UpgradeView upgrade, int x, int y, int w) {
        PhoneUi ui = ui();
        PhoneTheme t = CobbleCentroScreen.THEME;
        int h = 30;
        ui.round(x, y, x + w, y + h, t.card());
        ui.item(upgradeIcon(upgrade.kind()), x + 6, y + 7);
        MutableComponent title = Component.translatable("caricraft.cc.upgrade." + upgrade.kind()).copy().withStyle(ChatFormatting.BOLD);
        Component desc = Component.translatable("caricraft.cc.upgrade." + upgrade.kind() + ".desc", upgrade.amount(),
                percent(upgrade.chance()));
        String blocked = switch (upgrade.kind()) {
            case "iv" -> mon.ivTotal() >= 186 ? "maxed" : null;
            case "level" -> mon.level() >= 100 ? "maxed" : null;
            default -> mon.shiny() ? "maxed" : null;
        };
        Component label;
        Style style;
        if (blocked != null) {
            label = Component.translatable("caricraft.cc.upgrade_maxed");
            style = Style.DISABLED;
        } else if (cv.cobbleDollars() < upgrade.price()) {
            label = Component.translatable("caricraft.phone.cv.missing", CobbleCentroScreen.money(upgrade.price() - Math.max(0, cv.cobbleDollars())));
            style = Style.DISABLED;
        } else {
            label = Component.translatable("caricraft.cc.upgrade_go", CobbleCentroScreen.money(upgrade.price()));
            style = "shiny".equals(upgrade.kind()) ? Style.DANGER : Style.PRIMARY;
        }
        int bw = ui.buttonWidth(label);
        ui.textClipped(title, x + 26, y + 5, w - 36 - bw, t.text());
        ui.textClipped(desc, x + 26, y + 16, w - 36 - bw, t.textDim());
        ui.button(x + w - bw - 6, y + 7, bw, 15, label, style,
                () -> start("upgrade", PhoneAction.CV_UPGRADE, upgrade.id(), mon.uuid()));
        return h;
    }

    /** Cuántos objetos de cada tipo lleva el jugador en el inventario principal (lo mismo que cuenta el servidor). */
    private static Map<String, Integer> inventory() {
        Map<String, Integer> counts = new HashMap<>();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            for (ItemStack stack : player.getInventory().items) {
                if (!stack.isEmpty()) {
                    counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
                }
            }
        }
        return counts;
    }

    private int fusionRow(CobbleverseView cv, FusionView fusion, Map<String, Integer> inventory, int x, int y, int w) {
        PhoneUi ui = ui();
        PhoneTheme t = CobbleCentroScreen.THEME;
        int h = 30;
        ui.round(x, y, x + w, y + h, t.card());
        int ix = x + 6;
        String missing = null;
        int missingCount = 0;
        for (IngredientView ingredient : fusion.inputs()) {
            ui.item(ingredient.item(), ix, y + 7);
            int have = inventory.getOrDefault(ingredient.item(), 0);
            ui.g.pose().pushPose();
            ui.g.pose().translate(0, 0, 200);
            ui.text("×" + ingredient.count(), ix + 16, y + 12, have >= ingredient.count() ? t.text() : t.bad());
            ui.g.pose().popPose();
            ix += 16 + ui.font.width("×" + ingredient.count()) + 4;
            if (have < ingredient.count() && missing == null) {
                missing = ingredient.item();
                missingCount = ingredient.count() - have;
            }
        }
        ui.text("→", ix, y + 11, t.textDim());
        ix += ui.font.width("→") + 4;
        ui.item(fusion.output(), ix, y + 7);
        ui.g.pose().pushPose();
        ui.g.pose().translate(0, 0, 200);
        ui.text("×" + fusion.count(), ix + 16, y + 12, CobbleCentroScreen.GOLD);
        ui.g.pose().popPose();
        ix += 16 + ui.font.width("×" + fusion.count()) + 6;
        if (fusion.chance() < 1) {
            ui.chip(Component.literal(percent(fusion.chance())), ix, y + 9, PhoneTheme.mix(t.warn(), 0xFF000000, 0.45f), t.warn());
        }
        if (ui.hovered(x, y, x + w, y + h)) {
            tooltip(Component.translatable("caricraft.phone.market.offer", fusion.count(), PhoneText.idName(fusion.output())),
                    CobbleCentroScreen.GOLD);
        }
        Component label;
        Style style;
        if (missing != null) {
            label = Component.translatable("caricraft.cc.fuse_missing", missingCount);
            style = Style.DISABLED;
        } else if (cv.cobbleDollars() < fusion.price()) {
            label = Component.translatable("caricraft.phone.cv.missing", CobbleCentroScreen.money(fusion.price() - Math.max(0, cv.cobbleDollars())));
            style = Style.DISABLED;
        } else {
            label = fusion.price() > 0 ? Component.translatable("caricraft.cc.fuse_go", CobbleCentroScreen.money(fusion.price()))
                    : Component.translatable("caricraft.cc.fuse_free");
            style = fusion.chance() < 1 ? Style.DANGER : Style.PRIMARY;
        }
        int bw = ui.buttonWidth(label);
        ui.button(x + w - bw - 6, y + 7, bw, 15, label, style, () -> start("fusion", PhoneAction.CV_FUSE, fusion.id(), ""));
        return h;
    }

    // ---------------------------------------------------------------- capas de animación

    void overlay(CobbleverseView cv, int x, int y, int w, int h, float partialTick) {
        PhoneUi ui = ui();
        if (tooltipText != null) {
            int tw = ui.font.width(tooltipText) + 10;
            int tx = Mth.clamp(ui.mouseX + 8, x, x + w - tw);
            int ty = Mth.clamp(ui.mouseY - 16, y, y + h - 14);
            ui.g.pose().pushPose();
            ui.g.pose().translate(0, 0, 400);
            ui.round(tx, ty, tx + tw, ty + 13, 0xF0070A10);
            ui.g.fill(tx, ty + 12, tx + tw, ty + 13, tooltipColor);
            ui.text(tooltipText, tx + 5, ty + 3, 0xFFFFFFFF);
            ui.g.pose().popPose();
            tooltipText = null;
        }
        if ("crate".equals(waiting)) {
            backdrop(x, y, w, h);
            ui.textCentered(Component.translatable("caricraft.cc.crate_opening"), x + w / 2, y + h / 2, CobbleCentroScreen.THEME.text());
            ui.g.pose().popPose();
            return;
        }
        if (anim == null) {
            return;
        }
        switch (anim.result().kind()) {
            case "crate" -> crateReel(cv, x, y, w, h, partialTick);
            case "upgrade", "fusion" -> resultCard(cv, x, y, w, h, partialTick);
            default -> {
            }
        }
    }

    private void backdrop(int x, int y, int w, int h) {
        ui().g.pose().pushPose();
        ui().g.pose().translate(0, 0, 250);
        ui().g.fill(x - 2, y, x + w + 4, y + h, 0xF2070A10);
        ui().hit(x - 2, y, x + w + 4, y + h, () -> { }); // lo de debajo no se puede pulsar
    }

    /** Ruleta horizontal que frena hasta dejar el premio bajo la marca, y después el premio en grande. */
    private void crateReel(CobbleverseView cv, int x, int y, int w, int h, float partialTick) {
        PhoneUi ui = ui();
        PhoneTheme t = CobbleCentroScreen.THEME;
        CrateView crate = anim.crate();
        if (crate == null) {
            anim = null;
            return;
        }
        backdrop(x, y, w, h);
        long age = age();
        int cx = x + w / 2;
        int stripY = y + 24;
        ui.textCentered(named("caricraft.cc.crate.", crate.id()).copy().withStyle(ChatFormatting.BOLD), cx, y + 8, t.text());
        double progress = Math.min(1.0, age / (double) REEL_MS);
        double eased = 1 - Math.pow(1 - progress, 4);
        double target = WIN_TILE * TILE + TILE / 2.0;
        double offset = eased * target;
        ui.g.enableScissor(x + 4, stripY, x + w - 4, stripY + TILE + 8);
        for (int i = 0; i < anim.strip().size(); i++) {
            int tx = (int) Math.round(cx + i * TILE - offset);
            if (tx + TILE < x || tx > x + w) {
                continue;
            }
            RewardView reward = crate.rewards().get(anim.strip().get(i));
            int color = rarityColor(reward.rarity());
            boolean winner = progress >= 1 && i == WIN_TILE;
            ui.round(tx + 1, stripY + 2, tx + TILE - 1, stripY + TILE + 6, PhoneTheme.mix(color, 0xFF0E1420, winner ? 0.2f : 0.55f));
            ui.g.fill(tx + 2, stripY + TILE + 3, tx + TILE - 2, stripY + TILE + 6, color);
            ui.item(reward.icon(), tx + TILE / 2 - 8, stripY + TILE / 2 - 5);
        }
        ui.g.disableScissor();
        ui.g.fill(cx - 1, stripY - 2, cx + 1, stripY + TILE + 10, CobbleCentroScreen.GOLD);
        if (progress < 1) {
            ui.g.pose().popPose();
            return;
        }
        // Premio
        LuckResultView result = anim.result();
        RewardView reward = crate.rewards().get(Math.max(0, Math.min(crate.rewards().size() - 1, result.reward())));
        int color = rarityColor(reward.rarity());
        long after = age - REEL_MS;
        if (after < 300) {
            int alpha = (int) (200 * (1 - after / 300f));
            ui.g.fill(x - 2, y, x + w + 4, y + h, (alpha << 24) | (color & 0xFFFFFF));
        }
        int py = stripY + TILE + 16;
        int size = Math.min(70, h - (py - y) - 50);
        if ("POKEMON".equals(reward.kind()) && result.species() != null) {
            int top = py;
            if (result.shiny()) {
                sparkles(cx, top + size / 2, size / 2 + 8);
            }
            s.stage(x, py, x + w, py + size, () -> s.model("crate:" + result.time(), result.species(),
                    CobbleCentroScreen.aspects(result.aspects(), result.shiny()), cx, top, size, CobbleCentroScreen.spin(), partialTick));
        } else {
            ui.g.pose().pushPose();
            ui.g.pose().translate(cx - 24, py + size / 2f - 24, 0);
            ui.g.pose().scale(3f, 3f, 1f);
            ui.item(reward.icon(), 0, 0);
            ui.g.pose().popPose();
        }
        int ty = py + size + 4;
        ui.textCentered(Component.translatable("caricraft.cc.rarity." + reward.rarity()).copy().withStyle(ChatFormatting.BOLD), cx, ty, color);
        Component got = switch (reward.kind()) {
            case "POKEMON" -> Component.translatable(result.shiny() ? "caricraft.cc.wonder_got_shiny" : "caricraft.cc.wonder_got",
                    CobbleCentroScreen.species(result.species()), result.amount());
            case "COBBLEDOLLARS" -> Component.translatable("caricraft.cc.got", CobbleCentroScreen.money(result.amount()));
            case "CARINS" -> Component.translatable("caricraft.cc.got", Component.translatable("caricraft.cc.reward.carins", result.amount()));
            default -> Component.translatable("caricraft.cc.got",
                    Component.translatable("caricraft.phone.market.offer", result.amount(), PhoneText.idName(reward.item())));
        };
        ui.textCentered(got, cx, ty + 11, t.text());
        okButton(cx, ty + 24);
        ui.g.pose().popPose();
    }

    private void sparkles(int cx, int cy, int radius) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 8; i++) {
            double a = now / 600.0 + i * Math.PI / 4;
            ui().text("✦", cx + (int) (Math.cos(a) * radius) - 3, cy + (int) (Math.sin(a) * radius * 0.8) - 4, CobbleCentroScreen.SHINY);
        }
    }

    private void okButton(int cx, int y) {
        okButton(cx, y, true);
    }

    private void okButton(int cx, int y, boolean good) {
        Component ok = Component.translatable(good ? "caricraft.cc.wonder_ok" : "caricraft.cc.ok_bad");
        int bw = Math.max(70, ui().buttonWidth(ok));
        ui().button(cx - bw / 2, y, bw, 16, ok, good ? Style.GOOD : Style.SECONDARY, () -> anim = null);
    }

    /** Resultado de una mejora o una fusión: con suspense si es una apuesta (variocolor, fusión con probabilidad). */
    private void resultCard(CobbleverseView cv, int x, int y, int w, int h, float partialTick) {
        PhoneUi ui = ui();
        PhoneTheme t = CobbleCentroScreen.THEME;
        LuckResultView result = anim.result();
        LuckView luck = cv.luck();
        boolean fusion = "fusion".equals(result.kind());
        UpgradeView upgrade = fusion || luck == null ? null
                : luck.upgrades().stream().filter(u -> u.id().equals(result.id())).findFirst().orElse(null);
        FusionView fusionView = !fusion || luck == null ? null
                : luck.fusions().stream().filter(f -> f.id().equals(result.id())).findFirst().orElse(null);
        double chance = upgrade != null ? upgrade.chance() : fusionView != null ? fusionView.chance() : 1;
        long suspense = chance < 1 ? SHINY_SUSPENSE_MS : 250;
        backdrop(x, y, w, h);
        long age = age();
        int cx = x + w / 2;
        int size = Math.min(90, h - 70);
        int top = y + 14;
        boolean revealed = age >= suspense;
        if (revealed && age - suspense < 300) {
            int alpha = (int) (200 * (1 - (age - suspense) / 300f));
            int color = result.success() ? (result.shiny() && upgrade != null && "shiny".equals(upgrade.kind()) ? 0xFFFFE08A : t.good()) : t.bad();
            ui.g.fill(x - 2, y, x + w + 4, y + h, (alpha << 24) | (color & 0xFFFFFF));
        }
        if (fusion) {
            String icon = fusionView == null ? "minecraft:barrier" : revealed && result.success() ? fusionView.output()
                    : fusionView.inputs().getFirst().item();
            float shake = revealed ? 0 : (float) Math.sin(System.currentTimeMillis() / 50.0) * 3f;
            ui.g.pose().pushPose();
            ui.g.pose().translate(cx - 24 + shake, top + size / 2f - 24, 0);
            ui.g.pose().scale(3f, 3f, 1f);
            ui.item(icon, 0, 0);
            ui.g.pose().popPose();
        } else if (result.species() != null) {
            boolean showShiny = revealed ? result.shiny() : result.shiny() && !(upgrade != null && "shiny".equals(upgrade.kind()));
            if (revealed && result.success() && result.shiny()) {
                sparkles(cx, top + size / 2, size / 2 + 6);
            }
            float yaw = revealed ? CobbleCentroScreen.spin() : 35f + (float) Math.sin(System.currentTimeMillis() / 90.0) * 25f;
            s.stage(x, top, x + w, top + size, () -> s.model("upgrade:" + result.time() + (showShiny ? "s" : ""), result.species(),
                    CobbleCentroScreen.aspects(List.of(), showShiny), cx, top, size, yaw, partialTick));
        }
        int ty = top + size + 6;
        if (!revealed) {
            ui.textCentered(Component.translatable(fusion ? "caricraft.cc.fusing" : "caricraft.cc.upgrading_now"), cx, ty, t.textDim());
            ui.g.pose().popPose();
            return;
        }
        Component title;
        if (fusion) {
            title = result.success() && fusionView != null
                    ? Component.translatable("caricraft.cc.fuse_result_ok", result.amount(), PhoneText.idName(fusionView.output()))
                    : Component.translatable("caricraft.cc.fuse_result_fail");
        } else {
            String kind = upgrade == null ? "" : upgrade.kind();
            title = !result.success() ? Component.translatable("caricraft.cc.upgrade_result_fail")
                    : Component.translatable("caricraft.cc.upgrade_result." + kind, CobbleCentroScreen.species(result.species()), result.amount());
        }
        ui.textCentered(title.copy().withStyle(ChatFormatting.BOLD), cx, ty, result.success() ? t.good() : t.bad());
        okButton(cx, ty + 16, result.success());
        ui.g.pose().popPose();
    }
}
