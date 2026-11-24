package com.chicoestudioso.caricraft.core.party;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Datos personales de un jugador: grupo, cosméticos y tema. */
public final class PlayerProfile {
    public static final String DEFAULT_THEME = "classic";

    private final UUID uuid;
    private String name;
    private UUID partyId;
    private final Set<String> ownedCosmetics = new LinkedHashSet<>();
    private String theme = DEFAULT_THEME;
    /** Hasta cuándo no puede vincularse (tras desvincular). */
    private long linkCooldownUntil;
    private boolean starterPhoneGiven;
    /** Momento de la primera pulsación de "desvincular" (no se persiste). */
    private transient long leaveRequestedAt;

    public PlayerProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    public UUID partyId() {
        return partyId;
    }

    public void setPartyId(UUID partyId) {
        this.partyId = partyId;
    }

    public Set<String> ownedCosmetics() {
        return ownedCosmetics;
    }

    public String theme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme == null || theme.isBlank() ? DEFAULT_THEME : theme;
    }

    public long linkCooldownUntil() {
        return linkCooldownUntil;
    }

    public void setLinkCooldownUntil(long linkCooldownUntil) {
        this.linkCooldownUntil = linkCooldownUntil;
    }

    public boolean starterPhoneGiven() {
        return starterPhoneGiven;
    }

    public void setStarterPhoneGiven(boolean starterPhoneGiven) {
        this.starterPhoneGiven = starterPhoneGiven;
    }

    public long leaveRequestedAt() {
        return leaveRequestedAt;
    }

    public void setLeaveRequestedAt(long leaveRequestedAt) {
        this.leaveRequestedAt = leaveRequestedAt;
    }
}
