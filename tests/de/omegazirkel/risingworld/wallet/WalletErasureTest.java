package de.omegazirkel.risingworld.wallet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.Test;

public class WalletErasureTest {
    @Test
    public void transfersAllBalancesAndRemovesPlayerReferences() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            WalletDatabase database = new WalletDatabase(connection);
            WalletService service = new WalletService(database);
            assertTrue(service.registerCurrency("OZC", "Coins", "coin", "wallet", true).success);
            assertTrue(service.registerCurrency("ALT", "Other", "coin", "wallet").success);
            assertTrue(service.createSystemAccount("world::test", "WORLD", "World", "wallet").success);
            assertTrue(service.deposit(7, 100, "Seed", "OZC", "wallet").success);
            assertTrue(service.deposit(7, 25, "Seed", "ALT", "wallet").success);
            assertTrue(service.transferIdempotent(7, 8, 30, "gift", "OZC", "wallet", "gift-1").success);
            assertTrue(service.transferPlayerToSystemIdempotent(7, "world::test", 10, "fee", "OZC",
                    "wallet", "fee-1").success);

            database.erasePlayer(7, "world::test");

            assertEquals(0, count(connection, "SELECT COUNT(*) FROM wallet_balances WHERE player_db_id = 7"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM wallet_transactions WHERE player_db_id = 7"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM wallet_transfers WHERE payer_db_id = 7 OR payee_db_id = 7"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM wallet_account_transfers WHERE payer_reference = '7' OR payee_reference = '7'"));
            assertEquals(30, database.getBalance(8, "OZC"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM wallet_transactions WHERE player_db_id = 8 "
                    + "AND reason = 'gift'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM wallet_system_transactions WHERE "
                    + "reason = 'fee'"));
            assertEquals(70, database.getSystemBalance("world::test", "OZC"));
            assertEquals(25, database.getSystemBalance("world::test", "ALT"));

            database.erasePlayer(7, "world::test");
            assertEquals(70, database.getSystemBalance("world::test", "OZC"));
        }
    }

    @Test
    public void missingTreasuryCannotDiscardMoney() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            WalletDatabase database = new WalletDatabase(connection);
            WalletService service = new WalletService(database);
            assertTrue(service.registerCurrency("OZC", "Coins", "coin", "wallet", true).success);
            assertTrue(service.deposit(7, 100, "Seed", "OZC", "wallet").success);

            assertThrows(SQLException.class, () -> database.erasePlayer(7, "world::missing"));
            assertEquals(100, database.getBalance(7, "OZC"));
        }
    }

    @Test
    public void treasuryOverflowRollsBackEveryCurrency() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            WalletDatabase database = new WalletDatabase(connection);
            WalletService service = new WalletService(database);
            assertTrue(service.registerCurrency("OZC", "Coins", "coin", "wallet", true).success);
            assertTrue(service.createSystemAccount("world::test", "WORLD", "World", "wallet").success);
            assertTrue(service.deposit(7, 1, "Seed", "OZC", "wallet").success);
            assertTrue(service.creditSystemAccountIdempotent("world::test", Long.MAX_VALUE,
                    "Seed", "OZC", "wallet", "seed-max").success);

            assertThrows(ArithmeticException.class, () -> database.erasePlayer(7, "world::test"));
            assertEquals(1, database.getBalance(7, "OZC"));
            assertEquals(Long.MAX_VALUE, database.getSystemBalance("world::test", "OZC"));
        }
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }
}
