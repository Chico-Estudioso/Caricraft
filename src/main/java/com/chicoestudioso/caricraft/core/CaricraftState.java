package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.core.party.Invite;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Raíz del estado persistente de Caricraft Mod. Lo serializa {@code server.StateCodec}.
 */
public final class CaricraftState {
    /** Versión del esquema persistido. Incrementar y añadir migración en {@code StateCodec} si cambia. */
    public static final int SCHEMA_VERSION = 2;

    private final Map<UUID, Party> parties = new LinkedHashMap<>();
    private final Map<UUID, PlayerProfile> profiles = new LinkedHashMap<>();
    /** Invitaciones en memoria (no persistidas). */
    private final List<Invite> invites = new ArrayList<>();
    /** Peticiones de viaje estilo /tpa (no persistidas). */
    private final List<com.chicoestudioso.caricraft.core.camp.TravelRequest> travelRequests = new ArrayList<>();
    private Supplier<UUID> idGenerator = UUID::randomUUID;

    public Map<UUID, Party> parties() {
        return parties;
    }

    public Map<UUID, PlayerProfile> profiles() {
        return profiles;
    }

    public List<Invite> invites() {
        return invites;
    }

    public List<com.chicoestudioso.caricraft.core.camp.TravelRequest> travelRequests() {
        return travelRequests;
    }

    public UUID newId() {
        UUID id;
        do {
            id = idGenerator.get();
        } while (parties.containsKey(id));
        return id;
    }

    /** Para tests deterministas. */
    public void setIdGenerator(Supplier<UUID> idGenerator) {
        this.idGenerator = idGenerator;
    }

    public Optional<PlayerProfile> profile(UUID player) {
        return Optional.ofNullable(profiles.get(player));
    }

    /** Grupo del jugador o {@code null} si el jugador no tiene perfil. */
    public Party partyOf(UUID player) {
        PlayerProfile profile = profiles.get(player);
        return profile == null ? null : parties.get(profile.partyId());
    }

    public String nameOf(UUID player) {
        PlayerProfile profile = profiles.get(player);
        return profile == null ? player.toString().substring(0, 8) : profile.name();
    }
}
