package com.chicoestudioso.caricraft.core.economy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.SequencedSet;

/** Saldo de Carins de un grupo, su ledger reciente y las claves de idempotencia procesadas. */
public final class Wallet {
    /** Número de claves de idempotencia recordadas (las más antiguas se olvidan). */
    public static final int MAX_PROCESSED_KEYS = 4096;

    private long balance;
    private final Deque<LedgerEntry> ledger = new ArrayDeque<>();
    private final SequencedSet<String> processedKeys = new LinkedHashSet<>();

    public long balance() {
        return balance;
    }

    void setBalance(long balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("El saldo no puede ser negativo");
        }
        this.balance = balance;
    }

    /** Asientos del más reciente al más antiguo. */
    public Deque<LedgerEntry> ledger() {
        return ledger;
    }

    public SequencedSet<String> processedKeys() {
        return processedKeys;
    }

    void record(LedgerEntry entry, int maxEntries) {
        ledger.addFirst(entry);
        while (ledger.size() > maxEntries) {
            ledger.removeLast();
        }
    }

    boolean markProcessed(String key) {
        if (!processedKeys.add(key)) {
            return false;
        }
        while (processedKeys.size() > MAX_PROCESSED_KEYS) {
            processedKeys.removeFirst();
        }
        return true;
    }

    /** Solo para cargar desde disco. */
    public void restore(long balance, Iterable<LedgerEntry> newestFirst, Iterable<String> keysOldestFirst) {
        setBalance(balance);
        ledger.clear();
        newestFirst.forEach(ledger::addLast);
        processedKeys.clear();
        keysOldestFirst.forEach(processedKeys::add);
    }
}
