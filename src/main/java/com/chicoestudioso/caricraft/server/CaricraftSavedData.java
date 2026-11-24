package com.chicoestudioso.caricraft.server;

import com.chicoestudioso.caricraft.core.CaricraftState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Contenedor de {@link CaricraftState} en el almacenamiento de datos del Overworld ({@code data/caricraft.dat}). */
public final class CaricraftSavedData extends SavedData {
    public static final String NAME = "caricraft";
    private static final Factory<CaricraftSavedData> FACTORY = new Factory<>(
            CaricraftSavedData::new, CaricraftSavedData::load, null);

    private final CaricraftState state;

    private CaricraftSavedData() {
        this(new CaricraftState());
    }

    private CaricraftSavedData(CaricraftState state) {
        this.state = state;
    }

    private static CaricraftSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        return new CaricraftSavedData(StateCodec.read(tag));
    }

    public static CaricraftSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public CaricraftState state() {
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.merge(StateCodec.write(state));
        return tag;
    }
}
