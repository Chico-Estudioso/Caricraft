package com.chicoestudioso.caricraft.client;

import com.chicoestudioso.caricraft.CaricraftConstants;
import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.network.PhoneNotifyPayload;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import com.chicoestudioso.caricraft.client.phone.PhoneScreen;
import com.chicoestudioso.caricraft.client.phone.PhoneText;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Caché en cliente del último snapshot recibido. Solo se usa para pintar; las decisiones las toma el servidor.
 * Todo se ejecuta en el hilo del cliente.
 */
public final class ClientPhoneState {
    private static final int MAX_NOTIFICATIONS = 4;
    private static PhoneSnapshot snapshot;
    private static boolean protocolMismatch;
    private static long snapshotVersion;
    private static final Deque<Notification> NOTIFICATIONS = new ArrayDeque<>();

    private ClientPhoneState() {
    }

    public static void acceptSnapshot(String json) {
        try {
            PhoneSnapshot parsed = PhoneSnapshot.fromJson(json);
            if (parsed == null) {
                return;
            }
            protocolMismatch = parsed.protocolVersion() != CaricraftConstants.PROTOCOL_VERSION;
            snapshot = protocolMismatch ? null : parsed;
            snapshotVersion++;
        } catch (JsonParseException e) {
            CaricraftMod.LOGGER.warn("Snapshot del CariPhone no válido", e);
        }
    }

    public static void acceptNotification(PhoneNotifyPayload payload) {
        NOTIFICATIONS.addFirst(new Notification(payload.success(), payload.key(), payload.args(), System.currentTimeMillis()));
        while (NOTIFICATIONS.size() > MAX_NOTIFICATIONS) {
            NOTIFICATIONS.removeLast();
        }
        Minecraft minecraft = Minecraft.getInstance();
        // Con el teléfono cerrado, los avisos de eventos se muestran como notificación propia (con sonido).
        if (!(minecraft.screen instanceof PhoneScreen) && payload.key().startsWith("caricraft.notify.")) {
            NotificationHud.push(PhoneText.format(snapshot, payload.key(), payload.args()), payload.success());
        }
    }

    public static PhoneSnapshot snapshot() {
        return snapshot;
    }

    public static long snapshotVersion() {
        return snapshotVersion;
    }

    public static boolean protocolMismatch() {
        return protocolMismatch;
    }

    public static Deque<Notification> notifications() {
        return NOTIFICATIONS;
    }

    public static void clear() {
        snapshot = null;
        protocolMismatch = false;
        NOTIFICATIONS.clear();
    }

    public record Notification(boolean success, String key, java.util.List<String> args, long createdAt) {
    }
}
