package com.chicoestudioso.caricraft.client.phone;

import com.chicoestudioso.caricraft.client.CaricraftClient;
import com.chicoestudioso.caricraft.network.PhoneAction;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.AppView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.ChronicleView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.InviteView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.LedgerView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.MemberView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.MissionView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.ObjectiveView;
import com.chicoestudioso.caricraft.client.phone.PhoneUi.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Dibujo de cada página del CariPhone. Devuelve siempre la altura usada. */
final class PhonePages {
    private static final int GAP = 4;
    private static final int BUTTON_H = 13;
    private static final List<String> THEMES = List.of("classic", "theme_sunset", "theme_ocean", "theme_forest");

    private PhonePages() {
    }

    // ================================================================ zona fija

    static int renderFixed(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        if (s.page == PhonePage.DUO && canInvite(snap)) {
            return inputRow(s, x, y, w, Component.translatable("caricraft.phone.duo.invite"));
        }
        if (s.page == PhonePage.CHRONICLES && hasApp(snap, "chronicles")) {
            return inputRow(s, x, y, w, Component.translatable("caricraft.phone.chronicles.add"));
        }
        if (s.page == PhonePage.CARILENS && com.chicoestudioso.caricraft.client.ClientPhotos.hasPending()) {
            return inputRow(s, x, y, w, Component.translatable("caricraft.phone.lens.save"));
        }
        return 0;
    }

    private static int inputRow(PhoneScreen s, int x, int y, int w, Component label) {
        PhoneUi ui = s.ui;
        int buttonWidth = ui.buttonWidth(label);
        s.placeInput(x, y, w - buttonWidth - 4);
        ui.button(x + w - buttonWidth, y, buttonWidth, 14, label, Style.PRIMARY, s::submitInput);
        return 14;
    }

    // ================================================================ despacho

    static int render(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        return switch (s.page) {
            case HOME -> home(s, snap, x, y, w);
            case MISSIONS -> missions(s, snap, x, y, w);
            case WALLET -> wallet(s, snap, x, y, w);
            case DUO -> duo(s, snap, x, y, w);
            case SHOP -> shop(s, snap, x, y, w);
            case SETTINGS -> settings(s, snap, x, y, w);
            case CHRONICLES -> hasApp(snap, "chronicles") ? chronicles(s, snap, x, y, w) : locked(s, snap, x, y, w, "chronicles");
            case EXPLORER -> hasApp(snap, "explorer") ? explorer(s, snap, x, y, w) : locked(s, snap, x, y, w, "explorer");
            case STATS -> hasApp(snap, "stats") ? stats(s, snap, x, y, w) : locked(s, snap, x, y, w, "stats");
            case CHALLENGE -> challenge(s, snap, x, y, w);
            case MARKET -> market(s, snap, x, y, w);
            case CARICAMP -> hasApp(snap, "caricamp") ? caricamp(s, snap, x, y, w) : locked(s, snap, x, y, w, "caricamp");
            case CARILENS -> hasApp(snap, "carilens") ? carilens(s, snap, x, y, w) : locked(s, snap, x, y, w, "carilens");
            case LOCKED -> locked(s, snap, x, y, w, s.lockedAppId);
        };
    }

    // ================================================================ inicio

    private static int home(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int top = y;

        // Tarjeta de bienvenida
        MemberView self = snap.party().members().stream().filter(MemberView::self).findFirst().orElse(null);
        Component hello = Component.translatable("caricraft.phone.home.hello", self == null ? "" : self.name());
        Component duoLine;
        if (snap.party().duo()) {
            String partner = snap.party().members().stream().filter(m -> !m.self()).map(MemberView::name).findFirst().orElse("?");
            duoLine = Component.translatable("caricraft.phone.home.duo_with", partner);
        } else {
            duoLine = Component.translatable("caricraft.phone.home.solo");
        }
        int textWidth = w - 12;
        int cardHeight = 8 + ui.font.lineHeight + 2 + ui.wrappedHeight(duoLine, textWidth) + 4;
        ui.round(x, y, x + w, y + cardHeight, t.header());
        ui.text(hello.copy().withStyle(ChatFormatting.BOLD), x + 6, y + 5, t.headerText());
        ui.wrapped(duoLine, x + 6, y + 5 + ui.font.lineHeight + 2, textWidth, PhoneTheme.mix(t.headerText(), t.header(), 0.2f));
        y += cardHeight + 8;

        // Cuadrícula de apps
        List<AppView> apps = snap.apps().stream().filter(a -> "APP".equals(a.kind())).toList();
        int columns = w >= 200 ? 4 : 3;
        int cell = w / columns;
        int cellHeight = 54;
        long claimable = snap.missions().stream().filter(m -> "COMPLETED".equals(m.status())).count();
        for (int i = 0; i < apps.size(); i++) {
            AppView app = apps.get(i);
            int cx = x + (i % columns) * cell;
            int cy = y + (i / columns) * cellHeight;
            int iconX = cx + (cell - 28) / 2;
            boolean usable = "BUILTIN".equals(app.state()) || "OWNED".equals(app.state());
            boolean hover = ui.hovered(cx, cy, cx + cell, cy + cellHeight);
            int bg = usable ? app.color() : PhoneTheme.mix(app.color(), t.track(), 0.65f);
            if (hover) {
                bg = PhoneTheme.mix(bg, 0xFFFFFFFF, 0.2f);
            }
            ui.round(iconX, cy + 2, iconX + 28, cy + 30, bg);
            ui.item(app.icon(), iconX + 6, cy + 8);
            ui.textFit(PhoneText.appName(app.id()), cx + cell / 2, cy + 33, cell - 2, usable ? t.text() : t.textDim());
            if ("COMING_SOON".equals(app.state())) {
                Component soon = Component.translatable("caricraft.phone.soon");
                ui.textCentered(soon, cx + cell / 2, cy + 42 - 1, t.textDim());
            } else if (!usable) {
                String price = PhoneText.number(app.price());
                int pw = ui.font.width(price) + 9;
                int px = cx + (cell - pw) / 2;
                s.drawCoin(px, cy + 42);
                ui.text(price, px + 9, cy + 42, t.warn());
            }
            int badge = switch (app.id()) {
                case "missions" -> (int) claimable;
                case "duo" -> snap.incomingInvites().size();
                default -> 0;
            };
            if (badge > 0) {
                String b = String.valueOf(badge);
                int bw = Math.max(9, ui.font.width(b) + 4);
                ui.round(iconX + 28 - bw + 3, cy, iconX + 31, cy + 10, t.bad());
                ui.text(b, iconX + 31 - bw + (bw - ui.font.width(b)) / 2, cy + 1, 0xFFFFFFFF);
            }
            ui.hit(cx, cy, cx + cell, cy + cellHeight, () -> s.openApp(app.id(), app.state()));
        }
        y += ((apps.size() + columns - 1) / columns) * cellHeight;
        return y - top;
    }

    // ================================================================ misiones

