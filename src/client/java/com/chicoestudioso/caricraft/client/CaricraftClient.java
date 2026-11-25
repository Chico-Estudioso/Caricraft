package com.chicoestudioso.caricraft.client;

import com.chicoestudioso.caricraft.client.phone.PhoneScreen;
import com.chicoestudioso.caricraft.item.CariPhoneItem;
import com.chicoestudioso.caricraft.item.PhoneBinding;
import com.chicoestudioso.caricraft.network.PhoneNotifyPayload;
import com.chicoestudioso.caricraft.network.PhoneSnapshotPayload;
import com.chicoestudioso.caricraft.network.PhotoDataPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Inicializador del cliente: tecla, receptores de red y apertura del CariPhone. */
public final class CaricraftClient implements ClientModInitializer {
    public static final String KEY_CATEGORY = "key.categories.caricraft";
    private static KeyMapping openPhoneKey;

    @Override
    public void onInitializeClient() {
        CariPhoneItem.setClientOpener(CaricraftClient::openPhone);

        openPhoneKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.caricraft.open_phone", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, KEY_CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(ClientPhotos::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openPhoneKey.consumeClick()) {
                if (client.player != null && client.screen == null
                        && client.player.getInventory().contains(stack -> PhoneBinding.usableBy(stack, client.player.getUUID()))) {
                    openPhone();
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(PhoneSnapshotPayload.TYPE,
                (payload, context) -> ClientPhoneState.acceptSnapshot(payload.json()));
        ClientPlayNetworking.registerGlobalReceiver(PhoneNotifyPayload.TYPE,
                (payload, context) -> ClientPhoneState.acceptNotification(payload));
        ClientPlayNetworking.registerGlobalReceiver(PhotoDataPayload.TYPE,
                (payload, context) -> ClientPhotos.accept(payload.photoId(), payload.png()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientPhoneState.clear();
            NotificationHud.clear();
            ClientPhotos.clear();
        });
        HudRenderCallback.EVENT.register(NotificationHud::render);
    }

    public static KeyMapping openPhoneKey() {
        return openPhoneKey;
    }

    public static void openPhone() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new PhoneScreen());
    }
}
