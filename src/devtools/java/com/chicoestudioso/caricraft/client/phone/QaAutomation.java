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
        } else if ("I".equals(role)) {
            scriptPhoneItem();
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

    private static CobbleCentroScreen cc() {
        return (CobbleCentroScreen) Minecraft.getInstance().screen;
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
