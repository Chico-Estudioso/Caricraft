package com.chicoestudioso.caricraft.client.phone;

import com.chicoestudioso.caricraft.client.ClientPhoneState;
import com.chicoestudioso.caricraft.network.PhoneAction;
import com.chicoestudioso.caricraft.network.PhoneActionPayload;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;

/**
 * Herramienta de QA solo para desarrollo (no se incluye en el JAR): juega un guion contra un servidor
 * dedicado y guarda capturas del CariPhone. Se activa con {@code -Dcaricraft.qa.role=A|B}.
 */
public final class QaAutomation implements ClientModInitializer {
    private record Step(int delay, BooleanSupplier until, Runnable action) {
    }

    private final Deque<Step> steps = new ArrayDeque<>();
    private int wait;
    private boolean started;

    @Override
    public void onInitializeClient() {
        String role = System.getProperty("caricraft.qa.role");
        if (role == null) {
            return;
        }
        if ("A".equals(role)) {
            scriptA();
        } else {
            scriptB();
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }
        if (!started) {
            started = true;
            wait = 300; // deja pasar los avisos del juego al entrar
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = steps.peekFirst();
        if (step == null) {
            return;
        }
        if (step.until != null && !step.until.getAsBoolean()) {
            return;
        }
        steps.removeFirst();
        try {
            step.action.run();
        } catch (RuntimeException e) {
            System.err.println("[QA] Paso fallido: " + e);
            e.printStackTrace();
        }
        Step next = steps.peekFirst();
        wait = next == null ? 0 : next.delay;
    }

    // ------------------------------------------------------------------ utilidades

    private void then(int delay, Runnable action) {
        steps.addLast(new Step(delay, null, action));
    }

    private void when(BooleanSupplier until, Runnable action) {
        steps.addLast(new Step(5, until, action));
    }

    private static void cmd(String command) {
        Minecraft.getInstance().getConnection().sendCommand(command);
    }

    private static void act(PhoneAction action, String arg) {
        ClientPlayNetworking.send(PhoneActionPayload.of(action, arg));
    }

    private static PhoneScreen phone() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof PhoneScreen)) {
            mc.setScreen(new PhoneScreen());
        }
        return (PhoneScreen) mc.screen;
    }

    private void page(PhonePage page, int tab) {
        then(10, () -> {
            PhoneScreen phone = phone();
            phone.navigate(page);
            phone.missionTab = tab;
        });
    }

    private void lockedPage(String appId) {
        then(10, () -> {
            PhoneScreen phone = phone();
            phone.lockedAppId = appId;
            phone.navigate(PhonePage.LOCKED);
        });
    }

    private void shot(String name) {
        // Quita los avisos emergentes del juego (recetas, logros) para que no tapen el teléfono.
        then(15, () -> Minecraft.getInstance().getToasts().clear());
        then(10, () -> {
            Minecraft mc = Minecraft.getInstance();
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> System.out.println("[QA] " + msg.getString()));
        });
    }

    private void closePhone() {
        then(5, () -> Minecraft.getInstance().setScreen(null));
    }

    private void travel(int hops) {
        for (int i = 0; i < hops; i++) {
            int dir = i % 8 < 4 ? 1 : -1;
            then(45, () -> cmd("tp @s ~" + (55 * dir) + " ~ ~" + 20));
        }
    }

    private static PhoneSnapshot snap() {
        return ClientPhoneState.snapshot();
    }

    private static boolean status(String mission, String status) {
        PhoneSnapshot s = snap();
        return s != null && s.missions().stream().anyMatch(m -> m.id().equals(mission) && m.status().equals(status));
    }

    // ------------------------------------------------------------------ guiones

    private void scriptA() {
        then(0, () -> {
            cmd("gamemode creative");
            cmd("time set day");
            cmd("gamerule doDaylightCycle false");
            cmd("weather clear");
            cmd("tp @s ~ 120 ~");
        });
        then(20, () -> phone().navigate(PhonePage.HOME));
        when(() -> snap() != null, () -> {
        });
        shot("01_inicio_solo");
        lockedPage("chronicles");
        shot("02_app_bloqueada");
        closePhone();
        then(10, () -> {
            cmd("carins give @s 1500");
            act(PhoneAction.ACCEPT_MISSION, "first_steps");
            act(PhoneAction.ACCEPT_MISSION, "lumberjack");
        });
        then(10, () -> {
            act(PhoneAction.ACCEPT_MISSION, "craftsman");
            act(PhoneAction.ACCEPT_MISSION, "daily_walk");
        });
        travel(12);
        page(PhonePage.MISSIONS, 0);
        shot("03_misiones_activas");
        page(PhonePage.MISSIONS, 1);
        shot("04_misiones_nuevas");
        page(PhonePage.SHOP, 0);
        shot("05_tienda");
        then(5, () -> {
            act(PhoneAction.BUY_ITEM, "chronicles");
        });
        then(10, () -> act(PhoneAction.BUY_ITEM, "explorer"));
        then(10, () -> act(PhoneAction.BUY_ITEM, "stats"));
        then(10, () -> act(PhoneAction.ADD_NOTE, "Primera base junto al río, cerca del pueblo"));
        // Esperar al compañero e invitarle
        when(() -> Minecraft.getInstance().getConnection().getOnlinePlayers().size() >= 2, () -> act(PhoneAction.INVITE_PLAYER, "Dev2"));
        when(() -> snap() != null && snap().party().duo(), () -> {
            act(PhoneAction.ACCEPT_MISSION, "duo_expedition");
            act(PhoneAction.ACCEPT_MISSION, "duo_supplies");
        });
        travel(10);
        then(10, () -> {
            cmd("give @s minecraft:oak_log 64");
            cmd("give @s minecraft:iron_ingot 20");
        });
        page(PhonePage.MISSIONS, 0);
        shot("06_misiones_duo");
        then(10, () -> act(PhoneAction.DELIVER_MISSION, "duo_supplies"));
        when(() -> status("duo_supplies", "COMPLETED"), () -> {
        });
        page(PhonePage.HOME, 0);
        shot("07_inicio_duo");
        page(PhonePage.MISSIONS, 0);
        shot("08_misiones_reclamables");
        then(10, () -> {
            for (String id : new String[]{"first_steps", "daily_walk", "duo_supplies", "new_horizons", "duo_expedition"}) {
                act(PhoneAction.CLAIM_MISSION, id);
            }
        });
        page(PhonePage.MISSIONS, 2);
        shot("09_misiones_hechas");
        page(PhonePage.WALLET, 0);
        shot("10_cartera");
        page(PhonePage.DUO, 0);
        shot("11_duo");
        page(PhonePage.CHRONICLES, 0);
        shot("12_cronicas");
        page(PhonePage.EXPLORER, 0);
        shot("13_exploracion");
        page(PhonePage.STATS, 0);
        shot("14_estadisticas");
        then(10, () -> act(PhoneAction.BUY_ITEM, "theme_ocean"));
        then(10, () -> act(PhoneAction.SELECT_THEME, "theme_ocean"));
        page(PhonePage.SETTINGS, 0);
        shot("15_ajustes_oceano");
        page(PhonePage.HOME, 0);
        shot("16_inicio_oceano");
        then(10, () -> {
            act(PhoneAction.BUY_ITEM, "theme_sunset");
        });
        then(10, () -> act(PhoneAction.SELECT_THEME, "theme_sunset"));
        page(PhonePage.MISSIONS, 1);
        shot("17_misiones_atardecer");
        then(10, () -> act(PhoneAction.LEAVE_PARTY, ""));
        page(PhonePage.DUO, 0);
        shot("18_desvincular_confirmar");
        closePhone();
        shot("19_juego");
        then(100, () -> Minecraft.getInstance().stop());
    }

    private void scriptB() {
        then(0, () -> {
            cmd("gamemode creative");
            cmd("tp @s ~ 120 ~");
        });
        when(() -> snap() != null && !snap().incomingInvites().isEmpty(), () -> {
        });
        page(PhonePage.DUO, 0);
        shot("b01_invitacion");
        then(10, () -> act(PhoneAction.ACCEPT_INVITE, snap().incomingInvites().getFirst().uuid()));
        closePhone();
        travel(8);
        page(PhonePage.HOME, 0);
        shot("b02_inicio_duo");
        then(1200, () -> Minecraft.getInstance().stop());
    }
}
