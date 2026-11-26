package com.chicoestudioso.caricraft.core.cobbleverse;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.Clock;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Gift;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Offer;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Reglas de la parte Cobbleverse del Mercado: ofertas del día, límites por jugador, regalo misterioso diario
 * y ventas de Pokémon. El dinero es CobbleDollars (de otro mod), así que aquí solo se decide; el servidor
 * cobra, paga y entrega.
 */
public final class CobbleverseService {
    public static final long DAY_MS = 86_400_000L;

    private final CaricraftConfig config;
    private final CobbleverseCatalog catalog;
    private final Clock clock;

    public CobbleverseService(CaricraftConfig config, CobbleverseCatalog catalog, Clock clock) {
        this.config = config;
        this.catalog = catalog;
        this.clock = clock;
    }

    public CobbleverseCatalog catalog() {
        return catalog;
    }

    /** Activado en la configuración (el servidor comprueba además que Cobblemon esté instalado). */
    public boolean enabled() {
        return config.cobbleverse.enabled;
    }

    private com.chicoestudioso.caricraft.core.GameCalendar calendar() {
        return com.chicoestudioso.caricraft.core.GameCalendar.of(config.timeZone);
    }

    /** Día en la zona horaria configurada: todo lo diario cambia a las 00:00 locales. */
    public long day() {
        return calendar().day(clock.now());
    }

    public long nextDayAt() {
        return calendar().nextDayAt(clock.now());
    }

    /** Ofertas de hoy: las mismas para todos, distintas cada día. */
    public List<Offer> offersOfDay(long day) {
        List<Offer> shuffled = new ArrayList<>(catalog.offers());
        Collections.shuffle(shuffled, new Random(day * 7_919L + 104_729L));
        return List.copyOf(shuffled.subList(0, catalog.dailyOffers()));
    }

    static void rollDay(PlayerProfile profile, long day) {
        if (profile.cobbleverseDay() != day) {
            profile.setCobbleverseDay(day);
            profile.setSalesToday(0);
            profile.setWondersToday(0);
            profile.boughtToday().clear();
        }
    }

    /** Contador diario por clave (compras, cajas, tiradas…); vale 0 si es de otro día. */
    static int counter(PlayerProfile profile, String key, long day) {
        return profile.cobbleverseDay() == day ? profile.boughtToday().getOrDefault(key, 0) : 0;
    }

    static void count(PlayerProfile profile, String key, long day) {
        rollDay(profile, day);
        profile.boughtToday().merge(key, 1, Integer::sum);
    }

    public CaricraftConfig config() {
        return config;
    }

    public int boughtToday(PlayerProfile profile, String offerId, long day) {
        return profile.cobbleverseDay() == day ? profile.boughtToday().getOrDefault(offerId, 0) : 0;
    }

    public int salesToday(PlayerProfile profile, long day) {
        return profile.cobbleverseDay() == day ? profile.salesToday() : 0;
    }

    public int maxSalesPerDay() {
        return config.cobbleverse.maxSalesPerDay;
    }

    /** Comprueba una compra con el saldo de CobbleDollars del jugador. */
    public ActionResult canBuy(PlayerProfile profile, String offerId, long day, long balance) {
        Offer offer = offersOfDay(day).stream().filter(o -> o.id().equals(offerId)).findFirst().orElse(null);
        if (offer == null) {
            return ActionResult.fail("cv_offer_gone");
        }
        if (boughtToday(profile, offerId, day) >= offer.dailyLimitOrDefault()) {
            return ActionResult.fail("cv_sold_out");
        }
        if (balance < offer.price()) {
            return ActionResult.fail("cv_insufficient", offer.price() - balance);
        }
        return ActionResult.ok("ok");
    }

    public void recordPurchase(PlayerProfile profile, String offerId, long day) {
        rollDay(profile, day);
        profile.boughtToday().merge(offerId, 1, Integer::sum);
    }

    public ActionResult canSell(PlayerProfile profile, long day) {
        if (salesToday(profile, day) >= maxSalesPerDay()) {
            return ActionResult.fail("cv_sell_limit", maxSalesPerDay());
        }
        return ActionResult.ok("ok");
    }

