package de.omegazirkel.risingworld.wallet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.Test;

public class WalletTransferTest {
    @Test
    public void atomicTransferRetriesWithoutBookingTwice() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            WalletDatabase database = new WalletDatabase(connection);
            WalletService service = new WalletService(database);
            assertTrue(service.registerCurrency("OZC", "OZC", "coin", "OZ - Wallet", true).success);
            assertTrue(service.deposit(11, 100L, "Seed", "OZC", "test").success);

            WalletTransferResult first = service.transferIdempotent(11, 22, 30L, "COD mail", "OZC",
                    "OZ - Mail", "mail:claim:one");
            WalletTransferResult retry = service.transferIdempotent(11, 22, 30L, "COD mail", "OZC",
                    "OZ - Mail", "mail:claim:one");

            assertTrue(first.success);
            assertTrue(retry.success);
            assertEquals(first.transfer.getDebitTransactionId(), retry.transfer.getDebitTransactionId());
            assertEquals(70L, database.getBalance(11, "OZC"));
            assertEquals(30L, database.getBalance(22, "OZC"));
        }
    }

    @Test
    public void idempotencyConflictAndInsufficientFundsDoNotChangeBalances() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            WalletDatabase database = new WalletDatabase(connection);
            WalletService service = new WalletService(database);
            assertTrue(service.registerCurrency("OZC", "OZC", "coin", "OZ - Wallet", true).success);
            assertTrue(service.deposit(11, 10L, "Seed", "OZC", "test").success);
            assertTrue(service.transferIdempotent(11, 22, 5L, "COD mail", "OZC", "OZ - Mail", "claim-a").success);

            WalletTransferResult conflict = service.transferIdempotent(11, 22, 6L, "COD mail", "OZC",
                    "OZ - Mail", "claim-a");
            WalletTransferResult insufficient = service.transferIdempotent(11, 22, 10L, "COD mail", "OZC",
                    "OZ - Mail", "claim-b");

            assertFalse(conflict.success);
            assertEquals(WalletErrorCode.IDEMPOTENCY_CONFLICT, conflict.errorCode);
            assertFalse(insufficient.success);
            assertEquals(WalletErrorCode.INSUFFICIENT_FUNDS, insufficient.errorCode);
            assertEquals(5L, database.getBalance(11, "OZC"));
            assertEquals(5L, database.getBalance(22, "OZC"));
        }
    }
}
