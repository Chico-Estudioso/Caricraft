package com.chicoestudioso.caricraft.core.camp;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.CaricraftState;
import com.chicoestudioso.caricraft.core.Clock;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleService;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
import com.chicoestudioso.caricraft.core.shop.ShopService;

import java.util.UUID;

/**
 * Viajes de pago del dúo con tiempo de espera:
 * <ul>
 *     <li>CariCamp ({@link AppCatalog#CARICAMP}): un campamento por grupo y viaje hasta él.</li>
 *     <li>TP Dúo ({@link AppCatalog#TPDUO}): peticiones estilo /tpa para ir junto al compañero o traerlo.</li>
 * </ul>
 * Las dos apps comparten la espera entre viajes. El servidor valida la hoguera y el destino antes de cobrar;
 * aquí solo se aplican las reglas.
 */
public final class CampService {
    public enum Destination { CAMP, PARTNER }

    private final CaricraftConfig config;
    private final EconomyService economy;
    private final ChronicleService chronicle;
    private final ShopService shop;
    private final Clock clock;

    public CampService(CaricraftConfig config, EconomyService economy, ChronicleService chronicle, ShopService shop, Clock clock) {
        this.config = config;
        this.economy = economy;
        this.chronicle = chronicle;
        this.shop = shop;
        this.clock = clock;
    }

    /** Tiene CariCamp (campamento y viaje al campamento). */
    public boolean hasApp(Party party) {
        return party != null && shop.hasApp(party, AppCatalog.CARICAMP);
    }

    /** Tiene TP Dúo (viajes junto al compañero). */
    public boolean hasTravelApp(Party party) {
        return party != null && shop.hasApp(party, AppCatalog.TPDUO);
    }

    /** App que hace falta para viajar a ese destino. */
    public static String requiredApp(Destination destination) {
        return destination == Destination.CAMP ? AppCatalog.CARICAMP : AppCatalog.TPDUO;
    }

    public boolean canTravel(Party party, Destination destination) {
        return destination == Destination.CAMP ? hasApp(party) : hasTravelApp(party);
    }

    public long cost(Destination destination) {
        return destination == Destination.CAMP ? config.camp.campTravelCost : config.camp.partnerTravelCost;
    }

    public long cooldownEndsAt(Party party, UUID player) {
        return party.travelCooldowns().getOrDefault(player, 0L);
    }

    public ActionResult setCamp(Party party, UUID player, String playerName, String dimension, int x, int y, int z) {
        if (!hasApp(party)) {
            return ActionResult.fail("app_locked", AppCatalog.CARICAMP);
        }
        party.setCamp(new CampSite(dimension, x, y, z, clock.now()));
        chronicle.add(party, ChronicleEntry.CAMP_SET, player, playerName, dimension);
        return ActionResult.ok("camp_set");
    }

    /**
     * Comprueba las reglas y cobra el viaje. Si devuelve éxito, el servidor teletransporta; si el teletransporte
     * fallara, debe llamar a {@link #refund}.
     *
     * @param challengeNight si el grupo está en noche de desafío
     */
    public ActionResult chargeTravel(Party party, UUID player, Destination destination, boolean challengeNight) {
        if (!canTravel(party, destination)) {
            return ActionResult.fail("app_locked", requiredApp(destination));
        }
        if (destination == Destination.CAMP && party.camp() == null) {
            return ActionResult.fail("camp_missing");
        }
        if (destination == Destination.PARTNER && !party.isDuo()) {
            return ActionResult.fail("not_in_duo");
        }
        if (challengeNight && config.camp.blockDuringChallenge) {
            return ActionResult.fail("travel_challenge");
        }
        long now = clock.now();
        long until = cooldownEndsAt(party, player);
        if (until > now) {
            return ActionResult.fail("travel_cooldown", Math.max(1, (until - now + 999) / 1000));
        }
        long cost = cost(destination);
        if (cost > 0) {
            EconomyService.Result result = economy.debit(party.wallet(), cost,
                    destination == Destination.CAMP ? "travel_camp" : "travel_partner", "");
            if (result == EconomyService.Result.INSUFFICIENT_FUNDS) {
                return ActionResult.fail("insufficient_funds", cost - party.wallet().balance());
            }
            if (result != EconomyService.Result.OK) {
                return ActionResult.fail("invalid_amount");
            }
        }
        party.travelCooldowns().put(player, now + config.camp.cooldownSeconds * 1000L);
        party.incrementStat("travels", 1);
        return ActionResult.ok(destination == Destination.CAMP ? "travel_camp" : "travel_partner", cost);
    }

