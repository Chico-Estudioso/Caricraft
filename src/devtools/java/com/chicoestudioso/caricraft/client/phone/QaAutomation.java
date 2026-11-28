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
import java.util.ArrayList;
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
        } else if ("C".equals(role)) {
            scriptChallenge();
        } else if ("N".equals(role)) {
            scriptNewApps();
        } else if ("K".equals(role)) {
            scriptCampOnly();
        } else if ("M".equals(role)) {
            scriptNewAppsPartner();
        } else if ("P".equals(role)) {
            scriptPhone();
        } else if ("T".equals(role)) {
            scriptNotices();
        } else if ("S".equals(role)) {
            scriptSquads();
        } else if ("V".equals(role)) {
            scriptCobbleverseUi();
        } else if ("W".equals(role)) {
            scriptCobblemonReal();
        } else if ("L".equals(role)) {
            scriptLuck();
        } else if ("G".equals(role)) {
            scriptChests();
        } else if ("I".equals(role)) {
            scriptPhoneItem();
        } else if ("D".equals(role)) {
            scriptDuoCobbleverse();
        } else if ("R".equals(role)) {
            scriptRoutine();
        } else if ("Z".equals(role)) {
            scriptCleaner();
        } else if ("H".equals(role)) {
            scriptSavingsTrainer();
        } else {
            scriptB();
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientTickEvents.START_CLIENT_TICK.register(mc -> keepFakeCobbleverse());
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
            phone().openApp(appId, "LOCKED");
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

    private static int count(String entityId) {
        Minecraft mc = Minecraft.getInstance();
        int n = 0;
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (net.minecraft.world.entity.EntityType.getKey(e.getType()).toString().equals(entityId)) {
                n++;
            }
        }
        return n;
    }

    private static void log(String message) {
        System.out.println("[QA] " + message);
    }

    /** Guion del Desafío Dúo: programar, empezar, mobs equipados, recompensa, amanecer y filtro. */
    private void scriptChallenge() {
        then(0, () -> {
            cmd("gamemode creative");
            cmd("time set 1000");
            cmd("gamerule doDaylightCycle false");
            cmd("weather clear");
            cmd("effect give @s minecraft:night_vision 99999 0 true");
            cmd("tp @s ~ 120 ~ 0 10");
        });
        then(40, () -> cmd("summon minecraft:zombie ~ ~ ~4"));
        then(20, () -> log("zombis fuera del desafío (deben ser 0): " + count("minecraft:zombie")));
        page(PhonePage.CHALLENGE, 0);
        shot("c01_desafio_pendiente");
        then(10, () -> act(PhoneAction.CHALLENGE_BRING_FORWARD, ""));
        page(PhonePage.CHALLENGE, 0);
        shot("c02_desafio_esta_noche");
        closePhone();
        then(10, () -> cmd("time set 14000"));
        then(40, () -> {
            // Plataforma despejada para ver bien a los mobs (quietos con NoAI).
            cmd("tp @s ~ 200 ~ 0 15");
        });
        then(20, () -> {
            cmd("fill ~-6 ~-1 ~-3 ~6 ~-1 ~12 minecraft:grass_block");
            cmd("summon minecraft:zombie ~-1 ~ ~4 {NoAI:1b,Rotation:[180f,0f]}");
            cmd("summon minecraft:skeleton ~1.5 ~ ~5 {NoAI:1b,Rotation:[180f,0f]}");
            cmd("summon minecraft:husk ~-3 ~ ~5 {NoAI:1b,Rotation:[180f,0f]}");
            cmd("summon minecraft:spider ~3.5 ~ ~7 {NoAI:1b,Rotation:[180f,0f]}");
        });
        then(40, () -> log("mobs en desafío: zombie=" + count("minecraft:zombie") + " skeleton=" + count("minecraft:skeleton")
                + " husk=" + count("minecraft:husk") + " spider=" + count("minecraft:spider")));
        shot("c03_mobs_equipados");
        // Nivel alto en la última oleada: equipo de netherita, efectos y élites.
        then(10, () -> {
            cmd("caricraft desafio nivel 20");
            cmd("time set 21000");
            cmd("kill @e[type=!minecraft:player,distance=..40]");
        });
        then(40, () -> {
            cmd("summon minecraft:zombie ~-1 ~ ~4 {NoAI:1b,Rotation:[180f,0f]}");
            cmd("summon minecraft:skeleton ~1.5 ~ ~5 {NoAI:1b,Rotation:[180f,0f]}");
            cmd("summon minecraft:husk ~-3 ~ ~5 {NoAI:1b,Rotation:[180f,0f]}");
            cmd("summon minecraft:zombie ~3.5 ~ ~6 {NoAI:1b,Rotation:[180f,0f]}");
            cmd("summon minecraft:stray ~0 ~ ~8 {NoAI:1b,Rotation:[180f,0f]}");
        });
        then(60, () -> log("mobs a nivel alto (con hordas): " + count("minecraft:zombie") + " zombis, "
                + count("minecraft:skeleton") + " esqueletos"));
        shot("c03b_mobs_nivel_alto");
        then(10, () -> cmd("damage @e[type=minecraft:zombie,limit=1,sort=nearest] 200 minecraft:player_attack by @s"));
        then(30, () -> cmd("damage @e[type=minecraft:husk,limit=1,sort=nearest] 200 minecraft:player_attack by @s"));
        page(PhonePage.CHALLENGE, 0);
        shot("c04_desafio_activo");
        closePhone();
        then(20, () -> cmd("time set 23500"));
        then(60, () -> log("mobs tras el amanecer (deben ser 0): skeleton=" + count("minecraft:skeleton") + " spider=" + count("minecraft:spider")));
        page(PhonePage.CHALLENGE, 0);
        shot("c05_desafio_superado");
        page(PhonePage.WALLET, 0);
        shot("c06_cartera_desafio");
        closePhone();
        then(10, () -> cmd("summon minecraft:skeleton ~ ~ ~4"));
        then(20, () -> log("esqueletos tras el desafío (deben ser 0): " + count("minecraft:skeleton")));
        then(40, () -> Minecraft.getInstance().stop());
    }

    private static double distanceTo(String playerName) {
        Minecraft mc = Minecraft.getInstance();
        for (net.minecraft.world.entity.player.Player p : mc.level.players()) {
            if (p.getGameProfile().getName().equals(playerName)) {
                return Math.sqrt(p.distanceToSqr(mc.player));
            }
        }
        return -1;
    }

    private static int inventoryCount(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        int n = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            var stack = mc.player.getInventory().getItem(i);
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** Guion de Mercado, CariCamp y CariLens (jugador A). */
    private void scriptNewApps() {
        final String[] boughtItem = new String[1];
        final double[] camp = new double[3];
        then(0, () -> {
            cmd("gamemode creative");
            cmd("time set 1000");
            cmd("gamerule doDaylightCycle false");
            cmd("weather clear");
            cmd("tp @s 0 120 0 -90 15");
            cmd("fill -4 119 -4 4 119 4 minecraft:grass_block");
            cmd("setblock 2 120 0 minecraft:campfire[lit=true]");
            cmd("tp @s 0 120 0 -90 15");
            cmd("carins give @s 5000");
        });
        then(20, () -> phone().goHome());
        shot("n00_inicio_secciones");
        page(PhonePage.MARKET, 0);
        shot("n01_mercado");
        then(10, () -> {
            var offer = snap().market().offers().getFirst();
            boughtItem[0] = offer.item();
            log("compro en el mercado " + offer.count() + " x " + offer.item() + " (antes: " + inventoryCount(offer.item()) + ")");
            act(PhoneAction.MARKET_BUY, offer.id());
        });
        then(20, () -> log("tras comprar: " + inventoryCount(boughtItem[0]) + " x " + boughtItem[0]));
        page(PhonePage.MARKET, 0);
        shot("n02_mercado_comprado");
        then(5, () -> act(PhoneAction.BUY_ITEM, "caricamp"));
        then(10, () -> act(PhoneAction.BUY_ITEM, "tpduo"));
        then(10, () -> act(PhoneAction.BUY_ITEM, "carilens"));
        then(10, () -> act(PhoneAction.BUY_ITEM, "carilens_album"));
        then(10, () -> act(PhoneAction.BUY_ITEM, "caribox"));
        page(PhonePage.SHOP, 0);
        shot("n03_tienda_mejoras");
        then(10, () -> phone().goHome());
        shot("n03b_inicio_con_apps");
        // Navegación: inicio → CariCamp → TP Dúo (desde el aviso) → atrás → atrás.
        then(10, () -> {
            PhoneScreen phone = phone();
            phone.navigate(PhonePage.CARICAMP);
            phone.navigate(PhonePage.TPDUO);
            phone.back();
            log("NAV atrás desde TP Dúo → " + phone.page + " (debe ser CARICAMP)");
            phone.keyPressed(256, 0, 0);
            log("NAV Esc desde CariCamp → " + phone.page + " (debe ser HOME)");
            phone.navigate(PhonePage.SHOP);
            phone.openApp("stats", "AVAILABLE");
            phone.navigate(PhonePage.SHOP);
            phone.mouseClicked(0, 0, 3);
            log("NAV botón lateral desde Tienda → " + phone.page + " / " + phone.lockedAppId + " (debe ser LOCKED / stats)");
            phone.mouseClicked(0, 0, 3);
            log("NAV botón lateral → " + phone.page + " (debe ser SHOP)");
            phone.goHome();
            log("NAV inicio → " + phone.page + "; Esc en inicio cierra: " + !phone.back());
        });
        // Campamento
        then(10, () -> {
            var mc = Minecraft.getInstance();
            log("bloque en 2,120,0: " + mc.level.getBlockState(new net.minecraft.core.BlockPos(2, 120, 0))
                    + " · jugador en " + mc.player.blockPosition());
            act(PhoneAction.CAMP_SET, "");
        });
        then(20, () -> {
            var n = com.chicoestudioso.caricraft.client.ClientPhoneState.notifications().peekFirst();
            log("último aviso: " + (n == null ? "-" : n.key() + " " + n.args()));
        });
        then(5, () -> {
            var c = snap().camp().camp();
            log("campamento: " + (c == null ? "ninguno" : c.x() + "," + c.y() + "," + c.z()));
            if (c != null) {
                camp[0] = c.x();
                camp[1] = c.y();
                camp[2] = c.z();
            }
        });
        // Dúo con Dev2
        when(() -> Minecraft.getInstance().getConnection().getOnlinePlayers().size() >= 2, () -> act(PhoneAction.INVITE_PLAYER, "Dev2"));
        when(() -> snap() != null && snap().party().duo(), () -> cmd("tp @s 300 120 40 -90 15"));
        then(60, () -> {
        });
        page(PhonePage.CARICAMP, 0);
        shot("n04_caricamp_lejos");
        then(10, () -> act(PhoneAction.CAMP_TRAVEL, "camp"));
        page(PhonePage.CARICAMP, 0);
        shot("n04b_cuenta_atras");
        then(80, () -> {
            var me = Minecraft.getInstance().player;
            double d = Math.sqrt(me.distanceToSqr(camp[0], camp[1], camp[2]));
            log("distancia al campamento tras viajar (debe ser < 4): " + String.format("%.1f", d));
        });
        page(PhonePage.CARICAMP, 0);
        shot("n05_caricamp_en_campamento");
        // Viajes estilo /tpa con TP Dúo: Dev2 tiene que aceptar.
        then(10, () -> cmd("tp @s 0 120 0 -90 15"));
        page(PhonePage.TPDUO, 0);
        then(40, () -> {
        });
        shot("n05a_tpduo_companero");
        then(100, () -> act(PhoneAction.TRAVEL_REQUEST, "go"));
        page(PhonePage.TPDUO, 0);
        shot("n05b_tpduo_peticion_enviada");
        then(10, () -> phone().goHome());
        shot("n05c_inicio_aviso_enviado");
        when(() -> distanceTo("Dev2") >= 0 && distanceTo("Dev2") < 3, () ->
                log("IR: distancia a Dev2 tras aceptar y esperar (debe ser < 3): " + String.format("%.1f", distanceTo("Dev2"))));
        closePhone();
        then(140, () -> cmd("tp @s 0 120 -30 0 10"));
        then(20, () -> act(PhoneAction.TRAVEL_REQUEST, "bring"));
        when(() -> distanceTo("Dev2") >= 0 && distanceTo("Dev2") < 3, () ->
                log("TRAER: Dev2 ha llegado hasta mí: " + String.format("%.1f", distanceTo("Dev2"))));
        // Foto
        then(20, () -> {
            cmd("tp @s 0 121 -6 0 10");
        });
        then(40, () -> phone().takePhoto());
        when(com.chicoestudioso.caricraft.client.ClientPhotos::hasPending, () -> {
        });
        page(PhonePage.CARILENS, 0);
        shot("n06_carilens_vista_previa");
        then(10, () -> {
            log("foto pendiente: " + com.chicoestudioso.caricraft.client.ClientPhotos.pendingSize() + " bytes");
            com.chicoestudioso.caricraft.client.ClientPhotos.uploadPending("Primera foto del dúo");
        });
        when(() -> snap() != null && snap().lens() != null && !snap().lens().photos().isEmpty(), () -> {
        });
        page(PhonePage.CARILENS, 0);
        then(40, () -> {
        });
        shot("n07_carilens_album");
        then(10, () -> phone().openPhoto(snap().lens().photos().getFirst().id()));
        shot("n08_carilens_foto");
        page(PhonePage.CHRONICLES, 0);
        shot("n09_cronicas_foto");
        // CariBox: guardar diamantes desde cualquier sitio.
        then(10, () -> cmd("give @s minecraft:diamond 16"));
        page(PhonePage.CARIBOX, 0);
        shot("n09b_caribox_app");
        then(10, () -> act(PhoneAction.CARIBOX_OPEN, ""));
        when(() -> Minecraft.getInstance().player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu, () -> {
            var mc = Minecraft.getInstance();
            var menu = mc.player.containerMenu;
            for (int i = 27; i < menu.slots.size(); i++) {
                if (menu.slots.get(i).getItem().is(net.minecraft.world.item.Items.DIAMOND)) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player);
                    break;
                }
            }
        });
        then(20, () -> {
            var menu = Minecraft.getInstance().player.containerMenu;
            int diamonds = 0;
            for (int i = 0; i < 27; i++) {
                if (menu.slots.get(i).getItem().is(net.minecraft.world.item.Items.DIAMOND)) {
                    diamonds += menu.slots.get(i).getItem().getCount();
                }
            }
            log("CariBox de Dev1: " + diamonds + " diamantes guardados");
        });
        shot("n09c_caribox_abierto");
        closePhone();
        // Desafío: los viajes quedan bloqueados
        then(10, () -> act(PhoneAction.CHALLENGE_BRING_FORWARD, ""));
        then(10, () -> cmd("time set 14000"));
        then(60, () -> act(PhoneAction.CAMP_TRAVEL, "camp"));
        page(PhonePage.CARICAMP, 0);
        shot("n10_caricamp_desafio");
        page(PhonePage.TPDUO, 0);
        shot("n10b_tpduo_desafio");
        then(5, () -> phone().goHome());
        shot("n10c_inicio_desafio");
        then(10, () -> cmd("time set 23500"));
        page(PhonePage.WALLET, 0);
        shot("n11_cartera");
        then(100, () -> Minecraft.getInstance().stop());
    }

    /** Compañero del guion de apps nuevas (jugador B). */
    private void scriptNewAppsPartner() {
        then(0, () -> {
            cmd("gamemode creative");
            cmd("tp @s -40 120 25 90 15");
        });
        then(10, () -> cmd("fill -43 119 22 -37 119 28 minecraft:stone"));
        when(() -> snap() != null && !snap().incomingInvites().isEmpty(),
                () -> act(PhoneAction.ACCEPT_INVITE, snap().incomingInvites().getFirst().uuid()));
        // Petición "ir": la vemos en el chat (botones clicables) y en el teléfono, y aceptamos.
        when(() -> snap() != null && snap().tpduo() != null && snap().tpduo().incoming() != null, () -> {
        });
        shot("m00_chat_peticion");
        then(5, () -> phone().goHome());
        shot("m00a_inicio_aviso");
        page(PhonePage.TPDUO, 0);
        shot("m00b_peticion_recibida");
        then(10, () -> act(PhoneAction.TRAVEL_ACCEPT, ""));
        closePhone();
        // Petición "traer": aceptamos y nos quedamos quietos durante la cuenta atrás.
        when(() -> snap() != null && snap().tpduo() != null && snap().tpduo().incoming() != null, () -> {
            log("petición recibida: " + snap().tpduo().incoming().kind());
            act(PhoneAction.TRAVEL_ACCEPT, "");
        });
        page(PhonePage.TPDUO, 0);
        then(10, () -> {
        });
        shot("m00c_cuenta_atras_traer");
        when(() -> snap() != null && snap().lens() != null && !snap().lens().photos().isEmpty(), () -> {
        });
        page(PhonePage.CARILENS, 0);
        then(60, () -> {
        });
        shot("m01_carilens_companero");
        then(10, () -> phone().openPhoto(snap().lens().photos().getFirst().id()));
        shot("m02_foto_companero");
        // El compañero ve en su CariBox lo que guardó Dev1.
        when(() -> snap() != null && snap().box() != null && snap().box().usedSlots() > 0,
                () -> act(PhoneAction.CARIBOX_OPEN, ""));
        when(() -> Minecraft.getInstance().player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu, () -> {
        });
        then(20, () -> {
            var menu = Minecraft.getInstance().player.containerMenu;
            int diamonds = 0;
            for (int i = 0; i < 27; i++) {
                if (menu.slots.get(i).getItem().is(net.minecraft.world.item.Items.DIAMOND)) {
                    diamonds += menu.slots.get(i).getItem().getCount();
                }
            }
            log("CariBox visto por Dev2: " + diamonds + " diamantes");
        });
        shot("m03_caribox_companero");
        closePhone();
        then(1200, () -> Minecraft.getInstance().stop());
    }

    // ------------------------------------------------------------------ cobbleverse con Cobblemon real

    private static void logNotices() {
        var n = ClientPhoneState.notifications().peekFirst();
        log("aviso: " + (n == null ? "-" : n.key() + " " + n.args()));
    }

    /** Pestaña Cobbleverse con Cobblemon y CobbleDollars de verdad (cliente de producción, rol W). */
    private void scriptCobblemonReal() {
        then(0, () -> {
            cmd("gamemode creative");
            cmd("time set 6000");
            cmd("gamerule doDaylightCycle false");
            cmd("tp @s 0 -60 0 0 10");
            cmd("cobbledollars give @s 200000");
            cmd("pokegive pikachu level=24");
            cmd("pokegive gible level=31 shiny");
            cmd("pokegive magikarp level=10");
        });
        then(60, () -> {
        });
        page(PhonePage.MARKET, 0);
        then(30, () -> phone().marketTab = 1);
        then(40, () -> {
            var cv = snap().cobbleverse();
            log("CV vista: " + (cv == null ? "null" : "₽" + cv.cobbleDollars() + ", regalo=" + cv.giftAvailable()
                    + ", ofertas=" + cv.offers().stream().map(PhoneSnapshot.CvOfferView::id).toList()
                    + ", equipo=" + cv.party().stream().map(m -> m.species() + ":" + m.price() + (m.blocked() == null ? "" : "(" + m.blocked() + ")")).toList()));
        });
        shot("w01_cobbleverse_real");
        then(10, () -> act(PhoneAction.CV_GIFT, ""));
        then(40, QaAutomation::logNotices);
        shot("w02_regalo");
        then(10, () -> {
            var cv = snap().cobbleverse();
            var offer = cv.offers().stream().filter(o -> !o.species().isEmpty()).findFirst()
                    .orElse(cv.offers().getFirst());
            log("CV compro " + offer.id() + " por ₽" + offer.price());
            act(PhoneAction.CV_BUY, offer.id());
        });
        then(40, () -> {
            logNotices();
            log("CV saldo tras comprar: ₽" + snap().cobbleverse().cobbleDollars() + " · equipo="
                    + snap().cobbleverse().party().stream().map(PhoneSnapshot.SellView::species).toList());
        });
        then(10, () -> {
            var mon = snap().cobbleverse().party().stream().filter(m -> m.species().endsWith("magikarp")).findFirst().orElse(null);
            if (mon != null) {
                log("CV vendo magikarp por ₽" + mon.price());
                act(PhoneAction.CV_SELL, mon.uuid());
            }
        });
        then(40, () -> {
            logNotices();
            log("CV saldo tras vender: ₽" + snap().cobbleverse().cobbleDollars() + " · equipo="
                    + snap().cobbleverse().party().stream().map(PhoneSnapshot.SellView::species).toList());
        });
        then(5, () -> phone().mouseScrolled(0, 0, 0, -40));
        shot("w03_tras_vender");
        // CobbleCentro: la pantalla grande con modelos 3D.
        then(10, () -> {
            phone().mouseScrolled(0, 0, 0, 40);
            phone().openCobbleCentro();
        });
        then(40, () -> cc().select(CobbleCentroScreen.Tab.HOME));
        shot("w04_cc_inicio");
        then(10, () -> cc().select(CobbleCentroScreen.Tab.SHOP));
        shot("w05_cc_tienda");
        then(10, () -> cc().select(CobbleCentroScreen.Tab.SELL));
        shot("w06_cc_vender");
        then(10, () -> {
            CobbleCentroScreen cc = cc();
            cc.select(CobbleCentroScreen.Tab.WONDER);
            var mon = snap().cobbleverse().party().stream().filter(m -> m.species().endsWith("pikachu")).findFirst().orElse(null);
            log("CC prodigioso: quedan " + snap().cobbleverse().wondersLeft() + ", bolsa=" + snap().cobbleverse().wonderPool()
                    + ", elijo " + (mon == null ? "-" : mon.species()));
            if (mon != null) {
                cc.wonderPick = mon.uuid();
            }
        });
        shot("w07_cc_prodigioso_elegido");
        then(5, () -> cc().startWonder());
        then(4, () -> {
        });
        shot("w08_cc_prodigioso_buscando");
        then(60, () -> {
            logNotices();
            var cv = snap().cobbleverse();
            log("CC recibido: " + (cv.lastWonder() == null ? "-" : cv.lastWonder().species() + " Nv." + cv.lastWonder().level()
                    + (cv.lastWonder().shiny() ? " shiny" : "") + " de '" + cv.lastWonder().from() + "'")
                    + " · quedan " + cv.wondersLeft() + " · equipo=" + cv.party().stream().map(PhoneSnapshot.SellView::species).toList());
        });
        shot("w09_cc_prodigioso_recibido");
        then(10, () -> {
            cc().dismissReveal();
            act(PhoneAction.CV_WONDER, snap().cobbleverse().party().getFirst().uuid());
        });
        then(30, QaAutomation::logNotices);
        shot("w10_cc_prodigioso_agotado");
        then(20, () -> Minecraft.getInstance().stop());
    }

    private static long dollars() {
        return snap().cobbleverse().cobbleDollars();
    }

    private static int itemCount(String item) {
        int total = 0;
        for (var stack : Minecraft.getInstance().player.getInventory().items) {
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void logLuck(String what) {
        var luck = snap().cobbleverse().luck();
        var last = luck == null ? null : luck.last();
        log("SUERTE " + what + ": ₽" + dollars() + " · último=" + (last == null ? "-" : last.kind() + "/" + last.id() + " premio="
                + last.reward() + " especie=" + last.species() + " shiny=" + last.shiny() + " cantidad=" + last.amount() + " rodillos="
                + last.reels() + " pago=" + last.payout() + " éxito=" + last.success()));
        logNotices();
    }

    /** Cajas, tragaperras, mejoras y fusiones con Cobblemon y CobbleDollars reales (rol L). */
    private void scriptLuck() {
        then(0, () -> {
            cmd("gamemode creative");
            cmd("time set 6000");
            cmd("gamerule doDaylightCycle false");
            cmd("tp @s 0 -60 0 0 10");
            cmd("clear @s");
            cmd("cobbledollars give @s 600000");
            cmd("give @s cobblemon:poke_ball 12");
            cmd("give @s cobblemon:ultra_ball 16");
            cmd("pokegive eevee level=20");
        });
        then(80, () -> phone().openCobbleCentro());
        then(40, () -> {
            var luck = snap().cobbleverse().luck();
            log("SUERTE vista: cajas=" + luck.crates().stream().map(c -> c.id() + ":" + c.rewards().size()).toList()
                    + " apuestas=" + luck.slots().bets() + " retorno=" + luck.slots().returnToPlayer()
                    + " mejoras=" + luck.upgrades().stream().map(u -> u.id()).toList()
                    + " fusiones=" + luck.fusions().stream().map(f -> f.id()).toList());
            cc().select(CobbleCentroScreen.Tab.CRATES);
        });
        shot("l01_cajas");
        then(5, () -> cc().luck.start("crate", PhoneAction.CV_CRATE, "crate_master", ""));
        then(25, () -> {
        });
        shot("l02_caja_ruleta");
        then(70, () -> logLuck("caja maestra"));
        shot("l03_caja_premio");
        then(5, () -> {
            cc().luck.dismiss();
            cc().luck.start("crate", PhoneAction.CV_CRATE, "crate_poke", "");
        });
        then(100, () -> logLuck("caja poké"));
        then(5, () -> {
            cc().luck.dismiss();
            cc().select(CobbleCentroScreen.Tab.SLOTS);
        });
        shot("l04_tragaperras");
        then(5, () -> {
            cc().luck.betIndex = 1;
            cc().luck.start("spin", PhoneAction.CV_SPIN, "1", "");
        });
        then(8, () -> {
        });
        shot("l05_girando");
        then(50, () -> logLuck("tirada 1"));
        shot("l06_tirada");
        for (int i = 2; i <= 8; i++) {
            int n = i;
            then(5, () -> {
                cc().luck.dismiss();
                cc().luck.start("spin", PhoneAction.CV_SPIN, "0", "");
            });
            then(20, () -> logLuck("tirada " + n));
        }
        then(5, () -> {
            cc().luck.dismiss();
            cc().select(CobbleCentroScreen.Tab.UPGRADES);
            var eevee = snap().cobbleverse().party().stream().filter(m -> m.species().endsWith("eevee")).findFirst().orElse(null);
            if (eevee != null) {
                cc().luck.upgradePick = eevee.uuid();
                log("SUERTE eevee antes: nv." + eevee.level() + " IV=" + eevee.ivTotal() + " shiny=" + eevee.shiny());
            }
            log("SUERTE inventario: poké=" + itemCount("cobblemon:poke_ball") + " super=" + itemCount("cobblemon:great_ball")
                    + " ultra=" + itemCount("cobblemon:ultra_ball") + " master=" + itemCount("cobblemon:master_ball"));
        });
        shot("l07_mejoras");
        then(5, () -> cc().luck.start("upgrade", PhoneAction.CV_UPGRADE, "level_boost", cc().luck.upgradePick));
        then(40, () -> logLuck("subida de nivel"));
        shot("l08_mejora_nivel");
        then(5, () -> {
            cc().luck.dismiss();
            cc().luck.start("upgrade", PhoneAction.CV_UPGRADE, "iv_training", cc().luck.upgradePick);
        });
        then(30, () -> logLuck("IV"));
        then(5, () -> {
            cc().luck.dismiss();
            cc().luck.start("upgrade", PhoneAction.CV_UPGRADE, "shiny_charm", cc().luck.upgradePick);
        });
        then(12, () -> {
        });
        shot("l09_variocolor_suspense");
        then(40, () -> logLuck("amuleto variocolor"));
        shot("l10_variocolor_resultado");
        then(5, () -> {
            cc().luck.dismiss();
            var eevee = snap().cobbleverse().party().stream().filter(m -> m.uuid().equals(cc().luck.upgradePick)).findFirst().orElse(null);
            log("SUERTE eevee después: " + (eevee == null ? "-" : "nv." + eevee.level() + " IV=" + eevee.ivTotal() + " shiny=" + eevee.shiny()));
            cc().luck.start("fusion", PhoneAction.CV_FUSE, "great_balls", "");
        });
        then(30, () -> logLuck("fusión super balls"));
        shot("l11_fusion");
        then(5, () -> {
            cc().luck.dismiss();
            cc().luck.start("fusion", PhoneAction.CV_FUSE, "master_ball", "");
        });
        then(40, () -> {
            logLuck("fusión master ball");
            log("SUERTE inventario: poké=" + itemCount("cobblemon:poke_ball") + " super=" + itemCount("cobblemon:great_ball")
                    + " ultra=" + itemCount("cobblemon:ultra_ball") + " master=" + itemCount("cobblemon:master_ball"));
        });
        shot("l12_fusion_master");
        then(5, () -> {
            cc().luck.dismiss();
            cc().luck.start("fusion", PhoneAction.CV_FUSE, "ability_patch", "");
        });
        then(30, () -> logLuck("fusión sin ingredientes"));
        then(5, () -> cc().mouseScrolled(0, 0, 0, -20));
        shot("l13_fusiones_lista");
        then(20, () -> Minecraft.getInstance().stop());
    }

    // ------------------------------------------------------------------ cofres del desafío (rol C, con Cobblemon real)

    private static java.util.List<PhoneSnapshot.ChestView> chests() {
        var c = snap() == null ? null : snap().challenge();
        return c == null || c.chests() == null ? java.util.List.of() : c.chests();
    }

    private static int nearby(String typeId, double radius) {
        var mc = Minecraft.getInstance();
        int count = 0;
        for (var entity : mc.level.entitiesForRendering()) {
            if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(typeId)
                    && entity.distanceTo(mc.player) <= radius) {
                count++;
            }
        }
        return count;
    }

    private static String mobsNear() {
        return "zombis=" + nearby("minecraft:zombie", 48) + " husks=" + nearby("minecraft:husk", 48)
                + " esqueletos=" + nearby("minecraft:skeleton", 48) + " strays=" + nearby("minecraft:stray", 48)
                + " arañas=" + nearby("minecraft:spider", 48) + " pokémon=" + nearby("cobblemon:pokemon", 48);
    }

    private static double[] chestPos;

    private void scriptChests() {
        then(0, () -> {
            cmd("gamemode creative");
            cmd("time set 6000");
            cmd("gamerule doDaylightCycle false");
            cmd("tp @s 0 -60 0 0 10");
            cmd("clear @s");
            cmd("caricraft desafio nivel 6");
        });
        then(60, () -> cmd("caricraft desafio cofre emboscada"));
        then(40, () -> {
            log("COFRES tras crear: " + chests().stream().map(c -> c.kind() + "@" + (int) c.x() + "," + (int) c.y() + "," + (int) c.z()
                    + " oleadas=" + c.waves() + " nivel=" + c.level()).toList());
            logNotices();
        });
        page(PhonePage.CHALLENGE, 0);
        then(20, () -> phone().mouseScrolled(0, 0, 0, -6));
        shot("c01_app_cofre");
        closePhone();
        then(5, () -> {
            var chest = chests().getFirst();
            chestPos = new double[]{chest.x(), chest.y(), chest.z()};
            cmd(String.format(java.util.Locale.ROOT, "tp @s %.1f %.1f %.1f 135 15", chest.x() + 5, chest.y(), chest.z() + 5));
        });
        then(80, () -> {
            log("COFRE emboscada empezada: " + chests().stream().map(c -> "combate=" + c.fighting() + " oleada=" + c.wave()).toList()
                    + " · " + mobsNear());
            logNotices();
        });
        shot("c02_emboscada");
        for (int i = 1; i <= 4; i++) {
            int n = i;
            then(20, () -> cmd("kill @e[tag=caricraft_ambush]"));
            then(100, () -> {
                log("COFRE tras matar " + n + ": " + chests().stream().map(c -> "oleada=" + c.wave() + "/" + c.waves()).toList()
                        + " · " + mobsNear());
                logNotices();
            });
        }
        then(10, () -> {
            cmd(String.format(java.util.Locale.ROOT, "tp @s %.1f %.1f %.1f -135 30", chestPos[0] + 1.5, chestPos[1], chestPos[2] + 1.5));
        });
        then(30, () -> {
            var mc = Minecraft.getInstance();
            var pos = net.minecraft.core.BlockPos.containing(chestPos[0], chestPos[1], chestPos[2]);
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
        });
        then(30, () -> {
            var menu = Minecraft.getInstance().player.containerMenu;
            java.util.List<String> items = new java.util.ArrayList<>();
            for (int i = 0; i < Math.min(27, menu.slots.size()); i++) {
                var stack = menu.slots.get(i).getItem();
                if (!stack.isEmpty()) {
                    items.add(stack.getCount() + "x" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                            + (stack.isEnchanted() ? "(encantado)" : ""));
                }
            }
            log("COFRE botín: " + items);
        });
        shot("c03_botin");
        then(5, () -> Minecraft.getInstance().setScreen(null));

        // Guardián
        then(20, () -> cmd("tp @s 0 -60 0 0 10"));
        then(40, () -> cmd("caricraft desafio cofre guardian"));
        then(40, () -> {
            logNotices();
            var chest = chests().stream().filter(c -> c.kind().equals("GUARDIAN")).findFirst().orElse(null);
            log("COFRE guardián: " + (chest == null ? "-" : chest.guardian() + " nv." + chest.guardianLevel()));
            if (chest != null) {
                chestPos = new double[]{chest.x(), chest.y(), chest.z()};
                cmd(String.format(java.util.Locale.ROOT, "tp @s %.1f %.1f %.1f -90 15", chest.x() - 7, chest.y(), chest.z()));
            }
        });
        then(80, () -> {
            log("COFRE guardián cerca: " + mobsNear() + " · " + chests().stream().map(c -> "combate=" + c.fighting()).toList());
            logNotices();
        });
        shot("c04_guardian");
        then(5, () -> cmd("kill @e[tag=caricraft_ambush]"));
        then(60, () -> log("COFRE guardián tras matarlo sin combate (debe volver): " + mobsNear()));
        then(5, () -> cmd(String.format(java.util.Locale.ROOT, "tp @s %.1f %.1f %.1f", chestPos[0] + 150, chestPos[1], chestPos[2])));
        then(520, () -> cmd(String.format(java.util.Locale.ROOT, "tp @s %.1f %.1f %.1f -90 15", chestPos[0] - 7, chestPos[1], chestPos[2])));
        then(20, () -> log("COFRE guardián al volver (se había retirado; está esperando): " + mobsNear() + " · "
                + chests().stream().map(c -> "combate=" + c.fighting()).toList()));
        then(60, () -> log("COFRE guardián otra vez: " + mobsNear()));
        page(PhonePage.CHALLENGE, 0);
        then(20, () -> phone().mouseScrolled(0, 0, 0, -6));
        shot("c05_app_guardian");
        closePhone();

        // Limpieza al amanecer
        then(5, () -> {
            cmd("tp @s 0 -60 0 0 10");
            cmd("time set 14000");
        });
        then(40, () -> cmd("caricraft desafio iniciar"));
        then(40, () -> {
            for (int i = 0; i < 3; i++) {
                cmd("summon zombie ~" + (i * 2 + 3) + " ~ ~3");
            }
        });
        then(40, () -> log("AMANECER zombis del desafío de noche: " + mobsNear()));
        then(5, () -> cmd("tp @s 600 -60 600"));
        then(40, () -> cmd("time set 23500"));
        then(300, () -> cmd("tp @s 0 -60 0 0 10"));
        then(80, () -> {
            log("AMANECER al volver de día (deben ser 0): " + mobsNear());
            logNotices();
        });
        page(PhonePage.CHALLENGE, 0);
        shot("c06_app_nivel");
        closePhone();

        // Regalo misterioso: dos al día
        then(5, () -> act(PhoneAction.CV_GIFT, ""));
        then(30, QaAutomation::logNotices);
        then(5, () -> act(PhoneAction.CV_GIFT, ""));
        then(30, QaAutomation::logNotices);
        then(5, () -> act(PhoneAction.CV_GIFT, ""));
        then(30, () -> {
            logNotices();
            var cv = snap().cobbleverse();
            log("REGALOS quedan " + cv.giftsLeft() + "/" + cv.giftsPerDay());
        });
        then(20, () -> Minecraft.getInstance().stop());
    }

    private static CobbleCentroScreen cc() {
        return (CobbleCentroScreen) Minecraft.getInstance().screen;
    }

    // ------------------------------------------------------------------ bloque Cobbleverse del dúo (rol D)

    /** Una especie de cada tipo de la caza del día, para simular capturas que cuenten. */
    private static final java.util.Map<String, String> SPECIES_OF_TYPE = java.util.Map.ofEntries(
            java.util.Map.entry("normal", "rattata"), java.util.Map.entry("water", "magikarp"), java.util.Map.entry("grass", "oddish"),
            java.util.Map.entry("bug", "caterpie"), java.util.Map.entry("flying", "pidgey"), java.util.Map.entry("rock", "geodude"),
            java.util.Map.entry("ground", "diglett"), java.util.Map.entry("poison", "ekans"), java.util.Map.entry("electric", "pikachu"),
            java.util.Map.entry("fire", "growlithe"), java.util.Map.entry("fighting", "machop"), java.util.Map.entry("psychic", "abra"));

    private static void logRecentNotices(int n) {
        var list = new ArrayList<>(ClientPhoneState.notifications());
        for (int i = Math.min(n, list.size()) - 1; i >= 0; i--) {
            var notice = list.get(i);
            log("aviso: " + notice.key() + " " + notice.args());
        }
    }

    private static void logHunt(String label) {
        var hunt = snap().cobbleverse().hunt();
        log("CAZA " + label + ": " + hunt.targets().stream().map(t -> t.kind() + ":" + t.value() + " " + t.progress() + "/" + t.count()
                + (t.done() ? "✔" : "")).toList() + " completa=" + hunt.cleared() + " duo=" + hunt.duoBonus() + " semana="
                + hunt.weekClears() + "/" + hunt.weeklyClears() + " aportes=" + hunt.contrib() + " ₽" + snap().cobbleverse().cobbleDollars()
                + " carins=" + snap().balance());
    }

    private void scriptDuoCobbleverse() {
        then(0, () -> {
            cmd("gamemode creative");
            cmd("time set 6000");
            cmd("gamerule doDaylightCycle false");
            cmd("tp @s 0 -60 0 0 10");
            cmd("clear @s");
            cmd("cobbledollars give @s 50000");
            cmd("pokegive geodude level=18");
            cmd("pokegive pidgey level=12");
            cmd("pokegive magikarp level=8");
        });
        then(40, () -> {
            cmd("caricraft cobbleverse herir");
            cmd("caricraft cobbleverse cap");
        });
        then(60, () -> phone().openCobbleCentro());
        then(40, () -> {
            var cv = snap().cobbleverse();
            log("DUO vista: cap=" + cv.levelCap() + " capSpawns=" + cv.levelCapSpawns() + " heal=" + cv.heal() + " brújula=" + cv.shinyCompass());
        });
        shot("d01_inicio_curar");
        then(5, () -> cc().send(PhoneAction.CV_HEAL, ""));
        then(45, () -> {
            logRecentNotices(1);
            log("CURA después: " + snap().cobbleverse().heal() + " ₽" + snap().cobbleverse().cobbleDollars());
        });
        shot("d02_curado");
        then(5, () -> cc().send(PhoneAction.CV_HEAL, ""));
        then(30, () -> logRecentNotices(1));

        // Caza del día
        then(5, () -> {
            cc().select(CobbleCentroScreen.Tab.HUNT);
            logHunt("inicio");
        });
        shot("d03_caza_inicio");
        then(5, () -> {
            var hunt = snap().cobbleverse().hunt();
            var first = hunt.targets().getFirst();
            cmd("caricraft cobbleverse captura " + species(first));
        });
        then(40, () -> logHunt("una captura"));
        shot("d04_caza_progreso");
        then(5, () -> {
            for (var target : snap().cobbleverse().hunt().targets()) {
                for (int i = target.progress(); i < target.count(); i++) {
                    cmd("caricraft cobbleverse captura " + species(target));
                }
            }
        });
        then(60, () -> {
            logHunt("completa");
            logRecentNotices(6);
        });
        shot("d05_caza_completa");

        // Pokédex del dúo
        then(5, () -> {
            cmd("caricraft cobbleverse captura gible variocolor");
            cmd("caricraft cobbleverse captura magikarp variocolor");
            cmd("caricraft cobbleverse captura mewtwo");
            cmd("caricraft cobbleverse captura dratini");
            cc().select(CobbleCentroScreen.Tab.DEX);
        });
        then(50, () -> {
            var dex = snap().cobbleverse().dex();
            log("DEX: especies=" + dex.species() + " capturas=" + dex.captures() + " shinies=" + dex.shinyList().stream()
                    .map(e -> e.species() + "@" + e.player()).toList() + " legendarios=" + dex.legendList().stream().map(e -> e.species()).toList()
                    + " raras=" + dex.rares().stream().map(e -> e.species() + ":" + e.catchRate()).toList() + " metas="
                    + dex.goals().stream().filter(g -> g.done()).map(g -> g.id()).toList());
            logRecentNotices(5);
        });
        shot("d06_dex");
        then(5, () -> cc().mouseScrolled(0, 0, 0, -12));
        shot("d07_dex_metas");

        // Expedición
        then(5, () -> cc().select(CobbleCentroScreen.Tab.EXPEDITION));
        shot("d08_expedicion_elegir");
        then(5, () -> {
            var geodude = snap().cobbleverse().party().stream().filter(m -> m.species().endsWith("geodude")).findFirst().orElseThrow();
            log("EXP mando " + geodude.species() + " nv." + geodude.level() + " a la cueva 4 h; ₽" + snap().cobbleverse().cobbleDollars());
            cc().send(PhoneAction.CV_EXP_START, "cave:4", geodude.uuid());
        });
        then(45, () -> {
            logRecentNotices(1);
            log("EXP fuera: " + snap().cobbleverse().expedition().away() + " equipo=" + snap().cobbleverse().party().size());
        });
        shot("d09_expedicion_fuera");
        then(5, () -> cc().send(PhoneAction.CV_EXP_CLAIM, ""));
        then(30, () -> logRecentNotices(1));
        then(5, () -> cmd("caricraft cobbleverse expediciones"));
        then(50, () -> log("EXP lista: " + snap().cobbleverse().expedition().away()));
        shot("d10_expedicion_vuelta");
        then(5, () -> {
            log("EXP antes de recoger: ₽" + snap().cobbleverse().cobbleDollars() + " carbón=" + itemCount("minecraft:coal")
                    + " hierro=" + itemCount("minecraft:raw_iron") + " amatista=" + itemCount("minecraft:amethyst_shard"));
            cc().send(PhoneAction.CV_EXP_CLAIM, "");
        });
        then(45, () -> {
            logRecentNotices(1);
            log("EXP recogida: ₽" + snap().cobbleverse().cobbleDollars() + " equipo=" + snap().cobbleverse().party().size()
                    + " hoy=" + snap().cobbleverse().expedition().startedToday());
        });
        shot("d11_expedicion_recogida");

        // Aviso de variocolor con brújula
        then(5, () -> {
            Minecraft.getInstance().setScreen(null);
            cmd("caricraft cobbleverse variocolor gible");
        });
        then(30, () -> {
            logRecentNotices(1);
            log("VARIOCOLOR avisos: " + snap().cobbleverse().shinyAlerts());
        });
        shot("d12_brujula");
        then(5, () -> {
            var player = Minecraft.getInstance().player;
            var alert = snap().cobbleverse().shinyAlerts().getFirst();
            // Mira hacia el sur (yaw 0) para comprobar la flecha.
            log("VARIOCOLOR jugador en " + Math.round(player.getX()) + "," + Math.round(player.getZ()) + " → " + Math.round(alert.x()) + ","
                    + Math.round(alert.z()));
            cmd("tp @s ~ ~ ~ 0 10");
        });
        then(25, () -> {
        });
        shot("d13_brujula_sur");
        then(5, () -> phone().openCobbleCentro());
        then(20, () -> cc().select(CobbleCentroScreen.Tab.HOME));
        shot("d14_aviso_inicio");
        then(5, () -> {
            Minecraft.getInstance().setScreen(null);
            var alert = snap().cobbleverse().shinyAlerts().getFirst();
            cmd("tp @s " + (alert.x() + 2) + " " + (alert.y() + 1) + " " + alert.z());
        });
        then(30, () -> {
        });
        shot("d15_aqui");
        then(5, () -> cmd("kill @e[type=cobblemon:pokemon,distance=..12]"));
        then(60, () -> log("VARIOCOLOR tras desaparecer: " + snap().cobbleverse().shinyAlerts()));

        // Level cap con RCT: un Pokémon de nivel alto sube el rango de apariciones; el cap las frena.
        then(5, () -> {
            cmd("tp @s 0 -60 0 0 10");
            cmd("pokegive charizard level=70");
        });
        then(1200, () -> cmd("caricraft cobbleverse cap"));
        then(40, () -> {
            phone().openCobbleCentro();
        });
        then(30, () -> cc().select(CobbleCentroScreen.Tab.HOME));
        shot("d16_level_cap");
        then(20, () -> Minecraft.getInstance().stop());
    }

    // ------------------------------------------------------------------ bloque normal (rol R)

    private static void logRoutine(String label) {
        var r = snap().routine();
        if (r == null) {
            log("RUTINA " + label + ": null");
            return;
        }
        log("RUTINA " + label + ": racha=" + r.streak() + " evento=" + r.weekly() + " medallas="
                + r.medals().stream().filter(m -> m.tier() > 0).map(m -> m.id() + ":" + m.tier()).toList()
                + " trofeos=" + r.trophies().size() + " carins=" + snap().balance());
    }

    /** Racha diaria, evento semanal, medallas, vitrina y un jefe del desafío con su trofeo. */
    private void scriptRoutine() {
        then(0, () -> {
            cmd("gamemode creative");
            cmd("time set 6000");
            cmd("gamerule doDaylightCycle false");
            cmd("tp @s 0 -60 0 0 10");
            cmd("clear @s");
            cmd("caricraft desafio nivel 6");
        });
        then(60, () -> {
            logRoutine("inicio");
            act(PhoneAction.ACCEPT_MISSION, "daily_walk");
        });
        travel(22);
        then(40, () -> act(PhoneAction.CLAIM_MISSION, "daily_walk"));
        then(40, () -> {
            logRecentNotices(3);
            logRoutine("tras misión");
        });
        page(PhonePage.HOME, 0);
        shot("r01_inicio_racha");
        then(5, () -> {
            var weekly = snap().routine().weekly();
            cmd("caricraft rutina evento " + (weekly.goal() / 2));
        });
        then(140, () -> {
            logRecentNotices(3);
            logRoutine("mitad del evento");
        });
        then(5, () -> cmd("caricraft rutina evento " + snap().routine().weekly().goal()));
        then(140, () -> {
            logRecentNotices(4);
            logRoutine("evento completo");
            cmd("caricraft rutina");
        });
        page(PhonePage.HOME, 0);
        shot("r02_inicio_evento");
        page(PhonePage.SHOWCASE, 0);
        shot("r03_vitrina");
        // Jefe del desafío
        closePhone();
        then(5, () -> {
            cmd("time set 13500");
            cmd("caricraft desafio iniciar");
        });
        then(60, () -> cmd("caricraft desafio jefe"));
        then(60, () -> {
            logRecentNotices(2);
            var b = snap().challenge().bosses();
            log("JEFE vista: " + b);
        });
        page(PhonePage.CHALLENGE, 0);
        shot("r04_desafio_jefe");
        closePhone();
        then(5, () -> {
            var fight = snap().challenge().bosses().fight();
            if (fight != null) {
                cmd(String.format(java.util.Locale.ROOT, "tp @s %.1f %.1f %.1f", fight.x() + 9, fight.y() + 1, fight.z() + 9));
            }
        });
        then(10, () -> cmd("tp @s ~ ~ ~ facing entity @e[tag=caricraft_boss,limit=1] eyes"));
        then(30, () -> {
        });
        shot("r05_jefe");
        then(5, () -> cmd("damage @e[tag=caricraft_boss,limit=1] 5000 minecraft:player_attack by @s"));
        then(60, () -> {
            logRecentNotices(3);
            log("JEFE tras derrota: " + snap().challenge().bosses() + " trofeos en inventario=" + itemCount("caricraft:challenge_trophy")
                    + " diamantes en el suelo=" + Minecraft.getInstance().level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    Minecraft.getInstance().player.getBoundingBox().inflate(16)).size());
            logRoutine("final");
        });
        shot("r06_jefe_derrotado");
        page(PhonePage.SHOWCASE, 0);
        then(10, () -> phone().mouseScrolled(0, 0, 0, -20));
        shot("r07_vitrina_trofeo");
        then(20, () -> Minecraft.getInstance().stop());
    }

    // ------------------------------------------------------------------ limpieza del suelo (rol Z)

    private static String groundAt(double x, double z, double radius) {
        var level = Minecraft.getInstance().level;
        var box = new net.minecraft.world.phys.AABB(x - radius, -64, z - radius, x + radius, 320, z + radius);
        var items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).stream()
                .map(e -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).getPath()
                        + (e.getItem().has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) ? "(nombre)" : "")).sorted().toList();
        int orbs = level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, box).size();
        int arrows = level.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class, box).size();
        int mobs = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box).size();
        return "objetos=" + items + " orbes=" + orbs + " flechas=" + arrows + " mobs=" + mobs;
    }

    private static void summonGround() {
        cmd("summon item 3 -60 3 {Item:{id:\"minecraft:dirt\",count:64},Age:600}");
        cmd("summon item 3 -60 4 {Item:{id:\"minecraft:cobblestone\",count:64},Age:600}");
        cmd("summon item 4 -60 3 {Item:{id:\"minecraft:diamond\",count:1,components:{\"minecraft:custom_name\":'\"Regalo\"'}},Age:600}");
        cmd("summon item 4 -60 4 {Item:{id:\"minecraft:elytra\",count:1},Age:600}");
        cmd("summon item 5 -60 5 {Item:{id:\"minecraft:stick\",count:3},Age:0}");
        cmd("summon experience_orb 3 -60 5 {Value:7}");
        cmd("summon arrow 5 -59 3");
        cmd("summon cow 6 -60 6");
    }

    /** Limpieza del suelo: qué se borra, qué se protege, la zona de una muerte y los avisos de la limpieza automática. */
    private void scriptCleaner() {
        then(0, () -> {
            cmd("gamemode creative");
            cmd("time set 6000");
            cmd("gamerule doDaylightCycle false");
            cmd("gamerule doImmediateRespawn true");
            cmd("tp @s 0 -60 0 0 30");
            cmd("clear @s");
            cmd("caricraft limpieza");
            summonGround();
        });
        then(60, () -> log("LIMPIEZA antes: " + groundAt(4, 4, 4)));
        then(5, () -> cmd("caricraft limpieza ahora"));
        then(40, () -> {
            log("LIMPIEZA después: " + groundAt(4, 4, 4));
            logRecentNotices(1);
        });
        // Zona de una muerte
        then(5, () -> cmd("tp @s 40 -60 40"));
        then(30, () -> cmd("kill @s"));
        then(40, () -> {
            cmd("tp @s 0 -60 0 0 30");
            cmd("summon item 41 -60 41 {Item:{id:\"minecraft:iron_ingot\",count:20},Age:600}");
            cmd("summon item 60 -60 60 {Item:{id:\"minecraft:gold_ingot\",count:20},Age:600}");
        });
        then(40, () -> log("LIMPIEZA muerte antes: junto a la muerte " + groundAt(41, 41, 3) + " · lejos " + groundAt(60, 60, 3)));
        then(5, () -> cmd("caricraft limpieza ahora"));
        then(40, () -> log("LIMPIEZA muerte después: junto a la muerte " + groundAt(41, 41, 3) + " · lejos " + groundAt(60, 60, 3)));
        // Limpieza automática (en QA cada minuto) con sus avisos
        then(5, () -> {
            summonGround();
            cmd("caricraft limpieza");
        });
        when(() -> Minecraft.getInstance().gui.getChat() != null && ClientPhoneState.notifications().stream()
                .anyMatch(n -> n.key().equals("caricraft.notify.cleaner_soon")), () -> logRecentNotices(1));
        then(560, () -> {
        });
        shot("z01_aviso_limpieza");
        when(() -> ClientPhoneState.notifications().stream().anyMatch(n -> n.key().equals("caricraft.notify.cleaner_done")
                && System.currentTimeMillis() - n.createdAt() < 5000), () -> {
            logRecentNotices(1);
            log("LIMPIEZA automática después: " + groundAt(4, 4, 4));
        });
        shot("z02_limpieza_hecha");
        then(20, () -> Minecraft.getInstance().stop());
    }

    // ------------------------------------------------------------------ hucha, entrenador y mejoras del CobbleCentro (rol H)

    private static void logSavings(String label) {
        log("HUCHA " + label + ": " + snap().savings() + " monedero=" + snap().balance());
    }

    private static String upgrades() {
        return snap().apps().stream().filter(a -> a.id().startsWith("cc_")).map(a -> a.id() + ":" + a.level() + "/" + a.maxLevel()).toList()
                .toString();
    }

    private void scriptSavingsTrainer() {
        then(0, () -> {
            cmd("gamemode creative");
            cmd("time set 6000");
            cmd("gamerule doDaylightCycle false");
            cmd("tp @s 0 -60 0 0 10");
            cmd("clear @s");
            cmd("carins give Dev1 5000");
            cmd("pokegive pidgey level=10");
            cmd("pokegive geodude level=18");
            cmd("pokegive magikarp level=8");
        });
        then(60, () -> {
            logSavings("inicio");
            act(PhoneAction.BUY_ITEM, "savings");
        });
        then(30, () -> {
            logRecentNotices(1);
            act(PhoneAction.SAVINGS_DEPOSIT, "500");
        });
        then(30, () -> {
            logRecentNotices(1);
            act(PhoneAction.SAVINGS_WITHDRAW, "100");
        });
        then(30, () -> {
            logRecentNotices(1);
            act(PhoneAction.SAVINGS_WITHDRAW, "9999");
        });
        then(30, () -> {
            logRecentNotices(1);
            logSavings("tras meter 500 y sacar 100");
        });
        page(PhonePage.SAVINGS, 0);
        shot("h01_hucha");
        // Mejoras del CobbleCentro
        then(5, () -> {
            log("MEJORAS antes: " + upgrades());
            act(PhoneAction.BUY_ITEM, "cc_heal");
        });
        then(20, () -> act(PhoneAction.BUY_ITEM, "cc_heal"));
        then(20, () -> act(PhoneAction.BUY_ITEM, "cc_heal"));
        then(20, () -> act(PhoneAction.BUY_ITEM, "cc_expedition"));
        then(20, () -> act(PhoneAction.BUY_ITEM, "cc_radar"));
        then(30, () -> {
            logRecentNotices(3);
            log("MEJORAS después: " + upgrades() + " monedero=" + snap().balance());
        });
        then(5, () -> phone().openCobbleCentro());
        then(30, () -> cc().select(CobbleCentroScreen.Tab.WORKSHOP));
        then(20, () -> log("ENTRENADOR vista: " + snap().cobbleverse().trainer() + " equipo=" + snap().cobbleverse().party().stream()
                .map(m -> m.species() + ":" + m.level()).toList()));
        shot("h02_taller");
        then(5, () -> {
            var pidgey = snap().cobbleverse().party().stream().filter(m -> m.species().endsWith("pidgey")).findFirst().orElseThrow();
            cc().send(PhoneAction.CV_TRAIN, pidgey.uuid());
        });
        then(30, () -> {
            logRecentNotices(1);
            var pidgey = snap().cobbleverse().party().stream().filter(m -> m.species().endsWith("pidgey")).findFirst().orElseThrow();
            cc().send(PhoneAction.CV_TRAIN, pidgey.uuid());
        });
        then(30, () -> {
            logRecentNotices(1);
            log("ENTRENADOR después: " + snap().cobbleverse().trainer() + " equipo=" + snap().cobbleverse().party().stream()
                    .map(m -> m.species() + ":" + m.level()).toList() + " monedero=" + snap().balance());
        });
        shot("h03_entrenado");
        // Dos expediciones a la vez
        then(5, () -> {
            cc().select(CobbleCentroScreen.Tab.EXPEDITION);
            var geodude = snap().cobbleverse().party().stream().filter(m -> m.species().endsWith("geodude")).findFirst().orElseThrow();
            cc().send(PhoneAction.CV_EXP_START, "cave:1", geodude.uuid());
        });
        then(30, () -> {
            logRecentNotices(1);
            var magikarp = snap().cobbleverse().party().stream().filter(m -> m.species().endsWith("magikarp")).findFirst().orElseThrow();
            cc().send(PhoneAction.CV_EXP_START, "coast:1", magikarp.uuid());
        });
        then(40, () -> {
            logRecentNotices(1);
            var exp = snap().cobbleverse().expedition();
            log("EXPEDICIONES: plazas=" + exp.slots() + " fuera=" + exp.away().stream().map(a -> a.species() + "@" + a.destination()).toList()
                    + " hoy=" + exp.startedToday() + "/" + exp.perDay());
        });
        shot("h04_dos_expediciones");
        then(5, () -> cmd("caricraft cobbleverse expediciones"));
        then(50, () -> cc().send(PhoneAction.CV_EXP_CLAIM, "1"));
        then(30, () -> {
            logRecentNotices(1);
            log("EXPEDICIONES tras recoger la 2.ª: " + snap().cobbleverse().expedition().away().stream().map(a -> a.species()).toList());
        });
        // Centro Pokémon con la mejora a tope: espera de 5 min
        then(5, () -> cmd("caricraft cobbleverse herir"));
        then(20, () -> cc().send(PhoneAction.CV_HEAL, ""));
        then(40, () -> {
            var heal = snap().cobbleverse().heal();
            log("CURA con mejora: espera " + Math.round((heal.readyAt() - snap().serverTime()) / 60000.0) + " min");
        });
        then(20, () -> Minecraft.getInstance().stop());
    }

    private static String species(PhoneSnapshot.HuntTargetView target) {
        return switch (target.kind()) {
            case "SPECIES" -> target.value();
            case "TYPE" -> SPECIES_OF_TYPE.getOrDefault(target.value(), "rattata");
            default -> "rattata";
        };
    }

    // ------------------------------------------------------------------ modelo del CariPhone

    /** Cómo se ve el ítem del CariPhone: barra, mano, tercera persona e inventario (rol I). */
    private void scriptPhoneItem() {
        then(0, () -> {
            cmd("gamemode survival");
            cmd("time set 6000");
            cmd("gamerule doDaylightCycle false");
            cmd("weather clear");
            cmd("fill -3 119 -3 3 119 3 minecraft:grass_block");
            cmd("tp @s 0 120 0 0 10");
        });
        then(40, () -> Minecraft.getInstance().player.getInventory().selected = phoneSlot());
        then(20, () -> {
        });
        shot("i01_primera_persona");
        then(5, () -> Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        then(20, () -> {
        });
        shot("i02_tercera_persona_frente");
        then(5, () -> Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        then(20, () -> {
        });
        shot("i03_tercera_persona_espalda");
        then(5, () -> {
            var mc = Minecraft.getInstance();
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        });
        then(20, () -> {
        });
        shot("i04_inventario");
        closePhone();
        then(40, () -> Minecraft.getInstance().stop());
    }

    // ------------------------------------------------------------------ cobbleverse (solo interfaz)

    /** Mientras no sea null, se vuelve a poner el snapshot de ejemplo cada vez que el servidor manda uno nuevo. */
    private static Boolean fakeCobbleverseGift;

    private static void keepFakeCobbleverse() {
        if (fakeCobbleverseGift != null && snap() != null && snap().cobbleverse() == null) {
            injectCobbleverse(fakeCobbleverseGift);
        }
    }

    /** Snapshot de ejemplo con la parte Cobbleverse (en desarrollo no hay Cobblemon). */
    private static void injectCobbleverse(boolean giftAvailable) {
        fakeCobbleverseGift = giftAvailable;
        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(snap().toJson()).getAsJsonObject();
        String cv = """
                {"cobbleDollarsInstalled":true,"cobbleDollars":42350,"giftEnabled":true,"giftAvailable":%s,
                 "nextDayAt":%d,"shopEnabled":true,"offers":[
                  {"id":"shiny_mystery","icon":"minecraft:ender_eye","count":1,"species":["eevee","dratini","gible"],"level":15,"shiny":true,"perfectIvs":2,"moves":[],"price":80000,"boughtToday":0,"dailyLimit":1},
                  {"id":"eevee_extremespeed","icon":"minecraft:snowball","count":1,"species":["eevee"],"level":15,"shiny":false,"perfectIvs":2,"moves":["extremespeed","quickattack","bite","protect"],"price":30000,"boughtToday":0,"dailyLimit":1},
                  {"id":"ditto_6iv","icon":"minecraft:slime_ball","count":1,"species":["ditto"],"level":20,"shiny":false,"perfectIvs":6,"moves":[],"price":60000,"boughtToday":1,"dailyLimit":1},
                  {"id":"rare_candy","icon":"minecraft:sugar","item":"minecraft:sugar","count":5,"species":[],"level":0,"shiny":false,"perfectIvs":0,"moves":[],"price":12500,"boughtToday":0,"dailyLimit":2}],
                 "sellEnabled":true,"party":[
                  {"uuid":"11111111-1111-1111-1111-111111111111","species":"cobblemon:pikachu","nickname":"Chispa","level":24,"shiny":false,"ivTotal":112,"price":1700},
                  {"uuid":"22222222-2222-2222-2222-222222222222","species":"cobblemon:gible","nickname":"","level":31,"shiny":true,"ivTotal":140,"price":57870},
                  {"uuid":"33333333-3333-3333-3333-333333333333","species":"cobblemon:mewtwo","nickname":"","level":70,"shiny":false,"ivTotal":150,"price":0,"blocked":"legendary"}],
                 "salesToday":3,"maxSales":10}
                """.formatted(giftAvailable, snap().serverTime() + 5L * 3_600_000L + 1_200_000L);
        json.add("cobbleverse", com.google.gson.JsonParser.parseString(cv));
        com.chicoestudioso.caricraft.client.ClientPhoneState.acceptSnapshot(json.toString());
    }

    /** Pestaña Cobbleverse del Mercado con datos de ejemplo (rol V). */
    private void scriptCobbleverseUi() {
        then(0, () -> {
            cmd("gamemode creative");
            cmd("time set 1000");
            cmd("gamerule doDaylightCycle false");
            cmd("tp @s 0 120 0 -90 15");
        });
        page(PhonePage.MARKET, 0);
        then(20, () -> {
            injectCobbleverse(true);
            phone().marketTab = 1;
        });
        then(5, () -> {
        });
        shot("v01_cobbleverse_regalo");
        then(5, () -> {
            injectCobbleverse(true);
            phone().mouseScrolled(0, 0, 0, -12);
        });
        then(5, () -> {
        });
        shot("v02_cobbleverse_tienda");
        then(5, () -> {
            injectCobbleverse(false);
            phone().mouseScrolled(0, 0, 0, -30);
            phone().sellArmed = "11111111-1111-1111-1111-111111111111";
            phone().sellArmedAt = System.currentTimeMillis();
        });
        then(5, () -> {
        });
        shot("v03_cobbleverse_vender");
        then(5, () -> {
            injectCobbleverse(false);
            phone().mouseScrolled(0, 0, 0, 100);
        });
        then(5, () -> {
        });
        shot("v04_cobbleverse_regalo_abierto");
        then(5, () -> {
            injectCobbleverse(true);
            phone().goHome();
            phone().navigate(PhonePage.MARKET);
            phone().marketTab = 0;
        });
        then(5, () -> {
        });
        shot("v05_mercado_pestanas");
        then(40, () -> Minecraft.getInstance().stop());
    }

    // ------------------------------------------------------------------ escuadrones con botín

    private static net.minecraft.core.BlockPos cachePos(int index) {
        var caches = snap().challenge().caches();
        if (caches.size() <= index) {
            return null;
        }
        var c = caches.get(index);
        return net.minecraft.core.BlockPos.containing(c.x(), c.y(), c.z());
    }

    private static void useBlock(net.minecraft.core.BlockPos pos) {
        var mc = Minecraft.getInstance();
        mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                        net.minecraft.core.Direction.UP, pos, false));
    }

    /** Escuadrón con botín: aparece, el cofre está bloqueado, se abre al derrotarlo y desaparece al amanecer (rol S). */
    private void scriptSquads() {
        final net.minecraft.core.BlockPos[] chest = new net.minecraft.core.BlockPos[2];
        final long[] balance = new long[1];
        then(0, () -> {
            cmd("gamemode creative");
            cmd("gamerule doDaylightCycle false");
            cmd("gamerule doMobSpawning false");
            cmd("time set 1000");
            cmd("weather clear");
            cmd("fill -45 119 -45 45 119 45 minecraft:stone");
            cmd("tp @s 0 120 0 -90 15");
            cmd("give @s minecraft:diamond_sword");
        });
        then(40, () -> act(PhoneAction.CHALLENGE_BRING_FORWARD, ""));
        then(20, () -> cmd("time set 13000"));
        then(40, () -> cmd("time set 13700"));
        when(() -> snap() != null && snap().challenge() != null && !snap().challenge().caches().isEmpty(), () -> {
            chest[0] = cachePos(0);
            balance[0] = snap().balance();
            log("SQUAD aparece en " + chest[0] + " · guardias=" + snap().challenge().caches().getFirst().guardsLeft()
                    + " · bloque=" + Minecraft.getInstance().level.getBlockState(chest[0]).getBlock());
        });
        then(10, () -> {
        });
        shot("s01_escuadron_aviso");
        page(PhonePage.CHALLENGE, 0);
        then(20, () -> {
        });
        shot("s02_app_botin");
        closePhone();
        // Cerca del cofre, mirándolo.
        then(10, () -> {
            var p = chest[0];
            cmd("tp @s " + (p.getX() + 7) + " " + (p.getY() + 1) + " " + (p.getZ() + 0.5) + " facing " + (p.getX() + 0.5) + " "
                    + (p.getY() + 0.5) + " " + (p.getZ() + 0.5));
        });
        then(30, () -> {
        });
        shot("s03_escuadron_cerca");
        // Bloqueado mientras quedan guardias (al lado del cofre, al alcance de la mano).
        then(10, () -> {
            var p = chest[0];
            cmd("tp @s " + (p.getX() + 2.5) + " " + p.getY() + " " + (p.getZ() + 0.5) + " facing " + (p.getX() + 0.5) + " "
                    + (p.getY() + 0.5) + " " + (p.getZ() + 0.5));
        });
        then(20, () -> useBlock(chest[0]));
        then(20, () -> log("SQUAD abrir bloqueado (false): " + (Minecraft.getInstance().player.containerMenu
                instanceof net.minecraft.world.inventory.ChestMenu)));
        shot("s04_cofre_bloqueado");
        // Se derrota al escuadrón.
        then(10, () -> cmd("kill @e[tag=caricraft_squad]"));
        when(() -> snap().challenge().caches().stream().allMatch(PhoneSnapshot.CacheView::open), () ->
                log("SQUAD derrotado · Carins +" + (snap().balance() - balance[0])));
        then(10, () -> {
        });
        shot("s05_escuadron_derrotado");
        then(10, () -> useBlock(chest[0]));
        when(() -> Minecraft.getInstance().player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu, () -> {
            var menu = Minecraft.getInstance().player.containerMenu;
            java.util.List<String> loot = new java.util.ArrayList<>();
            for (int i = 0; i < 27; i++) {
                var stack = menu.slots.get(i).getItem();
                if (!stack.isEmpty()) {
                    loot.add(stack.getCount() + "x" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
                }
            }
            log("SQUAD botín (" + loot.size() + "): " + loot);
        });
        then(10, () -> {
        });
        shot("s06_botin");
        then(10, () -> Minecraft.getInstance().player.closeContainer());
        // Amanece; otra noche, y esta vez no se derrota: el cofre desaparece al amanecer.
        then(10, () -> cmd("time set 23100"));
        then(60, () -> log("SQUAD cofre abierto tras amanecer sigue (chest): "
                + Minecraft.getInstance().level.getBlockState(chest[0]).getBlock()));
        then(10, () -> cmd("tp @s 0 120 0 -90 15"));
        // «time set» es absoluto: la noche siguiente es 24000 + 13000.
        then(20, () -> act(PhoneAction.CHALLENGE_BRING_FORWARD, ""));
        then(20, () -> cmd("time set 37000"));
        then(40, () -> cmd("time set 37700"));
        when(() -> snap() != null && !snap().challenge().caches().isEmpty(), () -> {
            chest[1] = cachePos(0);
            log("SQUAD segunda noche en " + chest[1]);
        });
        then(20, () -> cmd("time set 47100"));
        then(60, () -> {
            var mc = Minecraft.getInstance();
            int guards = mc.level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, mc.player.getBoundingBox().inflate(96),
                    m -> m.getTags().contains("caricraft_squad")).size();
            log("SQUAD al amanecer sin derrotar: bloque=" + mc.level.getBlockState(chest[1]).getBlock()
                    + " (aire) · guardias=" + guards + " (0) · botines en el teléfono=" + snap().challenge().caches().size() + " (0)");
        });
        then(60, () -> Minecraft.getInstance().stop());
    }

    // ------------------------------------------------------------------ avisos, consejos y barra

    /** Consejo en el chat, notificación emergente y barra de jefe del desafío (rol T). */
    private void scriptNotices() {
        then(0, () -> {
            cmd("gamemode creative");
            cmd("gamerule doDaylightCycle false");
            cmd("time set 1000");
            cmd("weather clear");
            cmd("fill -4 119 -4 4 119 4 minecraft:stone");
            cmd("tp @s 0 120 0 -90 15");
        });
        // El primer consejo llega al entrar; pedimos otro para la captura.
        then(40, () -> cmd("caricraft consejo"));
        then(20, () -> {
        });
        shot("t01_consejo_chat");
        // Desafío adelantado: aviso amarillo antes de anochecer.
        then(10, () -> act(PhoneAction.CHALLENGE_BRING_FORWARD, ""));
        then(20, () -> cmd("time set 11800"));
        then(40, () -> {
        });
        shot("t02_barra_aviso");
        // Empieza: notificación emergente y barra roja.
        then(10, () -> cmd("time set 13000"));
        then(30, () -> {
        });
        shot("t03_notificacion_y_barra");
        then(10, () -> cmd("time set 18000"));
        then(40, () -> {
        });
        shot("t04_barra_oleada");
        // Ajustes: desactivar consejos.
        then(10, () -> act(PhoneAction.TOGGLE_TIPS, ""));
        page(PhonePage.SETTINGS, 0);
        then(30, () -> {
        });
        shot("t05_ajustes_consejos");
        then(5, () -> log("TIPS activados tras desactivar (false): " + snap().tipsEnabled()));
        closePhone();
        // Amanece: notificación de noche superada y la barra desaparece.
        then(10, () -> cmd("time set 23100"));
        then(30, () -> {
        });
        shot("t06_amanecer");
        then(60, () -> Minecraft.getInstance().stop());
    }

    // ------------------------------------------------------------------ CariPhone personal

    /** Teléfonos del inventario: cuántos y de quién (dueño#serie@hueco). */
    private static String phones() {
        var player = Minecraft.getInstance().player;
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (com.chicoestudioso.caricraft.item.PhoneBinding.isPhone(stack)) {
                var tag = com.chicoestudioso.caricraft.item.PhoneBinding.read(stack);
                out.add((tag.bound() ? tag.ownerName() + "#" + tag.serial() : "sin-dueño") + "@" + i);
            }
        }
        return out.size() + " " + out;
    }

    private static int groundPhones() {
        var mc = Minecraft.getInstance();
        return mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                mc.player.getBoundingBox().inflate(32), e -> com.chicoestudioso.caricraft.item.PhoneBinding.isPhone(e.getItem())).size();
    }

    private static int phoneSlot() {
        var player = Minecraft.getInstance().player;
        for (int i = 0; i < 36; i++) {
            if (com.chicoestudioso.caricraft.item.PhoneBinding.isPhone(player.getInventory().getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    /** CariPhone personal: único, no se tira ni se guarda, vuelve al morir y se repone (rol P). */
    private void scriptPhone() {
        then(0, () -> {
            cmd("gamemode survival");
            cmd("time set 1000");
            cmd("gamerule doDaylightCycle false");
            cmd("weather clear");
            cmd("fill -4 119 -4 4 119 4 minecraft:stone");
            cmd("tp @s 0 120 0 -90 15");
        });
        then(40, () -> {
            log("PHONE inicial (1 propio): " + phones());
            var stack = Minecraft.getInstance().player.getInventory().getItem(phoneSlot());
            var lines = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(Minecraft.getInstance().level),
                    Minecraft.getInstance().player, net.minecraft.world.item.TooltipFlag.NORMAL);
            log("PHONE descripción: " + lines.stream().map(net.minecraft.network.chat.Component::getString).toList());
        });
        // 1. Tecla de tirar con el teléfono en la mano
        then(10, () -> {
            var player = Minecraft.getInstance().player;
            player.getInventory().selected = phoneSlot();
        });
        then(10, () -> Minecraft.getInstance().player.drop(false));
        then(5, () -> {
        });
        shot("p01_no_se_tira");
        then(20, () -> log("PHONE tras Q (1, 0 en el suelo): " + phones() + " · suelo=" + groundPhones()));
        // 2. Tirarlo desde el inventario
        then(10, () -> {
            var mc = Minecraft.getInstance();
            int slot = phoneSlot();
            int menuSlot = slot < 9 ? 36 + slot : slot;
            mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, menuSlot, 1,
                    net.minecraft.world.inventory.ClickType.THROW, mc.player);
        });
        then(30, () -> log("PHONE tras tirarlo desde el inventario (1, 0 en el suelo): " + phones() + " · suelo=" + groundPhones()));
        // 3. Cofre: mayúsculas+clic y colocarlo a mano
        then(10, () -> cmd("setblock 2 120 0 minecraft:chest"));
        then(20, () -> {
            var mc = Minecraft.getInstance();
            mc.player.getInventory().selected = 8;
            var pos = new net.minecraft.core.BlockPos(2, 120, 0);
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                            net.minecraft.core.Direction.WEST, pos, false));
        });
        when(() -> Minecraft.getInstance().player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu, () -> {
            var mc = Minecraft.getInstance();
            var menu = mc.player.containerMenu;
            int slot = phoneSlot();
            int menuSlot = slot < 9 ? 54 + slot : 27 + slot - 9;
            mc.gameMode.handleInventoryMouseClick(menu.containerId, menuSlot, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player);
            mc.gameMode.handleInventoryMouseClick(menu.containerId, menuSlot, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
            mc.gameMode.handleInventoryMouseClick(menu.containerId, 4, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
        });
        then(10, () -> {
        });
        shot("p02_cofre");
        then(10, () -> {
            var mc = Minecraft.getInstance();
            var menu = mc.player.containerMenu;
            int inChest = 0;
            for (int i = 0; i < 27; i++) {
                if (com.chicoestudioso.caricraft.item.PhoneBinding.isPhone(menu.slots.get(i).getItem())) {
                    inChest++;
                }
            }
            boolean carried = com.chicoestudioso.caricraft.item.PhoneBinding.isPhone(menu.getCarried());
            log("PHONE en el cofre (0): " + inChest + " · en el cursor: " + carried);
            // Lo devolvemos a un hueco libre del inventario.
            for (int i = 27; i < 63 && carried; i++) {
                if (menu.slots.get(i).getItem().isEmpty()) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
                    break;
                }
            }
        });
        then(10, () -> Minecraft.getInstance().player.closeContainer());
        then(30, () -> log("PHONE tras el cofre (1): " + phones()));
        // 4. Uno de más y uno de otro jugador
        then(10, () -> cmd("give @s caricraft:cariphone"));
        then(10, () -> cmd("give @s caricraft:cariphone[minecraft:custom_data={caricraft_phone:{owner:[I;1,2,3,4],name:\"Otro\",serial:1}}]"));
        then(50, () -> log("PHONE tras /give extra y ajeno (1, el mismo): " + phones()));
        // 5. Desaparece: vuelve con la serie siguiente
        then(10, () -> cmd("clear @s caricraft:cariphone"));
        then(10, () -> log("PHONE justo tras /clear (0): " + phones()));
        then(100, () -> log("PHONE tras esperar (1, serie +1): " + phones()));
        // 6. Morir sin keepInventory
        then(10, () -> {
            cmd("gamerule keepInventory false");
            cmd("give @s minecraft:dirt 5");
        });
        then(10, () -> cmd("kill @s"));
        then(40, () -> Minecraft.getInstance().player.respawn());
        then(10, () -> cmd("tp @s 0 120 0 -90 15"));
        then(30, () -> {
        });
        shot("p03_tras_morir");
        then(10, () -> log("PHONE tras morir (1, misma serie, 0 en el suelo): " + phones() + " · suelo=" + groundPhones()));
        // 7. La tecla abre el teléfono
        then(10, () -> {
            var mc = Minecraft.getInstance();
            mc.setScreen(new PhoneScreen());
        });
        then(30, () -> log("PHONE la pantalla se abre: " + (Minecraft.getInstance().screen instanceof PhoneScreen)));
        closePhone();
        then(60, () -> Minecraft.getInstance().stop());
    }

    /** Guion corto solo para el campamento. */
    private void scriptCampOnly() {
        then(0, () -> {
            cmd("gamemode creative");
            cmd("tp @s 0 120 0 -90 15");
        });
        then(20, () -> {
            cmd("fill -4 119 -4 4 119 4 minecraft:grass_block");
            cmd("setblock 2 120 0 minecraft:campfire[lit=true]");
            cmd("carins give @s 1000");
        });
        then(10, () -> act(PhoneAction.BUY_ITEM, "caricamp"));
        then(20, () -> {
            var mc = Minecraft.getInstance();
            log("bloque en 2,120,0: " + mc.level.getBlockState(new net.minecraft.core.BlockPos(2, 120, 0))
                    + " · jugador en " + mc.player.blockPosition());
            act(PhoneAction.CAMP_SET, "");
        });
        then(20, () -> {
            var n = com.chicoestudioso.caricraft.client.ClientPhoneState.notifications().peekFirst();
            log("último aviso: " + (n == null ? "-" : n.key() + " " + n.args()));
            log("campamento: " + snap().camp().camp());
        });
        then(20, () -> Minecraft.getInstance().stop());
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
