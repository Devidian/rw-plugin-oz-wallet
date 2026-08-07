package de.omegazirkel.risingworld.wallet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.omegazirkel.risingworld.Wallet;

public class WalletDatabase {
    private static final int SCHEMA_VERSION = 4;

    private final Connection connection;

    public WalletDatabase(Connection connection) throws SQLException {
        this.connection = connection;
        initialize();
    }

    public void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON;");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS wallet_currencies (
                        identifier TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        icon TEXT NOT NULL,
                        source_plugin TEXT NOT NULL,
                        registered_at BIGINT NOT NULL,
                        is_default INTEGER NOT NULL DEFAULT 0
                    );
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS wallet_balances (
                        player_db_id INTEGER NOT NULL,
                        currency_identifier TEXT NOT NULL,
                        balance BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (player_db_id, currency_identifier),
                        FOREIGN KEY (currency_identifier) REFERENCES wallet_currencies(identifier)
                    );
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS wallet_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_db_id INTEGER NOT NULL,
                        currency_identifier TEXT NOT NULL,
                        delta BIGINT NOT NULL,
                        resulting_balance BIGINT NOT NULL,
                        source_plugin TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        created_at BIGINT NOT NULL,
                        FOREIGN KEY (currency_identifier) REFERENCES wallet_currencies(identifier)
                    );
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS wallet_transfers (
                        correlation_id TEXT PRIMARY KEY,
                        payer_db_id INTEGER NOT NULL,
                        payee_db_id INTEGER NOT NULL,
                        currency_identifier TEXT NOT NULL,
                        amount BIGINT NOT NULL,
                        debit_transaction_id INTEGER NOT NULL,
                        credit_transaction_id INTEGER NOT NULL,
                        source_plugin TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        created_at BIGINT NOT NULL,
                        FOREIGN KEY (currency_identifier) REFERENCES wallet_currencies(identifier),
                        FOREIGN KEY (debit_transaction_id) REFERENCES wallet_transactions(id),
                        FOREIGN KEY (credit_transaction_id) REFERENCES wallet_transactions(id)
                    );
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_wallet_transactions_player_created
                    ON wallet_transactions (player_db_id, created_at DESC, id DESC);
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS wallet_system_accounts (
                        account_id TEXT PRIMARY KEY,
                        owner_plugin TEXT NOT NULL,
                        account_type TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        status TEXT NOT NULL,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL
                    );
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS wallet_system_balances (
                        account_id TEXT NOT NULL,
                        currency_identifier TEXT NOT NULL,
                        balance BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (account_id, currency_identifier),
                        FOREIGN KEY (account_id) REFERENCES wallet_system_accounts(account_id),
                        FOREIGN KEY (currency_identifier) REFERENCES wallet_currencies(identifier)
                    );
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS wallet_system_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        account_id TEXT NOT NULL,
                        currency_identifier TEXT NOT NULL,
                        delta BIGINT NOT NULL,
                        resulting_balance BIGINT NOT NULL,
                        source_plugin TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        created_at BIGINT NOT NULL,
                        FOREIGN KEY (account_id) REFERENCES wallet_system_accounts(account_id),
                        FOREIGN KEY (currency_identifier) REFERENCES wallet_currencies(identifier)
                    );
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS wallet_account_transfers (
                        correlation_id TEXT PRIMARY KEY,
                        payer_kind TEXT NOT NULL,
                        payer_reference TEXT NOT NULL,
                        payee_kind TEXT NOT NULL,
                        payee_reference TEXT NOT NULL,
                        currency_identifier TEXT NOT NULL,
                        amount BIGINT NOT NULL,
                        debit_transaction_id BIGINT NOT NULL,
                        credit_transaction_id BIGINT NOT NULL,
                        source_plugin TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        created_at BIGINT NOT NULL,
                        FOREIGN KEY (currency_identifier) REFERENCES wallet_currencies(identifier)
                    );
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS wallet_system_issuances (
                        correlation_id TEXT PRIMARY KEY,
                        account_id TEXT NOT NULL,
                        currency_identifier TEXT NOT NULL,
                        amount BIGINT NOT NULL,
                        transaction_id BIGINT NOT NULL,
                        source_plugin TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        created_at BIGINT NOT NULL,
                        FOREIGN KEY (account_id) REFERENCES wallet_system_accounts(account_id),
                        FOREIGN KEY (currency_identifier) REFERENCES wallet_currencies(identifier),
                        FOREIGN KEY (transaction_id) REFERENCES wallet_system_transactions(id)
                    );
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_wallet_system_accounts_created
                    ON wallet_system_accounts (created_at DESC, account_id ASC);
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_wallet_system_transactions_account_created
                    ON wallet_system_transactions (account_id, created_at DESC, id DESC);
                    """);
            statement.execute("PRAGMA user_version = " + SCHEMA_VERSION + ";");
        }
    }

    public SystemAccount insertSystemAccount(String accountId, String ownerPlugin, String accountType,
            String displayName) throws SQLException {
        long now = now();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO wallet_system_accounts(
                    account_id, owner_plugin, account_type, display_name, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                """)) {
            statement.setString(1, accountId);
            statement.setString(2, ownerPlugin);
            statement.setString(3, accountType);
            statement.setString(4, displayName);
            statement.setLong(5, now);
            statement.setLong(6, now);
            statement.executeUpdate();
        }
        return findSystemAccount(accountId).orElseThrow();
    }

    public Optional<SystemAccount> findSystemAccount(String accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT account_id, owner_plugin, account_type, display_name, status, created_at, updated_at
                FROM wallet_system_accounts WHERE account_id = ?
                """)) {
            statement.setString(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readSystemAccount(result)) : Optional.empty();
            }
        }
    }

    public List<SystemAccount> listSystemAccounts(String search, int offset, int limit) throws SQLException {
        boolean filtered = search != null && !search.isBlank();
        String sql = """
                SELECT account.account_id, account.owner_plugin, account.account_type, account.display_name,
                       account.status, account.created_at, account.updated_at
                FROM wallet_system_accounts account
                LEFT JOIN wallet_system_balances balance ON balance.account_id = account.account_id
                """ + (filtered ? "WHERE lower(account.account_id) LIKE ? OR lower(account.display_name) LIKE ? " : "")
                + "GROUP BY account.account_id "
                + "ORDER BY COALESCE(SUM(balance.balance), 0) DESC, account.created_at DESC, account.account_id ASC "
                + "LIMIT ? OFFSET ?";
        List<SystemAccount> accounts = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (filtered) {
                String pattern = "%" + search.toLowerCase(java.util.Locale.ROOT) + "%";
                statement.setString(index++, pattern);
                statement.setString(index++, pattern);
            }
            statement.setInt(index++, limit);
            statement.setInt(index, offset);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) accounts.add(readSystemAccount(result));
            }
        }
        return accounts;
    }

    public int countSystemAccounts(String search) throws SQLException {
        boolean filtered = search != null && !search.isBlank();
        String sql = "SELECT COUNT(*) FROM wallet_system_accounts"
                + (filtered ? " WHERE lower(account_id) LIKE ? OR lower(display_name) LIKE ?" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (filtered) {
                String pattern = "%" + search.toLowerCase(java.util.Locale.ROOT) + "%";
                statement.setString(1, pattern);
                statement.setString(2, pattern);
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    public void archiveSystemAccount(String accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE wallet_system_accounts SET status = 'ARCHIVED', updated_at = ?
                WHERE account_id = ? AND status = 'ACTIVE'
                """)) {
            statement.setLong(1, now());
            statement.setString(2, accountId);
            statement.executeUpdate();
        }
    }

    public void updateSystemAccountDisplayName(String accountId, String displayName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE wallet_system_accounts SET display_name = ?, updated_at = ?
                WHERE account_id = ? AND status = 'ACTIVE'
                """)) {
            statement.setString(1, displayName);
            statement.setLong(2, now());
            statement.setString(3, accountId);
            statement.executeUpdate();
        }
    }

    public long getSystemBalance(String accountId, String currencyIdentifier) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT balance FROM wallet_system_balances
                WHERE account_id = ? AND currency_identifier = ?
                """)) {
            statement.setString(1, accountId);
            statement.setString(2, currencyIdentifier);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong("balance") : 0L;
            }
        }
    }

    public List<SystemAccountBalance> listSystemBalances(String accountId) throws SQLException {
        List<SystemAccountBalance> balances = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT balance.account_id, balance.balance, balance.updated_at,
                    currency.identifier, currency.name, currency.icon,
                    currency.source_plugin, currency.registered_at, currency.is_default
                FROM wallet_system_balances balance JOIN wallet_currencies currency
                    ON currency.identifier = balance.currency_identifier
                WHERE balance.account_id = ?
                ORDER BY currency.is_default DESC, currency.identifier ASC
                """)) {
            statement.setString(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    balances.add(new SystemAccountBalance(accountId, readCurrency(result),
                            result.getLong("balance"), result.getLong("updated_at")));
                }
            }
        }
        return balances;
    }

    public WalletCurrency upsertCurrency(
            String identifier,
            String name,
            String iconKey,
            String pluginIdentifier,
            boolean defaultCurrency) throws SQLException {
        long now = now();
        String sql = """
                INSERT INTO wallet_currencies(identifier, name, icon, source_plugin, registered_at, is_default)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(identifier) DO UPDATE SET
                    name=excluded.name,
                    icon=excluded.icon,
                    source_plugin=excluded.source_plugin,
                    is_default=MAX(wallet_currencies.is_default, excluded.is_default);
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, identifier);
            statement.setString(2, name);
            statement.setString(3, iconKey);
            statement.setString(4, pluginIdentifier);
            statement.setLong(5, now);
            statement.setInt(6, defaultCurrency ? 1 : 0);
            statement.executeUpdate();
        }
        return findCurrency(identifier).orElse(new WalletCurrency(
                identifier,
                name,
                iconKey,
                pluginIdentifier,
                now,
                defaultCurrency));
    }

    public Optional<WalletCurrency> findCurrency(String identifier) throws SQLException {
        String sql = """
                SELECT identifier, name, icon, source_plugin, registered_at, is_default
                FROM wallet_currencies
                WHERE identifier = ?;
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, identifier);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(readCurrency(result));
                }
            }
        }
        return Optional.empty();
    }

    public List<WalletCurrency> listCurrencies() throws SQLException {
        String sql = """
                SELECT identifier, name, icon, source_plugin, registered_at, is_default
                FROM wallet_currencies
                ORDER BY is_default DESC, identifier ASC;
                """;
        List<WalletCurrency> currencies = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                currencies.add(readCurrency(result));
            }
        }
        return currencies;
    }

    public long getBalance(int playerDbId, String currencyIdentifier) throws SQLException {
        String sql = """
                SELECT balance
                FROM wallet_balances
                WHERE player_db_id = ? AND currency_identifier = ?;
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, playerDbId);
            statement.setString(2, currencyIdentifier);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getLong("balance");
                }
            }
        }
        return 0L;
    }

    public Optional<Long> getBalanceUpdatedAt(int playerDbId, String currencyIdentifier) throws SQLException {
        String sql = """
                SELECT updated_at
                FROM wallet_balances
                WHERE player_db_id = ? AND currency_identifier = ?;
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, playerDbId);
            statement.setString(2, currencyIdentifier);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(result.getLong("updated_at"));
                }
            }
        }
        return Optional.empty();
    }

    public List<WalletBalance> listBalancesForPlayer(int playerDbId, String defaultCurrencyIdentifier)
            throws SQLException {
        String sql = """
                SELECT c.identifier, c.name, c.icon, c.source_plugin, c.registered_at, c.is_default,
                       COALESCE(b.balance, 0) AS balance,
                       COALESCE(b.updated_at, 0) AS updated_at
                FROM wallet_currencies c
                LEFT JOIN wallet_balances b
                    ON b.currency_identifier = c.identifier AND b.player_db_id = ?
                WHERE b.player_db_id IS NOT NULL OR c.identifier = ?
                ORDER BY c.is_default DESC, c.identifier ASC;
                """;
        List<WalletBalance> balances = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, playerDbId);
            statement.setString(2, defaultCurrencyIdentifier);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    balances.add(new WalletBalance(
                            playerDbId,
                            readCurrency(result),
                            result.getLong("balance"),
                            result.getLong("updated_at")));
                }
            }
        }
        return balances;
    }

    public List<WalletBalance> listGlobalBalances() throws SQLException {
        String sql = """
                SELECT c.identifier, c.name, c.icon, c.source_plugin, c.registered_at, c.is_default,
                       COALESCE(SUM(b.balance), 0) AS balance,
                       COALESCE(MAX(b.updated_at), 0) AS updated_at
                FROM wallet_currencies c
                LEFT JOIN wallet_balances b ON b.currency_identifier = c.identifier
                GROUP BY c.identifier, c.name, c.icon, c.source_plugin, c.registered_at, c.is_default
                ORDER BY c.is_default DESC, c.identifier ASC;
                """;
        List<WalletBalance> balances = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                balances.add(new WalletBalance(
                        0,
                        readCurrency(result),
                        result.getLong("balance"),
                        result.getLong("updated_at")));
            }
        }
        return balances;
    }

    public List<WalletBalance> listTopBalances(String currencyIdentifier, int limit) throws SQLException {
        String sql = """
                SELECT b.player_db_id,
                       c.identifier, c.name, c.icon, c.source_plugin, c.registered_at, c.is_default,
                       b.balance,
                       b.updated_at
                FROM wallet_balances b
                JOIN wallet_currencies c ON c.identifier = b.currency_identifier
                WHERE b.currency_identifier = ? AND b.balance > 0
                ORDER BY b.balance DESC, b.player_db_id ASC
                LIMIT ?;
                """;
        List<WalletBalance> balances = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, currencyIdentifier);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    balances.add(new WalletBalance(
                            result.getInt("player_db_id"),
                            readCurrency(result),
                            result.getLong("balance"),
                            result.getLong("updated_at")));
                }
            }
        }
        return balances;
    }

    public synchronized WalletTransaction changeBalance(
            int playerDbId,
            WalletCurrency currency,
            long delta,
            String pluginIdentifier,
            String reason) throws SQLException, InsufficientFundsException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            long currentBalance = getBalance(playerDbId, currency.getIdentifier());
            long resultingBalance = Math.addExact(currentBalance, delta);
            if (resultingBalance < 0) {
                throw new InsufficientFundsException();
            }

            long now = now();
            upsertBalance(playerDbId, currency.getIdentifier(), resultingBalance, now);
            long transactionId = insertTransaction(
                    playerDbId,
                    currency.getIdentifier(),
                    delta,
                    resultingBalance,
                    pluginIdentifier,
                    reason,
                    now);
            connection.commit();
            return new WalletTransaction(
                    transactionId,
                    playerDbId,
                    currency,
                    delta,
                    resultingBalance,
                    pluginIdentifier,
                    reason,
                    now);
        } catch (SQLException | RuntimeException | InsufficientFundsException ex) {
            rollbackQuietly();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    /**
     * Moves funds between two accounts in one SQLite transaction. A correlation
     * ID is immutable: an exact retry returns the original transfer, while a
     * changed request with the same ID is rejected.
     */
    public synchronized WalletTransfer transferIdempotent(int payerDbId, int payeeDbId, WalletCurrency currency,
            long amount, String pluginIdentifier, String reason, String correlationId)
            throws SQLException, InsufficientFundsException, IdempotencyConflictException {
        Optional<WalletTransfer> existing = findTransfer(correlationId);
        if (existing.isPresent()) {
            WalletTransfer transfer = existing.get();
            if (transfer.getPayerDbId() == payerDbId && transfer.getPayeeDbId() == payeeDbId
                    && transfer.getCurrency().getIdentifier().equals(currency.getIdentifier())
                    && transfer.getAmount() == amount && transfer.getPluginIdentifier().equals(pluginIdentifier)
                    && transfer.getReason().equals(reason)) return transfer;
            throw new IdempotencyConflictException();
        }
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            long payerBalance = getBalance(payerDbId, currency.getIdentifier());
            long payeeBalance = getBalance(payeeDbId, currency.getIdentifier());
            long payerResult = Math.subtractExact(payerBalance, amount);
            long payeeResult = Math.addExact(payeeBalance, amount);
            if (payerResult < 0) throw new InsufficientFundsException();
            long now = now();
            upsertBalance(payerDbId, currency.getIdentifier(), payerResult, now);
            upsertBalance(payeeDbId, currency.getIdentifier(), payeeResult, now);
            long debitId = insertTransaction(payerDbId, currency.getIdentifier(), -amount, payerResult,
                    pluginIdentifier, reason, now);
            long creditId = insertTransaction(payeeDbId, currency.getIdentifier(), amount, payeeResult,
                    pluginIdentifier, reason, now);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO wallet_transfers(correlation_id, payer_db_id, payee_db_id, currency_identifier, amount,
                        debit_transaction_id, credit_transaction_id, source_plugin, reason, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, correlationId);
                statement.setInt(2, payerDbId);
                statement.setInt(3, payeeDbId);
                statement.setString(4, currency.getIdentifier());
                statement.setLong(5, amount);
                statement.setLong(6, debitId);
                statement.setLong(7, creditId);
                statement.setString(8, pluginIdentifier);
                statement.setString(9, reason);
                statement.setLong(10, now);
                statement.executeUpdate();
            }
            connection.commit();
            return new WalletTransfer(correlationId, payerDbId, payeeDbId, currency, amount, debitId, creditId,
                    pluginIdentifier, reason, now);
        } catch (SQLException | RuntimeException | InsufficientFundsException ex) {
            rollbackQuietly();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public Optional<WalletTransfer> findTransfer(String correlationId) throws SQLException {
        if (correlationId == null || correlationId.isBlank()) return Optional.empty();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT transfer.correlation_id, transfer.payer_db_id, transfer.payee_db_id, transfer.amount,
                    transfer.debit_transaction_id, transfer.credit_transaction_id, transfer.source_plugin,
                    transfer.reason, transfer.created_at, currency.identifier, currency.name, currency.icon,
                    currency.source_plugin AS currency_source_plugin, currency.registered_at, currency.is_default
                FROM wallet_transfers transfer JOIN wallet_currencies currency
                    ON currency.identifier = transfer.currency_identifier
                WHERE transfer.correlation_id = ?
                """)) {
            statement.setString(1, correlationId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new WalletTransfer(result.getString("correlation_id"), result.getInt("payer_db_id"),
                        result.getInt("payee_db_id"), readAliasedCurrency(result), result.getLong("amount"),
                        result.getLong("debit_transaction_id"), result.getLong("credit_transaction_id"),
                        result.getString("source_plugin"), result.getString("reason"), result.getLong("created_at")));
            }
        }
    }

    public synchronized AccountTransfer transferAccountIdempotent(String payerKind, String payerReference,
            String payeeKind, String payeeReference, WalletCurrency currency, long amount, String pluginIdentifier,
            String reason, String correlationId)
            throws SQLException, InsufficientFundsException, IdempotencyConflictException {
        Optional<AccountTransfer> existing = findAccountTransfer(correlationId);
        if (existing.isPresent()) {
            AccountTransfer transfer = existing.get();
            if (transfer.getPayerKind().equals(payerKind)
                    && transfer.getPayerReference().equals(payerReference)
                    && transfer.getPayeeKind().equals(payeeKind)
                    && transfer.getPayeeReference().equals(payeeReference)
                    && transfer.getCurrency().getIdentifier().equals(currency.getIdentifier())
                    && transfer.getAmount() == amount
                    && transfer.getPluginIdentifier().equals(pluginIdentifier)
                    && transfer.getReason().equals(reason)) return transfer;
            throw new IdempotencyConflictException();
        }

        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            long payerBalance = accountBalance(payerKind, payerReference, currency.getIdentifier());
            long payeeBalance = accountBalance(payeeKind, payeeReference, currency.getIdentifier());
            long payerResult = Math.subtractExact(payerBalance, amount);
            long payeeResult = Math.addExact(payeeBalance, amount);
            if (payerResult < 0) throw new InsufficientFundsException();

            long now = now();
            upsertAccountBalance(payerKind, payerReference, currency.getIdentifier(), payerResult, now);
            upsertAccountBalance(payeeKind, payeeReference, currency.getIdentifier(), payeeResult, now);
            long debitId = insertAccountTransaction(payerKind, payerReference, currency.getIdentifier(), -amount,
                    payerResult, pluginIdentifier, reason, now);
            long creditId = insertAccountTransaction(payeeKind, payeeReference, currency.getIdentifier(), amount,
                    payeeResult, pluginIdentifier, reason, now);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO wallet_account_transfers(
                        correlation_id, payer_kind, payer_reference, payee_kind, payee_reference,
                        currency_identifier, amount, debit_transaction_id, credit_transaction_id,
                        source_plugin, reason, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, correlationId);
                statement.setString(2, payerKind);
                statement.setString(3, payerReference);
                statement.setString(4, payeeKind);
                statement.setString(5, payeeReference);
                statement.setString(6, currency.getIdentifier());
                statement.setLong(7, amount);
                statement.setLong(8, debitId);
                statement.setLong(9, creditId);
                statement.setString(10, pluginIdentifier);
                statement.setString(11, reason);
                statement.setLong(12, now);
                statement.executeUpdate();
            }
            connection.commit();
            return new AccountTransfer(correlationId, payerKind, payerReference, payeeKind, payeeReference,
                    currency, amount, debitId, creditId, pluginIdentifier, reason, now);
        } catch (SQLException | RuntimeException | InsufficientFundsException ex) {
            rollbackQuietly();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    /** Credits a system account from its owning plugin's explicitly audited issuance source. */
    public synchronized boolean creditSystemAccountIdempotent(String accountId, WalletCurrency currency, long amount,
            String pluginIdentifier, String reason, String correlationId)
            throws SQLException, IdempotencyConflictException {
        try (PreparedStatement lookup = connection.prepareStatement("""
                SELECT account_id, currency_identifier, amount, source_plugin, reason
                FROM wallet_system_issuances WHERE correlation_id = ?
                """)) {
            lookup.setString(1, correlationId);
            try (ResultSet existing = lookup.executeQuery()) {
                if (existing.next()) {
                    if (accountId.equals(existing.getString("account_id"))
                            && currency.getIdentifier().equals(existing.getString("currency_identifier"))
                            && amount == existing.getLong("amount")
                            && pluginIdentifier.equals(existing.getString("source_plugin"))
                            && reason.equals(existing.getString("reason"))) return true;
                    throw new IdempotencyConflictException();
                }
            }
        }
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            long current = accountBalance("SYSTEM", accountId, currency.getIdentifier());
            long resulting = Math.addExact(current, amount);
            long now = now();
            upsertAccountBalance("SYSTEM", accountId, currency.getIdentifier(), resulting, now);
            long transactionId = insertAccountTransaction("SYSTEM", accountId, currency.getIdentifier(), amount,
                    resulting, pluginIdentifier, reason, now);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO wallet_system_issuances(
                        correlation_id, account_id, currency_identifier, amount, transaction_id,
                        source_plugin, reason, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                insert.setString(1, correlationId);
                insert.setString(2, accountId);
                insert.setString(3, currency.getIdentifier());
                insert.setLong(4, amount);
                insert.setLong(5, transactionId);
                insert.setString(6, pluginIdentifier);
                insert.setString(7, reason);
                insert.setLong(8, now);
                insert.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (SQLException | RuntimeException ex) {
            rollbackQuietly();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public Optional<AccountTransfer> findAccountTransfer(String correlationId) throws SQLException {
        if (correlationId == null || correlationId.isBlank()) return Optional.empty();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT transfer.correlation_id, transfer.payer_kind, transfer.payer_reference,
                    transfer.payee_kind, transfer.payee_reference, transfer.amount,
                    transfer.debit_transaction_id, transfer.credit_transaction_id, transfer.source_plugin,
                    transfer.reason, transfer.created_at, currency.identifier, currency.name, currency.icon,
                    currency.source_plugin AS currency_source_plugin, currency.registered_at, currency.is_default
                FROM wallet_account_transfers transfer JOIN wallet_currencies currency
                    ON currency.identifier = transfer.currency_identifier
                WHERE transfer.correlation_id = ?
                """)) {
            statement.setString(1, correlationId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new AccountTransfer(result.getString("correlation_id"),
                        result.getString("payer_kind"), result.getString("payer_reference"),
                        result.getString("payee_kind"), result.getString("payee_reference"), readAliasedCurrency(result),
                        result.getLong("amount"), result.getLong("debit_transaction_id"),
                        result.getLong("credit_transaction_id"), result.getString("source_plugin"),
                        result.getString("reason"), result.getLong("created_at")));
            }
        }
    }

    public List<SystemAccountTransaction> listLatestSystemTransactions(String accountId, int limit)
            throws SQLException {
        List<SystemAccountTransaction> transactions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT tx.id, tx.account_id, tx.delta, tx.resulting_balance, tx.source_plugin,
                    tx.reason, tx.created_at, currency.identifier, currency.name, currency.icon,
                    currency.source_plugin AS currency_source_plugin, currency.registered_at, currency.is_default
                FROM wallet_system_transactions tx JOIN wallet_currencies currency
                    ON currency.identifier = tx.currency_identifier
                WHERE tx.account_id = ? ORDER BY tx.created_at DESC, tx.id DESC LIMIT ?
                """)) {
            statement.setString(1, accountId);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    transactions.add(new SystemAccountTransaction(result.getLong("id"), accountId,
                            readAliasedCurrency(result), result.getLong("delta"), result.getLong("resulting_balance"),
                            result.getString("source_plugin"), result.getString("reason"),
                            result.getLong("created_at")));
                }
            }
        }
        return transactions;
    }

    public boolean hasNonZeroSystemBalance(String accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM wallet_system_balances WHERE account_id = ? AND balance <> 0 LIMIT 1
                """)) {
            statement.setString(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public List<WalletTransaction> listLatestTransactions(int playerDbId, int limit) throws SQLException {
        String sql = """
                SELECT t.id, t.player_db_id, t.delta, t.resulting_balance, t.source_plugin, t.reason, t.created_at,
                       c.identifier, c.name, c.icon, c.source_plugin AS currency_source_plugin,
                       c.registered_at, c.is_default
                FROM wallet_transactions t
                JOIN wallet_currencies c ON c.identifier = t.currency_identifier
                WHERE t.player_db_id = ?
                ORDER BY t.created_at DESC, t.id DESC
                LIMIT ?;
                """;
        List<WalletTransaction> transactions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, playerDbId);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    WalletCurrency currency = new WalletCurrency(
                            result.getString("identifier"),
                            result.getString("name"),
                            result.getString("icon"),
                            result.getString("currency_source_plugin"),
                            result.getLong("registered_at"),
                            result.getInt("is_default") == 1);
                    transactions.add(new WalletTransaction(
                            result.getLong("id"),
                            result.getInt("player_db_id"),
                            currency,
                            result.getLong("delta"),
                            result.getLong("resulting_balance"),
                            result.getString("source_plugin"),
                            result.getString("reason"),
                            result.getLong("created_at")));
                }
            }
        }
        return transactions;
    }

    public List<WalletTransaction> listLatestGlobalTransactions(int limit) throws SQLException {
        boolean limited = limit > 0;
        String sql = """
                SELECT t.id, t.player_db_id, t.delta, t.resulting_balance, t.source_plugin, t.reason, t.created_at,
                       c.identifier, c.name, c.icon, c.source_plugin AS currency_source_plugin,
                       c.registered_at, c.is_default
                FROM wallet_transactions t
                JOIN wallet_currencies c ON c.identifier = t.currency_identifier
                ORDER BY t.created_at DESC, t.id DESC
                """;
        if (limited) {
            sql += "LIMIT ?;";
        }
        List<WalletTransaction> transactions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (limited) {
                statement.setInt(1, limit);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    WalletCurrency currency = new WalletCurrency(
                            result.getString("identifier"),
                            result.getString("name"),
                            result.getString("icon"),
                            result.getString("currency_source_plugin"),
                            result.getLong("registered_at"),
                            result.getInt("is_default") == 1);
                    transactions.add(new WalletTransaction(
                            result.getLong("id"),
                            result.getInt("player_db_id"),
                            currency,
                            result.getLong("delta"),
                            result.getLong("resulting_balance"),
                            result.getString("source_plugin"),
                            result.getString("reason"),
                            result.getLong("created_at")));
                }
            }
        }
        return transactions;
    }

    private void upsertBalance(int playerDbId, String currencyIdentifier, long balance, long updatedAt)
            throws SQLException {
        String sql = """
                INSERT INTO wallet_balances(player_db_id, currency_identifier, balance, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_db_id, currency_identifier) DO UPDATE SET
                    balance=excluded.balance,
                    updated_at=excluded.updated_at;
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, playerDbId);
            statement.setString(2, currencyIdentifier);
            statement.setLong(3, balance);
            statement.setLong(4, updatedAt);
            statement.executeUpdate();
        }
    }

    private long accountBalance(String kind, String reference, String currencyIdentifier) throws SQLException {
        return "PLAYER".equals(kind) ? getBalance(Integer.parseInt(reference), currencyIdentifier)
                : getSystemBalance(reference, currencyIdentifier);
    }

    private void upsertAccountBalance(String kind, String reference, String currencyIdentifier, long balance,
            long updatedAt) throws SQLException {
        if ("PLAYER".equals(kind)) {
            upsertBalance(Integer.parseInt(reference), currencyIdentifier, balance, updatedAt);
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO wallet_system_balances(account_id, currency_identifier, balance, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(account_id, currency_identifier) DO UPDATE SET
                    balance=excluded.balance, updated_at=excluded.updated_at
                """)) {
            statement.setString(1, reference);
            statement.setString(2, currencyIdentifier);
            statement.setLong(3, balance);
            statement.setLong(4, updatedAt);
            statement.executeUpdate();
        }
    }

    private long insertAccountTransaction(String kind, String reference, String currencyIdentifier, long delta,
            long resultingBalance, String pluginIdentifier, String reason, long createdAt) throws SQLException {
        if ("PLAYER".equals(kind)) {
            return insertTransaction(Integer.parseInt(reference), currencyIdentifier, delta, resultingBalance,
                    pluginIdentifier, reason, createdAt);
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO wallet_system_transactions(
                    account_id, currency_identifier, delta, resulting_balance, source_plugin, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, reference);
            statement.setString(2, currencyIdentifier);
            statement.setLong(3, delta);
            statement.setLong(4, resultingBalance);
            statement.setString(5, pluginIdentifier);
            statement.setString(6, reason);
            statement.setLong(7, createdAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT last_insert_rowid();")) {
            return result.next() ? result.getLong(1) : 0L;
        }
    }

    private long insertTransaction(
            int playerDbId,
            String currencyIdentifier,
            long delta,
            long resultingBalance,
            String pluginIdentifier,
            String reason,
            long createdAt) throws SQLException {
        String sql = """
                INSERT INTO wallet_transactions(
                    player_db_id, currency_identifier, delta, resulting_balance, source_plugin, reason, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?);
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, playerDbId);
            statement.setString(2, currencyIdentifier);
            statement.setLong(3, delta);
            statement.setLong(4, resultingBalance);
            statement.setString(5, pluginIdentifier);
            statement.setString(6, reason);
            statement.setLong(7, createdAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT last_insert_rowid();")) {
            return result.next() ? result.getLong(1) : 0L;
        }
    }

    private WalletCurrency readCurrency(ResultSet result) throws SQLException {
        return new WalletCurrency(
                result.getString("identifier"),
                result.getString("name"),
                result.getString("icon"),
                result.getString("source_plugin"),
                result.getLong("registered_at"),
                result.getInt("is_default") == 1);
    }

    private WalletCurrency readAliasedCurrency(ResultSet result) throws SQLException {
        return new WalletCurrency(
                result.getString("identifier"),
                result.getString("name"),
                result.getString("icon"),
                result.getString("currency_source_plugin"),
                result.getLong("registered_at"),
                result.getInt("is_default") == 1);
    }

    private SystemAccount readSystemAccount(ResultSet result) throws SQLException {
        return new SystemAccount(result.getString("account_id"), result.getString("owner_plugin"),
                result.getString("account_type"), result.getString("display_name"),
                result.getString("status"), result.getLong("created_at"), result.getLong("updated_at"));
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException rollbackEx) {
            Wallet.logger().error("Wallet transaction rollback failed: " + rollbackEx.getMessage());
        }
    }

    public static class InsufficientFundsException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    public static class IdempotencyConflictException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
