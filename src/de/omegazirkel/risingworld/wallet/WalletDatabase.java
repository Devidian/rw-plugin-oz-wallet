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
    private static final int SCHEMA_VERSION = 1;

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
                    CREATE INDEX IF NOT EXISTS idx_wallet_transactions_player_created
                    ON wallet_transactions (player_db_id, created_at DESC, id DESC);
                    """);
            statement.execute("PRAGMA user_version = " + SCHEMA_VERSION + ";");
        }
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
}
