package com.chicoestudioso.caricraft.client;

import com.chicoestudioso.caricraft.CaricraftConstants;
import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.client.phone.PhonePage;
import com.chicoestudioso.caricraft.client.phone.PhoneScreen;
import com.chicoestudioso.caricraft.core.lens.PngCodec;
import com.chicoestudioso.caricraft.network.PhoneAction;
import com.chicoestudioso.caricraft.network.PhoneActionPayload;
import com.chicoestudioso.caricraft.network.PhotoUploadPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * CariLens en el cliente: hace la foto (sin interfaz), la reduce a 256×144, la codifica en PNG y la sube
 * troceada; además descarga y cachea como texturas las fotos del álbum.
 */
public final class ClientPhotos {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 144;
    private static final ResourceLocation PENDING_TEXTURE = CaricraftConstants.id("photo/pending");

    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private static final Set<String> REQUESTED = new HashSet<>();
    private static byte[] pendingPng;
    private static int captureCountdown = -1;
    private static boolean previousHideGui;
    private static int uploadCounter;

    private ClientPhotos() {
    }

    // ------------------------------------------------------------------ captura

    /** Cierra el teléfono, oculta la interfaz y hace la foto dentro de un par de ticks. */
    public static void beginCapture() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(null);
        previousHideGui = minecraft.options.hideGui;
        minecraft.options.hideGui = true;
        captureCountdown = 3;
    }

    /** Llamado al final de cada tick del cliente. */
    public static void tick(Minecraft minecraft) {
        if (captureCountdown < 0) {
            return;
        }
        if (--captureCountdown > 0) {
            return;
        }
        captureCountdown = -1;
        try (NativeImage shot = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            int[] rgb = downscale(shot);
            setPending(PngCodec.encodeRgb(WIDTH, HEIGHT, rgb), rgb);
        } catch (RuntimeException e) {
            CaricraftMod.LOGGER.error("No se pudo hacer la foto", e);
        } finally {
            minecraft.options.hideGui = previousHideGui;
        }
        PhoneScreen screen = new PhoneScreen();
        screen.openPage(PhonePage.CARILENS);
        minecraft.setScreen(screen);
    }

    /** Recorta al centro en 16:9 y promedia bloques de píxeles hasta 256×144. */
    static int[] downscale(NativeImage source) {
        int sw = source.getWidth();
        int sh = source.getHeight();
        int cropW = Math.min(sw, sh * 16 / 9);
        int cropH = Math.min(sh, sw * 9 / 16);
        int ox = (sw - cropW) / 2;
        int oy = (sh - cropH) / 2;
        int[] out = new int[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            int y0 = oy + y * cropH / HEIGHT;
            int y1 = Math.max(y0 + 1, oy + (y + 1) * cropH / HEIGHT);
            for (int x = 0; x < WIDTH; x++) {
                int x0 = ox + x * cropW / WIDTH;
                int x1 = Math.max(x0 + 1, ox + (x + 1) * cropW / WIDTH);
                long r = 0, g = 0, b = 0;
                int n = 0;
                for (int sy = y0; sy < y1; sy++) {
                    for (int sx = x0; sx < x1; sx++) {
                        int p = source.getPixelRGBA(sx, sy); // formato ABGR
                        r += p & 0xFF;
                        g += (p >> 8) & 0xFF;
                        b += (p >> 16) & 0xFF;
                        n++;
                    }
                }
                out[y * WIDTH + x] = (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
            }
        }
        return out;
    }

    private static void setPending(byte[] png, int[] rgb) {
        clearPending();
        pendingPng = png;
        NativeImage preview = new NativeImage(WIDTH, HEIGHT, false);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int p = rgb[y * WIDTH + x];
                preview.setPixelRGBA(x, y, 0xFF000000 | (p & 0xFF) << 16 | (p & 0xFF00) | (p >> 16) & 0xFF);
            }
        }
        Minecraft.getInstance().getTextureManager().register(PENDING_TEXTURE, new DynamicTexture(preview));
    }

    public static boolean hasPending() {
        return pendingPng != null;
    }

    public static ResourceLocation pendingTexture() {
        return PENDING_TEXTURE;
    }

    public static int pendingSize() {
        return pendingPng == null ? 0 : pendingPng.length;
    }

    public static void clearPending() {
        if (pendingPng != null) {
            Minecraft.getInstance().getTextureManager().release(PENDING_TEXTURE);
        }
        pendingPng = null;
    }

    /** Sube la foto pendiente en trozos de 30 KB. */
    public static void uploadPending(String caption) {
        if (pendingPng == null || !ClientPlayNetworking.canSend(PhotoUploadPayload.TYPE)) {
            return;
        }
        byte[] png = pendingPng;
        int total = (png.length + PhotoUploadPayload.MAX_CHUNK - 1) / PhotoUploadPayload.MAX_CHUNK;
        int uploadId = ++uploadCounter;
        for (int i = 0; i < total; i++) {
            byte[] chunk = Arrays.copyOfRange(png, i * PhotoUploadPayload.MAX_CHUNK,
                    Math.min(png.length, (i + 1) * PhotoUploadPayload.MAX_CHUNK));
            ClientPlayNetworking.send(new PhotoUploadPayload(uploadId, i, total, caption == null ? "" : caption, chunk));
        }
        clearPending();
    }

    // ------------------------------------------------------------------ álbum

    /** Textura de la foto o {@code null} (y la pide al servidor la primera vez). */
    public static ResourceLocation texture(String photoId) {
        ResourceLocation texture = TEXTURES.get(photoId);
        if (texture == null && REQUESTED.add(photoId) && ClientPlayNetworking.canSend(PhoneActionPayload.TYPE)) {
            ClientPlayNetworking.send(PhoneActionPayload.of(PhoneAction.PHOTO_REQUEST, photoId));
        }
        return texture;
    }

    public static void accept(String photoId, byte[] png) {
        try {
            NativeImage image = NativeImage.read(png);
            ResourceLocation id = CaricraftConstants.id("photo/" + photoId);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            TEXTURES.put(photoId, id);
        } catch (IOException | RuntimeException e) {
            CaricraftMod.LOGGER.warn("Foto {} no válida", photoId, e);
        }
    }

    public static void forget(String photoId) {
        ResourceLocation texture = TEXTURES.remove(photoId);
        REQUESTED.remove(photoId);
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(texture);
        }
    }

    public static void clear() {
        for (String id : Set.copyOf(TEXTURES.keySet())) {
            forget(id);
        }
        REQUESTED.clear();
        clearPending();
    }
}
