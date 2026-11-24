package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.economy.EconomyService.Result;
import com.chicoestudioso.caricraft.core.economy.Wallet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyServiceTest {
    private final CaricraftConfig config = new CaricraftConfig().validated();
    private final EconomyService economy = new EconomyService(config, () -> 1000L);

    @Test
    void creditAndDebitKeepLedger() {
        Wallet wallet = new Wallet();
        assertEquals(Result.OK, economy.credit(wallet, 100, "test", "a", null));
        assertEquals(Result.OK, economy.debit(wallet, 30, "purchase", "b"));
        assertEquals(70, wallet.balance());
        assertEquals(2, wallet.ledger().size());
        assertEquals(-30, wallet.ledger().getFirst().delta());
        assertEquals(70, wallet.ledger().getFirst().balanceAfter());
    }

    @Test
    void debitNeverGoesNegative() {
        Wallet wallet = new Wallet();
        economy.credit(wallet, 10, "test", "", null);
        assertEquals(Result.INSUFFICIENT_FUNDS, economy.debit(wallet, 11, "purchase", ""));
        assertEquals(10, wallet.balance());
        assertEquals(1, wallet.ledger().size());
    }

    @Test
    void invalidAmountsAreRejected() {
        Wallet wallet = new Wallet();
        assertEquals(Result.INVALID_AMOUNT, economy.credit(wallet, 0, "test", "", null));
        assertEquals(Result.INVALID_AMOUNT, economy.credit(wallet, -5, "test", "", null));
        assertEquals(Result.INVALID_AMOUNT, economy.debit(wallet, -5, "test", ""));
        assertEquals(Result.INVALID_AMOUNT, economy.set(wallet, -1, "admin", ""));
        assertEquals(0, wallet.balance());
    }

    @Test
    void creditIsIdempotentByKey() {
        Wallet wallet = new Wallet();
        assertEquals(Result.OK, economy.credit(wallet, 50, "mission", "x", "mission:x"));
        assertEquals(Result.DUPLICATE, economy.credit(wallet, 50, "mission", "x", "mission:x"));
        assertEquals(50, wallet.balance());
        assertEquals(1, wallet.ledger().size());
    }

    @Test
    void creditBeyondCapIsRejectedWhole() {
        Wallet wallet = new Wallet();
        economy.set(wallet, EconomyService.MAX_BALANCE - 5, "admin", "");
        assertEquals(Result.INVALID_AMOUNT, economy.credit(wallet, 10, "test", "", null));
        assertEquals(EconomyService.MAX_BALANCE - 5, wallet.balance());
    }

    @Test
    void ledgerIsBounded() {
        Wallet wallet = new Wallet();
        for (int i = 0; i < config.ledgerSize + 25; i++) {
            economy.credit(wallet, 1, "test", "", null);
        }
        assertEquals(config.ledgerSize, wallet.ledger().size());
        assertEquals(config.ledgerSize + 25, wallet.balance());
    }

    @Test
    void transferIsAtomic() {
        Wallet a = new Wallet();
        Wallet b = new Wallet();
        economy.credit(a, 40, "test", "", null);
        assertEquals(Result.INSUFFICIENT_FUNDS, economy.transfer(a, b, 50, "move", ""));
        assertEquals(40, a.balance());
        assertEquals(0, b.balance());
        assertEquals(Result.OK, economy.transfer(a, b, 25, "move", ""));
        assertEquals(15, a.balance());
        assertEquals(25, b.balance());
    }

    @Test
    void concurrentDebitsNeverOverspend() throws Exception {
        Wallet wallet = new Wallet();
        economy.credit(wallet, 1000, "test", "", null);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Result>> results = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return economy.debit(wallet, 7, "purchase", "");
            }));
        }
        start.countDown();
        int ok = 0;
        for (Future<Result> result : results) {
            if (result.get() == Result.OK) {
                ok++;
            }
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(1000 / 7, ok);
        assertEquals(1000 - ok * 7L, wallet.balance());
    }

    @Test
    void concurrentIdempotentCreditsPayOnce() throws Exception {
        Wallet wallet = new Wallet();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Result>> results = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            results.add(pool.submit(() -> economy.credit(wallet, 75, "mission", "m", "mission:same")));
        }
        long ok = 0;
        for (Future<Result> result : results) {
            if (result.get() == Result.OK) {
                ok++;
            }
        }
        pool.shutdown();
        assertEquals(1, ok);
        assertEquals(75, wallet.balance());
    }
}
