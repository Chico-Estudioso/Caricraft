package com.chicoestudioso.caricraft.core.lens;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.Clock;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleService;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
import com.chicoestudioso.caricraft.core.shop.ShopService;

import java.util.HexFormat;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** CariLens: reglas del álbum de fotos del dúo. */
public final class LensService {
    public static final int MAX_CAPTION = 80;
    public static final int MIN_WIDTH = 32;
    public static final int MIN_HEIGHT = 18;
    public static final int MAX_WIDTH = 320;
    public static final int MAX_HEIGHT = 180;
    public static final Pattern PHOTO_ID = Pattern.compile("[0-9a-f]{16}");

    private final CaricraftConfig config;
    private final ChronicleService chronicle;
    private final ShopService shop;
    private final Clock clock;
    private Supplier<String> idGenerator;

    public LensService(CaricraftConfig config, ChronicleService chronicle, ShopService shop, Clock clock) {
        this.config = config;
        this.chronicle = chronicle;
        this.shop = shop;
        this.clock = clock;
        Random random = new Random();
        this.idGenerator = () -> HexFormat.of().formatHex(new byte[]{
                (byte) random.nextInt(), (byte) random.nextInt(), (byte) random.nextInt(), (byte) random.nextInt(),
                (byte) random.nextInt(), (byte) random.nextInt(), (byte) random.nextInt(), (byte) random.nextInt()});
    }

    public void setIdGenerator(Supplier<String> idGenerator) {
        this.idGenerator = idGenerator;
    }

    public boolean hasApp(Party party) {
        return shop.hasApp(party, AppCatalog.CARILENS);
    }

    public int capacity(Party party) {
        return config.lens.basePhotos + config.lens.photosPerExpansion * party.upgradeLevel(AppCatalog.CARILENS_ALBUM);
    }

    /** Comprueba si el grupo puede guardar otra foto (antes de recibir los datos). */
    public ActionResult canUpload(Party party) {
        if (!hasApp(party)) {
            return ActionResult.fail("app_locked", AppCatalog.CARILENS);
        }
        if (party.photos().size() >= capacity(party)) {
            return ActionResult.fail("album_full", capacity(party));
        }
        return ActionResult.ok("photo_upload_ok");
    }

    /** Valida el PNG recibido y devuelve sus datos o {@code null}. */
    public static PngCodec.Info validate(byte[] png, int maxBytes) {
        if (png == null || png.length > maxBytes) {
            return null;
        }
        PngCodec.Info info = PngCodec.readInfo(png);
        if (info == null || info.width() < MIN_WIDTH || info.height() < MIN_HEIGHT
                || info.width() > MAX_WIDTH || info.height() > MAX_HEIGHT || info.bitDepth() != 8) {
            return null;
        }
        return info;
    }

    /** Registra la foto ya validada. El servidor guarda el archivo con el id devuelto. */
    public Photo addPhoto(Party party, UUID author, String authorName, String rawCaption, PngCodec.Info info, int bytes) {
        String caption = ChronicleService.sanitizeNote(rawCaption);
        if (caption.length() > MAX_CAPTION) {
            caption = caption.substring(0, MAX_CAPTION).strip();
        }
        String id = idGenerator.get();
        while (containsId(party, id)) {
            id = idGenerator.get();
        }
        Photo photo = new Photo(id, author, authorName, clock.now(), caption, info.width(), info.height(), bytes);
        party.photos().addFirst(photo);
        party.incrementStat("photos", 1);
        chronicle.add(party, ChronicleEntry.PHOTO, author, authorName, caption);
        return photo;
    }

    private static boolean containsId(Party party, String id) {
        for (Photo photo : party.photos()) {
            if (photo.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Borra la foto del álbum del grupo. Cualquier miembro puede hacerlo. */
    public ActionResult deletePhoto(Party party, String photoId) {
        boolean removed = party.photos().removeIf(p -> p.id().equals(photoId));
        return removed ? ActionResult.ok("photo_deleted") : ActionResult.fail("photo_missing");
    }
}
