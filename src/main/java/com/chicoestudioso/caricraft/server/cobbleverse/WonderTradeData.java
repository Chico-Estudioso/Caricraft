package com.chicoestudioso.caricraft.server.cobbleverse;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Bolsa del intercambio prodigioso: Pokémon que otros jugadores han enviado y esperan a que alguien los
 * reciba. Se guarda en {@code data/caricraft_wonder.dat} (los Pokémon, con el formato NBT de Cobblemon).
 */
public final class WonderTradeData extends SavedData {
    public static final String NAME = "caricraft_wonder";
    private static final Factory<WonderTradeData> FACTORY = new Factory<>(WonderTradeData::new, WonderTradeData::load, null);

    /** Un Pokémon en la bolsa. */
    public record Entry(UUID owner, String ownerName, long time, CompoundTag pokemon) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public static WonderTradeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static WonderTradeData load(CompoundTag tag, HolderLookup.Provider registries) {
        WonderTradeData data = new WonderTradeData();
        ListTag pool = tag.getList("pool", Tag.TAG_COMPOUND);
        for (int i = 0; i < pool.size(); i++) {
            CompoundTag e = pool.getCompound(i);
            if (e.hasUUID("owner")) {
                data.entries.add(new Entry(e.getUUID("owner"), e.getString("name"), e.getLong("time"), e.getCompound("pokemon")));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag pool = new ListTag();
        for (Entry entry : entries) {
            CompoundTag e = new CompoundTag();
            e.putUUID("owner", entry.owner());
            e.putString("name", entry.ownerName());
            e.putLong("time", entry.time());
            e.put("pokemon", entry.pokemon());
            pool.add(e);
        }
        tag.put("pool", pool);
        return tag;
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public List<UUID> owners() {
        return entries.stream().map(Entry::owner).toList();
    }

    public Entry take(int index) {
        Entry entry = entries.remove(index);
        setDirty();
        return entry;
    }

    /** Añade un Pokémon; si la bolsa se llena, se va el más antiguo. */
    public void add(Entry entry, int maxSize) {
        entries.add(entry);
        while (entries.size() > maxSize) {
            entries.removeFirst();
        }
        setDirty();
    }
}
