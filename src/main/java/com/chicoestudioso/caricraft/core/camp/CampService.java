package com.chicoestudioso.caricraft.core.camp;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.Clock;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleService;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
import com.chicoestudioso.caricraft.core.shop.ShopService;

import java.util.UUID;

/**
 * CariCamp: un campamento por grupo y viajes de pago (al campamento o junto al compañero) con tiempo de espera.
 * El servidor valida la hoguera y el destino antes de cobrar; aquí solo se aplican las reglas.
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

    public boolean hasApp(Party party) {
        return shop.hasApp(party, AppCatalog.CARICAMP);
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
        if (!hasApp(party)) {
            return ActionResult.fail("app_locked", AppCatalog.CARICAMP);
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
