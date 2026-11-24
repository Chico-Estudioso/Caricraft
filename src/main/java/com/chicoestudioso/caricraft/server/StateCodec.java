package com.chicoestudioso.caricraft.server;

import com.chicoestudioso.caricraft.core.CaricraftState;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.economy.LedgerEntry;
import com.chicoestudioso.caricraft.core.mission.MissionState;
import com.chicoestudioso.caricraft.core.mission.MissionStatus;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serialización NBT explícita y versionada de {@link CaricraftState}.
 * Si cambia el formato: incrementar {@link CaricraftState#SCHEMA_VERSION} y añadir el paso en {@link #migrate}.
 */
public final class StateCodec {
    private static final Logger LOGGER = LoggerFactory.getLogger("Caricraft Mod");

    private StateCodec() {
    }

    public static CompoundTag write(CaricraftState state) {
        CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", CaricraftState.SCHEMA_VERSION);
        ListTag parties = new ListTag();
        state.parties().values().forEach(party -> parties.add(writeParty(party)));
        root.put("parties", parties);
        ListTag profiles = new ListTag();
        state.profiles().values().forEach(profile -> profiles.add(writeProfile(profile)));
        root.put("profiles", profiles);
        return root;
    }

    public static CaricraftState read(CompoundTag root) {
        CompoundTag data = migrate(root.copy());
        CaricraftState state = new CaricraftState();
        ListTag parties = data.getList("parties", Tag.TAG_COMPOUND);
        for (int i = 0; i < parties.size(); i++) {
            try {
                Party party = readParty(parties.getCompound(i));
                state.parties().put(party.id(), party);
            } catch (RuntimeException e) {
                LOGGER.error("Grupo {} ilegible; se omite.", i, e);
            }
        }
        ListTag profiles = data.getList("profiles", Tag.TAG_COMPOUND);
        for (int i = 0; i < profiles.size(); i++) {
            try {
                PlayerProfile profile = readProfile(profiles.getCompound(i));
                state.profiles().put(profile.uuid(), profile);
            } catch (RuntimeException e) {
                LOGGER.error("Perfil {} ilegible; se omite.", i, e);
            }
        }
        return state;
    }

    /** Aplica migraciones de esquema en orden. */
    static CompoundTag migrate(CompoundTag root) {
        int version = root.contains("schemaVersion", Tag.TAG_INT) ? root.getInt("schemaVersion") : CaricraftState.SCHEMA_VERSION;
        if (version > CaricraftState.SCHEMA_VERSION) {
            LOGGER.warn("Datos guardados con un esquema más nuevo ({} > {}). Se intentará leer igualmente.",
                    version, CaricraftState.SCHEMA_VERSION);
        }
        // Sin migraciones todavía: la versión 1 es la primera.
        root.putInt("schemaVersion", CaricraftState.SCHEMA_VERSION);
        return root;
    }

    private static CompoundTag writeParty(Party party) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", party.id());
        tag.putLong("createdAt", party.createdAt());
        ListTag members = new ListTag();
        party.members().forEach(member -> members.add(StringTag.valueOf(member.toString())));
        tag.put("members", members);

        tag.putLong("balance", party.wallet().balance());
        ListTag ledger = new ListTag();
        for (LedgerEntry entry : party.wallet().ledger()) {
            CompoundTag e = new CompoundTag();
            e.putLong("t", entry.time());
            e.putLong("d", entry.delta());
            e.putLong("b", entry.balanceAfter());
            e.putString("r", entry.reason());
            e.putString("x", entry.detail() == null ? "" : entry.detail());
            ledger.add(e);
        }
        tag.put("ledger", ledger);
        tag.put("keys", strings(party.wallet().processedKeys()));
        tag.put("apps", strings(party.ownedApps()));
        tag.put("claimed", strings(party.claimedEver()));
        tag.put("biomes", strings(party.discoveredBiomes()));

        ListTag missions = new ListTag();
        for (Map.Entry<String, MissionState> entry : party.missions().entrySet()) {
            MissionState state = entry.getValue();
            CompoundTag m = new CompoundTag();
            m.putString("key", entry.getKey());
            m.putString("id", state.missionId());
            if (state.owner() != null) {
                m.putUUID("owner", state.owner());
            }
            m.putString("status", state.status().name());
            m.putLongArray("progress", state.progress());
            CompoundTag contributions = new CompoundTag();
            state.contributions().forEach((uuid, amount) -> contributions.putLong(uuid.toString(), amount));
            m.put("contrib", contributions);
            m.putLong("cycle", state.cycle());
            m.putLong("acceptedAt", state.acceptedAt());
            m.putLong("completedAt", state.completedAt());
            m.putLong("claimedAt", state.claimedAt());
            missions.add(m);
        }
        tag.put("missions", missions);

        ListTag chronicle = new ListTag();
        for (ChronicleEntry entry : party.chronicle()) {
            CompoundTag c = new CompoundTag();
            c.putLong("t", entry.time());
            c.putString("type", entry.type());
            if (entry.actor() != null) {
                c.putUUID("actor", entry.actor());
            }
            c.putString("name", entry.actorName());
            c.put("args", strings(entry.args()));
            chronicle.add(c);
        }
        tag.put("chronicle", chronicle);

        CompoundTag stats = new CompoundTag();
        party.stats().forEach(stats::putLong);
        tag.put("stats", stats);
        return tag;
    }

    private static Party readParty(CompoundTag tag) {
        Party party = new Party(tag.getUUID("id"), tag.getLong("createdAt"));
        for (String member : readStrings(tag.getList("members", Tag.TAG_STRING))) {
            party.members().add(UUID.fromString(member));
        }
        List<LedgerEntry> ledger = new ArrayList<>();
        ListTag ledgerTag = tag.getList("ledger", Tag.TAG_COMPOUND);
        for (int i = 0; i < ledgerTag.size(); i++) {
            CompoundTag e = ledgerTag.getCompound(i);
            ledger.add(new LedgerEntry(e.getLong("t"), e.getLong("d"), e.getLong("b"), e.getString("r"), e.getString("x")));
        }
        party.wallet().restore(Math.max(0, tag.getLong("balance")), ledger, readStrings(tag.getList("keys", Tag.TAG_STRING)));
        party.ownedApps().addAll(readStrings(tag.getList("apps", Tag.TAG_STRING)));
        party.claimedEver().addAll(readStrings(tag.getList("claimed", Tag.TAG_STRING)));
        party.discoveredBiomes().addAll(readStrings(tag.getList("biomes", Tag.TAG_STRING)));

        ListTag missions = tag.getList("missions", Tag.TAG_COMPOUND);
        for (int i = 0; i < missions.size(); i++) {
            CompoundTag m = missions.getCompound(i);
            MissionStatus status;
            try {
                status = MissionStatus.valueOf(m.getString("status"));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Estado de misión desconocido '{}'; se omite.", m.getString("status"));
                continue;
            }
            MissionState state = new MissionState(m.getString("id"), m.hasUUID("owner") ? m.getUUID("owner") : null,
                    status, m.getLongArray("progress"), m.getLong("cycle"), m.getLong("acceptedAt"));
            state.setCompletedAt(m.getLong("completedAt"));
            state.setClaimedAt(m.getLong("claimedAt"));
            CompoundTag contributions = m.getCompound("contrib");
            for (String key : contributions.getAllKeys()) {
                state.contributions().put(UUID.fromString(key), contributions.getLong(key));
            }
            party.missions().put(m.getString("key"), state);
        }

        ListTag chronicle = tag.getList("chronicle", Tag.TAG_COMPOUND);
        for (int i = 0; i < chronicle.size(); i++) {
            CompoundTag c = chronicle.getCompound(i);
            party.chronicle().addLast(new ChronicleEntry(c.getLong("t"), c.getString("type"),
                    c.hasUUID("actor") ? c.getUUID("actor") : null, c.getString("name"),
                    readStrings(c.getList("args", Tag.TAG_STRING))));
        }

        CompoundTag stats = tag.getCompound("stats");
        for (String key : stats.getAllKeys()) {
            party.stats().put(key, stats.getLong(key));
        }
        return party;
    }

    private static CompoundTag writeProfile(PlayerProfile profile) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("uuid", profile.uuid());
        tag.putString("name", profile.name());
        if (profile.partyId() != null) {
            tag.putUUID("party", profile.partyId());
        }
        tag.put("cosmetics", strings(profile.ownedCosmetics()));
        tag.putString("theme", profile.theme());
        tag.putLong("cooldown", profile.linkCooldownUntil());
        tag.putBoolean("starterPhone", profile.starterPhoneGiven());
        return tag;
    }

    private static PlayerProfile readProfile(CompoundTag tag) {
        PlayerProfile profile = new PlayerProfile(tag.getUUID("uuid"), tag.getString("name"));
        if (tag.hasUUID("party")) {
            profile.setPartyId(tag.getUUID("party"));
        }
        profile.ownedCosmetics().addAll(readStrings(tag.getList("cosmetics", Tag.TAG_STRING)));
        profile.setTheme(tag.getString("theme"));
        profile.setLinkCooldownUntil(tag.getLong("cooldown"));
        profile.setStarterPhoneGiven(tag.getBoolean("starterPhone"));
        return profile;
    }

    private static ListTag strings(Collection<String> values) {
        ListTag list = new ListTag();
        values.forEach(value -> list.add(StringTag.valueOf(value)));
        return list;
    }

    private static List<String> readStrings(ListTag list) {
        List<String> values = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            values.add(list.getString(i));
        }
        return values;
    }
}
