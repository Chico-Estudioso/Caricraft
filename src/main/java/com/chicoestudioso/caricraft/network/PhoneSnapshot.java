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
        boolean cobblemonLoaded,
        ChallengeView challenge,
        MarketView market,
        CampView camp,
        DuoTravelView tpduo,
        LensView lens,
        BoxView box) {

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
                          String icon, int color, int level, int maxLevel) {
    }

    /** CariBox: filas y huecos ocupados. */
    public record BoxView(boolean enabled, int rows, int usedSlots) {
    }

    /** Álbum de CariLens (vacío si el grupo no tiene la app). */
    public record LensView(boolean enabled, int capacity, List<PhotoView> photos) {
    }

    public record PhotoView(String id, String author, long time, String caption, int width, int height) {
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

    /**
     * CariCamp: campamento del grupo.
     *
     * @param camp          campamento o {@code null}
     * @param warmupSeconds cuenta atrás hacia el campamento en marcha (0 si no hay)
     */
    public record CampView(PositionView camp, long cooldownEndsAt, long cost, boolean challengeBlocked,
                           int warmupSeconds) {
    }

    /**
     * TP Dúo: dónde está el compañero y peticiones de viaje estilo /tpa. Las posiciones solo se envían a
     * miembros del grupo que tienen la app.
     *
     * @param partners           demás miembros conectados
     * @param warmupSeconds      cuenta atrás hacia el compañero en marcha (0 si no hay)
     * @param requestSeconds     lo que dura una petición sin respuesta
     * @param warmupTotalSeconds duración total de la cuenta atrás
     */
    public record DuoTravelView(List<PositionView> partners, long cooldownEndsAt, long cost, boolean challengeBlocked,
                                TravelView incoming, TravelView outgoing, int warmupSeconds, int requestSeconds,
                                int warmupTotalSeconds) {
    }

    /** Petición de viaje estilo /tpa: {@code kind} es {@code GO} (ir hacia) o {@code BRING} (traer). */
    public record TravelView(String name, String kind, long expiresAt) {
    }

    /** Posición de un jugador o del campamento. {@code uuid}, {@code name} y {@code biome} pueden ir vacíos. */
    public record PositionView(String uuid, String name, String dimension, String biome, double x, double y, double z) {
    }

    /** Mercado del día. */
    public record MarketView(boolean enabled, long refreshAt, List<OfferView> offers) {
    }

    public record OfferView(String id, String item, int count, long price, int stockLeft, int dailyStock, String source) {
    }

    /**
     * Desafío Dúo.
     *
     * @param tonight          hay desafío programado para esta noche (aún no ha empezado)
     * @param nightsUntilForced noches hasta el próximo desafío obligatorio (0 = esta noche)
     * @param blockedMobs      mobs que MobsBeGone sigue bloqueando (el servidor debe prepararse)
     */
    public record ChallengeView(boolean enabled, boolean active, boolean tonight, boolean forced, boolean night,
                                long nightsUntilForced, int intervalNights, int level, int maxLevel,
                                int nightsCompleted, long totalKills, long totalCarins, int nightKills,
                                long nightCarins, long killReward, long nightReward, boolean blockSleep,
                                List<String> blockedMobs, int wave, int effectiveLevel, boolean waves) {
    }
}
