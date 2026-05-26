package de.omegazirkel.risingworld.wallet;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.omegazirkel.risingworld.Wallet;
import de.omegazirkel.risingworld.wallet.WalletDatabase.InsufficientFundsException;

public class WalletService {
    public static final int MAX_REASON_LENGTH = 255;

    private final WalletDatabase database;
    private String defaultCurrencyIdentifier;
    private WalletCurrency defaultCurrency;

    public WalletService(WalletDatabase database) {
        this.database = database;
    }

    public WalletCurrencyResult registerCurrency(
            String currencyIdentifier,
            String name,
            String icon,
            String pluginIdentifier) {
        return registerCurrency(currencyIdentifier, name, icon, pluginIdentifier, false);
    }

    public WalletCurrencyResult registerCurrency(
            String currencyIdentifier,
            String name,
            String icon,
            String pluginIdentifier,
            boolean defaultCurrency) {
        String normalizedIdentifier = normalizeCurrencyIdentifier(currencyIdentifier);
        String normalizedName = normalizeRequired(name);
        String normalizedIcon = normalizeRequired(icon);
        String normalizedPluginIdentifier = normalizeRequired(pluginIdentifier);

        if (normalizedIdentifier == null || normalizedName == null || normalizedIcon == null
                || normalizedPluginIdentifier == null) {
            return WalletCurrencyResult.failure(
                    WalletErrorCode.INVALID_ARGUMENT,
                    "Currency identifier, name, icon, and plugin identifier are required.");
        }

        try {
            Optional<WalletCurrency> existingCurrency = database.findCurrency(normalizedIdentifier);
            if (existingCurrency.isPresent()
                    && !existingCurrency.get().getPluginIdentifier().equals(normalizedPluginIdentifier)) {
                return WalletCurrencyResult.failure(
                        WalletErrorCode.CURRENCY_ALREADY_REGISTERED,
                        "Currency identifier is already registered by another plugin.");
            }
            WalletCurrency currency = database.upsertCurrency(
                    normalizedIdentifier,
                    normalizedName,
                    normalizedIcon,
                    normalizedPluginIdentifier,
                    defaultCurrency);
            if (defaultCurrency) {
                this.defaultCurrencyIdentifier = normalizedIdentifier;
                this.defaultCurrency = currency;
            }
            return WalletCurrencyResult.success(currency);
        } catch (SQLException ex) {
            Wallet.logger().error("registerCurrency failed: " + ex.getMessage());
            return WalletCurrencyResult.failure(WalletErrorCode.DATABASE_ERROR, "Currency registration failed.");
        }
    }

    public WalletTransactionResult deposit(
            int playerDbId,
            long value,
            String reason,
            String currencyIdentifier,
            String pluginIdentifier) {
        if (value <= 0) {
            return WalletTransactionResult.failure(WalletErrorCode.INVALID_ARGUMENT, "Value must be positive.");
        }
        return changeBalance(playerDbId, value, reason, currencyIdentifier, pluginIdentifier);
    }

    public WalletTransactionResult withdraw(
            int playerDbId,
            long value,
            String reason,
            String currencyIdentifier,
            String pluginIdentifier) {
        if (value <= 0) {
            return WalletTransactionResult.failure(WalletErrorCode.INVALID_ARGUMENT, "Value must be positive.");
        }
        return changeBalance(playerDbId, -value, reason, currencyIdentifier, pluginIdentifier);
    }

    public WalletBalanceResult balance(int playerDbId, String currencyIdentifier) {
        if (playerDbId <= 0) {
            return WalletBalanceResult.failure(WalletErrorCode.INVALID_ARGUMENT, "Player database id must be positive.");
        }
        String normalizedIdentifier = normalizeCurrencyIdentifier(currencyIdentifier);
        if (normalizedIdentifier == null) {
            return WalletBalanceResult.failure(WalletErrorCode.INVALID_ARGUMENT, "Currency identifier is required.");
        }

        try {
            Optional<WalletCurrency> currency = database.findCurrency(normalizedIdentifier);
            if (currency.isEmpty()) {
                return WalletBalanceResult.failure(WalletErrorCode.UNKNOWN_CURRENCY, "Currency is not registered.");
            }
            long balance = database.getBalance(playerDbId, normalizedIdentifier);
            long updatedAt = database.getBalanceUpdatedAt(playerDbId, normalizedIdentifier).orElse(0L);
            return WalletBalanceResult.success(new WalletBalance(playerDbId, currency.get(), balance, updatedAt));
        } catch (SQLException ex) {
            Wallet.logger().error("balance failed: " + ex.getMessage());
            return WalletBalanceResult.failure(WalletErrorCode.DATABASE_ERROR, "Balance lookup failed.");
        }
    }

