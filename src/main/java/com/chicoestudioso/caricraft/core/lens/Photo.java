package com.chicoestudioso.caricraft.core.lens;

import java.util.UUID;

/** Metadatos de una foto del álbum. El PNG se guarda aparte, en {@code data/caricraft_photos/<id>.png}. */
public record Photo(String id, UUID author, String authorName, long time, String caption, int width, int height, int bytes) {
}
