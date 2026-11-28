package com.chicoestudioso.caricraft.client.phone;

import com.chicoestudioso.caricraft.network.PhoneAction;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Racha diaria y evento semanal (tarjetas del inicio), la app Vitrina (medallas y trofeos) y la sección de jefes de
 * la app Desafío.
 */
final class RoutinePages {
    static final int BRONZE = 0xFFCD7F32;
    static final int SILVER = 0xFFC0C7CF;
    static final int GOLD = 0xFFFFC83D;
    private static final int FLAME = 0xFFFF7A1A;
    private static final int BOSS = 0xFF7B2CBF;

    private RoutinePages() {
    }

    static int tierColor(int tier, PhoneTheme t) {
        return switch (tier) {
            case 1 -> BRONZE;
            case 2 -> SILVER;
            case 3 -> GOLD;
            default -> t.card();
        };
    }

    // ================================================================ inicio

    static int homeCards(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        PhoneSnapshot.RoutineView r = snap.routine();
        if (r == null) {
            return 0;
        }
        int top = y;
        if (r.streak() != null) {
            y += streakCard(s, snap, r.streak(), x, y, w) + 5;
        }
        if (r.weekly() != null) {
            y += weeklyCard(s, snap, r.weekly(), x, y, w) + 5;
        }
        return y - top;
    }

