package com.chicoestudioso.caricraft.server;

import com.chicoestudioso.caricraft.core.CaricraftState;
import com.chicoestudioso.caricraft.core.TestFixture;
import com.chicoestudioso.caricraft.core.mission.MissionStatus;
import com.chicoestudioso.caricraft.core.mission.ObjectiveType;
import com.chicoestudioso.caricraft.core.mission.ProgressEvent;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateCodecTest {
    @Test
    void roundTripPreservesEverything() throws IOException {
        TestFixture f = TestFixture.withDefaultMissions();
        Party duo = f.formDuo();
        f.give(f.alice, 2000);
        f.core.shop().buy(f.state, f.alice, AppCatalog.CHRONICLES);
        f.core.shop().buy(f.state, f.bob, "theme_sunset");
        f.core.shop().selectTheme(f.state, f.bob, "theme_sunset");
        f.state.profiles().get(f.bob).setPhoneSerial(4);
        f.core.addNote(f.alice, "Nota de prueba");
        f.core.onBiomeSampled(f.alice, "minecraft:plains", true);
        f.core.missions().accept(f.state, f.alice, "duo_expedition");
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.TRAVEL, 1234));
        f.core.missions().accept(f.state, f.bob, "first_steps");
        f.core.progress(f.bob, ProgressEvent.of(ObjectiveType.TRAVEL, 600));
        f.core.missions().claim(f.state, f.bob, "first_steps");

        f.core.challenge().bringForward(duo, 7, true);
        f.core.challenge().update(duo, 7, true);
        f.core.challenge().onKill(duo);

        CompoundTag written = StateCodec.write(f.state);
        // Pasar por bytes como haría el disco.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.write(written, new DataOutputStream(bytes));
        CompoundTag reread = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        CaricraftState loaded = StateCodec.read(reread);

        Party loadedDuo = loaded.partyOf(f.alice);
        assertEquals(duo.id(), loadedDuo.id());
        assertEquals(duo.members(), loadedDuo.members());
        assertEquals(duo.wallet().balance(), loadedDuo.wallet().balance());
        assertEquals(duo.wallet().ledger().stream().toList(), loadedDuo.wallet().ledger().stream().toList());
        assertEquals(duo.wallet().processedKeys().stream().toList(), loadedDuo.wallet().processedKeys().stream().toList());
        assertEquals(duo.ownedApps(), loadedDuo.ownedApps());
        assertEquals(duo.claimedEver(), loadedDuo.claimedEver());
        assertEquals(duo.discoveredBiomes(), loadedDuo.discoveredBiomes());
        assertEquals(duo.stats(), loadedDuo.stats());
        assertEquals(duo.chronicle().stream().toList(), loadedDuo.chronicle().stream().toList());
        assertEquals(duo.missions().keySet(), loadedDuo.missions().keySet());
        assertEquals(1834, loadedDuo.missions().get("duo_expedition").progress()[0]); // 1234 de Alice + 600 de Bob
        assertEquals(600L, loadedDuo.missions().get("duo_expedition").contributions().get(f.bob));
        assertEquals(MissionStatus.ACCEPTED, loadedDuo.missions().get("duo_expedition").status());
        assertEquals(1234L, loadedDuo.missions().get("duo_expedition").contributions().get(f.alice));
        assertTrue(loadedDuo.challenge().active());
        assertEquals(7, loadedDuo.challenge().scheduledDay());
        assertEquals(1, loadedDuo.challenge().nightKills());
        assertEquals(duo.challenge().totalCarins(), loadedDuo.challenge().totalCarins());
        assertEquals("theme_sunset", loaded.profiles().get(f.bob).theme());
        assertEquals(4, loaded.profiles().get(f.bob).phoneSerial());
        assertTrue(loaded.profiles().get(f.bob).ownedCosmetics().contains("theme_sunset"));
        assertEquals(f.state.profiles().keySet(), loaded.profiles().keySet());
        assertEquals(f.state.parties().keySet(), loaded.parties().keySet());
    }

    @Test
    void claimedMissionsStayClaimedAfterReload() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.core.missions().accept(f.state, f.alice, "first_steps");
        f.core.progress(f.alice, ProgressEvent.of(ObjectiveType.TRAVEL, 600));
        f.core.missions().claim(f.state, f.alice, "first_steps");
        long balance = f.balance(f.alice);

        TestFixture reloaded = new TestFixture();
        CaricraftState state = StateCodec.read(StateCodec.write(f.state));
        reloaded.core = new com.chicoestudioso.caricraft.core.CaricraftCore(state, reloaded.config,
                TestFixture.defaultCatalog(true), reloaded.clock);
        assertEquals("caricraft.result.mission_already_claimed",
                reloaded.core.missions().claim(state, f.alice, "first_steps").key());
        assertEquals(balance, state.partyOf(f.alice).wallet().balance());
    }

    @Test
    void emptyOrOldDataLoads() {
        CaricraftState empty = StateCodec.read(new CompoundTag());
        assertTrue(empty.parties().isEmpty());
        CompoundTag future = new CompoundTag();
        future.putInt("schemaVersion", CaricraftState.SCHEMA_VERSION + 5);
        assertTrue(StateCodec.read(future).profiles().isEmpty());
    }

    @Test
    void oldSavesWithCariCampGetTpDuo() {
        TestFixture f = TestFixture.withDefaultMissions();
        f.formDuo();
        f.give(f.alice, 2000);
        f.core.shop().buy(f.state, f.alice, AppCatalog.CARICAMP);
        f.core.shop().buy(f.state, f.carol, AppCatalog.STATS);

        CompoundTag v1 = StateCodec.write(f.state);
        v1.putInt("schemaVersion", 1);
        CaricraftState migrated = StateCodec.read(v1);
        assertTrue(migrated.partyOf(f.alice).ownedApps().contains(AppCatalog.TPDUO),
                "quien compró CariCamp antes de TP Dúo no pierde los viajes con el compañero");
        assertFalse(migrated.partyOf(f.carol).ownedApps().contains(AppCatalog.TPDUO));

        // Con el esquema actual no se regala nada: CariCamp ya no incluye los viajes al compañero.
        CaricraftState current = StateCodec.read(StateCodec.write(f.state));
        assertFalse(current.partyOf(f.alice).ownedApps().contains(AppCatalog.TPDUO));
    }
}
