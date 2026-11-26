package com.chicoestudioso.caricraft.core.party;

import com.chicoestudioso.caricraft.core.camp.CampSite;
import com.chicoestudioso.caricraft.core.challenge.ChallengeState;
import com.chicoestudioso.caricraft.core.lens.Photo;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.economy.Wallet;
import com.chicoestudioso.caricraft.core.mission.MissionState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Grupo de jugadores. Un jugador sin vincular es un grupo de un miembro; un dúo es un grupo de dos.
 * Todo lo compartido vive aquí: saldo, apps, misiones, crónicas, exploración y estadísticas.
 */
public final class Party {
    private final UUID id;
    private final long createdAt;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Wallet wallet = new Wallet();
    private final Set<String> ownedApps = new LinkedHashSet<>();
    /** Clave: id de misión (cooperativa) o {@code id@uuid} (individual). */
    private final Map<String, MissionState> missions = new LinkedHashMap<>();
    /** Misiones reclamadas alguna vez (mismas claves que {@link #missions}). */
    private final Set<String> claimedEver = new LinkedHashSet<>();
    /** Del más reciente al más antiguo. */
    private final Deque<ChronicleEntry> chronicle = new ArrayDeque<>();
    private final Set<String> discoveredBiomes = new LinkedHashSet<>();
    private final Map<String, Long> stats = new LinkedHashMap<>();
    private final ChallengeState challenge = new ChallengeState();
    /** Caza del día y Pokédex del dúo (Cobbleverse). */
    private final com.chicoestudioso.caricraft.core.cobbleverse.DuoPokemonState pokemon =
            new com.chicoestudioso.caricraft.core.cobbleverse.DuoPokemonState();
    /** Niveles comprados de mejoras (p. ej. ampliaciones de álbum). */
    private final Map<String, Integer> upgradeLevels = new LinkedHashMap<>();
    /** Fotos de CariLens, de la más reciente a la más antigua. */
    private final java.util.List<Photo> photos = new java.util.ArrayList<>();
    /** Campamento de CariCamp, o {@code null}. */
    private CampSite camp;
    /** Fin de la espera entre viajes de cada miembro. */
    private final Map<UUID, Long> travelCooldowns = new LinkedHashMap<>();
    /** Compras del Mercado: clave {@code dia:oferta} → unidades compradas. */
    private final Map<String, Integer> marketPurchases = new LinkedHashMap<>();

    public Party(UUID id, long createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public long createdAt() {
        return createdAt;
    }

    public Set<UUID> members() {
        return members;
    }

    public boolean isDuo() {
        return members.size() >= 2;
    }

    public Wallet wallet() {
        return wallet;
    }

    public Set<String> ownedApps() {
        return ownedApps;
    }

    public Map<String, MissionState> missions() {
        return missions;
    }

    public Set<String> claimedEver() {
        return claimedEver;
    }

    public Deque<ChronicleEntry> chronicle() {
        return chronicle;
    }

    public Set<String> discoveredBiomes() {
        return discoveredBiomes;
    }

    public Map<String, Long> stats() {
        return stats;
    }

    public Map<String, Integer> upgradeLevels() {
        return upgradeLevels;
    }

    public int upgradeLevel(String id) {
        return upgradeLevels.getOrDefault(id, 0);
    }

    public java.util.List<Photo> photos() {
        return photos;
    }

    public CampSite camp() {
        return camp;
    }

    public void setCamp(CampSite camp) {
        this.camp = camp;
    }

    public Map<UUID, Long> travelCooldowns() {
        return travelCooldowns;
    }

    public Map<String, Integer> marketPurchases() {
        return marketPurchases;
    }

    public ChallengeState challenge() {
        return challenge;
    }

    public com.chicoestudioso.caricraft.core.cobbleverse.DuoPokemonState pokemon() {
        return pokemon;
    }

    public void incrementStat(String key, long amount) {
        if (amount > 0) {
            stats.merge(key, amount, (a, b) -> {
                long sum = a + b;
                return sum < 0 ? Long.MAX_VALUE : sum;
            });
        }
    }

    public long stat(String key) {
        return stats.getOrDefault(key, 0L);
    }
}
