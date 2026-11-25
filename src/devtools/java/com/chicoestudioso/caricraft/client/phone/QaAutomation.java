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
        then(10, () -> act(PhoneAction.BUY_ITEM, "carilens"));
        then(10, () -> act(PhoneAction.BUY_ITEM, "carilens_album"));
        then(10, () -> act(PhoneAction.BUY_ITEM, "caribox"));
        page(PhonePage.SHOP, 0);
        shot("n03_tienda_mejoras");
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
        // Viajes estilo /tpa: Dev2 tiene que aceptar.
        then(140, () -> act(PhoneAction.TRAVEL_REQUEST, "go"));
        page(PhonePage.CARICAMP, 0);
        shot("n05b_peticion_enviada");
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
        when(() -> snap() != null && snap().camp() != null && snap().camp().incoming() != null, () -> {
        });
        shot("m00_chat_peticion");
        page(PhonePage.CARICAMP, 0);
        shot("m00b_peticion_recibida");
        then(10, () -> act(PhoneAction.TRAVEL_ACCEPT, ""));
        closePhone();
        // Petición "traer": aceptamos y nos quedamos quietos durante la cuenta atrás.
        when(() -> snap() != null && snap().camp() != null && snap().camp().incoming() != null, () -> {
            log("petición recibida: " + snap().camp().incoming().kind());
            act(PhoneAction.TRAVEL_ACCEPT, "");
        });
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