    // ------------------------------------------------------------------ peticiones estilo /tpa

    /** Resultado de aceptar una petición. */
    public record Acceptance(ActionResult result, TravelRequest request) {
    }

    /**
     * {@code from} pide ir hacia {@code to} o traerlo. Comprueba las reglas del viajero, pero no cobra:
     * se cobra al llegar.
     */
    public ActionResult request(CaricraftState state, UUID from, UUID to, TravelRequest.Kind kind, boolean challengeNight) {
        Party party = state.partyOf(from);
        if (!hasTravelApp(party)) {
            return ActionResult.fail("app_locked", AppCatalog.TPDUO);
        }
        if (to == null || from.equals(to) || !party.members().contains(to)) {
            return ActionResult.fail("not_in_duo");
        }
        if (challengeNight && config.camp.blockDuringChallenge) {
            return ActionResult.fail("travel_challenge");
        }
        TravelRequest request = new TravelRequest(from, to, kind, clock.now() + config.camp.requestSeconds * 1000L);
        long until = cooldownEndsAt(party, request.traveler());
        if (until > clock.now()) {
            return ActionResult.fail("travel_cooldown", Math.max(1, (until - clock.now() + 999) / 1000));
        }
        if (party.wallet().balance() < cost(Destination.PARTNER)) {
            return ActionResult.fail("insufficient_funds", cost(Destination.PARTNER) - party.wallet().balance());
        }
        purgeExpired(state);
        state.travelRequests().removeIf(r -> r.from().equals(from));
        state.travelRequests().add(request);
        return ActionResult.ok(kind == TravelRequest.Kind.GO ? "travel_request_go" : "travel_request_bring", state.nameOf(to));
    }

    /** {@code accepter} acepta la petición más reciente que le han hecho. */
    public Acceptance accept(CaricraftState state, UUID accepter) {
        purgeExpired(state);
        TravelRequest found = null;
        for (TravelRequest request : state.travelRequests()) {
            if (request.to().equals(accepter)) {
                found = request;
            }
        }
        if (found == null) {
            return new Acceptance(ActionResult.fail("travel_request_missing"), null);
        }
        state.travelRequests().remove(found);
        return new Acceptance(ActionResult.ok("travel_request_accepted", state.nameOf(found.from())), found);
    }

    /** Rechaza las peticiones recibidas o cancela las enviadas por {@code player}. */
    public ActionResult decline(CaricraftState state, UUID player) {
        purgeExpired(state);
        boolean removed = state.travelRequests().removeIf(r -> r.to().equals(player) || r.from().equals(player));
        return removed ? ActionResult.ok("travel_request_declined") : ActionResult.fail("travel_request_missing");
    }

    public java.util.Optional<TravelRequest> incoming(CaricraftState state, UUID player) {
        purgeExpired(state);
        return state.travelRequests().stream().filter(r -> r.to().equals(player)).reduce((a, b) -> b);
    }

    public java.util.Optional<TravelRequest> outgoing(CaricraftState state, UUID player) {
        purgeExpired(state);
        return state.travelRequests().stream().filter(r -> r.from().equals(player)).findFirst();
    }

    private void purgeExpired(CaricraftState state) {
        long now = clock.now();
        state.travelRequests().removeIf(r -> r.expired(now));
    }

    /** Devuelve el coste y quita la espera si el viaje no pudo hacerse. */
    public void refund(Party party, UUID player, Destination destination) {
        long cost = cost(destination);
        if (cost > 0) {
            economy.credit(party.wallet(), cost, "travel_refund", "", null);
        }
        party.travelCooldowns().remove(player);
    }

    public void clearCamp(Party party) {
        party.setCamp(null);
    }
}
