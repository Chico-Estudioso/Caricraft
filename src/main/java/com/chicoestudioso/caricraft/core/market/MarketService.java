package com.chicoestudioso.caricraft.core.market;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.CaricraftState;
import com.chicoestudioso.caricraft.core.Clock;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.party.Party;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Mercado: cada día (UTC) salen {@code offersPerDay} ofertas sorteadas por peso, iguales para todos.
 * Cada grupo tiene un stock diario por oferta. El servidor entrega el ítem.
 */
public final class MarketService {
    public static final String STAT_PURCHASES = "market_purchases";
    private static final long DAY_MS = 86_400_000L;

    /** Entrega los ítems al jugador. Lo implementa el servidor. */
    @FunctionalInterface
    public interface ItemGiver {
        void give(String item, int count);
    }

    private final CaricraftConfig config;
    private final EconomyService economy;
    private final Clock clock;
    private MarketCatalog catalog;

    public MarketService(CaricraftConfig config, EconomyService economy, Clock clock, MarketCatalog catalog) {
        this.config = config;
        this.economy = economy;
        this.clock = clock;
        this.catalog = catalog;
    }

    public MarketCatalog catalog() {
        return catalog;
    }

    public boolean enabled() {
        return config.market.enabled && !catalog.pool().isEmpty();
    }

    public long day() {
        return com.chicoestudioso.caricraft.core.GameCalendar.of(config.timeZone).day(clock.now());
    }

    /** Momento en que cambian las ofertas (00:00 de la zona configurada). */
    public long nextRefresh() {
        return com.chicoestudioso.caricraft.core.GameCalendar.of(config.timeZone).nextDayAt(clock.now());
    }

    /** Ofertas del día: sorteo ponderado sin repetición, determinista por día. */
    public List<MarketOffer> offers(long day) {
        List<MarketOffer> remaining = new ArrayList<>(catalog.pool());
        List<MarketOffer> chosen = new ArrayList<>();
        Random random = new Random(day * 0x9E3779B97F4A7C15L ^ 0xC0FFEEL);
        int count = Math.min(config.market.offersPerDay, remaining.size());
        for (int i = 0; i < count; i++) {
            int total = remaining.stream().mapToInt(MarketOffer::weight).sum();
            int roll = random.nextInt(total);
            for (int j = 0; j < remaining.size(); j++) {
                roll -= remaining.get(j).weight();
                if (roll < 0) {
                    chosen.add(remaining.remove(j));
                    break;
                }
            }
        }
        return chosen;
    }

    public int bought(Party party, long day, String offerId) {
        return party.marketPurchases().getOrDefault(day + ":" + offerId, 0);
    }

    public int stockLeft(Party party, long day, MarketOffer offer) {
        return Math.max(0, offer.dailyStock() - bought(party, day, offer.id()));
    }

    public ActionResult buy(CaricraftState state, UUID player, String offerId, ItemGiver giver) {
        Party party = state.partyOf(player);
        if (party == null) {
            return ActionResult.fail("no_profile");
        }
        if (!enabled()) {
            return ActionResult.fail("market_closed");
        }
        long day = day();
        // Limpieza de compras de días anteriores.
        party.marketPurchases().keySet().removeIf(key -> !key.startsWith(day + ":"));
        Optional<MarketOffer> found = offers(day).stream().filter(o -> o.id().equals(offerId)).findFirst();
        if (found.isEmpty()) {
            return ActionResult.fail("offer_expired");
        }
        MarketOffer offer = found.get();
        if (stockLeft(party, day, offer) <= 0) {
            return ActionResult.fail("offer_sold_out");
        }
        EconomyService.Result result = economy.debit(party.wallet(), offer.price(), "market", offer.item());
        if (result == EconomyService.Result.INSUFFICIENT_FUNDS) {
            return ActionResult.fail("insufficient_funds", offer.price() - party.wallet().balance());
        }
        if (result != EconomyService.Result.OK) {
            return ActionResult.fail("invalid_amount");
        }
        try {
            giver.give(offer.item(), offer.count());
        } catch (RuntimeException e) {
            economy.credit(party.wallet(), offer.price(), "market_refund", offer.item(), null);
            return ActionResult.fail("internal_error");
        }
        party.marketPurchases().merge(day + ":" + offer.id(), 1, Integer::sum);
        party.incrementStat(STAT_PURCHASES, 1);
        return ActionResult.ok("market_bought", offer.count(), offer.item());
    }
}