    public void recordSale(PlayerProfile profile, long day) {
        rollDay(profile, day);
        profile.setSalesToday(profile.salesToday() + 1);
    }

    public long sellPrice(PokemonPricing.PokemonInfo info) {
        return PokemonPricing.sellPrice(info, config.cobbleverse.sellPriceMultiplier);
    }

    /** Clave del motivo por el que no se puede vender, o {@code null}. */
    public String sellBlocked(PokemonPricing.PokemonInfo info, boolean lastInParty, boolean inBattle) {
        if (inBattle) {
            return "in_battle";
        }
        if (lastInParty) {
            return "last";
        }
        if (info.legendary() && !config.cobbleverse.sellLegendaries) {
            return "legendary";
        }
        return null;
    }

    // ------------------------------------------------------------------ intercambio prodigioso

    public int wondersLeft(PlayerProfile profile, long day) {
        if (!config.cobbleverse.wonderTrade) {
            return 0;
        }
        int done = profile.cobbleverseDay() == day ? profile.wondersToday() : 0;
        return Math.max(0, config.cobbleverse.wonderTradesPerDay - done);
    }

    public ActionResult canWonderTrade(PlayerProfile profile, long day) {
        if (!config.cobbleverse.wonderTrade) {
            return ActionResult.fail("cv_disabled");
        }
        if (wondersLeft(profile, day) <= 0) {
            return ActionResult.fail("cv_wonder_done");
        }
        return ActionResult.ok("ok");
    }

    public void recordWonderTrade(PlayerProfile profile, long day) {
        rollDay(profile, day);
        profile.setWondersToday(profile.wondersToday() + 1);
    }

    /**
     * Elige al azar un Pokémon de la bolsa que no sea del propio jugador.
     *
     * @param owners dueño de cada Pokémon de la bolsa, en orden
     * @return índice elegido, o -1 si no hay ninguno de otro jugador (entonces se genera uno)
     */
    public static int pickFromPool(List<java.util.UUID> owners, java.util.UUID player, Random random) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < owners.size(); i++) {
            if (!owners.get(i).equals(player)) {
                candidates.add(i);
            }
        }
        return candidates.isEmpty() ? -1 : candidates.get(random.nextInt(candidates.size()));
    }

    /** Nivel del Pokémon generado: el del ofrecido ±3. */
    public static int wonderLevel(int offeredLevel, Random random) {
        return Math.max(1, Math.min(100, offeredLevel + random.nextInt(7) - 3));
    }

    public double wonderShinyChance() {
        return config.cobbleverse.wonderShinyChance;
    }

    // ------------------------------------------------------------------ regalo misterioso

    public boolean giftAvailable(PlayerProfile profile, long day) {
        return config.cobbleverse.mysteryGift && !catalog.gifts().isEmpty() && giftsLeft(profile, day) > 0;
    }

    /** Regalos que quedan hoy ({@code giftsPerDay} al día). */
    public int giftsLeft(PlayerProfile profile, long day) {
        int opened = profile.giftDay() == day ? Math.max(1, profile.giftsToday()) : 0;
        return Math.max(0, config.cobbleverse.giftsPerDay - opened);
    }

    public int giftsPerDay() {
        return config.cobbleverse.giftsPerDay;
    }

    /** Premio al azar según los pesos del catálogo. */
    public Gift pickGift(Random random) {
        int total = catalog.gifts().stream().mapToInt(Gift::weight).sum();
        int roll = random.nextInt(total);
        for (Gift gift : catalog.gifts()) {
            roll -= gift.weight();
            if (roll < 0) {
                return gift;
            }
        }
        return catalog.gifts().getLast();
    }

    public void recordGift(PlayerProfile profile, long day) {
        if (profile.giftDay() != day) {
            profile.setGiftDay(day);
            profile.setGiftsToday(0);
        }
        profile.setGiftsToday(profile.giftsToday() + 1);
    }
}
