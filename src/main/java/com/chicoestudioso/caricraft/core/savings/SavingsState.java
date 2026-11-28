package com.chicoestudioso.caricraft.core.savings;

/** La hucha del dúo: Carins apartados que crecen un poco cada día. */
public final class SavingsState {
    long balance;
    /** Día del que se lleva la cuenta (el mínimo de ese día decide los intereses). */
    long day = Long.MIN_VALUE;
    /** Saldo más bajo que ha tenido la hucha en ese día. */
    long minToday;
    long totalInterest;
    long lastInterest;

    public long balance() {
        return balance;
    }

    public long day() {
        return day;
    }

    public long minToday() {
        return minToday;
    }

    public long totalInterest() {
        return totalInterest;
    }

    public long lastInterest() {
        return lastInterest;
    }

    public void restore(long balance, long day, long minToday, long totalInterest, long lastInterest) {
        this.balance = Math.max(0, balance);
        this.day = day;
        this.minToday = Math.max(0, Math.min(minToday, this.balance));
        this.totalInterest = Math.max(0, totalInterest);
        this.lastInterest = Math.max(0, lastInterest);
    }

    /** Al formar un dúo se juntan las dos huchas. */
    public void mergeFrom(SavingsState other) {
        balance += other.balance;
        minToday += other.minToday;
        totalInterest += other.totalInterest;
    }

    /** Al separarse, quien se va se lleva la mitad (hacia abajo). */
    public SavingsState split() {
        SavingsState half = new SavingsState();
        half.balance = balance / 2;
        half.minToday = Math.min(minToday, half.balance);
        half.day = day;
        balance -= half.balance;
        minToday = Math.min(minToday, balance);
        return half;
    }
}
