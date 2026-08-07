package de.omegazirkel.risingworld.wallet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.Test;

public class SystemAccountTest {
    @Test
    public void createsOwnedAccountsIdempotentlyAndRejectsHijacking() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            WalletService service = service(connection);

            SystemAccountResult created = service.createSystemAccount("city::area-42", "CITY", "Test City",
                    "OZ - Land Claim");
            SystemAccountResult retry = service.createSystemAccount("city::area-42", "CITY", "Test City",
                    "OZ - Land Claim");
            SystemAccountResult conflict = service.createSystemAccount("city::area-42", "CITY", "Hijack",
                    "another-plugin");
            SystemAccountResult metadataConflict = service.createSystemAccount("city::area-42", "CITY", "Other",
                    "OZ - Land Claim");

            assertTrue(created.success);
            assertTrue(retry.success);
            assertEquals(created.account.getCreatedAt(), retry.account.getCreatedAt());
            assertFalse(conflict.success);
            assertEquals(WalletErrorCode.ACCOUNT_OWNERSHIP_CONFLICT, conflict.errorCode);
            assertFalse(metadataConflict.success);
        }
    }

    @Test
    public void ownerCanUpdateDisplayNameWithoutChangingIdentity() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            WalletService service = service(connection);
            assertTrue(service.createSystemAccount("city::area-9", "CITY", "Old", "land").success);

            SystemAccountResult forbidden = service.updateSystemAccountDisplayName("city::area-9", "No", "other");
            SystemAccountResult renamed = service.updateSystemAccountDisplayName("city::area-9", "New", "land");

            assertFalse(forbidden.success);
            assertTrue(renamed.success);
            assertEquals("New", renamed.account.getDisplayName());
            assertEquals("city::area-9", renamed.account.getAccountId());
        }
    }

    @Test
    public void playerToSystemTransferIsAtomicAndIdempotent() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            WalletService service = service(connection);
            assertTrue(service.createSystemAccount("world::test", "WORLD", "World", "OZ - Wallet").success);
            assertTrue(service.deposit(7, 100, "Seed", "OZC", "test").success);

            AccountTransferResult first = service.transferPlayerToSystemIdempotent(7, "world::test", 35,
                    "Shop purchase", "OZC", "OZ - Shop", "shop:one");
            AccountTransferResult retry = service.transferPlayerToSystemIdempotent(7, "world::test", 35,
                    "Shop purchase", "OZC", "OZ - Shop", "shop:one");

            assertTrue(first.success);
            assertTrue(retry.success);
            assertEquals(first.transfer.getDebitTransactionId(), retry.transfer.getDebitTransactionId());
            assertEquals(65, service.balance(7, "OZC").balance.getBalance());
            assertEquals(35, service.systemAccountBalances("world::test").balances.get(0).getBalance());
            assertEquals(1, service.systemAccountTransactions("world::test", 10).transactions.size());
        }
    }

    @Test
    public void onlyOwnerCanDebitAndNonEmptyAccountCannotBeArchived() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            WalletService service = service(connection);
            assertTrue(service.createSystemAccount("city::area-2", "CITY", "City", "OZ - Land Claim").success);
            assertTrue(service.deposit(7, 50, "Seed", "OZC", "test").success);
            assertTrue(service.transferPlayerToSystemIdempotent(7, "city::area-2", 20, "Purchase", "OZC",
                    "OZ - Land Claim", "land:purchase").success);

            AccountTransferResult forbidden = service.transferSystemToPlayerIdempotent("city::area-2", 8, 5,
                    "Theft", "OZC", "another-plugin", "theft");
            SystemAccountResult nonEmpty = service.archiveSystemAccount("city::area-2", "OZ - Land Claim");
            AccountTransferResult payout = service.transferSystemToPlayerIdempotent("city::area-2", 8, 20,
                    "Payout", "OZC", "OZ - Land Claim", "city:payout");
            SystemAccountResult archived = service.archiveSystemAccount("city::area-2", "OZ - Land Claim");

            assertFalse(forbidden.success);
            assertEquals(WalletErrorCode.ACCOUNT_OWNERSHIP_CONFLICT, forbidden.errorCode);
            assertFalse(nonEmpty.success);
            assertEquals(WalletErrorCode.ACCOUNT_NOT_EMPTY, nonEmpty.errorCode);
            assertTrue(payout.success);
            assertTrue(archived.success);
            assertFalse(archived.account.isActive());
        }
    }

    @Test
    public void transfersBetweenSystemAccountsAndRejectsChangedRetry() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            WalletService service = service(connection);
            assertTrue(service.createSystemAccount("city::area-3", "CITY", "City", "land").success);
            assertTrue(service.createSystemAccount("world::test", "WORLD", "World", "wallet").success);
            assertTrue(service.deposit(7, 80, "Seed", "OZC", "test").success);
            assertTrue(service.transferPlayerToSystemIdempotent(7, "city::area-3", 80, "Fund", "OZC", "land",
                    "fund").success);

            assertTrue(service.transferSystemToSystemIdempotent("city::area-3", "world::test", 30, "Expand",
                    "OZC", "land", "expand").success);
            AccountTransferResult conflict = service.transferSystemToSystemIdempotent("city::area-3",
                    "world::test", 31, "Expand", "OZC", "land", "expand");

            assertFalse(conflict.success);
            assertEquals(WalletErrorCode.IDEMPOTENCY_CONFLICT, conflict.errorCode);
            assertEquals(50, service.systemAccountBalances("city::area-3").balances.get(0).getBalance());
            assertEquals(30, service.systemAccountBalances("world::test").balances.get(0).getBalance());
        }
    }

    @Test
    public void accountListIsFilteredAndPaged() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            WalletService service = service(connection);
            assertTrue(service.createSystemAccount("world::test", "WORLD", "World Treasury", "wallet").success);
            assertTrue(service.createSystemAccount("city::area-1", "CITY", "Alpha", "land").success);
            assertTrue(service.createSystemAccount("city::area-2", "CITY", "Beta", "land").success);

            SystemAccountsResult filtered = service.listSystemAccounts("city", 0, 1);
            assertTrue(filtered.success);
            assertEquals(2, filtered.total);
            assertEquals(1, filtered.accounts.size());
        }
    }

    @Test
    public void accountListIsSortedByBalanceDescending() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            WalletService service = service(connection);
            assertTrue(service.createSystemAccount("archived::zero", "TEST", "Archived", "test").success);
            assertTrue(service.createSystemAccount("city::low", "CITY", "Low", "test").success);
            assertTrue(service.createSystemAccount("world::high", "WORLD", "High", "test").success);
            assertTrue(service.deposit(7, 110, "Seed", "OZC", "test").success);
            assertTrue(service.transferPlayerToSystemIdempotent(7, "city::low", 10, "Fund", "OZC", "test",
                    "fund-low").success);
            assertTrue(service.transferPlayerToSystemIdempotent(7, "world::high", 100, "Fund", "OZC", "test",
                    "fund-high").success);

            SystemAccountsResult result = service.listSystemAccounts("", 0, 10);
            assertTrue(result.success);
            assertEquals("world::high", result.accounts.get(0).getAccountId());
            assertEquals("city::low", result.accounts.get(1).getAccountId());
            assertEquals("archived::zero", result.accounts.get(2).getAccountId());
        }
    }

    @Test
    public void sourcePluginCanReverseItsOriginalTransferExactlyOnce() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            WalletService service = service(connection);
            assertTrue(service.createSystemAccount("world::test", "WORLD", "World", "wallet").success);
            assertTrue(service.deposit(7, 50, "Seed", "OZC", "test").success);
            assertTrue(service.transferPlayerToSystemIdempotent(7, "world::test", 20, "Purchase", "OZC", "shop",
                    "purchase").success);

            AccountTransferResult forbidden = service.reverseAccountTransferIdempotent("purchase", "refund-bad",
                    "Refund", "market");
            AccountTransferResult refund = service.reverseAccountTransferIdempotent("purchase", "refund", "Refund",
                    "shop");
            AccountTransferResult retry = service.reverseAccountTransferIdempotent("purchase", "refund", "Refund",
                    "shop");

            assertFalse(forbidden.success);
            assertEquals(WalletErrorCode.ACCOUNT_OWNERSHIP_CONFLICT, forbidden.errorCode);
            assertTrue(refund.success);
            assertTrue(retry.success);
            assertEquals(50, service.balance(7, "OZC").balance.getBalance());
            assertEquals(0, service.systemAccountBalances("world::test").balances.get(0).getBalance());
        }
    }

    @Test
    public void ownerCanIssueFundsIdempotentlyButAnotherPluginCannot() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            WalletService service = service(connection);
            assertTrue(service.createSystemAccount("trader::npc-9", "TRADER", "Trader", "OZ - Shop").success);
            assertFalse(service.creditSystemAccountIdempotent("trader::npc-9", 1000, "Seed", "OZC", "other", "seed")
                    .success);
            assertTrue(service.creditSystemAccountIdempotent("trader::npc-9", 1000, "Seed", "OZC", "OZ - Shop", "seed")
                    .success);
            assertTrue(service.creditSystemAccountIdempotent("trader::npc-9", 1000, "Seed", "OZC", "OZ - Shop", "seed")
                    .success);
            assertEquals(1000, service.systemAccountBalances("trader::npc-9").balances.get(0).getBalance());
        }
    }

    private WalletService service(Connection connection) throws Exception {
        WalletService service = new WalletService(new WalletDatabase(connection));
        assertTrue(service.registerCurrency("OZC", "OZC", "coin", "OZ - Wallet", true).success);
        return service;
    }
}
