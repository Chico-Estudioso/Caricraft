package com.chicoestudioso.caricraft.server.camp;

import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.CaricraftCore;
import com.chicoestudioso.caricraft.core.camp.CampService;
import com.chicoestudioso.caricraft.core.camp.CampService.Destination;
import com.chicoestudioso.caricraft.core.camp.TravelRequest;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import com.chicoestudioso.caricraft.server.challenge.ChallengeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Viajes en el servidor. TP Dúo funciona al estilo /tpa: el compañero acepta la petición y luego hay una
 * cuenta atrás sin moverse. Al campamento de CariCamp también hay cuenta atrás. Se cobra al llegar.
 */
public final class TravelManager {
    private record Warmup(Destination destination, UUID target, ResourceKey<Level> dimension, Vec3 start, int ticksLeft) {
        Warmup tick() {
            return new Warmup(destination, target, dimension, start, ticksLeft - 1);
        }
    }

    private static final Map<UUID, Warmup> WARMUPS = new HashMap<>();

    private TravelManager() {
    }

    public static void forget(UUID player) {
        WARMUPS.remove(player);
    }

    public static boolean warmingUp(UUID player) {
        return WARMUPS.containsKey(player);
    }

    /** Destino de la cuenta atrás en marcha, o {@code null}. */
    public static Destination warmupDestination(UUID player) {
        Warmup warmup = WARMUPS.get(player);
        return warmup == null ? null : warmup.destination();
    }

    public static int warmupSecondsLeft(UUID player) {
        Warmup warmup = WARMUPS.get(player);
        return warmup == null ? 0 : (warmup.ticksLeft() + 19) / 20;
    }

    // ------------------------------------------------------------------ acciones

    /** Viaje al campamento propio: comprueba, y empieza la cuenta atrás. */
    public static ActionResult travelToCamp(CaricraftServer mod, ServerPlayer player) {
        CaricraftCore core = mod.core();
        Party party = core.partyOf(player.getUUID());
        ActionResult check = CampTravel.checkCamp(mod, party);
        if (!check.success()) {
            return check;
        }
        ActionResult rules = preCheck(core, party, player, Destination.CAMP);
        if (!rules.success()) {
            return rules;
        }
        start(mod, player, Destination.CAMP, null);
        return ActionResult.ok("travel_warmup", core.config().camp.warmupSeconds);
    }

    /** Petición al compañero (ir hacia él o traerlo). */
    public static ActionResult request(CaricraftServer mod, ServerPlayer player, TravelRequest.Kind kind) {
        CaricraftCore core = mod.core();
        Party party = core.partyOf(player.getUUID());
        ServerPlayer partner = onlinePartner(mod, party, player.getUUID());
        if (partner == null) {
            return ActionResult.fail(party != null && party.isDuo() ? "partner_offline" : "not_in_duo");
        }
        boolean challenge = ChallengeManager.challengeTonight(player);
        ActionResult result = core.camp().request(core.state(), player.getUUID(), partner.getUUID(), kind, challenge);
        if (result.success()) {
            String name = player.getGameProfile().getName();
            mod.notify(partner, true, kind == TravelRequest.Kind.GO ? "caricraft.notify.travel_request_go"
                    : "caricraft.notify.travel_request_bring", List.of(name));
            partner.sendSystemMessage(requestMessage(name, kind, core.config().camp.requestSeconds));
            mod.sendSnapshot(partner);
        }
        return result;
    }

    private static MutableComponent requestMessage(String name, TravelRequest.Kind kind, int seconds) {
        MutableComponent message = Component.translatable(kind == TravelRequest.Kind.GO
                        ? "caricraft.travel.chat_go" : "caricraft.travel.chat_bring", name, seconds)
                .withStyle(ChatFormatting.AQUA);
        return message.append(" ")
                .append(button("caricraft.travel.chat_accept", "/caricraft viaje aceptar", ChatFormatting.GREEN))
                .append(" ")
                .append(button("caricraft.travel.chat_decline", "/caricraft viaje rechazar", ChatFormatting.RED));
    }

