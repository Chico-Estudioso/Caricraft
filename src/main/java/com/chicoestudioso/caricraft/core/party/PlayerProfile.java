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
    /** Número de serie del CariPhone vigente (0 = aún no tiene ninguno vinculado). */
    private int phoneSerial;
    /** Recibir consejos del CariPhone en el chat. */
    private boolean tipsEnabled = true;
    /** Día (índice) del último regalo misterioso abierto; -1 si nunca. */
    private long giftDay = -1;
    /** Día al que se refieren los contadores de la tienda Cobbleverse. */
    private long cobbleverseDay = -1;
    private int salesToday;
    private int wondersToday;
    private final java.util.Map<String, Integer> boughtToday = new java.util.HashMap<>();
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

    public int phoneSerial() {
        return phoneSerial;
    }

    public void setPhoneSerial(int phoneSerial) {
        this.phoneSerial = Math.max(0, phoneSerial);
    }

    public boolean tipsEnabled() {
        return tipsEnabled;
    }

    public void setTipsEnabled(boolean tipsEnabled) {
        this.tipsEnabled = tipsEnabled;
    }

    public long giftDay() {
        return giftDay;
    }

    public void setGiftDay(long giftDay) {
        this.giftDay = giftDay;
    }

    public long cobbleverseDay() {
        return cobbleverseDay;
    }

    public void setCobbleverseDay(long cobbleverseDay) {
        this.cobbleverseDay = cobbleverseDay;
    }

    public int salesToday() {
        return salesToday;
    }

    public void setSalesToday(int salesToday) {
        this.salesToday = Math.max(0, salesToday);
    }

    public int wondersToday() {
        return wondersToday;
    }

    public void setWondersToday(int wondersToday) {
        this.wondersToday = Math.max(0, wondersToday);
    }

    /** Compras de hoy en la tienda Cobbleverse, por oferta. */
    public java.util.Map<String, Integer> boughtToday() {
        return boughtToday;
    }

    public long leaveRequestedAt() {
        return leaveRequestedAt;
    }

    public void setLeaveRequestedAt(long leaveRequestedAt) {
        this.leaveRequestedAt = leaveRequestedAt;
    }
}