    private static int streakCard(PhoneScreen s, PhoneSnapshot snap, PhoneSnapshot.StreakView st, int x, int y, int w) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        Component status;
        if (st.doneToday()) {
            status = Component.translatable("caricraft.phone.streak.done", st.reward());
        } else {
            status = Component.translatable(st.alive() ? "caricraft.phone.streak.todo" : "caricraft.phone.streak.todo_new", st.reward());
        }
        Component duo = null;
        if (st.duo()) {
            duo = Component.translatable(st.duoPaid() ? "caricraft.phone.streak.duo_paid"
                    : st.partnerActive() ? "caricraft.phone.streak.duo_ready" : "caricraft.phone.streak.duo_wait", st.duoBonus());
        }
        int textW = w - 12;
        int h = 6 + 10 + 9 + 2 + ui.wrappedHeight(status, textW) + (duo == null ? 0 : ui.wrappedHeight(duo, textW) + 1) + 5;
        ui.round(x, y, x + w, y + h, PhoneTheme.mix(FLAME, t.card(), st.doneToday() ? 0.78f : 0.62f));
        ui.item("minecraft:campfire", x + 4, y + 3);
        Component title = Component.translatable(st.days() == 1 ? "caricraft.phone.streak.title_one" : "caricraft.phone.streak.title",
                st.days()).copy().withStyle(ChatFormatting.BOLD);
        ui.textClipped(title, x + 23, y + 7, w - 60, t.text());
        ui.textRight(Component.translatable("caricraft.phone.streak.best", st.best()), x + w - 5, y + 7, t.textDim());
        // Semana de racha: 7 puntos
        int dotsY = y + 22;
        int gap = Math.max(4, Math.min(16, (w - 12) / 7));
        for (int i = 1; i <= 7; i++) {
            int cx = x + 6 + (i - 1) * gap;
            boolean filled = st.doneToday() ? i <= st.cycleDay() : i < st.cycleDay();
            boolean today = i == st.cycleDay();
            int color = filled ? FLAME : today ? PhoneTheme.mix(FLAME, t.card(), 0.5f) : PhoneTheme.mix(t.textDim(), t.card(), 0.6f);
            ui.g.fill(cx, dotsY, cx + gap - 3, dotsY + 5, color);
        }
        int ty = dotsY + 9;
        ty += ui.wrapped(status, x + 6, ty, textW, t.text()) + 1;
        if (duo != null) {
            ui.wrapped(duo, x + 6, ty, textW, st.duoPaid() ? t.good() : t.textDim());
        }
        return h;
    }

    private static int weeklyCard(PhoneScreen s, PhoneSnapshot snap, PhoneSnapshot.WeeklyView wk, int x, int y, int w) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        Component effect = Component.translatable("caricraft.weekly." + wk.id() + ".bonus",
                String.format(java.util.Locale.ROOT, "%.1f", wk.bonus()).replace('.', ','));
        int textW = w - 12;
        int h = 6 + 10 + 2 + ui.wrappedHeight(effect, textW) + 3 + 6 + 3 + 10 + 10 + 4;
        int accent = t.accent();
        ui.round(x, y, x + w, y + h, PhoneTheme.mix(accent, t.card(), 0.78f));
        ui.item(wk.icon(), x + 4, y + 3);
        Component left = PhoneText.remaining(wk.endsAt() - snap.serverTime());
        int leftW = ui.font.width(left);
        ui.textClipped(Component.translatable("caricraft.weekly." + wk.id()).copy().withStyle(ChatFormatting.BOLD), x + 23, y + 7,
                w - 32 - leftW, t.text());
        ui.textRight(left, x + w - 5, y + 7, t.textDim());
        int ty = y + 20;
        ty += ui.wrapped(effect, x + 6, ty, textW, t.textDim()) + 3;
        long progress = Math.min(wk.progress(), wk.goal());
        ui.progressBar(x + 6, ty, w - 12, (int) Math.min(Integer.MAX_VALUE, progress), (int) Math.min(Integer.MAX_VALUE, wk.goal()),
                (wk.paid() & 2) != 0 ? t.good() : accent);
        int half = x + 6 + (w - 12) / 2;
        ui.g.fill(half, ty - 1, half + 1, ty + 5, (wk.paid() & 1) != 0 ? t.good() : t.text());
        ty += 9;
        String goal = PhoneText.number(progress) + "/" + PhoneText.number(wk.goal());
        ui.textClipped(Component.translatable("caricraft.weekly.stat." + wk.stat(), goal), x + 6, ty, textW, t.text());
        ty += 10;
        Component halfText = Component.translatable("caricraft.phone.weekly.half", wk.halfReward());
        Component fullText = Component.translatable("caricraft.phone.weekly.full", wk.fullReward());
        ui.text(Component.literal((wk.paid() & 1) != 0 ? "✔ " : "").append(halfText), x + 6, ty, (wk.paid() & 1) != 0 ? t.good() : t.textDim());
        ui.textRight(Component.literal((wk.paid() & 2) != 0 ? "✔ " : "").append(fullText), x + w - 5, ty,
                (wk.paid() & 2) != 0 ? t.good() : t.textDim());
        return h;
    }

    // ================================================================ vitrina

    static int showcase(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int top = y;
        PhoneSnapshot.RoutineView r = snap.routine();
        if (r == null) {
            return ui.wrapped(Component.translatable("caricraft.phone.showcase.off"), x, y, w, t.textDim());
        }
        int got = r.medals().stream().mapToInt(PhoneSnapshot.MedalView::tier).sum();
        int total = r.medals().size() * 3;
        ui.round(x, y, x + w, y + 26, PhoneTheme.mix(GOLD, t.card(), 0.7f));
        ui.item("caricraft:challenge_trophy", x + 4, y + 5);
        ui.text(Component.translatable("caricraft.phone.showcase.title").copy().withStyle(ChatFormatting.BOLD), x + 23, y + 5, t.text());
        ui.text(Component.translatable("caricraft.phone.showcase.count", got, total, r.trophies().size()), x + 23, y + 15, t.textDim());
        y += 30;

        // Trofeos de jefes
        ui.text(Component.translatable("caricraft.phone.showcase.trophies").copy().withStyle(ChatFormatting.BOLD), x, y, t.text());
        y += 12;
        if (r.trophies().isEmpty()) {
            y += ui.wrapped(Component.translatable("caricraft.phone.showcase.no_trophies"), x, y, w, t.textDim()) + 4;
        } else {
            for (PhoneSnapshot.BossRecordView b : r.trophies()) {
                ui.round(x, y, x + w, y + 26, PhoneTheme.mix(BOSS, t.card(), 0.75f));
                ui.item("caricraft:challenge_trophy", x + 4, y + 5);
                ui.textClipped(Component.translatable("caricraft.boss." + b.boss()).copy().withStyle(ChatFormatting.BOLD), x + 23, y + 4,
                        w - 60, t.text());
                ui.textRight(Component.translatable("caricraft.phone.showcase.level", b.level()), x + w - 5, y + 4, t.textDim());
                ui.textClipped(Component.literal(b.players() + " · " + java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
                        .format(java.time.Instant.ofEpochMilli(b.time()).atZone(java.time.ZoneId.systemDefault()))), x + 23, y + 15, w - 28, t.textDim());
                y += 29;
            }
        }
        y += 4;
        // Medallas
        int columns = w >= 170 ? 3 : 2;
        int cell = (w - (columns - 1) * 4) / columns;
        int cellH = 50;
        List<PhoneSnapshot.MedalView> medals = r.medals();
        for (int i = 0; i < medals.size(); i++) {
            PhoneSnapshot.MedalView m = medals.get(i);
            int cx = x + (i % columns) * (cell + 4);
            int cy = y + (i / columns) * (cellH + 4);
            int color = tierColor(m.tier(), t);
            ui.round(cx, cy, cx + cell, cy + cellH, m.tier() == 0 ? t.card() : PhoneTheme.mix(color, t.card(), 0.55f));
            ui.round(cx + cell / 2 - 10, cy + 3, cx + cell / 2 + 10, cy + 23, m.tier() == 0 ? PhoneTheme.mix(t.textDim(), t.card(), 0.7f) : color);
            ui.item(m.icon(), cx + cell / 2 - 8, cy + 5);
            ui.textFit(Component.translatable("caricraft.medal." + m.id()), cx + cell / 2, cy + 26, cell - 4, t.text());
            String line;
            if (m.tier() >= 3) {
                line = "★★★";
            } else {
                long next = m.tiers().get(m.tier());
                line = PhoneText.number(Math.min(m.value(), next)) + "/" + PhoneText.number(next);
            }
            ui.textFit(Component.literal(line), cx + cell / 2, cy + 37, cell - 4, m.tier() >= 3 ? GOLD : t.textDim());
        }
        y += ((medals.size() + columns - 1) / columns) * (cellH + 4) + 4;
        y += ui.wrapped(Component.translatable("caricraft.phone.showcase.help"), x, y, w, t.textDim()) + 6;

        return y - top;
    }

    // ================================================================ jefes (app Desafío)

    static int bosses(PhoneScreen s, PhoneSnapshot.ChallengeView c, int x, int y, int w) {
        PhoneSnapshot.BossesView b = c.bosses();
        if (b == null) {
            return 0;
        }
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int top = y;
        ui.text(Component.translatable("caricraft.phone.boss.title").copy().withStyle(ChatFormatting.BOLD), x, y, t.text());
        ui.textRight(Component.translatable("caricraft.phone.boss.trophies", b.defeated().size()), x + w, y, t.textDim());
        y += 12;
        Component title;
        Component sub;
        int bg;
        if (b.fight() != null) {
            title = Component.translatable("caricraft.phone.boss.fight", Component.translatable("caricraft.boss." + b.fight().boss()));
            sub = Component.translatable("caricraft.phone.boss.fight_sub", Math.round(b.fight().health()), Math.round(b.fight().maxHealth()));
            bg = BOSS;
        } else if (b.pendingLevel() > 0) {
            title = Component.translatable("caricraft.phone.boss.pending", Component.translatable("caricraft.boss." + b.pendingBoss()), b.pendingLevel());
            sub = Component.translatable("caricraft.phone.boss.pending_sub", b.reward());
            bg = PhoneTheme.mix(BOSS, t.card(), 0.4f);
        } else if (b.nextLevel() > 0) {
            title = Component.translatable("caricraft.phone.boss.next", Component.translatable("caricraft.boss." + b.nextBoss()), b.nextLevel());
            sub = Component.translatable("caricraft.phone.boss.next_sub", b.every());
            bg = PhoneTheme.mix(BOSS, t.card(), 0.75f);
        } else {
            title = Component.translatable("caricraft.phone.boss.all");
            sub = Component.empty();
            bg = PhoneTheme.mix(GOLD, t.card(), 0.6f);
        }
        int textW = w - 30;
        Component boldTitle = title.copy().withStyle(ChatFormatting.BOLD);
        int h = 5 + ui.wrappedHeight(boldTitle, textW) + 2 + ui.wrappedHeight(sub, textW) + (b.fight() != null ? 8 : 0) + 5;
        ui.round(x, y, x + w, y + h, bg);
        ui.item("caricraft:challenge_trophy", x + 4, y + (h - 16) / 2);
        int ty = y + 5;
        ty += ui.wrapped(boldTitle, x + 24, ty, textW, 0xFFFFFFFF) + 2;
        ty += ui.wrapped(sub, x + 24, ty, textW, 0xFFE9DDF5);
        if (b.fight() != null) {
            ui.progressBar(x + 24, ty + 2, textW, Math.round(b.fight().health()), Math.max(1, Math.round(b.fight().maxHealth())), 0xFFE5484D);
        }
        y += h + 6;
        return y - top;
    }

    // ================================================================ hucha del dúo

    private static final long[] AMOUNTS = {10, 50, 100, 500};
    private static final int POT = 0xFFE76F51;

    static int savings(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int top = y;
        PhoneSnapshot.SavingsView sv = snap.savings();
        if (sv == null) {
            return ui.wrapped(Component.translatable("caricraft.phone.savings.off"), x, y, w, t.textDim());
        }
        // Tarjeta principal
        int h = 46;
        ui.round(x, y, x + w, y + h, PhoneTheme.mix(POT, t.card(), 0.35f));
        ui.g.pose().pushPose();
        ui.g.pose().translate(x + 6, y + 7, 0);
        ui.g.pose().scale(2f, 2f, 1f);
        ui.item("minecraft:decorated_pot", 0, 0);
        ui.g.pose().popPose();
        ui.text(Component.translatable("caricraft.phone.savings.balance").copy().withStyle(ChatFormatting.BOLD), x + 44, y + 7, 0xFFFFFFFF);
        ui.text(Component.literal(PhoneText.number(sv.balance()) + " C").withStyle(ChatFormatting.BOLD), x + 44, y + 19, 0xFFFFF1D6);
        ui.text(Component.translatable("caricraft.phone.savings.tomorrow", sv.preview()), x + 44, y + 31, 0xFFFFE3D3);
        y += h + 5;
        String rate = String.format(java.util.Locale.ROOT, "%.1f", sv.dailyRate() * 100).replace('.', ',').replace(",0", "");
        y += ui.wrapped(Component.translatable("caricraft.phone.savings.help", rate, sv.dailyCap()), x, y, w, t.textDim()) + 3;
        if (sv.totalInterest() > 0) {
            y += ui.wrapped(Component.translatable("caricraft.phone.savings.earned", PhoneText.number(sv.totalInterest()), sv.lastInterest()),
                    x, y, w, t.good()) + 3;
        }
        y += 4;
        // Meter
        ui.text(Component.translatable("caricraft.phone.savings.deposit", PhoneText.number(snap.balance())).copy().withStyle(ChatFormatting.BOLD),
                x, y, t.text());
        y += 12;
        y += amountButtons(s, x, y, w, PhoneAction.SAVINGS_DEPOSIT, snap.balance()) + 6;
        // Sacar
        ui.text(Component.translatable("caricraft.phone.savings.withdraw").copy().withStyle(ChatFormatting.BOLD), x, y, t.text());
        y += 12;
        y += amountButtons(s, x, y, w, PhoneAction.SAVINGS_WITHDRAW, sv.balance()) + 6;
        return y - top;
    }

    private static int amountButtons(PhoneScreen s, int x, int y, int w, PhoneAction action, long available) {
        PhoneUi ui = s.ui;
        int columns = 5;
        int gap = 3;
        int bw = (w - gap * (columns - 1)) / columns;
        for (int i = 0; i < columns; i++) {
            boolean all = i == AMOUNTS.length;
            long amount = all ? available : AMOUNTS[i];
            Component label = all ? Component.translatable("caricraft.phone.savings.all") : Component.literal(String.valueOf(AMOUNTS[i]));
            boolean ready = available > 0 && (all || available >= amount);
            String arg = all ? "all" : String.valueOf(amount);
            ui.button(x + i * (bw + gap), y, bw, 14, label, ready ? (action == PhoneAction.SAVINGS_DEPOSIT ? PhoneUi.Style.PRIMARY
                    : PhoneUi.Style.SECONDARY) : PhoneUi.Style.DISABLED, () -> {
                if (ready) {
                    s.send(action, arg);
                }
            });
        }
        return 14;
    }
}
