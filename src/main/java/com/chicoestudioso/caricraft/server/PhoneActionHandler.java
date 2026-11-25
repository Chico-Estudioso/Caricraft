package com.chicoestudioso.caricraft.server;

import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.CaricraftCore;
import com.chicoestudioso.caricraft.core.CaricraftState;
import com.chicoestudioso.caricraft.network.PhoneAction;
import com.chicoestudioso.caricraft.core.lens.LensService;
import com.chicoestudioso.caricraft.core.camp.TravelRequest;
import com.chicoestudioso.caricraft.server.camp.CampTravel;
import com.chicoestudioso.caricraft.server.camp.TravelManager;
import com.chicoestudioso.caricraft.server.lens.PhotoUploads;
import com.chicoestudioso.caricraft.server.challenge.ChallengeManager;
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
                    UUID sourceParty = core.partyOf(uuid).id();
                    ActionResult r = core.parties().accept(state, uuid, from);
                    if (r.success()) {
                        com.chicoestudioso.caricraft.server.box.CariBoxManager.onMerge(mod, sourceParty, core.partyOf(uuid), player);
                    }
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
                case MARKET_BUY -> validId(payload.arg1())
                        ? core.market().buy(state, uuid, payload.arg1(), (item, count) -> giveItem(player, item, count)) : invalid();
                case PHOTO_REQUEST -> LensService.PHOTO_ID.matcher(payload.arg1()).matches()
                        ? PhotoUploads.send(mod, player, payload.arg1()) : invalid();
                case PHOTO_DELETE -> {
                    if (!LensService.PHOTO_ID.matcher(payload.arg1()).matches()) {
                        yield invalid();
                    }
                    ActionResult deleted = core.lens().deletePhoto(core.partyOf(uuid), payload.arg1());
                    if (deleted.success()) {
                        mod.photos().deleteIfUnused(payload.arg1(), state);
                    }
                    yield deleted;
                }
                case CAMP_SET -> CampTravel.setCamp(mod, player);
                case CAMP_TRAVEL -> "partner".equals(payload.arg1())
                        ? TravelManager.request(mod, player, TravelRequest.Kind.GO) : TravelManager.travelToCamp(mod, player);
                case TRAVEL_REQUEST -> TravelManager.request(mod, player,
                        "bring".equals(payload.arg1()) ? TravelRequest.Kind.BRING : TravelRequest.Kind.GO);
                case TRAVEL_ACCEPT -> TravelManager.accept(mod, player);
                case CARIBOX_OPEN -> com.chicoestudioso.caricraft.server.box.CariBoxManager.open(mod, player);
                case TRAVEL_DECLINE -> TravelManager.decline(mod, player);
                case CHALLENGE_BRING_FORWARD -> core.challenge().bringForward(core.partyOf(uuid),
                        ChallengeManager.day(mod.server()), ChallengeManager.night(mod.server()));
            };
        } catch (RuntimeException e) {
            CaricraftMod.LOGGER.error("Error procesando {} de {}", payload.action(), player.getGameProfile().getName(), e);
            result = ActionResult.fail("internal_error");
        }

        if ((payload.action() == PhoneAction.PHOTO_REQUEST || payload.action() == PhoneAction.CARIBOX_OPEN) && result == null) {
            return; // la respuesta es la foto o el propio cofre
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
            if (result.success() && payload.action() == PhoneAction.CHALLENGE_BRING_FORWARD) {
                notifyPartner(mod, player, "caricraft.notify.partner_challenge", List.of(player.getGameProfile().getName()));
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

    /** Entrega ítems; lo que no cabe en el inventario cae a los pies del jugador. */
    private static void giveItem(ServerPlayer player, String itemId, int count) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.parse(itemId);
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        int remaining = count;
        while (remaining > 0) {
            int size = Math.min(remaining, item.getDefaultMaxStackSize());
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, size);
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                player.drop(stack, false);
            }
            remaining -= size;
        }
        player.containerMenu.broadcastChanges();
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
