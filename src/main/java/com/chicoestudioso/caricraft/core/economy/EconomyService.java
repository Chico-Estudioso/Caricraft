package com.chicoestudioso.caricraft.core.economy;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.Clock;

/**
 * Operaciones sobre Carins. Todas las mutaciones deben ejecutarse en el hilo del servidor; los métodos
 * además están sincronizados para que el servicio sea seguro aunque se llame desde otro hilo.
 */
public final class EconomyService {
    /** Tope de saldo para evitar desbordamientos. Un ingreso que lo supere se rechaza entero. */
    public static final long MAX_BALANCE = 1_000_000_000L;

    public enum Result { OK, DUPLICATE, INSUFFICIENT_FUNDS, INVALID_AMOUNT }

    private final CaricraftConfig config;
    private final Clock clock;

    public EconomyService(CaricraftConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    /**
     * Ingresa Carins. Si {@code idempotencyKey} no es nulo y ya se procesó, no hace nada y devuelve
     * {@link Result#DUPLICATE}.
     */
    public synchronized Result credit(Wallet wallet, long amount, String reason, String detail, String idempotencyKey) {
        if (amount <= 0 || amount > MAX_BALANCE) {
            return Result.INVALID_AMOUNT;
        }
        if (idempotencyKey != null && wallet.processedKeys().contains(idempotencyKey)) {
            return Result.DUPLICATE;
        }
        if (wallet.balance() > MAX_BALANCE - amount) {
            return Result.INVALID_AMOUNT;
        }
        long newBalance = wallet.balance() + amount;
        if (idempotencyKey != null) {
            wallet.markProcessed(idempotencyKey);
        }
        wallet.setBalance(newBalance);
        wallet.record(new LedgerEntry(clock.now(), amount, newBalance, reason, detail), config.ledgerSize);
        return Result.OK;
    }

    /** Cobra Carins. Nunca deja el saldo en negativo. */
    public synchronized Result debit(Wallet wallet, long amount, String reason, String detail) {
        if (amount <= 0 || amount > MAX_BALANCE) {
            return Result.INVALID_AMOUNT;
        }
        if (wallet.balance() < amount) {
            return Result.INSUFFICIENT_FUNDS;
        }
        long newBalance = wallet.balance() - amount;
        wallet.setBalance(newBalance);
        wallet.record(new LedgerEntry(clock.now(), -amount, newBalance, reason, detail), config.ledgerSize);
        return Result.OK;
    }

    /** Fija el saldo (administración). */
    public synchronized Result set(Wallet wallet, long amount, String reason, String detail) {
        if (amount < 0 || amount > MAX_BALANCE) {
            return Result.INVALID_AMOUNT;
        }
        long delta = amount - wallet.balance();
        wallet.setBalance(amount);
        wallet.record(new LedgerEntry(clock.now(), delta, amount, reason, detail), config.ledgerSize);
        return Result.OK;
    }

    /** Mueve Carins entre carteras de forma atómica. */
    public synchronized Result transfer(Wallet from, Wallet to, long amount, String reason, String detail) {
        if (amount == 0) {
            return Result.OK;
        }
        Result debit = debit(from, amount, reason, detail);
        if (debit != Result.OK) {
            return debit;
        }
        Result credit = credit(to, amount, reason, detail, null);
        if (credit != Result.OK) {
            // Revertir: devolver lo cobrado sin dejar rastro inconsistente.
            from.setBalance(from.balance() + amount);
            from.record(new LedgerEntry(clock.now(), amount, from.balance(), reason + "_revert", detail), config.ledgerSize);
        }
        return credit;
    }
}
