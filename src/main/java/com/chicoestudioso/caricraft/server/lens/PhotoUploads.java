package com.chicoestudioso.caricraft.server.lens;

import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.CaricraftCore;
import com.chicoestudioso.caricraft.core.lens.LensService;
import com.chicoestudioso.caricraft.core.lens.Photo;
import com.chicoestudioso.caricraft.core.lens.PngCodec;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.network.PhotoDataPayload;
import com.chicoestudioso.caricraft.network.PhotoUploadPayload;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Recepción troceada y validada de fotos, y envío bajo demanda. Todo en el hilo del servidor. */
public final class PhotoUploads {
    private static final long STALE_MS = 30_000;
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST_UPLOAD = new HashMap<>();

    private static final class Pending {
        final int uploadId;
        final int total;
        final String caption;
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        int next;
        final long startedAt = System.currentTimeMillis();

        Pending(int uploadId, int total, String caption) {
            this.uploadId = uploadId;
            this.total = total;
            this.caption = caption;
        }
    }

    private PhotoUploads() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(PhotoUploadPayload.TYPE, (payload, context) -> receive(context.player(), payload));
    }

    public static void forget(UUID player) {
        PENDING.remove(player);
    }

    static void receive(ServerPlayer player, PhotoUploadPayload payload) {
        CaricraftServer mod = CaricraftServer.get();
        if (mod == null) {
            return;
        }
        CaricraftCore core = mod.core();
        UUID uuid = player.getUUID();
        int maxChunks = Math.min(PhotoUploadPayload.MAX_CHUNKS,
                (core.config().lens.maxPhotoBytes + PhotoUploadPayload.MAX_CHUNK - 1) / PhotoUploadPayload.MAX_CHUNK);
        Pending pending = PENDING.get(uuid);
        if (pending != null && System.currentTimeMillis() - pending.startedAt > STALE_MS) {
            PENDING.remove(uuid);
            pending = null;
        }
        if (payload.index() == 0) {
            Party party = core.partyOf(uuid);
            ActionResult allowed = party == null ? ActionResult.fail("no_profile") : core.lens().canUpload(party);
            long last = LAST_UPLOAD.getOrDefault(uuid, 0L);
            long wait = core.config().lens.uploadCooldownSeconds * 1000L - (System.currentTimeMillis() - last);
            if (allowed.success() && wait > 0) {
                allowed = ActionResult.fail("photo_cooldown", (wait + 999) / 1000);
            }
            if (allowed.success() && (payload.total() < 1 || payload.total() > maxChunks)) {
                allowed = ActionResult.fail("photo_invalid");
            }
            if (!allowed.success()) {
                PENDING.remove(uuid);
                mod.notify(player, false, allowed.key(), allowed.args());
                return;
            }
            pending = new Pending(payload.uploadId(), payload.total(), payload.caption());
            PENDING.put(uuid, pending);
        }
        if (pending == null || pending.uploadId != payload.uploadId() || pending.next != payload.index()
                || payload.total() != pending.total) {
            PENDING.remove(uuid);
            return; // fuera de orden o manipulado: se descarta en silencio
        }
        pending.data.writeBytes(payload.chunk());
        pending.next++;
        if (pending.data.size() > core.config().lens.maxPhotoBytes) {
            PENDING.remove(uuid);
            mod.notify(player, false, "caricraft.result.photo_invalid", List.of());
            return;
        }
        if (pending.next < pending.total) {
            return;
        }
        PENDING.remove(uuid);
        finish(mod, player, pending);
    }

    private static void finish(CaricraftServer mod, ServerPlayer player, Pending pending) {
        CaricraftCore core = mod.core();
        byte[] png = pending.data.toByteArray();
        PngCodec.Info info = LensService.validate(png, core.config().lens.maxPhotoBytes);
        Party party = core.partyOf(player.getUUID());
        if (info == null || party == null) {
            mod.notify(player, false, "caricraft.result.photo_invalid", List.of());
            return;
        }
        ActionResult allowed = core.lens().canUpload(party);
        if (!allowed.success()) {
            mod.notify(player, false, allowed.key(), allowed.args());
            return;
        }
        Photo photo = core.lens().addPhoto(party, player.getUUID(), player.getGameProfile().getName(), pending.caption, info, png.length);
        try {
            mod.photos().write(photo.id(), png);
        } catch (IOException e) {
            CaricraftMod.LOGGER.error("No se pudo guardar la foto {}", photo.id(), e);
            core.lens().deletePhoto(party, photo.id());
            mod.notify(player, false, "caricraft.result.internal_error", List.of());
            return;
        }
        LAST_UPLOAD.put(player.getUUID(), System.currentTimeMillis());
        mod.markDirty();
        mod.notify(player, true, "caricraft.result.photo_saved", List.of());
        for (UUID member : List.copyOf(party.members())) {
            ServerPlayer online = mod.server().getPlayerList().getPlayer(member);
            if (online != null && !online.getUUID().equals(player.getUUID())) {
                mod.notify(online, true, "caricraft.notify.partner_photo", List.of(player.getGameProfile().getName()));
            }
        }
        mod.sendPartySnapshots(player.getUUID());
    }

    /** Envía el PNG si la foto es del grupo del jugador. */
    public static ActionResult send(CaricraftServer mod, ServerPlayer player, String photoId) {
        Party party = mod.core().partyOf(player.getUUID());
        if (party == null || party.photos().stream().noneMatch(p -> p.id().equals(photoId))) {
            return ActionResult.fail("photo_missing");
        }
        byte[] png = mod.photos().read(photoId);
        if (png == null) {
            return ActionResult.fail("photo_missing");
        }
        ServerPlayNetworking.send(player, new PhotoDataPayload(photoId, png));
        return null; // sin aviso: la respuesta es la propia foto
    }
}
