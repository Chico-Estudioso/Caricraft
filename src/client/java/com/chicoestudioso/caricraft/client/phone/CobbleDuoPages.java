package com.chicoestudioso.caricraft.client.phone;

import com.chicoestudioso.caricraft.client.ClientPhoneState;
import com.chicoestudioso.caricraft.client.phone.PhoneUi.Style;
import com.chicoestudioso.caricraft.network.PhoneAction;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.CobbleverseView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.DexEntryView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.SellView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Map;

/**
 * Páginas "del dúo" del CobbleCentro: caza del día, Pokédex del dúo (variocolores, capturas raras, legendarios y
 * metas compartidas) y expediciones; y en el inicio, el Centro Pokémon de bolsillo y los avisos de variocolor.
 */
final class CobbleDuoPages {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private final CobbleCentroScreen s;
    /** Destino, duración y Pokémon elegidos para la próxima expedición. */
    private String destination = "forest";
    private int hours = 1;
    private String pick;
    private long cancelArmedAt;
    private int cancelArmedSlot = -1;
    /** Pokémon elegido en el Taller para el entrenador personal. */
    private String trainPick;

    CobbleDuoPages(CobbleCentroScreen screen) {
        this.s = screen;
    }

    private static long serverNow() {
        PhoneSnapshot snap = ClientPhoneState.snapshot();
        return snap == null ? System.currentTimeMillis() : snap.serverTime();
    }

    private static Component type(String type) {
        String key = "cobblemon.type." + type;
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(type);
    }

    private static String rewardText(long dollars, long carins) {
        StringBuilder out = new StringBuilder();
        if (dollars > 0) {
            out.append(CobbleCentroScreen.money(dollars));
        }
        if (carins > 0) {
            if (!out.isEmpty()) {
                out.append(" · ");
            }
            out.append(PhoneText.number(carins)).append(" C");
        }
        return out.toString();
    }

    // ================================================================ inicio: Centro Pokémon y variocolores

    int homeCards(CobbleverseView cv, int x, int y, int w) {
        int top = y;
        if (cv.shinyAlerts() != null) {
            for (PhoneSnapshot.ShinyAlertView alert : cv.shinyAlerts()) {
                y += shinyCard(cv, alert, x, y, w) + 8;
            }
        }
        if (cv.heal() != null) {
            y += healCard(cv, cv.heal(), x, y, w) + 10;
        }
        return y - top;
    }