    private static int missions(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int top = y;
        List<List<MissionView>> tabs = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (MissionView m : snap.missions()) {
            boolean milestone = "MILESTONE".equals(m.category());
            switch (m.status()) {
                case "ACCEPTED", "COMPLETED" -> tabs.get(0).add(m);
                case "CLAIMED" -> tabs.get(2).add(m);
                default -> (milestone && "AVAILABLE".equals(m.status()) ? tabs.get(0) : tabs.get(1)).add(m);
            }
        }
        String[] keys = {"caricraft.phone.missions.active", "caricraft.phone.missions.available", "caricraft.phone.missions.done"};
        int tabWidth = (w - 2 * GAP) / 3;
        boolean anyClaimable = tabs.get(0).stream().anyMatch(m -> "COMPLETED".equals(m.status()));
        for (int i = 0; i < 3; i++) {
            int index = i;
            int bx = x + i * (tabWidth + GAP);
            ui.button(bx, y, tabWidth, BUTTON_H, Component.translatable(keys[i]),
                    s.missionTab == i ? Style.PRIMARY : Style.SECONDARY, () -> s.missionTab = index);
            if (i == 0 && anyClaimable) {
                ui.round(bx + tabWidth - 5, y - 2, bx + tabWidth + 1, y + 4, t.bad());
            }
        }
        y += BUTTON_H + 6;

        List<MissionView> list = new ArrayList<>(tabs.get(s.missionTab));
        list.sort(Comparator.comparingInt((MissionView m) -> statusOrder(m.status())).thenComparingInt(m -> categoryOrder(m.category())));
        if (list.isEmpty()) {
            y += ui.wrapped(Component.translatable("caricraft.phone.missions.empty." + s.missionTab), x, y + 6, w, t.textDim()) + 6;
            return y - top;
        }
        for (MissionView mission : list) {
            int h = missionCard(s, snap, mission, x, y, w, false);
            boolean claimable = "COMPLETED".equals(mission.status());
            ui.round(x, y, x + w, y + h, claimable ? PhoneTheme.mix(t.card(), t.good(), 0.12f) : t.card());
            if (claimable) {
                ui.g.fill(x, y + 2, x + 2, y + h - 2, t.good());
            }
            missionCard(s, snap, mission, x, y, w, true);
            y += h + GAP;
        }
        return y - top;
    }

    private static int statusOrder(String status) {
        return switch (status) {
            case "COMPLETED" -> 0;
            case "ACCEPTED" -> 1;
            case "AVAILABLE" -> 2;
            case "LOCKED" -> 3;
            default -> 4;
        };
    }

    private static int categoryOrder(String category) {
        return switch (category) {
            case "DAILY" -> 0;
            case "WEEKLY" -> 1;
            case "STORY" -> 2;
            default -> 3;
        };
    }

    /** Dibuja (o mide, si {@code draw} es falso) la tarjeta de una misión. */
    private static int missionCard(PhoneScreen s, PhoneSnapshot snap, MissionView m, int x, int y, int w, boolean draw) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int pad = 5;
        int innerX = x + pad;
        int innerW = w - 2 * pad;
        int cy = y + pad;

        // Fila de título: icono, nombre y recompensa
        String reward = "+" + PhoneText.number(m.reward());
        int rewardWidth = ui.font.width(reward) + 9;
        if (draw) {
            ui.item(missionIcon(m), innerX, cy - 1);
            ui.textClipped(PhoneText.missionName(m).copy().withStyle(ChatFormatting.BOLD), innerX + 19, cy + 3,
                    innerW - 19 - rewardWidth - 3, t.text());
            s.drawCoin(innerX + innerW - rewardWidth, cy + 3);
            ui.text(reward, innerX + innerW - rewardWidth + 9, cy + 3, t.warn());
        }
        cy += 17;

        // Chapas
        List<Component> chips = new ArrayList<>();
        if (!"STORY".equals(m.category())) {
            chips.add(Component.translatable("caricraft.phone.category." + m.category().toLowerCase()));
        }
        if (!"INDIVIDUAL".equals(m.scope())) {
            chips.add(Component.translatable("caricraft.phone.scope." + m.scope().toLowerCase()));
        }
        if (m.requireAllMembers()) {
            chips.add(Component.translatable("caricraft.phone.missions.both"));
        }
        if (!chips.isEmpty()) {
            int chipX = innerX;
            for (Component chip : chips) {
                int cw = ui.font.width(chip) + 6;
                if (chipX + cw > innerX + innerW) {
                    break;
                }
                if (draw) {
                    ui.chip(chip, chipX, cy, t.track(), t.text());
                }
                chipX += cw + 3;
            }
            cy += ui.font.lineHeight + 6;
        }

        Component description = PhoneText.missionDescription(m);
        if (!description.getString().isEmpty()) {
            cy += draw ? ui.wrapped(description, innerX, cy, innerW, t.textDim()) : ui.wrappedHeight(description, innerW);
            cy += 2;
        }

        // Objetivos
        boolean showProgress = !"AVAILABLE".equals(m.status()) && !"LOCKED".equals(m.status()) || "MILESTONE".equals(m.category());
        for (ObjectiveView objective : m.objectives()) {
            Component text = PhoneText.objective(objective);
            String counter = showProgress ? PhoneText.number(objective.progress()) + "/" + PhoneText.number(objective.required()) : "";
            int counterWidth = counter.isEmpty() ? 0 : ui.font.width(counter) + 4;
            int textHeight = ui.wrappedHeight(text, innerW - counterWidth);
            if (draw) {
                ui.wrapped(text, innerX, cy, innerW - counterWidth, t.text());
                if (!counter.isEmpty()) {
                    ui.text(counter, innerX + innerW - counterWidth + 4, cy, t.textDim());
                }
            }
            cy += textHeight;
            if (showProgress) {
                if (draw) {
                    boolean full = objective.progress() >= objective.required();
                    ui.progressBar(innerX, cy, innerW, objective.progress(), objective.required(), full ? t.good() : t.accent());
                }
                cy += 7;
            }
        }