    private static MutableComponent button(String key, String command, ChatFormatting color) {
        return Component.translatable(key).withStyle(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    /** El jugador acepta la petición que le han hecho: empieza la cuenta atrás del viajero. */
    public static ActionResult accept(CaricraftServer mod, ServerPlayer accepter) {
        CaricraftCore core = mod.core();
        CampService.Acceptance acceptance = core.camp().accept(core.state(), accepter.getUUID());
        if (acceptance.request() == null) {
            return acceptance.result();
        }
        TravelRequest request = acceptance.request();
        ServerPlayer traveler = mod.server().getPlayerList().getPlayer(request.traveler());
        ServerPlayer requester = mod.server().getPlayerList().getPlayer(request.from());
        if (traveler == null || mod.server().getPlayerList().getPlayer(request.destination()) == null) {
            return ActionResult.fail("partner_offline");
        }
        Party party = core.partyOf(traveler.getUUID());
        ActionResult rules = preCheck(core, party, traveler, Destination.PARTNER);
        if (!rules.success()) {
            return rules;
        }
        start(mod, traveler, Destination.PARTNER, request.destination());
        if (requester != null && !requester.getUUID().equals(accepter.getUUID())) {
            mod.notify(requester, true, "caricraft.notify.travel_accepted", List.of(accepter.getGameProfile().getName()));
            mod.sendSnapshot(requester);
        }
        return acceptance.result();
    }

    public static ActionResult decline(CaricraftServer mod, ServerPlayer player) {
        CaricraftCore core = mod.core();
        var incoming = core.camp().incoming(core.state(), player.getUUID());
        ActionResult result = core.camp().decline(core.state(), player.getUUID());
        if (result.success() && incoming.isPresent()) {
            ServerPlayer requester = mod.server().getPlayerList().getPlayer(incoming.get().from());
            if (requester != null) {
                mod.notify(requester, false, "caricraft.notify.travel_declined", List.of(player.getGameProfile().getName()));
                mod.sendSnapshot(requester);
            }
        }
        return result;
    }

    private static ActionResult preCheck(CaricraftCore core, Party party, ServerPlayer traveler, Destination destination) {
        if (!core.camp().canTravel(party, destination)) {
            return ActionResult.fail("app_locked", CampService.requiredApp(destination));
        }
        if (WARMUPS.containsKey(traveler.getUUID())) {
            return ActionResult.fail("travel_in_progress");
        }
        if (ChallengeManager.challengeTonight(traveler) && core.config().camp.blockDuringChallenge) {
            return ActionResult.fail("travel_challenge");
        }
        long until = core.camp().cooldownEndsAt(party, traveler.getUUID());
        if (until > core.clock().now()) {
            return ActionResult.fail("travel_cooldown", Math.max(1, (until - core.clock().now() + 999) / 1000));
        }
        if (party.wallet().balance() < core.camp().cost(destination)) {
            return ActionResult.fail("insufficient_funds", core.camp().cost(destination) - party.wallet().balance());
        }
        return ActionResult.ok("ok");
    }

    private static void start(CaricraftServer mod, ServerPlayer traveler, Destination destination, UUID target) {
        int ticks = mod.core().config().camp.warmupSeconds * 20;
        WARMUPS.put(traveler.getUUID(), new Warmup(destination, target, traveler.level().dimension(), traveler.position(), ticks));
        if (ticks > 0) {
            traveler.displayClientMessage(Component.translatable("caricraft.travel.warmup", (ticks + 19) / 20)
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    // ------------------------------------------------------------------ tick

    public static void tick(CaricraftServer mod) {
        if (WARMUPS.isEmpty()) {
            return;
        }
        for (UUID uuid : List.copyOf(WARMUPS.keySet())) {
            Warmup warmup = WARMUPS.get(uuid);
            ServerPlayer traveler = mod.server().getPlayerList().getPlayer(uuid);
            if (traveler == null) {
                WARMUPS.remove(uuid);
                continue;
            }
            if (!traveler.level().dimension().equals(warmup.dimension()) || traveler.position().distanceToSqr(warmup.start()) > 1.0) {
                WARMUPS.remove(uuid);
                traveler.displayClientMessage(Component.translatable("caricraft.travel.cancelled_moved")
                        .withStyle(ChatFormatting.RED), true);
                mod.sendSnapshot(traveler);
                continue;
            }
            Warmup next = warmup.tick();
            if (next.ticksLeft() > 0) {
                WARMUPS.put(uuid, next);
                if (next.ticksLeft() % 20 == 0) {
                    traveler.displayClientMessage(Component.translatable("caricraft.travel.warmup", next.ticksLeft() / 20)
                            .withStyle(ChatFormatting.AQUA), true);
                }
                continue;
            }
            WARMUPS.remove(uuid);
            ActionResult result = finish(mod, traveler, warmup);
            mod.notify(traveler, result.success(), result.key(), result.args());
            mod.markDirty();
            mod.sendPartySnapshots(uuid);
        }
    }

    private static ActionResult finish(CaricraftServer mod, ServerPlayer traveler, Warmup warmup) {
        CaricraftCore core = mod.core();
        Party party = core.partyOf(traveler.getUUID());
        if (warmup.destination() == Destination.CAMP) {
            return CampTravel.travelToCamp(mod, traveler, party);
        }
        ServerPlayer target = mod.server().getPlayerList().getPlayer(warmup.target());
        if (target == null || party == null || !party.members().contains(target.getUUID())) {
            return ActionResult.fail("partner_offline");
        }
        ActionResult charged = core.camp().chargeTravel(party, traveler.getUUID(), Destination.PARTNER,
                ChallengeManager.challengeTonight(traveler));
        if (!charged.success()) {
            return charged;
        }
        if (!CampTravel.teleport(traveler, target.serverLevel(), target.position())) {
            core.camp().refund(party, traveler.getUUID(), Destination.PARTNER);
            return ActionResult.fail("internal_error");
        }
        mod.notify(target, true, "caricraft.notify.partner_arrived", List.of(traveler.getGameProfile().getName()));
        return charged;
    }

    private static ServerPlayer onlinePartner(CaricraftServer mod, Party party, UUID self) {
        if (party == null) {
            return null;
        }
        for (UUID member : party.members()) {
            if (!member.equals(self)) {
                ServerPlayer online = mod.server().getPlayerList().getPlayer(member);
                if (online != null) {
                    return online;
                }
            }
        }
        return null;
    }

    /** Para el snapshot. */
    static ServerLevel levelOf(ServerPlayer player) {
        return player.serverLevel();
    }
}