    private int shinyCard(CobbleverseView cv, PhoneSnapshot.ShinyAlertView alert, int x, int y, int w) {
        PhoneTheme t = CobbleCentroScreen.THEME;
        int h = 44;
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 250.0));
        s.ui.round(x, y, x + w, y + h, PhoneTheme.mix(0xFFB8860B, t.background(), 0.35f + 0.15f * pulse));
        s.ui.text(Component.literal("✦").withStyle(ChatFormatting.BOLD), x + 8, y + 8, CobbleCentroScreen.SHINY);
        Component title = Component.translatable("caricraft.cc.shiny_title", CobbleCentroScreen.species(alert.species()), alert.level());
        s.ui.textClipped(title.copy().withStyle(ChatFormatting.BOLD), x + 20, y + 8, w - 28, 0xFFFFFFFF);
        var player = Minecraft.getInstance().player;
        Component where;
        if (player != null && player.level().dimension().location().toString().equals(alert.dimension())) {
            double dx = alert.x() - player.getX();
            double dz = alert.z() - player.getZ();
            float target = (float) Math.toDegrees(Math.atan2(-dx, dz));
            String arrow = ARROWS[Math.floorMod(Math.round(Mth.wrapDegrees(target - player.getYRot()) / 45f), 8)];
            where = Component.translatable("caricraft.cc.shiny_where", Math.round(Math.sqrt(dx * dx + dz * dz)), arrow, alert.near());
        } else {
            where = Component.translatable("caricraft.cc.shiny_other_world", alert.near());
        }
        s.ui.textClipped(where, x + 20, y + 20, w - 28, 0xFFF3E6C4);
        long left = Math.max(0, alert.expiresAt() - serverNow());
        Component sub = Component.translatable(cv.shinyCompass() ? "caricraft.cc.shiny_compass_on" : "caricraft.cc.shiny_compass_off",
                PhoneText.remaining(left));
        s.ui.textClipped(sub, x + 20, y + 31, w - 28, 0xFFD9C79A);
        return h;
    }

    private int healCard(CobbleverseView cv, PhoneSnapshot.HealView heal, int x, int y, int w) {
        PhoneTheme t = CobbleCentroScreen.THEME;
        long wait = heal.readyAt() - serverNow();
        Component state;
        Style style = Style.DISABLED;
        Component label;
        if (heal.inBattle()) {
            state = Component.translatable("caricraft.cc.heal_battle");
            label = Component.translatable("caricraft.cc.heal_button_off");
        } else if (wait > 0) {
            state = Component.translatable("caricraft.cc.heal_wait", PhoneText.remaining(wait));
            label = Component.translatable("caricraft.cc.heal_button_off");
        } else if (!heal.needed()) {
            state = Component.translatable("caricraft.cc.heal_fine");
            label = Component.translatable("caricraft.cc.heal_button_off");
        } else {
            state = Component.translatable("caricraft.cc.heal_ready");
            label = heal.price() > 0 ? Component.translatable("caricraft.cc.heal_button", CobbleCentroScreen.money(heal.price()))
                    : Component.translatable("caricraft.cc.heal_button_free");
            style = cv.cobbleDollars() >= 0 && cv.cobbleDollars() < heal.price() ? Style.DISABLED : Style.GOOD;
        }
        int bw = Math.max(70, s.ui.buttonWidth(label));
        int textW = w - 44 - bw;
        int h = Math.max(40, 20 + s.ui.wrappedHeight(state, textW) + 6);
        s.ui.round(x, y, x + w, y + h, t.card());
        s.ui.item("cobblemon:hyper_potion", x + 8, y + (h - 16) / 2);
        s.ui.text(Component.translatable("caricraft.cc.heal_title").copy().withStyle(ChatFormatting.BOLD), x + 30, y + 7, t.text());
        s.ui.wrapped(state, x + 30, y + 19, textW, t.textDim());
        Style finalStyle = style;
        s.ui.button(x + w - bw - 8, y + (h - 16) / 2, bw, 16, label, style, () -> {
            if (finalStyle == Style.GOOD) {
                s.send(PhoneAction.CV_HEAL, "");
            }
        });
        return h;
    }

    // ================================================================ caza del día

    int hunt(CobbleverseView cv, int x, int y, int w, float partialTick) {
        PhoneTheme t = CobbleCentroScreen.THEME;
        PhoneSnapshot.HuntView hunt = cv.hunt();
        int top = y;
        long left = cv.nextDayAt() - serverNow();
        y += s.sectionTitle(Component.translatable("caricraft.cc.tab.hunt"),
                Component.translatable("caricraft.phone.cv.changes_in", PhoneText.remaining(left)), x, y, w);
        if (hunt == null) {
            return y - top + s.ui.wrapped(Component.translatable("caricraft.cc.off"), x, y, w, t.textDim());
        }
        y += s.ui.wrapped(Component.translatable(hunt.duo() ? "caricraft.cc.hunt_help_duo" : "caricraft.cc.hunt_help_solo",
                hunt.duoMinEach()), x, y, w, t.textDim()) + 6;
        for (PhoneSnapshot.HuntTargetView target : hunt.targets()) {
            y += targetCard(target, x, y, w, partialTick) + 6;
        }
        y += 4;
        // Bonus
        y += bonusRow(x, y, w, "cobblemon:great_ball", Component.translatable("caricraft.cc.hunt_clear"),
                rewardText(hunt.clearDollars(), hunt.clearCarins()), hunt.cleared()) + 4;
        if (hunt.duo()) {
            StringBuilder who = new StringBuilder();
            for (Map.Entry<String, Integer> e : hunt.contrib().entrySet()) {
                if (!who.isEmpty()) {
                    who.append(" · ");
                }
                who.append(e.getKey()).append(' ').append(Math.min(e.getValue(), hunt.duoMinEach())).append('/').append(hunt.duoMinEach());
            }
            y += bonusRow(x, y, w, "cobblemon:ultra_ball", Component.translatable("caricraft.cc.hunt_duo", who.toString()),
                    rewardText(hunt.duoDollars(), hunt.duoCarins()), hunt.duoBonus()) + 4;
        }
        y += bonusRow(x, y, w, "cobblemon:gilded_chest", Component.translatable(hunt.duo() ? "caricraft.cc.hunt_weekly_duo"
                        : "caricraft.cc.hunt_weekly", Math.min(hunt.weekClears(), hunt.weeklyClears()), hunt.weeklyClears()),
                rewardText(hunt.weeklyDollars(), hunt.weeklyCarins()), hunt.weekPaid()) + 6;
        y += s.ui.wrapped(Component.translatable("caricraft.cc.hunt_pay"), x, y, w, t.textDim()) + 4;
        return y - top;
    }

    private int targetCard(PhoneSnapshot.HuntTargetView target, int x, int y, int w, float partialTick) {
        PhoneTheme t = CobbleCentroScreen.THEME;
        int h = 40;
        s.ui.round(x, y, x + w, y + h, target.done() ? PhoneTheme.mix(t.good(), t.card(), 0.75f) : t.card());
        switch (target.kind()) {
            case "SPECIES" -> {
                String species = "cobblemon:" + target.value();
                s.stage(x + 2, y + 2, x + 38, y + h - 2, () -> s.model("hunt:" + species, species, java.util.Set.of(), x + 20, y + 2, 34,
                        CobbleCentroScreen.spin(), partialTick));
            }
            case "TYPE" -> s.ui.item("cobblemon:dusk_ball", x + 12, y + 12);
            default -> s.ui.item("cobblemon:poke_ball", x + 12, y + 12);
        }
        Component what = switch (target.kind()) {
            case "SPECIES" -> Component.translatable("caricraft.cc.hunt_species", target.count(), CobbleCentroScreen.species(target.value()));
            case "TYPE" -> Component.translatable("caricraft.cc.hunt_type", target.count(), type(target.value()));
            default -> Component.translatable("caricraft.cc.hunt_any", target.count());
        };
        String reward = rewardText(target.dollars(), target.carins());
        int rw = Minecraft.getInstance().font.width(reward);
        s.ui.textClipped(what.copy().withStyle(ChatFormatting.BOLD), x + 42, y + 7, w - 50, t.text());
        String count = target.done() ? "✔" : target.progress() + "/" + target.count();
        int cw = Minecraft.getInstance().font.width(count);
        int barW = w - 42 - rw - cw - 30;
        s.ui.progressBar(x + 42, y + 24, barW, target.progress(), target.count(), target.done() ? t.good() : t.accent());
        s.ui.text(count, x + 42 + barW + 6, y + 22, target.done() ? t.good() : t.textDim());
        s.ui.textRight(Component.literal(reward), x + w - 8, y + 22, target.done() ? t.good() : CobbleCentroScreen.GOLD);
        return h;
    }

    private int bonusRow(int x, int y, int w, String icon, Component title, String reward, boolean done) {
        PhoneTheme t = CobbleCentroScreen.THEME;
        int h = 24;
        s.ui.round(x, y, x + w, y + h, done ? PhoneTheme.mix(t.good(), t.card(), 0.7f) : PhoneTheme.mix(t.card(), t.background(), 0.4f));
        s.ui.item(icon, x + 5, y + 4);
        int rw = Minecraft.getInstance().font.width(reward) + 16;
        s.ui.textClipped(title, x + 26, y + 8, w - 34 - rw, done ? 0xFFFFFFFF : t.text());
        s.ui.textRight(Component.literal((done ? "✔ " : "") + reward), x + w - 8, y + 8, done ? 0xFFFFFFFF : CobbleCentroScreen.GOLD);
        return h;
    }

    // ================================================================ Pokédex del dúo

    int dex(CobbleverseView cv, int x, int y, int w, float partialTick) {
        PhoneTheme t = CobbleCentroScreen.THEME;
        PhoneSnapshot.DexView dex = cv.dex();
        int top = y;
        y += s.sectionTitle(Component.translatable("caricraft.cc.tab.dex"), null, x, y, w);
        if (dex == null) {
            return y - top + s.ui.wrapped(Component.translatable("caricraft.cc.off"), x, y, w, t.textDim());
        }
        y += s.ui.wrapped(Component.translatable("caricraft.cc.dex_help"), x, y, w, t.textDim()) + 6;
        // Cifras
        int cell = (w - 18) / 4;
        stat(x, y, cell, "cobblemon:pokedex_red", PhoneText.number(dex.species()), Component.translatable("caricraft.cc.dex_species"));
        stat(x + (cell + 6), y, cell, "cobblemon:poke_ball", PhoneText.number(dex.captures()), Component.translatable("caricraft.cc.dex_captures"));
        stat(x + 2 * (cell + 6), y, cell, "minecraft:nether_star", PhoneText.number(dex.shinies()), Component.translatable("caricraft.cc.dex_shinies"));
        stat(x + 3 * (cell + 6), y, cell, "cobblemon:master_ball", PhoneText.number(dex.legends()), Component.translatable("caricraft.cc.dex_legends"));
        y += 40;

        y += s.sectionTitle(Component.translatable("caricraft.cc.dex_showcase"), Component.literal(String.valueOf(dex.shinies())), x, y, w);
        y += showcase(dex.shinyList(), "shiny", x, y, w, partialTick, Component.translatable("caricraft.cc.dex_no_shinies")) + 6;
        y += s.sectionTitle(Component.translatable("caricraft.cc.dex_legend_list"), Component.literal(String.valueOf(dex.legends())), x, y, w);
        y += showcase(dex.legendList(), "legend", x, y, w, partialTick, Component.translatable("caricraft.cc.dex_no_legends")) + 6;

        y += s.sectionTitle(Component.translatable("caricraft.cc.dex_rares"), null, x, y, w);
        if (dex.rares().isEmpty()) {
            y += s.ui.wrapped(Component.translatable("caricraft.cc.dex_no_rares"), x, y, w, t.textDim()) + 6;
        } else {
            for (DexEntryView entry : dex.rares()) {
                s.ui.round(x, y, x + w, y + 18, t.card());
                s.ui.textClipped(CobbleCentroScreen.species(entry.species()).copy().withStyle(ChatFormatting.BOLD), x + 6, y + 5, w / 2 - 10,
                        entry.shiny() ? CobbleCentroScreen.SHINY : t.text());
                s.ui.textRight(Component.translatable("caricraft.cc.dex_rare_row", entry.level(), entry.player(), entry.catchRate()),
                        x + w - 6, y + 5, t.textDim());
                y += 20;
            }
            y += 4;
        }

        y += s.sectionTitle(Component.translatable("caricraft.cc.dex_goals"), Component.literal(
                dex.goals().stream().filter(PhoneSnapshot.GoalView::done).count() + "/" + dex.goals().size()), x, y, w);
        for (PhoneSnapshot.GoalView goal : dex.goals()) {
            int h = 30;
            s.ui.round(x, y, x + w, y + h, goal.done() ? PhoneTheme.mix(t.good(), t.card(), 0.75f) : t.card());
            String reward = rewardText(goal.dollars(), goal.carins());
            int rw = Minecraft.getInstance().font.width(reward) + 10;
            s.ui.textClipped(Component.translatable("caricraft.phone.cv.goal." + goal.id()).copy().withStyle(ChatFormatting.BOLD),
                    x + 6, y + 5, w - 14 - rw, t.text());
            s.ui.textRight(Component.literal(reward), x + w - 6, y + 5, goal.done() ? t.good() : CobbleCentroScreen.GOLD);
            s.ui.progressBar(x + 6, y + 19, w - 60, goal.progress(), goal.count(), goal.done() ? t.good() : t.accent());
            s.ui.textRight(Component.literal(goal.done() ? "✔" : goal.progress() + "/" + goal.count()), x + w - 6, y + 17,
                    goal.done() ? t.good() : t.textDim());
            y += h + 4;
        }
        return y - top;
    }

    private void stat(int x, int y, int w, String icon, String value, Component label) {
        PhoneTheme t = CobbleCentroScreen.THEME;
        s.ui.round(x, y, x + w, y + 34, t.card());
        s.ui.item(icon, x + 4, y + 3);
        s.ui.textClipped(Component.literal(value).withStyle(ChatFormatting.BOLD), x + 23, y + 7, w - 26, t.text());
        s.ui.textFit(label, x + w / 2, y + 22, w - 6, t.textDim());
    }

    private int showcase(List<DexEntryView> list, String key, int x, int y, int w, float partialTick, Component empty) {
        PhoneTheme t = CobbleCentroScreen.THEME;
        if (list.isEmpty()) {
            return s.ui.wrapped(empty, x, y, w, t.textDim());
        }
        int cell = 58;
        int columns = Math.max(1, w / cell);
        int gap = (w - columns * cell) / Math.max(1, columns);
        int top = y;
        for (int i = 0; i < list.size(); i++) {
            DexEntryView entry = list.get(i);
            int cx = x + (i % columns) * (cell + gap);
            int cy = top + (i / columns) * (cell + 24);
            boolean hover = s.ui.hovered(cx, cy, cx + cell, cy + cell + 20);
            s.ui.round(cx, cy, cx + cell, cy + cell + 20, hover ? t.cardHover() : t.card());
            if (entry.shiny()) {
                s.ui.g.fill(cx, cy, cx + cell, cy + 2, CobbleCentroScreen.SHINY);
            }
            int rowY = cy;
            s.stage(cx, rowY, cx + cell, rowY + cell, () -> s.model(key + ":" + entry.species() + ":" + entry.time(), entry.species(),
                    CobbleCentroScreen.aspects(List.of(), entry.shiny()), cx + cell / 2, rowY + 2, cell - 8, hover ? CobbleCentroScreen.spin() : 35f,
                    partialTick));
            s.ui.textFit(CobbleCentroScreen.species(entry.species()), cx + cell / 2, cy + cell, cell - 4, entry.shiny() ? CobbleCentroScreen.SHINY : t.text());
            s.ui.textFit(Component.literal(entry.player()), cx + cell / 2, cy + cell + 10, cell - 4, t.textDim());
        }
        int rows = (list.size() + columns - 1) / columns;
        return rows * (cell + 24);
    }

    // ================================================================ expediciones

    private static Component typesOf(PhoneSnapshot.DestinationView d) {
        StringBuilder types = new StringBuilder();
        for (String ty : d.types()) {
            if (!types.isEmpty()) {
                types.append(", ");
            }
            types.append(type(ty).getString());
        }
        return Component.literal(types.toString());
    }

    private int awayCard(PhoneSnapshot.AwayView away, int slot, int x, int y, int w, float partialTick) {
        PhoneTheme t = CobbleCentroScreen.THEME;
        int h = 84;
        s.ui.round(x, y, x + w, y + h, away.ready() ? PhoneTheme.mix(t.good(), t.card(), 0.7f) : t.card());
        int rowY = y;
        s.stage(x + 4, rowY + 4, x + 80, rowY + h - 4, () -> s.model("exp:" + slot + ":" + away.species(), away.species(),
                CobbleCentroScreen.aspects(away.aspects(), away.shiny()), x + 42, rowY + 6, 70, CobbleCentroScreen.spin(), partialTick));
        Component title = Component.translatable(away.ready() ? "caricraft.cc.exp_back_title" : "caricraft.cc.exp_away_title",
                CobbleCentroScreen.species(away.species()), Component.translatable("caricraft.phone.cv.exp." + away.destination()));
        s.ui.wrapped(title.copy().withStyle(ChatFormatting.BOLD), x + 86, y + 8, w - 94, 0xFFFFFFFF);
        long left = Math.max(0, away.endsAt() - serverNow());
        Component sub = away.ready() ? Component.translatable("caricraft.cc.exp_ready")
                : Component.translatable("caricraft.cc.exp_left", PhoneText.remaining(left), away.hours());
        s.ui.wrapped(sub, x + 86, y + 34, w - 94, t.textDim());
        String arg = String.valueOf(slot);
        if (away.ready()) {
            Component claim = Component.translatable("caricraft.cc.exp_claim");
            int bw = Math.max(90, s.ui.buttonWidth(claim));
            s.ui.button(x + w - bw - 8, y + h - 24, bw, 16, claim, Style.GOOD, () -> s.send(PhoneAction.CV_EXP_CLAIM, arg));
        } else {
            boolean armed = cancelArmedSlot == slot && System.currentTimeMillis() - cancelArmedAt < 4000;
            Component cancel = Component.translatable(armed ? "caricraft.cc.exp_cancel_confirm" : "caricraft.cc.exp_cancel");
            int bw = Math.max(90, s.ui.buttonWidth(cancel));
            s.ui.button(x + w - bw - 8, y + h - 24, bw, 16, cancel, armed ? Style.DANGER : Style.SECONDARY, () -> {
                if (cancelArmedSlot == slot && System.currentTimeMillis() - cancelArmedAt < 4000) {
                    cancelArmedAt = 0;
                    s.send(PhoneAction.CV_EXP_CANCEL, arg);
                } else {
                    cancelArmedSlot = slot;
                    cancelArmedAt = System.currentTimeMillis();
                }
            });
        }
        return h;
    }

    int expedition(CobbleverseView cv, int x, int y, int w, float partialTick) {
        PhoneTheme t = CobbleCentroScreen.THEME;
        PhoneSnapshot.ExpeditionView exp = cv.expedition();
        int top = y;
        if (exp == null) {
            y += s.sectionTitle(Component.translatable("caricraft.cc.tab.expedition"), null, x, y, w);
            return y - top + s.ui.wrapped(Component.translatable("caricraft.cc.off"), x, y, w, t.textDim());
        }
        y += s.sectionTitle(Component.translatable("caricraft.cc.tab.expedition"),
                Component.translatable("caricraft.cc.exp_today", exp.startedToday(), exp.perDay()), x, y, w);
        List<PhoneSnapshot.AwayView> aways = exp.away() == null ? List.of() : exp.away();
        for (int i = 0; i < aways.size(); i++) {
            y += awayCard(aways.get(i), i, x, y, w, partialTick) + 8;
        }
        if (exp.slots() > 1) {
            y += s.ui.wrapped(Component.translatable("caricraft.cc.exp_slots", aways.size(), exp.slots()), x, y, w, t.textDim()) + 4;
        }
        if (aways.size() >= exp.slots()) {
            y += s.ui.wrapped(Component.translatable("caricraft.cc.exp_help"), x, y, w, t.textDim()) + 4;
            return y - top;
        }
        y += s.ui.wrapped(Component.translatable("caricraft.cc.exp_help"), x, y, w, t.textDim()) + 6;

        // 1. Destino
        y += s.sectionTitle(Component.translatable("caricraft.cc.exp_step_destination"), null, x, y, w);
        int n = exp.destinations().size();
        int cw = (w - (n - 1) * 6) / Math.max(1, n);
        int maxH = 0;
        for (PhoneSnapshot.DestinationView d : exp.destinations()) {
            maxH = Math.max(maxH, 44 + s.ui.wrappedHeight(typesOf(d), cw - 10));
        }
        for (int i = 0; i < n; i++) {
            PhoneSnapshot.DestinationView d = exp.destinations().get(i);
            int dx = x + i * (cw + 6);
            int h = maxH;
            boolean chosen = d.id().equals(destination);
            boolean hover = s.ui.hovered(dx, y, dx + cw, y + h);
            s.ui.round(dx, y, dx + cw, y + h, chosen ? PhoneTheme.mix(t.accent(), t.card(), 0.55f) : hover ? t.cardHover() : t.card());
            s.ui.item(d.icon(), dx + 5, y + 5);
            s.ui.textClipped(Component.translatable("caricraft.cc.exp_name." + d.id()).copy().withStyle(ChatFormatting.BOLD), dx + 24, y + 9,
                    cw - 28, 0xFFFFFFFF);
            int typesH = s.ui.wrapped(typesOf(d), dx + 5, y + 25, cw - 10, t.textDim());
            int ix = dx + 5;
            for (String item : d.loot()) {
                if (ix + 16 > dx + cw - 4) {
                    break;
                }
                s.ui.item(item, ix, y + 27 + typesH);
                ix += 17;
            }
            String id = d.id();
            s.ui.hit(dx, y, dx + cw, y + h, () -> destination = id);
        }
        y += maxH + 8;

        // 2. Duración
        y += s.sectionTitle(Component.translatable("caricraft.cc.exp_step_hours"), null, x, y, w);
        int bx = x;
        for (int option : exp.hours()) {
            Component label = Component.translatable("caricraft.cc.exp_hours", option);
            int bw = Math.max(56, s.ui.buttonWidth(label));
            s.ui.button(bx, y, bw, 16, label, option == hours ? Style.PRIMARY : Style.SECONDARY, () -> hours = option);
            bx += bw + 6;
        }
        if (!exp.hours().contains(hours)) {
            hours = exp.hours().getFirst();
        }
        y += 22;

        // 3. Pokémon
        y += s.sectionTitle(Component.translatable("caricraft.cc.exp_step_pokemon"), null, x, y, w);
        List<SellView> party = cv.party();
        if (party.stream().noneMatch(m -> m.uuid().equals(pick))) {
            pick = null;
        }
        if (party.isEmpty()) {
            y += s.ui.wrapped(Component.translatable("caricraft.cc.team_empty"), x, y, w, t.textDim()) + 6;
        } else {
            int cell = Math.min(60, w / Math.max(3, party.size()));
            int sx = x + (w - cell * party.size()) / 2;
            for (int i = 0; i < party.size(); i++) {
                SellView mon = party.get(i);
                int px = sx + i * cell;
                boolean chosen = mon.uuid().equals(pick);
                boolean hover = s.ui.hovered(px + 2, y, px + cell - 2, y + cell + 20);
                s.ui.round(px + 2, y, px + cell - 2, y + cell + 20, chosen ? PhoneTheme.mix(t.accent(), t.card(), 0.55f) : hover ? t.cardHover() : t.card());
                int rowY = y;
                s.stage(px + 2, rowY, px + cell - 2, rowY + cell, () -> s.model("exppick:" + mon.uuid(), mon.species(),
                        CobbleCentroScreen.aspects(mon.aspects(), mon.shiny()), px + cell / 2, rowY + 2, cell - 8,
                        hover || chosen ? CobbleCentroScreen.spin() : 35f, partialTick));
                Component name = mon.nickname() == null || mon.nickname().isEmpty() ? CobbleCentroScreen.species(mon.species())
                        : Component.literal(mon.nickname());
                s.ui.textFit(name, px + cell / 2, y + cell, cell - 6, t.text());
                s.ui.textFit(Component.translatable("caricraft.phone.cv.level", mon.level()), px + cell / 2, y + cell + 10, cell - 6, t.textDim());
                String uuid = mon.uuid();
                s.ui.hit(px + 2, y, px + cell - 2, y + cell + 20, () -> pick = uuid);
            }
            y += cell + 26;
        }
        boolean limit = exp.startedToday() >= exp.perDay();
        Component go = Component.translatable(limit ? "caricraft.cc.exp_limit" : "caricraft.cc.exp_send", hours);
        int bw = Math.max(120, s.ui.buttonWidth(go));
        boolean ready = pick != null && !limit;
        String chosen = pick;
        s.ui.button(x + (w - bw) / 2, y, bw, 18, go, ready ? Style.GOOD : Style.DISABLED, () -> {
            if (ready) {
                s.send(PhoneAction.CV_EXP_START, destination + ":" + hours, chosen);
            }
        });
        y += 26;
        return y - top;
    }

    // ================================================================ taller: entrenador personal y mejoras

    private static final List<String> UPGRADES = List.of("cc_heal", "cc_expedition", "cc_radar");

    int workshop(CobbleverseView cv, int x, int y, int w, float partialTick) {
        PhoneTheme t = CobbleCentroScreen.THEME;
        PhoneSnapshot snap = ClientPhoneState.snapshot();
        int top = y;
        long carins = snap == null ? 0 : snap.balance();
        y += s.sectionTitle(Component.translatable("caricraft.cc.trainer_title"),
                Component.translatable("caricraft.cc.carins", PhoneText.number(carins)), x, y, w);
        PhoneSnapshot.TrainerView trainer = cv.trainer();
        if (trainer == null) {
            y += s.ui.wrapped(Component.translatable("caricraft.cc.off"), x, y, w, t.textDim()) + 8;
        } else {
            Component help = Component.translatable(trainer.cap() > 0 ? "caricraft.cc.trainer_help_cap" : "caricraft.cc.trainer_help",
                    trainer.basePrice(), trainer.levelsLeft(), trainer.levelsPerDay(), trainer.cap());
            y += s.ui.wrapped(help, x, y, w, t.textDim()) + 6;
            List<SellView> party = cv.party();
            if (party.stream().noneMatch(m -> m.uuid().equals(trainPick))) {
                trainPick = party.isEmpty() ? null : party.getFirst().uuid();
            }
            if (party.isEmpty()) {
                y += s.ui.wrapped(Component.translatable("caricraft.cc.team_empty"), x, y, w, t.textDim()) + 6;
            } else {
                int cell = Math.min(60, w / Math.max(3, party.size()));
                int sx = x + (w - cell * party.size()) / 2;
                SellView picked = null;
                for (int i = 0; i < party.size(); i++) {
                    SellView mon = party.get(i);
                    int px = sx + i * cell;
                    boolean chosen = mon.uuid().equals(trainPick);
                    if (chosen) {
                        picked = mon;
                    }
                    boolean hover = s.ui.hovered(px + 2, y, px + cell - 2, y + cell + 20);
                    s.ui.round(px + 2, y, px + cell - 2, y + cell + 20, chosen ? PhoneTheme.mix(t.accent(), t.card(), 0.55f) : hover ? t.cardHover() : t.card());
                    int rowY = y;
                    s.stage(px + 2, rowY, px + cell - 2, rowY + cell, () -> s.model("train:" + mon.uuid(), mon.species(),
                            CobbleCentroScreen.aspects(mon.aspects(), mon.shiny()), px + cell / 2, rowY + 2, cell - 8,
                            hover || chosen ? CobbleCentroScreen.spin() : 35f, partialTick));
                    Component name = mon.nickname() == null || mon.nickname().isEmpty() ? CobbleCentroScreen.species(mon.species())
                            : Component.literal(mon.nickname());
                    s.ui.textFit(name, px + cell / 2, y + cell, cell - 6, t.text());
                    boolean atCap = trainer.cap() > 0 && mon.level() >= trainer.cap();
                    s.ui.textFit(Component.translatable("caricraft.phone.cv.level", mon.level()), px + cell / 2, y + cell + 10, cell - 6,
                            atCap ? t.bad() : t.textDim());
                    String uuid = mon.uuid();
                    s.ui.hit(px + 2, y, px + cell - 2, y + cell + 20, () -> trainPick = uuid);
                }
                y += cell + 26;
                if (picked != null) {
                    long price = trainer.basePrice() + Math.max(1, picked.level());
                    int max = trainer.cap() > 0 ? Math.min(100, trainer.cap()) : 100;
                    boolean capped = picked.level() >= max;
                    boolean ready = !capped && trainer.levelsLeft() > 0 && carins >= price;
                    Component label = capped ? Component.translatable(trainer.cap() > 0 ? "caricraft.cc.trainer_at_cap" : "caricraft.cc.trainer_at_max", max)
                            : trainer.levelsLeft() <= 0 ? Component.translatable("caricraft.cc.trainer_no_levels")
                            : Component.translatable("caricraft.cc.trainer_button", picked.level() + 1, PhoneText.number(price));
                    int bw = Math.max(150, s.ui.buttonWidth(label));
                    String uuid = picked.uuid();
                    s.ui.button(x + (w - bw) / 2, y, bw, 18, label, ready ? Style.GOOD : Style.DISABLED, () -> {
                        if (ready) {
                            s.send(PhoneAction.CV_TRAIN, uuid);
                        }
                    });
                    y += 26;
                }
            }
        }

        // Mejoras del CobbleCentro (se compran con Carins y son de todo el dúo)
        y += 4;
        y += s.sectionTitle(Component.translatable("caricraft.cc.workshop_upgrades_title"), null, x, y, w);
        y += s.ui.wrapped(Component.translatable("caricraft.cc.workshop_upgrades_help"), x, y, w, t.textDim()) + 6;
        if (snap != null) {
            for (String id : UPGRADES) {
                PhoneSnapshot.AppView app = snap.apps().stream().filter(a -> a.id().equals(id)).findFirst().orElse(null);
                if (app != null) {
                    y += upgradeCard(app, carins, x, y, w) + 6;
                }
            }
        }
        return y - top;
    }

    private int upgradeCard(PhoneSnapshot.AppView app, long carins, int x, int y, int w) {
        PhoneTheme t = CobbleCentroScreen.THEME;
        boolean maxed = app.level() >= app.maxLevel();
        Component effect = Component.translatable("caricraft.cc.upgrade." + app.id() + "." + Math.min(app.level(), app.maxLevel()));
        Component next = maxed ? null : Component.translatable("caricraft.cc.upgrade." + app.id() + "." + (app.level() + 1));
        Component label = maxed ? Component.translatable("caricraft.cc.upgrade_max")
                : Component.translatable("caricraft.cc.upgrade_buy", PhoneText.number(app.price()));
        int bw = Math.max(96, s.ui.buttonWidth(label));
        int textW = w - 40 - bw;
        Component line = next == null ? effect : Component.translatable("caricraft.cc.upgrade_next", effect, next);
        int h = Math.max(42, 20 + s.ui.wrappedHeight(line, textW) + 6);
        s.ui.round(x, y, x + w, y + h, maxed ? PhoneTheme.mix(t.good(), t.card(), 0.75f) : t.card());
        s.ui.round(x + 5, y + (h - 24) / 2, x + 29, y + (h - 24) / 2 + 24, PhoneTheme.mix(app.color(), t.card(), 0.4f));
        s.ui.item(app.icon(), x + 9, y + (h - 16) / 2);
        Component title = PhoneText.appName(app.id()).copy().append(Component.literal("  " + app.level() + "/" + app.maxLevel()))
                .withStyle(ChatFormatting.BOLD);
        s.ui.textClipped(title, x + 36, y + 7, textW, t.text());
        s.ui.wrapped(line, x + 36, y + 19, textW, t.textDim());
        boolean ready = !maxed && "AVAILABLE".equals(app.state()) || (!maxed && "OWNED".equals(app.state()));
        boolean affordable = carins >= app.price();
        String id = app.id();
        s.ui.button(x + w - bw - 8, y + (h - 16) / 2, bw, 16, label, maxed ? Style.DISABLED : ready && affordable ? Style.GOOD : Style.DISABLED, () -> {
            if (!maxed && affordable) {
                s.send(PhoneAction.BUY_ITEM, id);
            }
        });
        return h;
    }
}
