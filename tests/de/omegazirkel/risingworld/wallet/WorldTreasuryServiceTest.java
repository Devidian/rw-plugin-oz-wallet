package de.omegazirkel.risingworld.wallet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.Test;

public class WorldTreasuryServiceTest {
    @Test
    public void catchesUpEachMissingYearOnceWithAnAuditableReason() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            WalletService wallet = service(connection);
            assertTrue(wallet.createSystemAccount("world::test", "WORLD", "World", "OZ - Wallet").success);
            WorldTreasuryService treasury = new WorldTreasuryService(wallet, "OZ - Wallet");

            assertTrue(treasury.reconcile("world::test", 3, 2.5f, 30, "OZC", 25_000L).success);
            assertTrue(treasury.reconcile("world::test", 3, 2.5f, 30, "OZC", 25_000L).success);

            assertEquals(75_000L, wallet.systemAccountBalances("world::test").balances.get(0).getBalance());
            SystemAccountTransactionsResult transactions = wallet.systemAccountTransactions("world::test", 10);
            assertEquals(3, transactions.transactions.size());
            assertEquals("Guthaben für Jahr 3", transactions.transactions.get(0).getReason());
            assertEquals("Guthaben für Jahr 1", transactions.transactions.get(2).getReason());
        }
    }

    @Test
    public void scalesAnnualCapitalWithSpeedAndDaysPerMonth() {
        assertEquals(25_000L, WorldTreasuryService.annualCapital(2.5f, 30, 25_000L));
        assertEquals(50_000L, WorldTreasuryService.annualCapital(5f, 30, 25_000L));
        assertEquals(12_500L, WorldTreasuryService.annualCapital(2.5f, 15, 25_000L));
        assertEquals(25_000L, WorldTreasuryService.annualCapital(0f, 0, 25_000L));
        assertEquals(0L, WorldTreasuryService.annualCapital(2.5f, 30, 0L));
    }

    private WalletService service(Connection connection) throws Exception {
        WalletService service = new WalletService(new WalletDatabase(connection));
        assertTrue(service.registerCurrency("OZC", "OZC", "coin", "OZ - Wallet", true).success);
        return service;
    }
}
