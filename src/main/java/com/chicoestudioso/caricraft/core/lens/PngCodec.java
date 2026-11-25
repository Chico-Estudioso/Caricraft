package com.chicoestudioso.caricraft.core.lens;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * PNG mínimo en Java puro: codifica RGB de 8 bits (filtro "Sub" por fila) y lee la cabecera para validar.
 * Así las fotos de CariLens ocupan poco y el servidor puede comprobarlas sin decodificarlas.
 */
public final class PngCodec {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** Datos de la cabecera IHDR. */
    public record Info(int width, int height, int bitDepth, int colorType) {
    }

    private PngCodec() {
    }

    /**
     * @param rgb píxeles {@code 0xRRGGBB} por filas
     */
    public static byte[] encodeRgb(int width, int height, int[] rgb) {
        if (width <= 0 || height <= 0 || rgb.length != width * height) {
            throw new IllegalArgumentException("Dimensiones no válidas");
        }
        byte[] raw = new byte[height * (1 + width * 3)];
        int offset = 0;
        for (int y = 0; y < height; y++) {
            raw[offset++] = 1; // filtro Sub
            int prevR = 0, prevG = 0, prevB = 0;
            for (int x = 0; x < width; x++) {
                int p = rgb[y * width + x];
                int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                raw[offset++] = (byte) (r - prevR);
                raw[offset++] = (byte) (g - prevG);
                raw[offset++] = (byte) (b - prevB);
                prevR = r;
                prevG = g;
                prevB = b;
            }
        }
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            compressed.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(SIGNATURE);
            ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
            DataOutputStream header = new DataOutputStream(ihdr);
            header.writeInt(width);
            header.writeInt(height);
            header.writeByte(8);  // bits por canal
            header.writeByte(2);  // RGB
            header.writeByte(0);
            header.writeByte(0);
            header.writeByte(0);
            chunk(out, "IHDR", ihdr.toByteArray());
            chunk(out, "IDAT", compressed.toByteArray());
            chunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream stream = new DataOutputStream(out);
        stream.writeInt(data.length);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        stream.write(typeBytes);
        stream.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        stream.writeInt((int) crc.getValue());
    }

    /** Lee la cabecera; devuelve {@code null} si no es un PNG bien formado. */
    public static Info readInfo(byte[] png) {
        if (png == null || png.length < 33 || !Arrays.equals(Arrays.copyOf(png, 8), SIGNATURE)) {
            return null;
        }
        int length = readInt(png, 8);
        String type = new String(png, 12, 4, StandardCharsets.US_ASCII);
        if (length != 13 || !"IHDR".equals(type)) {
            return null;
        }
        CRC32 crc = new CRC32();
        crc.update(png, 12, 4 + 13);
        if ((int) crc.getValue() != readInt(png, 29)) {
            return null;
        }
        return new Info(readInt(png, 16), readInt(png, 20), png[24] & 0xFF, png[25] & 0xFF);
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }
}
