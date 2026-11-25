package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.lens.LensService;
import com.chicoestudioso.caricraft.core.lens.Photo;
import com.chicoestudioso.caricraft.core.lens.PngCodec;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LensServiceTest {
    private static byte[] gradient(int w, int h) {
        int[] rgb = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                rgb[y * w + x] = (x * 255 / w) << 16 | (y * 255 / h) << 8 | 0x40;
            }
        }
        return PngCodec.encodeRgb(w, h, rgb);
    }

    @Test
    void encodedPngIsValidAndDecodable() throws IOException {
        byte[] png = gradient(256, 144);
        PngCodec.Info info = PngCodec.readInfo(png);
        assertNotNull(info);
        assertEquals(256, info.width());
        assertEquals(144, info.height());
        assertEquals(2, info.colorType());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(256, image.getWidth());
        assertEquals((255 * 100 / 256) & 0xFF, (image.getRGB(100, 50) >> 16) & 0xFF);
        assertTrue(png.length < 40_000, "una foto debe ocupar poco: " + png.length);
    }

    @Test
    void invalidImagesAreRejected() {
        assertNull(PngCodec.readInfo(new byte[]{1, 2, 3}));
        byte[] png = gradient(256, 144);
        png[20] ^= 1; // altera la cabecera: el CRC ya no cuadra
        assertNull(PngCodec.readInfo(png));
        assertNull(LensService.validate(gradient(400, 200), 512 * 1024), "demasiado grande");
        assertNull(LensService.validate(gradient(16, 9), 512 * 1024), "demasiado pequeña");
        assertNull(LensService.validate(gradient(256, 144), 100), "demasiados bytes");
        assertNotNull(LensService.validate(gradient(256, 144), 512 * 1024));
    }

    @Test
    void albumNeedsTheAppAndHasCapacity() {
        TestFixture f = TestFixture.withDefaultMissions();
        LensService lens = f.core.lens();
        Party party = f.state.partyOf(f.alice);
        assertEquals("caricraft.result.app_locked", lens.canUpload(party).key());
        f.give(f.alice, 1000);
        f.core.shop().buy(f.state, f.alice, AppCatalog.CARILENS);
        PngCodec.Info info = PngCodec.readInfo(gradient(64, 36));
        for (int i = 0; i < f.config.lens.basePhotos; i++) {
            assertTrue(lens.canUpload(party).success());
            lens.addPhoto(party, f.alice, "Alice", "Foto " + i, info, 100);
        }
        assertEquals("caricraft.result.album_full", lens.canUpload(party).key());
        f.core.shop().buy(f.state, f.alice, AppCatalog.CARILENS_ALBUM);
        assertEquals(f.config.lens.basePhotos + f.config.lens.photosPerExpansion, lens.capacity(party));
        assertTrue(lens.canUpload(party).success());
    }

    @Test
    void photosGoToChroniclesAndCanBeDeleted() {
        TestFixture f = TestFixture.withDefaultMissions();
        Party duo = f.formDuo();
        f.give(f.alice, 1000);
        f.core.shop().buy(f.state, f.alice, AppCatalog.CARILENS);
        Photo photo = f.core.lens().addPhoto(duo, f.bob, "Bob", "  Atardecer §l en la playa ", PngCodec.readInfo(gradient(64, 36)), 500);
        assertEquals("Atardecer en la playa", photo.caption());
        assertTrue(LensService.PHOTO_ID.matcher(photo.id()).matches());
        assertEquals(ChronicleEntry.PHOTO, duo.chronicle().getFirst().type());
        assertEquals("caricraft.result.photo_missing", f.core.lens().deletePhoto(duo, "0000000000000000").key());
        assertTrue(f.core.lens().deletePhoto(duo, photo.id()).success());
        assertTrue(duo.photos().isEmpty());
    }
}
