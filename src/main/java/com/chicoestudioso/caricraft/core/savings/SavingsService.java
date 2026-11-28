package com.chicoestudioso.caricraft.core.savings;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
import com.chicoestudioso.caricraft.core.shop.ShopService;

/**
 * Hucha del dúo (app de pago): se meten y sacan Carins del monedero cuando se quiera, y cada día crece un
 * {@code dailyRate} de lo que hubo <em>todo el día</em> (el saldo más bajo de ese día), con un tope diario. Así no
 * sirve meter justo antes de medianoche y sacar después, y no rompe la economía.
 */
public final class SavingsService {
    private final CaricraftConfig config;
    private final EconomyService economy;
    private final ShopService shop;

    public SavingsService(CaricraftConfig config, EconomyService economy, ShopService shop) {
        this.config = config;
        this.economy = economy;
        this.shop = shop;
    }

    public boolean enabled() {
        return config.savings.enabled;
    }

    public boolean usable(Party party) {
        return enabled() && shop.hasApp(party, AppCatalog.SAVINGS);
    }

    /** Intereses de un día sobre ese saldo. */
    public long interestFor(long balance) {
        return Math.max(0, Math.min(config.savings.dailyCap, (long) Math.floor(balance * config.savings.dailyRate)));
    }

    /** Lo que daría mañana si no se saca nada. */
    public long preview(Party party, long day) {
        SavingsState s = party.savings();
        return interestFor(s.day == day ? s.minToday : s.balance);
    }

    /**
     * Pone la hucha al día: por cada día completo desde la última vez, suma los intereses (dentro de la hucha) y
     * empieza la cuenta del día nuevo.
     *
     * @return Carins ganados ahora
     */
    public long accrue(Party party, long day) {
        SavingsState s = party.savings();
        if (s.day == Long.MIN_VALUE || s.day > day) {
            s.day = day;
            s.minToday = s.balance;
            return 0;
        }
        long gained = 0;
        long days = Math.min(30, day - s.day); // sin conexión mucho tiempo: como mucho un mes
        for (long i = 0; i < days && usable(party); i++) {
            long base = i == 0 ? s.minToday : s.balance;
            long interest = Math.min(interestFor(base), Math.max(0, config.savings.maxBalance - s.balance));
            s.balance += interest;
            gained += interest;
        }
        if (day != s.day) {
            s.day = day;
            s.minToday = s.balance;
            if (gained > 0) {
                s.totalInterest += gained;
                s.lastInterest = gained;
            }
        }
        return gained;
    }

    /** {@code amount} = Carins, o -1 para «todo». */
    public ActionResult deposit(Party party, long amount, long day) {
        if (!enabled()) {
            return ActionResult.fail("savings_disabled");
        }
        if (!usable(party)) {
            return ActionResult.fail("app_locked", AppCatalog.SAVINGS);
        }
        accrue(party, day);
        SavingsState s = party.savings();
        long room = Math.max(0, config.savings.maxBalance - s.balance);
        long wanted = amount < 0 ? party.wallet().balance() : amount;
        wanted = Math.min(wanted, room);
        if (wanted <= 0) {
            return ActionResult.fail(room <= 0 ? "savings_full" : "invalid_amount");
        }
        EconomyService.Result result = economy.debit(party.wallet(), wanted, "savings_deposit", "");
        if (result == EconomyService.Result.INSUFFICIENT_FUNDS) {
            return ActionResult.fail("insufficient_funds", wanted - party.wallet().balance());
        }
        if (result != EconomyService.Result.OK) {
            return ActionResult.fail("invalid_amount");
        }
        s.balance += wanted;
        return ActionResult.ok("savings_deposited", wanted, s.balance);
    }

    /** {@code amount} = Carins, o -1 para «todo». */
    public ActionResult withdraw(Party party, long amount, long day) {
        if (!enabled()) {
            return ActionResult.fail("savings_disabled");
        }
        accrue(party, day);
        SavingsState s = party.savings();
        long wanted = amount < 0 ? s.balance : amount;
        if (wanted <= 0) {
            return ActionResult.fail(s.balance <= 0 ? "savings_empty" : "invalid_amount");
        }
        if (wanted > s.balance) {
            return ActionResult.fail("savings_not_enough", s.balance);
        }
        EconomyService.Result result = economy.credit(party.wallet(), wanted, "savings_withdraw", "", null);
        if (result != EconomyService.Result.OK) {
            return ActionResult.fail("wallet_full");
        }
        s.balance -= wanted;
        s.minToday = Math.min(s.minToday, s.balance);
        return ActionResult.ok("savings_withdrawn", wanted, s.balance);
    }
}