        // Contribuciones del dúo
        if (!m.contributions().isEmpty() && !"INDIVIDUAL".equals(m.scope())) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Long> entry : m.contributions().entrySet()) {
                if (!sb.isEmpty()) {
                    sb.append(" · ");
                }
                sb.append(entry.getKey()).append(' ').append(PhoneText.number(entry.getValue()));
            }
            Component line = Component.translatable("caricraft.phone.missions.contributions", sb.toString());
            cy += draw ? ui.wrapped(line, innerX, cy, innerW, t.textDim()) : ui.wrappedHeight(line, innerW);
        }

        // Caducidad / bloqueo
        if (m.expiresAt() > 0 && !"CLAIMED".equals(m.status())) {
            Component line = Component.translatable("caricraft.phone.missions.expires", PhoneText.remaining(m.expiresAt() - snap.serverTime()));
            if (draw) {
                ui.text(line, innerX, cy, t.textDim());
            }
            cy += ui.font.lineHeight + 1;
        }
        if ("LOCKED".equals(m.status())) {
            boolean needsDuo = "COOP".equals(m.scope()) && !snap.party().duo();
            Component line = Component.translatable(needsDuo ? "caricraft.phone.missions.locked_duo" : "caricraft.phone.missions.locked_previous");
            cy += draw ? ui.wrapped(line, innerX, cy, innerW, t.bad()) : ui.wrappedHeight(line, innerW);
        }

        // Botones
        List<Object[]> buttons = new ArrayList<>();
        boolean milestone = "MILESTONE".equals(m.category());
        switch (m.status()) {
            case "AVAILABLE" -> {
                if (!milestone) {
                    buttons.add(new Object[]{Component.translatable("caricraft.phone.missions.accept"), Style.PRIMARY, PhoneAction.ACCEPT_MISSION});
                }
            }
            case "ACCEPTED" -> {
                if (m.objectives().stream().anyMatch(o -> "DELIVER_ITEM".equals(o.type()) && o.progress() < o.required())) {
                    buttons.add(new Object[]{Component.translatable("caricraft.phone.missions.deliver"), Style.PRIMARY, PhoneAction.DELIVER_MISSION});
                }
                if (!milestone) {
                    buttons.add(new Object[]{Component.translatable("caricraft.phone.missions.abandon"), Style.SECONDARY, PhoneAction.ABANDON_MISSION});
                }
            }
            case "COMPLETED" -> buttons.add(new Object[]{Component.translatable("caricraft.phone.missions.claim", PhoneText.number(m.reward())), Style.GOOD, PhoneAction.CLAIM_MISSION});
            default -> {
            }
        }
        if (!buttons.isEmpty()) {
            cy += 3;
            int bx = innerX + innerW;
            for (Object[] button : buttons) {
                Component label = (Component) button[0];
                int bw = ui.buttonWidth(label);
                bx -= bw;
                if (draw) {
                    PhoneAction action = (PhoneAction) button[2];
                    ui.button(bx, cy, bw, BUTTON_H, label, (Style) button[1], () -> s.send(action, m.id()));
                }
                bx -= GAP;
            }
            cy += BUTTON_H;
        }
        return cy - y + pad;
    }

    private static String missionIcon(MissionView m) {
        // El icono no viaja en el snapshot: se deduce del primer objetivo.
        if (m.objectives().isEmpty()) {
            return "minecraft:paper";
        }
        ObjectiveView first = m.objectives().getFirst();
        String target = first.target();
        if (target != null && !target.isEmpty() && !target.startsWith("#") && target.contains(":")
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.tryParse(target))) {
            return target;
        }
        return switch (first.type()) {
            case "TRAVEL" -> "minecraft:leather_boots";
            case "VISIT_BIOME" -> "minecraft:map";
            case "DISCOVER_BIOME" -> "minecraft:filled_map";
            case "MINE_BLOCK" -> "minecraft:iron_pickaxe";
            case "CRAFT_ITEM" -> "minecraft:crafting_table";
            case "KILL_ENTITY" -> "minecraft:iron_sword";
            case "DELIVER_ITEM" -> "minecraft:barrel";
            case "CATCH_POKEMON" -> "cobblemon:poke_ball";
            case "EVOLVE_POKEMON" -> "cobblemon:fire_stone";
            case "WIN_BATTLE" -> "minecraft:golden_sword";
            default -> "minecraft:paper";
        };
    }

    // ================================================================ cartera

    private static int wallet(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int top = y;
        ui.round(x, y, x + w, y + 40, t.header());
        Component label = Component.translatable(snap.party().duo() ? "caricraft.phone.wallet.duo_balance" : "caricraft.phone.wallet.balance");
        ui.text(label, x + 6, y + 5, PhoneTheme.mix(t.headerText(), t.header(), 0.2f));
        String balance = PhoneText.number(snap.balance());
        ui.g.pose().pushPose();
        ui.g.pose().translate(x + 6, y + 17, 0);
        ui.g.pose().scale(2f, 2f, 1f);
        s.drawCoin(0, 1);
        ui.text(balance, 10, 0, 0xFFFFE08A);
        ui.g.pose().popPose();
        ui.textRight(Component.literal("Carins"), x + w - 6, y + 28, t.headerText());
        y += 46;

        ui.text(Component.translatable("caricraft.phone.wallet.history").copy().withStyle(ChatFormatting.BOLD), x, y, t.text());
        y += 12;
        if (snap.ledger().isEmpty()) {
            y += ui.wrapped(Component.translatable("caricraft.phone.wallet.empty"), x, y, w, t.textDim());
        }
        for (LedgerView entry : snap.ledger()) {
            String delta = (entry.delta() >= 0 ? "+" : "") + PhoneText.number(entry.delta());
            int deltaWidth = ui.font.width(delta);
            Component reason = PhoneText.format(snap, "caricraft.ledger." + entry.reason(),
                    List.of(entry.detail() == null ? "" : entry.detail()));
            ui.round(x, y, x + w, y + 22, t.card());
            ui.textClipped(reason, x + 5, y + 3, w - deltaWidth - 14, t.text());
            ui.text(PhoneText.dateTime(entry.time()), x + 5, y + 12, t.textDim());
            ui.text(delta, x + w - 5 - deltaWidth, y + 7, entry.delta() >= 0 ? t.good() : t.bad());
            y += 22 + 3;
        }
        return y - top;
    }

    // ================================================================ dúo

    private static boolean canInvite(PhoneSnapshot snap) {
        return snap.party().members().size() < snap.party().maxSize() && snap.party().cooldownEndsAt() <= snap.serverTime();
    }

    private static int duo(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int top = y;

        ui.text(Component.translatable("caricraft.phone.duo.members").copy().withStyle(ChatFormatting.BOLD), x, y, t.text());
        y += 12;
        for (MemberView member : snap.party().members()) {
            ui.round(x, y, x + w, y + 18, t.card());
            ui.round(x + 5, y + 6, x + 11, y + 12, member.online() ? t.good() : t.track());
            Component name = member.self()
                    ? Component.translatable("caricraft.phone.duo.you", member.name()) : Component.literal(member.name());
            ui.textClipped(name, x + 15, y + 5, w - 20, t.text());
            y += 18 + 3;
        }
        y += 3;
        if (!snap.party().duo()) {
            y += ui.wrapped(Component.translatable("caricraft.phone.duo.solo_help"), x, y, w, t.textDim()) + 4;
        }
        if (snap.party().cooldownEndsAt() > snap.serverTime()) {
            y += ui.wrapped(Component.translatable("caricraft.phone.duo.cooldown",
                    PhoneText.remaining(snap.party().cooldownEndsAt() - snap.serverTime())), x, y, w, t.warn()) + 4;
        }

        if (!snap.incomingInvites().isEmpty()) {
            ui.text(Component.translatable("caricraft.phone.duo.incoming").copy().withStyle(ChatFormatting.BOLD), x, y, t.text());
            y += 12;
            for (InviteView invite : snap.incomingInvites()) {
                ui.round(x, y, x + w, y + 34, PhoneTheme.mix(t.card(), t.accent(), 0.12f));
                ui.textClipped(Component.translatable("caricraft.phone.duo.invited_by", invite.name()), x + 5, y + 4, w - 10, t.text());
                Component accept = Component.translatable("caricraft.phone.duo.accept");
                Component decline = Component.translatable("caricraft.phone.duo.decline");
                int aw = ui.buttonWidth(accept);
                int dw = ui.buttonWidth(decline);
                ui.button(x + w - 5 - aw, y + 16, aw, BUTTON_H, accept, Style.GOOD, () -> s.send(PhoneAction.ACCEPT_INVITE, invite.uuid()));
                ui.button(x + w - 5 - aw - GAP - dw, y + 16, dw, BUTTON_H, decline, Style.SECONDARY, () -> s.send(PhoneAction.DECLINE_INVITE, invite.uuid()));
                y += 34 + 3;
            }
            y += 3;
        }
        if (!snap.outgoingInvites().isEmpty()) {
            ui.text(Component.translatable("caricraft.phone.duo.outgoing").copy().withStyle(ChatFormatting.BOLD), x, y, t.text());
            y += 12;
            for (InviteView invite : snap.outgoingInvites()) {
                Component cancel = Component.translatable("caricraft.phone.duo.cancel");
                int cw = ui.buttonWidth(cancel);
                ui.round(x, y, x + w, y + 19, t.card());
                ui.textClipped(Component.literal(invite.name()), x + 5, y + 6, w - cw - 14, t.text());
                ui.button(x + w - 4 - cw, y + 3, cw, BUTTON_H, cancel, Style.SECONDARY, () -> s.send(PhoneAction.CANCEL_INVITE, invite.uuid()));
                y += 19 + 3;
            }
            y += 3;
        }

        if (snap.party().duo()) {
            y += 4;
            if (snap.leavePending()) {
                y += ui.wrapped(Component.translatable("caricraft.phone.duo.leave_warning"), x, y, w, t.bad()) + 3;
                Component confirm = Component.translatable("caricraft.phone.duo.leave_confirm");
                ui.button(x, y, w, BUTTON_H + 2, confirm, Style.DANGER, () -> s.send(PhoneAction.CONFIRM_LEAVE_PARTY, ""));
            } else {
                Component leave = Component.translatable("caricraft.phone.duo.leave");
                ui.button(x, y, w, BUTTON_H + 2, leave, Style.SECONDARY, () -> s.send(PhoneAction.LEAVE_PARTY, ""));
            }
            y += BUTTON_H + 2;
        }
        return y - top;
    }

    // ================================================================ tienda

    private static int shop(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int top = y;
        y += ui.wrapped(Component.translatable("caricraft.phone.shop.intro"), x, y, w, t.textDim()) + 6;
        for (String kind : List.of("APP", "UPGRADE", "COSMETIC")) {
            ui.text(Component.translatable("caricraft.phone.shop.section." + kind.toLowerCase()).copy().withStyle(ChatFormatting.BOLD), x, y, t.text());
            y += 12;
            for (AppView item : snap.apps()) {
                if (!kind.equals(item.kind()) || "BUILTIN".equals(item.state())) {
                    continue;
                }
                int h = shopCard(s, snap, item, x, y, w, false);
                ui.round(x, y, x + w, y + h, t.card());
                shopCard(s, snap, item, x, y, w, true);
                y += h + GAP;
            }
            y += 4;
        }
        return y - top;
    }

    private static int shopCard(PhoneScreen s, PhoneSnapshot snap, AppView item, int x, int y, int w, boolean draw) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int pad = 5;
        int innerX = x + pad;
        int innerW = w - 2 * pad;
        int cy = y + pad;
        String price = PhoneText.number(item.price());
        int priceWidth = ui.font.width(price) + 9;
        if (draw) {
            ui.round(innerX, cy - 1, innerX + 18, cy + 17, item.color());
            ui.item(item.icon(), innerX + 1, cy);
            ui.textClipped(PhoneText.appName(item.id()).copy().withStyle(ChatFormatting.BOLD), innerX + 22, cy + 4,
                    innerW - 22 - priceWidth - 3, t.text());
            if (!"OWNED".equals(item.state())) {
                s.drawCoin(innerX + innerW - priceWidth, cy + 4);
                ui.text(price, innerX + innerW - priceWidth + 9, cy + 4, t.warn());
            }
        }
        cy += 20;
        if (item.maxLevel() > 1) {
            Component level = Component.translatable("caricraft.phone.shop.level", item.level(), item.maxLevel());
            if (draw) {
                ui.chip(level, innerX, cy - 1, PhoneTheme.mix(item.color(), t.card(), 0.7f), t.text());
            }
            cy += ui.font.lineHeight + 5;
        }
        Component description = PhoneText.appDescription(item.id());
        cy += draw ? ui.wrapped(description, innerX, cy, innerW, t.textDim()) : ui.wrappedHeight(description, innerW);
        cy += 3;
        switch (item.state()) {
            case "OWNED" -> {
                if (draw) {
                    ui.chip(Component.translatable("COSMETIC".equals(item.kind()) ? "caricraft.phone.shop.owned_personal"
                                    : "UPGRADE".equals(item.kind()) ? "caricraft.phone.shop.maxed" : "caricraft.phone.shop.owned"),
                            innerX, cy, t.good(), 0xFFFFFFFF);
                }
                cy += ui.font.lineHeight + 3;
            }
            case "COMING_SOON" -> {
                if (draw) {
                    ui.chip(Component.translatable("caricraft.phone.soon"), innerX, cy, t.track(), t.textDim());
                }
                cy += ui.font.lineHeight + 3;
            }
            case "LOCKED" -> {
                if (draw) {
                    ui.chip(Component.translatable("caricraft.phone.shop.requirements"), innerX, cy, t.track(), t.bad());
                }
                cy += ui.font.lineHeight + 3;
            }
            default -> {
                boolean affordable = snap.balance() >= item.price();
                Component label = affordable ? Component.translatable(item.maxLevel() > 1 ? "caricraft.phone.shop.upgrade" : "caricraft.phone.shop.buy")
                        : Component.translatable("caricraft.phone.shop.missing", PhoneText.number(item.price() - snap.balance()));
                if (draw) {
                    int bw = ui.buttonWidth(label);
                    ui.button(innerX + innerW - bw, cy, bw, BUTTON_H, label, affordable ? Style.PRIMARY : Style.DISABLED,
                            () -> s.send(PhoneAction.BUY_ITEM, item.id()));
                }
                cy += BUTTON_H;
            }
        }
        return cy - y + pad;
    }

    // ================================================================ ajustes

    private static int settings(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int top = y;
        ui.text(Component.translatable("caricraft.phone.settings.theme").copy().withStyle(ChatFormatting.BOLD), x, y, t.text());
        y += 12;
        for (String theme : THEMES) {
            boolean owned = "classic".equals(theme) || snap.ownedCosmetics().contains(theme);
            boolean current = theme.equals(snap.theme()) || ("classic".equals(theme) && !THEMES.contains(snap.theme()));
            PhoneTheme preview = PhoneTheme.byId(theme);
            ui.round(x, y, x + w, y + 22, t.card());
            ui.round(x + 4, y + 4, x + 18, y + 18, preview.header());
            ui.g.fill(x + 11, y + 4, x + 18, y + 18, preview.accent());
            Component name = Component.translatable("caricraft.theme." + theme);
            Component action;
            Style style;
            if (current) {
                action = Component.translatable("caricraft.phone.settings.in_use");
                style = Style.DISABLED;
            } else if (owned) {
                action = Component.translatable("caricraft.phone.settings.use");
                style = Style.PRIMARY;
            } else {
                action = Component.translatable("caricraft.phone.settings.in_shop");
                style = Style.SECONDARY;
            }
            int bw = ui.buttonWidth(action);
            ui.textClipped(name, x + 23, y + 7, w - bw - 32, t.text());
            ui.button(x + w - 4 - bw, y + 4, bw, BUTTON_H + 1, action, style,
                    owned ? () -> s.send(PhoneAction.SELECT_THEME, theme) : () -> s.navigate(PhonePage.SHOP));
            y += 22 + 3;
        }
        y += 8;
        ui.text(Component.translatable("caricraft.phone.settings.about").copy().withStyle(ChatFormatting.BOLD), x, y, t.text());
        y += 12;
        Component key = CaricraftClient.openPhoneKey().getTranslatedKeyMessage();
        y += ui.wrapped(Component.translatable("caricraft.phone.settings.key", key), x, y, w, t.textDim()) + 2;
        y += ui.wrapped(Component.translatable("caricraft.phone.settings.server_authority"), x, y, w, t.textDim());
        return y - top;
    }

    // ================================================================ crónicas

    private static int chronicles(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int top = y;
        if (snap.chronicle().isEmpty()) {
            return ui.wrapped(Component.translatable("caricraft.phone.chronicles.empty"), x, y, w, t.textDim());
        }
        for (ChronicleView entry : snap.chronicle()) {
            Component text = PhoneText.chronicle(snap, entry);
            boolean note = "note".equals(entry.type());
            int textHeight = ui.wrappedHeight(text, w - 12);
            int h = textHeight + 16;
            ui.round(x, y, x + w, y + h, note ? PhoneTheme.mix(t.card(), t.accent(), 0.10f) : t.card());
            ui.g.fill(x, y + 2, x + 2, y + h - 2, note ? t.accent() : t.header());
            ui.text(PhoneText.dateTime(entry.time()), x + 6, y + 3, t.textDim());
            ui.wrapped(text, x + 6, y + 13, w - 12, t.text());
            y += h + 3;
        }
        return y - top;
    }

    // ================================================================ exploración

    private static int explorer(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int top = y;
        PhoneSnapshot.ExplorationView exploration = snap.exploration();
        ui.round(x, y, x + w, y + 34, t.header());
        ui.text(Component.translatable("caricraft.phone.explorer.discovered", exploration.discovered(), exploration.total()),
                x + 6, y + 6, t.headerText());
        ui.progressBar(x + 6, y + 20, w - 12, exploration.discovered(), Math.max(1, exploration.total()), t.accent());
        y += 40;
        if (exploration.biomes().isEmpty()) {
            return y - top + ui.wrapped(Component.translatable("caricraft.phone.explorer.empty"), x, y, w, t.textDim());
        }
        for (String biome : exploration.biomes()) {
            ui.round(x, y, x + w, y + 14, t.card());
            ui.round(x + 4, y + 4, x + 10, y + 10, t.good());
            ui.textClipped(PhoneText.idName(biome), x + 14, y + 3, w - 18, t.text());
            y += 14 + 2;
        }
        return y - top;
    }

    // ================================================================ estadísticas

    private static final List<String> STAT_ORDER = List.of("distance_m", "biomes_discovered", "pokemon_caught",
            "pokemon_evolved", "battles_won", "shiny_caught", "missions_claimed", "blocks_mined", "items_crafted",
            "items_delivered", "mobs_defeated", "purchases", "notes");

    private static int stats(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int top = y;
        List<String> keys = new ArrayList<>(STAT_ORDER);
        snap.stats().keySet().stream().filter(k -> !keys.contains(k)).sorted().forEach(keys::add);
        boolean any = false;
        for (String key : keys) {
            Long value = snap.stats().get(key);
            if (value == null) {
                continue;
            }
            any = true;
            String formatted = "distance_m".equals(key) && value >= 1000
                    ? String.format(java.util.Locale.ROOT, "%.1f km", value / 1000.0).replace('.', ',')
                    : PhoneText.number(value);
            ui.round(x, y, x + w, y + 16, t.card());
            int valueWidth = ui.font.width(formatted);
            ui.textClipped(Component.translatable("caricraft.stat." + key), x + 5, y + 4, w - valueWidth - 14, t.text());
            ui.text(formatted, x + w - 5 - valueWidth, y + 4, t.accent() == 0xFFE9C46A ? t.header() : t.header());
            y += 16 + 3;
        }
        if (!any) {
            y += ui.wrapped(Component.translatable("caricraft.phone.stats.empty"), x, y, w, t.textDim());
        }
        return y - top;
    }

    // ================================================================ carilens

    private static final int LENS_BLUE = 0xFF00B4D8;

    private static void photo(PhoneUi ui, net.minecraft.resources.ResourceLocation texture, int x, int y, int w, int h,
                              int texW, int texH) {
        ui.g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF101018);
        if (texture == null) {
            ui.g.fill(x, y, x + w, y + h, 0xFF2A2A36);
            ui.textCentered(Component.literal("…"), x + w / 2, y + h / 2 - 4, 0xFFAAAAAA);
            return;
        }
        ui.g.blit(texture, x, y, w, h, 0, 0, texW, texH, texW, texH);
    }

    private static int carilens(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int top = y;
        PhoneSnapshot.LensView lens = snap.lens();
        if (lens == null || !lens.enabled()) {
            return 0;
        }

        // Foto recién hecha, pendiente de guardar
        if (com.chicoestudioso.caricraft.client.ClientPhotos.hasPending()) {
            int h = w * 9 / 16;
            photo(ui, com.chicoestudioso.caricraft.client.ClientPhotos.pendingTexture(), x, y, w, h, 256, 144);
            y += h + 6;
            y += ui.wrapped(Component.translatable("caricraft.phone.lens.pending",
                    PhoneText.number(com.chicoestudioso.caricraft.client.ClientPhotos.pendingSize() / 1024)), x, y, w, t.textDim()) + 4;
            ui.button(x, y, w, BUTTON_H + 2, Component.translatable("caricraft.phone.lens.discard"), Style.SECONDARY, s::discardPhoto);
            return y + BUTTON_H + 2 - top;
        }

        // Foto abierta
        if (s.openPhotoId != null) {
            PhoneSnapshot.PhotoView open = lens.photos().stream().filter(p -> p.id().equals(s.openPhotoId)).findFirst().orElse(null);
            if (open == null) {
                s.openPhotoId = null;
            } else {
                int h = w * open.height() / Math.max(1, open.width());
                photo(ui, com.chicoestudioso.caricraft.client.ClientPhotos.texture(open.id()), x, y, w, h, open.width(), open.height());
                y += h + 5;
                if (!open.caption().isEmpty()) {
                    y += ui.wrapped(Component.literal(open.caption()).withStyle(ChatFormatting.ITALIC), x, y, w, t.text()) + 2;
                }
                ui.text(Component.translatable("caricraft.phone.lens.by", open.author(), PhoneText.dateTime(open.time())), x, y, t.textDim());
                y += 14;
                Component back = Component.translatable("caricraft.phone.lens.back");
                boolean armed = System.currentTimeMillis() - s.deleteArmedAt < 3000;
                Component delete = Component.translatable(armed ? "caricraft.phone.lens.delete_confirm" : "caricraft.phone.lens.delete");
                int bw = ui.buttonWidth(back);
                ui.button(x, y, bw, BUTTON_H, back, Style.SECONDARY, () -> s.openPhotoId = null);
                int dw = ui.buttonWidth(delete);
                ui.button(x + w - dw, y, dw, BUTTON_H, delete, armed ? Style.DANGER : Style.SECONDARY, () -> {
                    if (System.currentTimeMillis() - s.deleteArmedAt < 3000) {
                        s.send(PhoneAction.PHOTO_DELETE, open.id());
                        com.chicoestudioso.caricraft.client.ClientPhotos.forget(open.id());
                        s.openPhotoId = null;
                    } else {
                        s.deleteArmedAt = System.currentTimeMillis();
                    }
                });
                return y + BUTTON_H - top;
            }
        }

        // Cuadrícula
        boolean full = lens.photos().size() >= lens.capacity();
        ui.round(x, y, x + w, y + 30, LENS_BLUE);
        ui.text(Component.translatable("caricraft.phone.lens.album").copy().withStyle(ChatFormatting.BOLD), x + 6, y + 5, 0xFFFFFFFF);
        ui.text(Component.translatable("caricraft.phone.lens.count", lens.photos().size(), lens.capacity()), x + 6, y + 17, 0xFFE0F7FB);
        y += 36;
        ui.button(x, y, w, BUTTON_H + 3, Component.translatable(full ? "caricraft.phone.lens.full" : "caricraft.phone.lens.take"),
                full ? Style.DISABLED : Style.PRIMARY, s::takePhoto);
        y += BUTTON_H + 3 + 6;
        if (lens.photos().isEmpty()) {
            return y - top + ui.wrapped(Component.translatable("caricraft.phone.lens.empty"), x, y, w, t.textDim());
        }
        int cellW = (w - GAP) / 2;
        int cellH = cellW * 9 / 16;
        for (int i = 0; i < lens.photos().size(); i++) {
            PhoneSnapshot.PhotoView p = lens.photos().get(i);
            int cx = x + (i % 2) * (cellW + GAP);
            int cy = y + (i / 2) * (cellH + 16);
            boolean hover = ui.hovered(cx, cy, cx + cellW, cy + cellH + 12);
            photo(ui, com.chicoestudioso.caricraft.client.ClientPhotos.texture(p.id()), cx, cy, cellW, cellH, p.width(), p.height());
            if (hover) {
                ui.g.fill(cx, cy, cx + cellW, cy + cellH, 0x30FFFFFF);
            }
            Component label = p.caption().isEmpty() ? Component.literal(p.author()) : Component.literal(p.caption());
            ui.textClipped(label, cx, cy + cellH + 3, cellW, t.textDim());
            ui.hit(cx, cy, cx + cellW, cy + cellH + 12, () -> s.openPhoto(p.id()));
        }
        y += ((lens.photos().size() + 1) / 2) * (cellH + 16);
        return y - top;
    }

    // ================================================================ caricamp

    private static final int CAMP_ORANGE = 0xFFFB5607;

    private static int caricamp(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int top = y;
        PhoneSnapshot.CampView camp = snap.camp();
        net.minecraft.client.player.LocalPlayer me = net.minecraft.client.Minecraft.getInstance().player;
        if (camp == null || me == null) {
            return 0;
        }
        String myDimension = me.level().dimension().location().toString();
        long cooldown = Math.max(0, camp.cooldownEndsAt() - snap.serverTime());
        boolean canTravel = cooldown == 0 && !camp.challengeBlocked();

        // Campamento
        ui.text(Component.translatable("caricraft.phone.camp.camp").copy().withStyle(ChatFormatting.BOLD), x, y, t.text());
        y += 12;
        int cardHeight = camp.camp() == null ? 0 : 50;
        if (camp.camp() != null) {
            PhoneSnapshot.PositionView c = camp.camp();
            ui.round(x, y, x + w, y + cardHeight, PhoneTheme.mix(CAMP_ORANGE, t.card(), 0.85f));
            compass(s, me, myDimension, c, x + 22, y + 22, CAMP_ORANGE);
            locationText(s, me, myDimension, c, x + 44, y + 6, w - 48);
            ui.text(Component.literal((int) Math.floor(c.x()) + ", " + (int) c.y() + ", " + (int) Math.floor(c.z())), x + 44, y + 17, t.textDim());
            Component travel = Component.translatable("caricraft.phone.camp.travel", camp.campCost());
            int bw = ui.buttonWidth(travel);
            ui.button(x + w - 5 - bw, y + cardHeight - BUTTON_H - 5, bw, BUTTON_H, travel,
                    canTravel && snap.balance() >= camp.campCost() ? Style.PRIMARY : Style.DISABLED,
                    () -> s.send(PhoneAction.CAMP_TRAVEL, "camp"));
            y += cardHeight + 4;
        } else {
            y += ui.wrapped(Component.translatable("caricraft.phone.camp.none"), x, y, w, t.textDim()) + 4;
        }
        ui.button(x, y, w, BUTTON_H + 2, Component.translatable("caricraft.phone.camp.set"), Style.SECONDARY,
                () -> s.send(PhoneAction.CAMP_SET, ""));
        y += BUTTON_H + 2 + 8;

        // Compañero: viajes estilo /tpa (el otro tiene que aceptar)
        ui.text(Component.translatable("caricraft.phone.camp.partner").copy().withStyle(ChatFormatting.BOLD), x, y, t.text());
        y += 12;
        if (camp.warmupSeconds() > 0) {
            ui.round(x, y, x + w, y + 16, PhoneTheme.mix(t.header(), t.card(), 0.6f));
            ui.textClipped(Component.translatable("caricraft.phone.camp.warmup", camp.warmupSeconds()), x + 5, y + 4, w - 10, t.text());
            y += 16 + 4;
        }
        if (camp.incoming() != null) {
            PhoneSnapshot.TravelView in = camp.incoming();
            long left = Math.max(1, (in.expiresAt() - snap.serverTime()) / 1000);
            Component text = Component.translatable("GO".equals(in.kind()) ? "caricraft.phone.camp.incoming_go"
                    : "caricraft.phone.camp.incoming_bring", in.name(), left);
            int th = ui.wrappedHeight(text, w - 10);
            int ch = th + BUTTON_H + 12;
            ui.round(x, y, x + w, y + ch, PhoneTheme.mix(t.card(), t.accent(), 0.18f));
            ui.wrapped(text, x + 5, y + 4, w - 10, t.text());
            Component accept = Component.translatable("caricraft.phone.duo.accept");
            Component decline = Component.translatable("caricraft.phone.duo.decline");
            int aw = ui.buttonWidth(accept);
            int dw = ui.buttonWidth(decline);
            ui.button(x + w - 5 - aw, y + ch - BUTTON_H - 4, aw, BUTTON_H, accept, Style.GOOD,
                    () -> s.send(PhoneAction.TRAVEL_ACCEPT, ""));
            ui.button(x + w - 5 - aw - GAP - dw, y + ch - BUTTON_H - 4, dw, BUTTON_H, decline, Style.SECONDARY,
                    () -> s.send(PhoneAction.TRAVEL_DECLINE, ""));
            y += ch + 4;
        }
        if (camp.outgoing() != null) {
            PhoneSnapshot.TravelView out = camp.outgoing();
            long left = Math.max(1, (out.expiresAt() - snap.serverTime()) / 1000);
            Component text = Component.translatable("caricraft.phone.camp.outgoing", out.name(), left);
            Component cancel = Component.translatable("caricraft.phone.duo.cancel");
            int cw = ui.buttonWidth(cancel);
            int th = ui.wrappedHeight(text, w - cw - 14);
            int ch = Math.max(th + 8, BUTTON_H + 8);
            ui.round(x, y, x + w, y + ch, t.card());
            ui.wrapped(text, x + 5, y + 4, w - cw - 14, t.textDim());
            ui.button(x + w - 5 - cw, y + (ch - BUTTON_H) / 2, cw, BUTTON_H, cancel, Style.SECONDARY,
                    () -> s.send(PhoneAction.TRAVEL_DECLINE, ""));
            y += ch + 4;
        }
        if (!snap.party().duo()) {
            y += ui.wrapped(Component.translatable("caricraft.phone.camp.no_duo"), x, y, w, t.textDim()) + 4;
        } else if (camp.others().isEmpty()) {
            y += ui.wrapped(Component.translatable("caricraft.phone.camp.partner_offline"), x, y, w, t.textDim()) + 4;
        }
        boolean canRequest = canTravel && camp.outgoing() == null && camp.warmupSeconds() == 0 && snap.balance() >= camp.partnerCost();
        for (PhoneSnapshot.PositionView other : camp.others()) {
            int ch = 54;
            ui.round(x, y, x + w, y + ch, t.card());
            compass(s, me, myDimension, other, x + 22, y + 22, t.header());
            ui.textClipped(Component.literal(other.name()).withStyle(ChatFormatting.BOLD), x + 44, y + 6, w - 48, t.text());
            locationText(s, me, myDimension, other, x + 44, y + 17, w - 48);
            Component go = Component.translatable("caricraft.phone.camp.go", camp.partnerCost());
            Component bring = Component.translatable("caricraft.phone.camp.bring", camp.partnerCost());
            int gw = ui.buttonWidth(go);
            int bw = ui.buttonWidth(bring);
            ui.button(x + w - 5 - gw, y + ch - BUTTON_H - 5, gw, BUTTON_H, go, canRequest ? Style.PRIMARY : Style.DISABLED,
                    () -> s.send(PhoneAction.TRAVEL_REQUEST, "go"));
            ui.button(x + w - 5 - gw - GAP - bw, y + ch - BUTTON_H - 5, bw, BUTTON_H, bring,
                    canRequest ? Style.SECONDARY : Style.DISABLED, () -> s.send(PhoneAction.TRAVEL_REQUEST, "bring"));
            y += ch + 4;
        }

        y += 4;
        if (camp.challengeBlocked()) {
            y += ui.wrapped(Component.translatable("caricraft.phone.camp.challenge"), x, y, w, t.bad()) + 3;
        } else if (cooldown > 0) {
            y += ui.wrapped(Component.translatable("caricraft.phone.camp.cooldown", PhoneText.remaining(cooldown)), x, y, w, t.warn()) + 3;
        }
        y += ui.wrapped(Component.translatable("caricraft.phone.camp.help"), x, y, w, t.textDim());
        return y - top;
    }

    /** Brújula: flecha que apunta al objetivo relativo a hacia dónde mira el jugador. */
    private static void compass(PhoneScreen s, net.minecraft.client.player.LocalPlayer me, String myDimension,
                                PhoneSnapshot.PositionView target, int cx, int cy, int color) {
        PhoneUi ui = s.ui;
        ui.round(cx - 17, cy - 17, cx + 17, cy + 17, PhoneTheme.mix(color, 0xFFFFFFFF, 0.8f));
        ui.round(cx - 15, cy - 15, cx + 15, cy + 15, 0xFFFFFFFF);
        if (!myDimension.equals(target.dimension())) {
            ui.textCentered(Component.literal("?"), cx, cy - 4, color);
            return;
        }
        double dx = target.x() - me.getX();
        double dz = target.z() - me.getZ();
        if (dx * dx + dz * dz < 4) {
            ui.round(cx - 3, cy - 3, cx + 3, cy + 3, color);
            return;
        }
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float relative = net.minecraft.util.Mth.wrapDegrees(targetYaw - me.getYRot());
        var pose = ui.g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(relative));
        ui.g.fill(-1, -4, 2, 11, color);
        for (int i = 0; i < 6; i++) {
            ui.g.fill(-i, -11 + i, i + 1, -10 + i, color);
        }
        pose.popPose();
    }

    private static void locationText(PhoneScreen s, net.minecraft.client.player.LocalPlayer me, String myDimension,
                                     PhoneSnapshot.PositionView target, int x, int y, int maxWidth) {
        PhoneUi ui = s.ui;
        Component text;
        if (!myDimension.equals(target.dimension())) {
            text = Component.translatable("caricraft.phone.camp.other_dimension");
        } else {
            double dx = target.x() - me.getX();
            double dz = target.z() - me.getZ();
            long distance = Math.round(Math.sqrt(dx * dx + dz * dz));
            int octant = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0), 8);
            text = Component.translatable("caricraft.phone.camp.distance", PhoneText.number(distance),
                    Component.translatable("caricraft.dir." + octant));
        }
        ui.textClipped(text, x, y, maxWidth, ui.theme.text());
    }

    // ================================================================ mercado

    private static int market(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int top = y;
        PhoneSnapshot.MarketView market = snap.market();
        if (market == null || !market.enabled()) {
            return ui.wrapped(Component.translatable("caricraft.phone.market.closed"), x, y, w, t.textDim());
        }
        int purple = 0xFF6A4C93;
        ui.round(x, y, x + w, y + 30, purple);
        ui.text(Component.translatable("caricraft.phone.market.title").copy().withStyle(ChatFormatting.BOLD), x + 6, y + 5, 0xFFFFFFFF);
        ui.text(Component.translatable("caricraft.phone.market.refresh", PhoneText.remaining(market.refreshAt() - snap.serverTime())),
                x + 6, y + 17, 0xFFE8DDF5);
        y += 36;
        for (PhoneSnapshot.OfferView offer : market.offers()) {
            int h = 38;
            ui.round(x, y, x + w, y + h, t.card());
            net.minecraft.world.item.ItemStack stack = PhoneUi.stack(offer.item());
            stack.setCount(Math.min(offer.count(), stack.getMaxStackSize()));
            ui.round(x + 4, y + 4, x + 24, y + 24, PhoneTheme.mix(purple, t.card(), 0.75f));
            ui.g.renderItem(stack, x + 6, y + 6);
            ui.g.renderItemDecorations(ui.font, stack, x + 6, y + 6);
            String price = PhoneText.number(offer.price());
            int priceWidth = ui.font.width(price) + 9;
            Component name = Component.translatable("caricraft.phone.market.offer", offer.count(), PhoneText.idName(offer.item()));
            ui.textClipped(name, x + 28, y + 5, w - 28 - priceWidth - 8, t.text());
            s.drawCoin(x + w - 5 - priceWidth, y + 5);
            ui.text(price, x + w - 5 - priceWidth + 9, y + 5, t.warn());
            Component stock = Component.translatable("caricraft.phone.market.stock", offer.stockLeft(), offer.dailyStock());
            Component label;
            Style style;
            if (offer.stockLeft() <= 0) {
                label = Component.translatable("caricraft.phone.market.sold_out");
                style = Style.DISABLED;
            } else if (snap.balance() < offer.price()) {
                label = Component.translatable("caricraft.phone.shop.missing", PhoneText.number(offer.price() - snap.balance()));
                style = Style.DISABLED;
            } else {
                label = Component.translatable("caricraft.phone.shop.buy");
                style = Style.PRIMARY;
            }
            int bw = ui.buttonWidth(label);
            ui.textClipped(stock, x + 28, y + 16, w - 28 - bw - 12, offer.stockLeft() > 0 ? t.textDim() : t.bad());
            ui.button(x + w - 5 - bw, y + h - BUTTON_H - 4, bw, BUTTON_H, label, style,
                    () -> s.send(PhoneAction.MARKET_BUY, offer.id()));
            y += h + 3;
        }
        y += 3;
        y += ui.wrapped(Component.translatable("caricraft.phone.market.note"), x, y, w, t.textDim());
        return y - top;
    }

    // ================================================================ desafío dúo

    private static final int CHALLENGE_RED = 0xFF9D0208;

    private static int challenge(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int top = y;
        PhoneSnapshot.ChallengeView c = snap.challenge();
        if (c == null || !c.enabled()) {
            return ui.wrapped(Component.translatable("caricraft.phone.challenge.disabled"), x, y, w, t.textDim());
        }

        // Tarjeta de estado
        Component title;
        Component subtitle;
        int bg;
        if (c.active()) {
            title = Component.translatable("caricraft.phone.challenge.active");
            subtitle = Component.translatable("caricraft.phone.challenge.active_sub", c.nightKills(), PhoneText.number(c.nightCarins()));
            bg = CHALLENGE_RED;
        } else if (c.tonight()) {
            title = Component.translatable("caricraft.phone.challenge.tonight");
            subtitle = Component.translatable(c.night() ? "caricraft.phone.challenge.starting" : "caricraft.phone.challenge.tonight_sub");
            bg = PhoneTheme.mix(CHALLENGE_RED, t.warn(), 0.45f);
        } else {
            title = Component.translatable("caricraft.phone.challenge.next");
            subtitle = c.nightsUntilForced() == 0
                    ? Component.translatable("caricraft.phone.challenge.next_tonight")
                    : Component.translatable("caricraft.phone.challenge.next_in", c.nightsUntilForced());
            bg = PhoneTheme.mix(CHALLENGE_RED, t.header(), 0.55f);
        }
        int textWidth = w - 12;
        Component boldTitle = title.copy().withStyle(ChatFormatting.BOLD);
        int titleHeight = ui.wrappedHeight(boldTitle, textWidth);
        int cardHeight = 5 + titleHeight + 2 + ui.wrappedHeight(subtitle, textWidth) + 4;
        ui.round(x, y, x + w, y + cardHeight, bg);
        ui.wrapped(boldTitle, x + 6, y + 5, textWidth, 0xFFFFFFFF);
        ui.wrapped(subtitle, x + 6, y + 5 + titleHeight + 2, textWidth, 0xFFFFE3E3);
        y += cardHeight + 6;

        // Nivel (sin techo bajo: sube con cada noche superada)
        Component level = Component.translatable("caricraft.phone.challenge.level", c.level(), c.maxLevel())
                .withStyle(ChatFormatting.BOLD);
        ui.text(level, x, y + 1, t.text());
        y += 11;
        ui.progressBar(x, y, w, c.level(), Math.max(1, c.maxLevel()), CHALLENGE_RED);
        y += 7;
        if (c.active()) {
            Component wave = Component.translatable("caricraft.phone.challenge.wave", c.wave() + 1, c.effectiveLevel());
            ui.text(wave, x, y, CHALLENGE_RED);
            y += 11;
        }
        y += 3;

        // Botón
        if (!c.active() && !c.tonight()) {
            ui.button(x, y, w, BUTTON_H + 3, Component.translatable("caricraft.phone.challenge.bring_forward"), Style.DANGER,
                    () -> s.send(PhoneAction.CHALLENGE_BRING_FORWARD, ""));
            y += BUTTON_H + 3 + 4;
            y += ui.wrapped(Component.translatable("caricraft.phone.challenge.bring_forward_help"), x, y, w, t.textDim()) + 4;
        } else {
            y += ui.wrapped(Component.translatable("caricraft.phone.challenge.no_sleep"), x, y, w, t.bad()) + 4;
        }

        if (!c.blockedMobs().isEmpty()) {
            y += ui.wrapped(Component.translatable("caricraft.phone.challenge.blocked", c.blockedMobs().size()), x, y, w, t.bad()) + 4;
        }

        // Estadísticas
        y += 2;
        ui.text(Component.translatable("caricraft.phone.challenge.record").copy().withStyle(ChatFormatting.BOLD), x, y, t.text());
        y += 12;
        String[][] rows = {
                {"caricraft.phone.challenge.nights", PhoneText.number(c.nightsCompleted())},
                {"caricraft.phone.challenge.kills", PhoneText.number(c.totalKills())},
                {"caricraft.phone.challenge.carins", PhoneText.number(c.totalCarins())}};
        for (String[] row : rows) {
            ui.round(x, y, x + w, y + 15, t.card());
            int vw = ui.font.width(row[1]);
            ui.textClipped(Component.translatable(row[0]), x + 5, y + 4, w - vw - 14, t.text());
            ui.text(row[1], x + w - 5 - vw, y + 4, CHALLENGE_RED);
            y += 15 + 3;
        }

        // Reglas
        y += 4;
        ui.text(Component.translatable("caricraft.phone.challenge.rules").copy().withStyle(ChatFormatting.BOLD), x, y, t.text());
        y += 12;
        Component rules = Component.translatable("caricraft.phone.challenge.rules_text", c.intervalNights(),
                c.killReward(), c.nightReward());
        y += ui.wrapped(rules, x, y, w, t.textDim());
        return y - top;
    }

    // ================================================================ app bloqueada

    private static int locked(PhoneScreen s, PhoneSnapshot snap, int x, int y, int w, String appId) {
        PhoneUi ui = s.ui;
        PhoneTheme t = ui.theme;
        int top = y;
        AppView app = snap.apps().stream().filter(a -> a.id().equals(appId)).findFirst().orElse(null);
        if (app == null) {
            return ui.wrapped(Component.translatable("caricraft.phone.locked.unknown"), x, y, w, t.textDim());
        }
        int cx = x + w / 2;
        ui.round(cx - 20, y + 4, cx + 20, y + 44, app.color());
        ui.g.pose().pushPose();
        ui.g.pose().translate(cx - 16, y + 8, 0);
        ui.g.pose().scale(2f, 2f, 1f);
        ui.item(app.icon(), 0, 0);
        ui.g.pose().popPose();
        y += 52;
        ui.textCentered(PhoneText.appName(appId).copy().withStyle(ChatFormatting.BOLD), cx, y, t.text());
        y += 14;
        y += ui.wrapped(PhoneText.appDescription(appId), x, y, w, t.textDim()) + 8;
        if ("COMING_SOON".equals(app.state())) {
            y += ui.wrapped(Component.translatable("caricraft.phone.locked.soon"), x, y, w, t.text());
            return y - top;
        }
        y += ui.wrapped(Component.translatable("caricraft.phone.locked.buy", PhoneText.number(app.price())), x, y, w, t.text()) + 6;
        Component go = Component.translatable("caricraft.phone.locked.go_shop");
        ui.button(x, y, w, BUTTON_H + 2, go, Style.PRIMARY, () -> s.navigate(PhonePage.SHOP));
        y += BUTTON_H + 2;
        return y - top;
    }

    private static boolean hasApp(PhoneSnapshot snap, String id) {
        return snap.apps().stream().anyMatch(a -> a.id().equals(id) && ("OWNED".equals(a.state()) || "BUILTIN".equals(a.state())));
    }
}
