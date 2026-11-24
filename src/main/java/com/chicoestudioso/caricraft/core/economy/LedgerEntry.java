package com.chicoestudioso.caricraft.core.economy;

/** Asiento del ledger de un grupo. */
public record LedgerEntry(long time, long delta, long balanceAfter, String reason, String detail) {
}