    public String defaultCurrencyIdentifier() {
        return defaultCurrencyIdentifier;
    }

    public WalletCurrencyResult defaultCurrency() {
        if (defaultCurrency == null) {
            return WalletCurrencyResult.failure(WalletErrorCode.UNKNOWN_CURRENCY, "Default currency is not registered.");
        }
        return WalletCurrencyResult.success(defaultCurrency);
    }

    public WalletCurrenciesResult listCurrencies() {
        try {
            return WalletCurrenciesResult.success(database.listCurrencies());
        } catch (SQLException ex) {
            Wallet.logger().error("listCurrencies failed: " + ex.getMessage());
            return WalletCurrenciesResult.failure(WalletErrorCode.DATABASE_ERROR, "Currency list lookup failed.");
        }
    }

    public List<WalletBalance> listBalancesForPlayer(int playerDbId, String defaultCurrencyIdentifier)
            throws SQLException {
        return database.listBalancesForPlayer(playerDbId, normalizeCurrencyIdentifier(defaultCurrencyIdentifier));
    }

    public List<WalletBalance> listGlobalBalances() throws SQLException {
        return database.listGlobalBalances();
    }

    public List<WalletBalance> listTopBalances(String currencyIdentifier, int limit) throws SQLException {
        return database.listTopBalances(normalizeCurrencyIdentifier(currencyIdentifier), Math.min(Math.max(limit, 1), 100));
    }

    public List<WalletTransaction> listLatestTransactions(int playerDbId, int limit) throws SQLException {
        return database.listLatestTransactions(playerDbId, Math.min(Math.max(limit, 1), 100));
    }

    public List<WalletTransaction> listLatestGlobalTransactions(int limit) throws SQLException {
        return database.listLatestGlobalTransactions(Math.max(limit, 0));
    }

    private WalletTransactionResult changeBalance(
            int playerDbId,
            long signedValue,
            String reason,
            String currencyIdentifier,
            String pluginIdentifier) {
        if (playerDbId <= 0 || signedValue == 0 || signedValue == Long.MIN_VALUE) {
            return WalletTransactionResult.failure(
                    WalletErrorCode.INVALID_ARGUMENT,
                    "Player database id must be positive and value must be positive.");
        }
        String normalizedIdentifier = normalizeCurrencyIdentifier(currencyIdentifier);
        String normalizedPluginIdentifier = normalizeRequired(pluginIdentifier);
        String normalizedReason = normalizeReason(reason);
        if (normalizedIdentifier == null || normalizedPluginIdentifier == null || normalizedReason == null) {
            return WalletTransactionResult.failure(
                    WalletErrorCode.INVALID_ARGUMENT,
                    "Currency identifier, plugin identifier, and reason are required.");
        }

        long absoluteValue = Math.abs(signedValue);
        long delta = signedValue > 0 ? absoluteValue : -absoluteValue;
        try {
            Optional<WalletCurrency> currency = database.findCurrency(normalizedIdentifier);
            if (currency.isEmpty()) {
                return WalletTransactionResult.failure(WalletErrorCode.UNKNOWN_CURRENCY, "Currency is not registered.");
            }
            WalletTransaction transaction = database.changeBalance(
                    playerDbId,
                    currency.get(),
                    delta,
                    normalizedPluginIdentifier,
                    normalizedReason);
            return WalletTransactionResult.success(transaction);
        } catch (InsufficientFundsException ex) {
            return WalletTransactionResult.failure(
                    WalletErrorCode.INSUFFICIENT_FUNDS,
                    "Wallet balance is too low for this withdrawal.");
        } catch (ArithmeticException ex) {
            return WalletTransactionResult.failure(
                    WalletErrorCode.INVALID_ARGUMENT,
                    "Wallet transaction amount exceeds supported balance range.");
        } catch (SQLException ex) {
            Wallet.logger().error("changeBalance failed: " + ex.getMessage());
            return WalletTransactionResult.failure(WalletErrorCode.DATABASE_ERROR, "Wallet transaction failed.");
        }
    }

    public static String normalizeCurrencyIdentifier(String currencyIdentifier) {
        String normalized = normalizeRequired(currencyIdentifier);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeReason(String reason) {
        String normalized = normalizeRequired(reason);
        if (normalized == null) {
            return null;
        }
        return normalized.length() <= MAX_REASON_LENGTH ? normalized : normalized.substring(0, MAX_REASON_LENGTH);
    }

    private static String normalizeRequired(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
