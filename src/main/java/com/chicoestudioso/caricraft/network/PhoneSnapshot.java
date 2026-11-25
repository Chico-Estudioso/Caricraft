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
        boolean tipsEnabled,
        ChallengeView challenge,
        MarketView market,
        CobbleverseView cobbleverse,
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
                                List<String> blockedMobs, int wave, int effectiveLevel, boolean waves,
                                List<CacheView> caches) {
    }

    /**
     * Parte Cobbleverse del Mercado ({@code null} si está desactivada o falta Cobblemon).
     *
     * @param cobbleDollars saldo del jugador, o -1 si CobbleDollars no está instalado
     * @param nextDayAt     cuándo vuelve el regalo y cambian las ofertas
     */
    public record CobbleverseView(boolean cobbleDollarsInstalled, long cobbleDollars, boolean giftEnabled, boolean giftAvailable,
                                  long nextDayAt, boolean shopEnabled, List<CvOfferView> offers, boolean sellEnabled,
                                  List<SellView> party, int salesToday, int maxSales, boolean wonderEnabled, int wondersLeft,
                                  int wondersPerDay, int wonderPool, WonderView lastWonder, LuckView luck) {
    }

    /** Cajas, tragaperras, mejoras y fusiones del CobbleCentro. Las partes desactivadas llegan vacías o {@code null}. */
    public record LuckView(List<CrateView> crates, SlotsView slots, List<UpgradeView> upgrades, List<FusionView> fusions,
                           LuckResultView last) {
    }

    public record CrateView(String id, long price, String icon, int opened, int dailyLimit, List<RewardView> rewards) {
    }

    /**
     * Premio posible de una caja.
     *
     * @param kind   ITEM, POKEMON, CARINS o COBBLEDOLLARS
     * @param chance probabilidad (0–1)
     */
    public record RewardView(String id, String kind, String icon, String item, int min, int max, List<String> species, int level,
                             boolean shiny, int perfectIvs, String rarity, double chance) {
    }

    /** @param returnToPlayer lo que devuelve la máquina a la larga por cada CobbleDollar apostado (calculado en el servidor) */
    public record SlotsView(List<Long> bets, int spinsLeft, int spinsPerDay, List<SymbolView> symbols, double returnToPlayer) {
    }

    public record SymbolView(String id, String icon, double pay3, double pay2) {
    }

    /** Mejora de Pokémon: {@code kind} iv, level o shiny. */
    public record UpgradeView(String id, String kind, long price, int amount, double chance) {
    }

    public record FusionView(String id, List<IngredientView> inputs, long price, String output, int count, double chance) {
    }

    public record IngredientView(String item, int count) {
    }

    /**
     * Último resultado de suerte del jugador, para la animación.
     *
     * @param kind    crate, spin, upgrade o fusion
     * @param id      caja, mejora o fusión
     * @param reward  índice del premio de la caja
     * @param species Pokémon recibido (caja) o mejorado
     * @param amount  cantidad recibida (objetos, dinero) o valor nuevo (nivel)
     * @param reels   símbolos de la tragaperras
     * @param payout  lo que ha pagado la tragaperras
     */
    public record LuckResultView(String kind, String id, long time, int reward, String species, List<String> aspects, boolean shiny,
                                 int amount, List<Integer> reels, long payout, boolean success) {
    }

    /**
     * Último intercambio prodigioso del jugador (para la animación). {@code from} vacío = Pokémon generado.
     *
     * @param time momento del intercambio (la animación se lanza cuando cambia)
     */
    public record WonderView(String species, List<String> aspects, int level, boolean shiny, String from, String sent, long time) {
    }

    /** Oferta de la tienda Cobbleverse: un objeto ({@code item}) o un Pokémon ({@code species} no vacía). */
    public record CvOfferView(String id, String icon, String item, int count, List<String> species, int level, boolean shiny,
                              int perfectIvs, List<String> moves, long price, int boughtToday, int dailyLimit) {
    }

    /** Pokémon del equipo que se puede vender. {@code blocked} es el motivo si no se puede, o {@code null}. */
    public record SellView(String uuid, String species, String nickname, int level, boolean shiny, int ivTotal, long price,
                           String blocked, List<String> aspects) {
    }

    /** Botín protegido por un escuadrón: dónde está, cuántos guardias quedan y si ya se puede abrir. */
    public record CacheView(String dimension, double x, double y, double z, int guardsLeft, boolean open) {
    }
}
