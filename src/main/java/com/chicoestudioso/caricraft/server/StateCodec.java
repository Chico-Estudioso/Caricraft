package com.chicoestudioso.caricraft.server;

import com.chicoestudioso.caricraft.core.CaricraftState;
import com.chicoestudioso.caricraft.core.camp.CampSite;
import com.chicoestudioso.caricraft.core.challenge.ChallengeState;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.economy.LedgerEntry;
import com.chicoestudioso.caricraft.core.lens.Photo;
import com.chicoestudioso.caricraft.core.mission.MissionState;
import com.chicoestudioso.caricraft.core.mission.MissionStatus;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
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
        if (version < 2) {
            migrateV1ToV2(root);
        }
        root.putInt("schemaVersion", CaricraftState.SCHEMA_VERSION);
        return root;
    }

    /**
     * v1 → v2: los viajes con el compañero pasan de CariCamp a la app TP Dúo. Quien ya compró CariCamp
     * no pierde esa función: se le da TP Dúo.
     */
    private static void migrateV1ToV2(CompoundTag root) {
        ListTag parties = root.getList("parties", Tag.TAG_COMPOUND);
        for (int i = 0; i < parties.size(); i++) {
            CompoundTag party = parties.getCompound(i);
            ListTag apps = party.getList("apps", Tag.TAG_STRING);
            boolean camp = false;
            boolean travel = false;
            for (int j = 0; j < apps.size(); j++) {
                camp |= AppCatalog.CARICAMP.equals(apps.getString(j));
                travel |= AppCatalog.TPDUO.equals(apps.getString(j));
            }
            if (camp && !travel) {
                apps.add(StringTag.valueOf(AppCatalog.TPDUO));
                party.put("apps", apps);
            }
        }
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

        ChallengeState ch = party.challenge();
        CompoundTag challenge = new CompoundTag();
        challenge.putLong("last", ch.lastChallengeDay());
        challenge.putLong("scheduled", ch.scheduledDay());
        challenge.putBoolean("forced", ch.forced());
        challenge.putBoolean("active", ch.active());
        challenge.putInt("nights", ch.nightsCompleted());
        challenge.putLong("kills", ch.totalKills());
        challenge.putLong("carins", ch.totalCarins());
        challenge.putInt("nightKills", ch.nightKills());
        challenge.putLong("nightCarins", ch.nightCarins());
        tag.put("challenge", challenge);

        CompoundTag market = new CompoundTag();
        party.marketPurchases().forEach(market::putInt);
        tag.put("market", market);

        if (party.camp() != null) {
            CompoundTag camp = new CompoundTag();
            camp.putString("dim", party.camp().dimension());
            camp.putInt("x", party.camp().x());
            camp.putInt("y", party.camp().y());
            camp.putInt("z", party.camp().z());
            camp.putLong("t", party.camp().createdAt());
            tag.put("camp", camp);
        }
        CompoundTag cooldowns = new CompoundTag();
        party.travelCooldowns().forEach((uuid, until) -> cooldowns.putLong(uuid.toString(), until));
        tag.put("travel", cooldowns);

        CompoundTag upgrades = new CompoundTag();
        party.upgradeLevels().forEach(upgrades::putInt);
        tag.put("upgrades", upgrades);
        ListTag photos = new ListTag();
        for (Photo photo : party.photos()) {
            CompoundTag p = new CompoundTag();
            p.putString("id", photo.id());
            p.putUUID("author", photo.author());
            p.putString("name", photo.authorName());
            p.putLong("t", photo.time());
            p.putString("caption", photo.caption());
            p.putInt("w", photo.width());
            p.putInt("h", photo.height());
            p.putInt("bytes", photo.bytes());
            photos.add(p);
        }
        tag.put("photos", photos);
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

        if (tag.contains("challenge", Tag.TAG_COMPOUND)) {
            CompoundTag c = tag.getCompound("challenge");
            party.challenge().restore(c.getLong("last"), c.getLong("scheduled"), c.getBoolean("forced"),
                    c.getBoolean("active"), c.getInt("nights"), c.getLong("kills"), c.getLong("carins"),
                    c.getInt("nightKills"), c.getLong("nightCarins"));
        }
        CompoundTag market = tag.getCompound("market");
        for (String key : market.getAllKeys()) {
            party.marketPurchases().put(key, market.getInt(key));
        }
        if (tag.contains("camp", Tag.TAG_COMPOUND)) {
            CompoundTag camp = tag.getCompound("camp");
            party.setCamp(new CampSite(camp.getString("dim"), camp.getInt("x"), camp.getInt("y"), camp.getInt("z"), camp.getLong("t")));
        }
        CompoundTag cooldowns = tag.getCompound("travel");
        for (String key : cooldowns.getAllKeys()) {
            party.travelCooldowns().put(UUID.fromString(key), cooldowns.getLong(key));
        }
        CompoundTag upgrades = tag.getCompound("upgrades");
        for (String key : upgrades.getAllKeys()) {
            party.upgradeLevels().put(key, upgrades.getInt(key));
        }
        ListTag photos = tag.getList("photos", Tag.TAG_COMPOUND);
        for (int i = 0; i < photos.size(); i++) {
            CompoundTag p = photos.getCompound(i);
            if (p.hasUUID("author")) {
                party.photos().add(new Photo(p.getString("id"), p.getUUID("author"), p.getString("name"), p.getLong("t"),
                        p.getString("caption"), p.getInt("w"), p.getInt("h"), p.getInt("bytes")));
            }
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
        tag.putInt("phoneSerial", profile.phoneSerial());
        tag.putBoolean("tips", profile.tipsEnabled());
        tag.putLong("giftDay", profile.giftDay());
        tag.putLong("cvDay", profile.cobbleverseDay());
        tag.putInt("cvSales", profile.salesToday());
        tag.putInt("cvWonders", profile.wondersToday());
        CompoundTag bought = new CompoundTag();
        profile.boughtToday().forEach(bought::putInt);
        tag.put("cvBought", bought);
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
        profile.setPhoneSerial(tag.getInt("phoneSerial"));
        profile.setTipsEnabled(!tag.contains("tips") || tag.getBoolean("tips"));
        profile.setGiftDay(tag.contains("giftDay") ? tag.getLong("giftDay") : -1);
        profile.setCobbleverseDay(tag.contains("cvDay") ? tag.getLong("cvDay") : -1);
        profile.setSalesToday(tag.getInt("cvSales"));
        profile.setWondersToday(tag.getInt("cvWonders"));
        CompoundTag bought = tag.getCompound("cvBought");
        for (String key : bought.getAllKeys()) {
            profile.boughtToday().put(key, bought.getInt(key));
        }
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
