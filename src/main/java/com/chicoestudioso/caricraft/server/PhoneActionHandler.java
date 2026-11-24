package com.chicoestudioso.caricraft.server;

import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.CaricraftCore;
import com.chicoestudioso.caricraft.core.CaricraftState;
import com.chicoestudioso.caricraft.network.PhoneAction;
import com.chicoestudioso.caricraft.network.PhoneActionPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Recibe las acciones del CariPhone (en el hilo del servidor), las valida y delega en el núcleo.
 */
public final class PhoneActionHandler {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private PhoneActionHandler() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(PhoneActionPayload.TYPE,
                (payload, context) -> handle(context.player(), payload));
    }

    static void handle(ServerPlayer player, PhoneActionPayload payload) {
        CaricraftServer mod = CaricraftServer.get();
        if (mod == null || payload.action() == null) {
            return;
        }
        if (!mod.rateLimiter().tryAcquire(player.getUUID())) {
            return;
        }
        CaricraftCore core = mod.core();
        CaricraftState state = core.state();
        UUID uuid = player.getUUID();
        core.onPlayerJoin(uuid, player.getGameProfile().getName());

        // Jugadores cuyo teléfono cambia con esta acción, además del grupo del actor.
        Set<UUID> alsoRefresh = new HashSet<>();
        ActionResult result;
        try {
            result = switch (payload.action()) {
                case REQUEST_SNAPSHOT -> null;
                case BUY_ITEM -> validId(payload.arg1()) ? core.shop().buy(state, uuid, payload.arg1()) : invalid();
                case ACCEPT_MISSION -> validId(payload.arg1()) ? core.missions().accept(state, uuid, payload.arg1()) : invalid();
                case ABANDON_MISSION -> validId(payload.arg1()) ? core.missions().abandon(state, uuid, payload.arg1()) : invalid();
                case CLAIM_MISSION -> validId(payload.arg1()) ? core.missions().claim(state, uuid, payload.arg1()) : invalid();
                case DELIVER_MISSION -> validId(payload.arg1())
                        ? core.missions().deliver(state, uuid, payload.arg1(), new ServerInventoryAccess(player)) : invalid();
                case INVITE_PLAYER -> invite(mod, player, payload.arg1(), alsoRefresh);
                case ACCEPT_INVITE -> withUuid(payload.arg1(), from -> {
                    ActionResult r = core.parties().accept(state, uuid, from);
                    alsoRefresh.add(from);
                    return r;
                });
                case DECLINE_INVITE -> withUuid(payload.arg1(), from -> {
                    alsoRefresh.add(from);
                    return core.parties().decline(state, uuid, from);
                });
                case CANCEL_INVITE -> withUuid(payload.arg1(), to -> {
                    alsoRefresh.add(to);
                    return core.parties().cancel(state, uuid, to);
                });
                case LEAVE_PARTY -> core.parties().requestLeave(state, uuid);
                case CONFIRM_LEAVE_PARTY -> {
                    alsoRefresh.addAll(core.partyOf(uuid).members());
                    yield core.parties().confirmLeave(state, uuid);
                }
                case SELECT_THEME -> validId(payload.arg1()) ? core.shop().selectTheme(state, uuid, payload.arg1()) : invalid();
                case ADD_NOTE -> core.addNote(uuid, payload.arg1());
            };
        } catch (RuntimeException e) {
            CaricraftMod.LOGGER.error("Error procesando {} de {}", payload.action(), player.getGameProfile().getName(), e);
            result = ActionResult.fail("internal_error");
        }

        if (result != null) {
            mod.markDirty();
            mod.notify(player, result.success(), result.key(), result.args());
            if (result.success() && payload.action() == PhoneAction.CLAIM_MISSION) {
                notifyPartner(mod, player, "caricraft.notify.partner_claimed", List.of(player.getGameProfile().getName(), payload.arg1()));
            }
            if (result.success() && payload.action() == PhoneAction.BUY_ITEM) {
                notifyPartner(mod, player, "caricraft.notify.partner_bought", List.of(player.getGameProfile().getName(), payload.arg1()));
            }
            if (result.success() && payload.action() == PhoneAction.ACCEPT_INVITE) {
                ServerPlayer inviter = onlinePlayer(mod, payload.arg1());
                if (inviter != null) {
                    mod.notify(inviter, true, "caricraft.notify.invite_accepted", List.of(player.getGameProfile().getName()));
                }
            }
        }
        mod.sendPartySnapshots(uuid);
        for (UUID other : alsoRefresh) {
            ServerPlayer online = mod.server().getPlayerList().getPlayer(other);
            if (online != null && !core.partyOf(uuid).members().contains(other)) {
                mod.sendSnapshot(online);
            }
        }
    }

    private static ActionResult invite(CaricraftServer mod, ServerPlayer player, String name, Set<UUID> alsoRefresh) {
        if (!PLAYER_NAME.matcher(name).matches()) {
            return ActionResult.fail("player_not_found", name);
        }
        ServerPlayer target = mod.server().getPlayerList().getPlayerByName(name);
        if (target == null) {
            return ActionResult.fail("player_not_found", name);
        }
        CaricraftCore core = mod.core();
        core.onPlayerJoin(target.getUUID(), target.getGameProfile().getName());
        ActionResult result = core.parties().invite(core.state(), player.getUUID(), target.getUUID());
        if (result.success()) {
            alsoRefresh.add(target.getUUID());
            mod.notify(target, true, "caricraft.notify.invite_received", List.of(player.getGameProfile().getName()));
        }
        return result;
    }

    private static void notifyPartner(CaricraftServer mod, ServerPlayer actor, String key, List<String> args) {
        for (UUID member : new ArrayList<>(mod.core().partyOf(actor.getUUID()).members())) {
            if (!member.equals(actor.getUUID())) {
                ServerPlayer online = mod.server().getPlayerList().getPlayer(member);
                if (online != null) {
                    mod.notify(online, true, key, args);
                }
            }
        }
    }

    private static ServerPlayer onlinePlayer(CaricraftServer mod, String uuid) {
        try {
            return mod.server().getPlayerList().getPlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean validId(String id) {
        return ID.matcher(id).matches();
    }

    private static ActionResult invalid() {
        return ActionResult.fail("invalid_request");
    }

    private static ActionResult withUuid(String raw, java.util.function.Function<UUID, ActionResult> action) {
        try {
            return action.apply(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return invalid();
        }
    }
}
