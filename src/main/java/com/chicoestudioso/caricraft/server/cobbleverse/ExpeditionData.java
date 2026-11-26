package com.chicoestudioso.caricraft.server.cobbleverse;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pokémon que están de expedición (uno por jugador). Mientras está fuera, el Pokémon vive aquí con el formato NBT
 * de Cobblemon, en {@code data/caricraft_expeditions.dat}, y vuelve al equipo (o al PC) al recogerlo.
 */
public final class ExpeditionData extends SavedData {
    public static final String NAME = "caricraft_expeditions";
    private static final Factory<ExpeditionData> FACTORY = new Factory<>(ExpeditionData::new, ExpeditionData::load, null);

    /** Una expedición en marcha. */
    public static final class Away {
        final String destination;
        final int hours;
        final long start;
        final long end;
        final CompoundTag pokemon;
        final String species;
        final int level;
        final boolean shiny;
        final List<String> aspects;
        final List<String> types;
        boolean notified;

        Away(String destination, int hours, long start, long end, CompoundTag pokemon, String species, int level, boolean shiny,
             List<String> aspects, List<String> types, boolean notified) {
            this.destination = destination;
            this.hours = hours;
            this.start = start;
            this.end = end;
            this.pokemon = pokemon;
            this.species = species;
            this.level = level;
            this.shiny = shiny;
            this.aspects = List.copyOf(aspects);
            this.types = List.copyOf(types);
            this.notified = notified;
        }

        Away withEnd(long newEnd) {
            return new Away(destination, hours, start, newEnd, pokemon, species, level, shiny, aspects, types, false);
        }
    }

    private final Map<UUID, Away> away = new HashMap<>();

    public static ExpeditionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static ExpeditionData load(CompoundTag tag, HolderLookup.Provider registries) {
        ExpeditionData data = new ExpeditionData();
        ListTag list = tag.getList("away", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            if (e.hasUUID("player") && e.contains("pokemon", Tag.TAG_COMPOUND)) {
                data.away.put(e.getUUID("player"), new Away(e.getString("destination"), e.getInt("hours"), e.getLong("start"),
                        e.getLong("end"), e.getCompound("pokemon"), e.getString("species"), e.getInt("level"), e.getBoolean("shiny"),
                        strings(e.getList("aspects", Tag.TAG_STRING)), strings(e.getList("types", Tag.TAG_STRING)), e.getBoolean("notified")));
            }
        }
        return data;
    }

    private static List<String> strings(ListTag list) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return out;
    }

    private static ListTag tags(List<String> values) {
        ListTag list = new ListTag();
        values.forEach(v -> list.add(StringTag.valueOf(v)));
        return list;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        away.forEach((player, a) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("player", player);
            e.putString("destination", a.destination);
            e.putInt("hours", a.hours);
            e.putLong("start", a.start);
            e.putLong("end", a.end);
            e.put("pokemon", a.pokemon);
            e.putString("species", a.species);
            e.putInt("level", a.level);
            e.putBoolean("shiny", a.shiny);
            e.put("aspects", tags(a.aspects));
            e.put("types", tags(a.types));
            e.putBoolean("notified", a.notified);
            list.add(e);
        });
        tag.put("away", list);
        return tag;
    }

    public Away get(UUID player) {
        return away.get(player);
    }

    public Map<UUID, Away> all() {
        return away;
    }

    void put(UUID player, Away entry) {
        away.put(player, entry);
        setDirty();
    }

    Away remove(UUID player) {
        Away removed = away.remove(player);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }
}
