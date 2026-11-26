package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.mission.MissionCatalog;
import com.chicoestudioso.caricraft.core.mission.MissionDefinition;
import com.chicoestudioso.caricraft.core.party.Party;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/** Utilidades comunes para los tests del núcleo. */
public final class TestFixture {
    public static final long START = 1_767_225_600_000L; // 2026-01-01T00:00:00Z (jueves)

    public final MutableClock clock = new MutableClock(START);
    public final CaricraftConfig config = utc(new CaricraftConfig().validated());

    /** Los tests cuentan los días en UTC (los de zona horaria lo cambian a propósito). */
    private static CaricraftConfig utc(CaricraftConfig config) {
        config.timeZone = "UTC";
        return config;
    }
    public final CaricraftState state = new CaricraftState();
    public CaricraftCore core;

    public final UUID alice = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    public final UUID bob = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    public final UUID carol = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    public static TestFixture withDefaultMissions() {
        TestFixture f = new TestFixture();
        f.core = new CaricraftCore(f.state, f.config, defaultCatalog(true), f.clock);
        f.joinAll();
        return f;
    }

    public static TestFixture withMissions(Collection<MissionDefinition> missions) {
        TestFixture f = new TestFixture();
        f.core = new CaricraftCore(f.state, f.config, MissionCatalog.of(missions, mod -> true), f.clock);
        f.joinAll();
        return f;
    }

    public static MissionCatalog defaultCatalog(boolean cobblemon) {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                TestFixture.class.getResourceAsStream("/caricraft/default_missions.json")), StandardCharsets.UTF_8)) {
            return MissionCatalog.fromJson(reader, mod -> cobblemon || !"cobblemon".equals(mod));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void joinAll() {
        core.onPlayerJoin(alice, "Alice");
        core.onPlayerJoin(bob, "Bob");
        core.onPlayerJoin(carol, "Carol");
    }

    /** Forma el dúo Alice + Bob. */
    public Party formDuo() {
        ActionResult invite = core.parties().invite(state, alice, bob);
        if (!invite.success()) {
            throw new IllegalStateException(invite.key());
        }
        ActionResult accept = core.parties().accept(state, bob, alice);
        if (!accept.success()) {
            throw new IllegalStateException(accept.key());
        }
        return state.partyOf(alice);
    }

    public void give(UUID player, long amount) {
        core.economy().credit(state.partyOf(player).wallet(), amount, "test", "", null);
    }

    public long balance(UUID player) {
        return state.partyOf(player).wallet().balance();
    }

    public static final class MutableClock implements Clock {
        private long now;

        public MutableClock(long now) {
            this.now = now;
        }

        @Override
        public long now() {
            return now;
        }

        public void advance(long millis) {
            now += millis;
        }
    }
}
