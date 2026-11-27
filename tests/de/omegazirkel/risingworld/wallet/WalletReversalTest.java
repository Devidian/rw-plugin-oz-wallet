package de.omegazirkel.risingworld.wallet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.DriverManager;
import java.util.List;

import org.junit.Test;

public class WalletReversalTest {
    @Test
    public void reversalRetainsLedgerOrderingAndCanOnlyBeAppliedOnce() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            WalletDatabase database = new WalletDatabase(connection);
            WalletService service = new WalletService(database);
            assertTrue(service.registerCurrency("OZC", "OZC", "coin", "OZ - Wallet", true).success);
            var credit = service.deposit(8, 100, "initial funds", "OZC", "test").transaction;
            var debit = service.withdraw(8, 24, "purchase", "OZC", "test").transaction;
            assertTrue(service.deposit(9, 70, "other player", "OZC", "test").success);

            var reversal = service.reverseTransaction(debit.getId());
            assertTrue(reversal.success);
            assertEquals(24L, reversal.transaction.getDelta());
            assertEquals("Reversal of transaction #" + debit.getId(), reversal.transaction.getReason());
            assertEquals(100L, database.getBalance(8, "OZC"));
            assertEquals(70L, database.getBalance(9, "OZC"));
            assertEquals(WalletErrorCode.IDEMPOTENCY_CONFLICT, service.reverseTransaction(debit.getId()).errorCode);
            assertFalse(service.reverseTransaction(Long.MAX_VALUE).success);

            List<WalletTransaction> history = database.listLatestGlobalTransactions(0);
            assertEquals(4, history.size());
            assertEquals(reversal.transaction.getId(), history.getFirst().getId());
            assertEquals(credit.getId(), history.getLast().getId());
            assertEquals(-24L, database.findTransaction(debit.getId()).orElseThrow().getDelta());
        }
    }
}
