package com.chicoestudioso.caricraft.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;
import java.util.Map;

/**
 * Lo que el CariPhone necesita para pintar todas sus pantallas. Lo construye el servidor; el cliente
 * nunca lo modifica ni decide nada a partir de él salvo cómo mostrarlo.
 */
public record PhoneSnapshot(
        int protocolVersion,
        long serverTime,
        String viewerUuid,
        long balance,
        String theme,
        List<String> ownedCosmetics,
        PartyView party,
        List<AppView> apps,
        List<MissionView> missions,
        List<LedgerView> ledger,
        List<ChronicleView> chronicle,
        ExplorationView exploration,
        Map<String, Long> stats,
        List<InviteView> incomingInvites,
        List<InviteView> outgoingInvites,
        boolean leavePending,
        boolean cobblemonLoaded) {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public String toJson() {
        return GSON.toJson(this);
    }

    public static PhoneSnapshot fromJson(String json) {
        return GSON.fromJson(json, PhoneSnapshot.class);
    }

    /** Grupo del jugador. {@code duo} es verdadero si hay más de un miembro. */
    public record PartyView(String id, boolean duo, int maxSize, List<MemberView> members, long cooldownEndsAt) {
    }

    public record MemberView(String uuid, String name, boolean online, boolean self) {
    }

    /**
     * Artículo del catálogo.
     *
     * @param kind      {@code APP} o {@code COSMETIC}
     * @param ownership {@code PARTY} o {@code PERSONAL}
     * @param state     {@code BUILTIN}, {@code OWNED}, {@code AVAILABLE}, {@code LOCKED} o {@code COMING_SOON}
     * @param icon      id de ítem que el cliente dibuja como icono
     */
    public record AppView(String id, String kind, String ownership, long price, String state, List<String> requires,
                          String icon, int color) {
    }

    /**
     * Misión visible.
     *
     * @param title       texto literal para misiones personalizadas sin traducción (puede ser {@code null})
     * @param description idem
     */
    public record MissionView(String id, String scope, String category, String status, long reward,
                              boolean requireAllMembers, List<ObjectiveView> objectives,
                              Map<String, Long> contributions, long expiresAt, String title, String description) {
    }

    public record ObjectiveView(String type, String target, long required, long progress) {
    }

    public record LedgerView(long time, long delta, long balanceAfter, String reason, String detail) {
    }

    public record ChronicleView(long time, String type, String actor, List<String> args) {
    }

    public record ExplorationView(int discovered, int total, List<String> biomes) {
    }

    public record InviteView(String uuid, String name, long expiresAt) {
    }
}
