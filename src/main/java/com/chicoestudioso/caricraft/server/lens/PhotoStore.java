package com.chicoestudioso.caricraft.server.lens;

import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.core.CaricraftState;
import com.chicoestudioso.caricraft.core.lens.LensService;
import com.chicoestudioso.caricraft.core.party.Party;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Archivos PNG de CariLens en {@code <mundo>/data/caricraft_photos/<id>.png}. */
public final class PhotoStore {
    private final Path dir;

    public PhotoStore(MinecraftServer server) {
        this.dir = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("caricraft_photos");
    }

    private Path file(String id) {
        if (!LensService.PHOTO_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Id de foto no válido");
        }
        return dir.resolve(id + ".png");
    }

    public void write(String id, byte[] png) throws IOException {
        Files.createDirectories(dir);
        Path target = file(id);
        Path temp = dir.resolve(id + ".tmp");
        Files.write(temp, png);
        Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public byte[] read(String id) {
        try {
            Path path = file(id);
            return Files.exists(path) ? Files.readAllBytes(path) : null;
        } catch (IOException | IllegalArgumentException e) {
            CaricraftMod.LOGGER.warn("No se pudo leer la foto {}", id, e);
            return null;
        }
    }

    /** Borra el archivo si ningún grupo la conserva (tras desvincular ambos tienen copia de la lista). */
    public void deleteIfUnused(String id, CaricraftState state) {
        for (Party party : state.parties().values()) {
            if (party.photos().stream().anyMatch(p -> p.id().equals(id))) {
                return;
            }
        }
        try {
            Files.deleteIfExists(file(id));
        } catch (IOException | IllegalArgumentException e) {
            CaricraftMod.LOGGER.warn("No se pudo borrar la foto {}", id, e);
        }
    }
}
