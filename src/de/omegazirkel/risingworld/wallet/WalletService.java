package de.omegazirkel.risingworld.wallet;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.omegazirkel.risingworld.Wallet;
import de.omegazirkel.risingworld.wallet.WalletDatabase.InsufficientFundsException;
import de.omegazirkel.risingworld.wallet.WalletDatabase.IdempotencyConflictException;

public class WalletService {
    public static final int MAX_REASON_LENGTH = 255;
    public static final int MAX_ACCOUNT_ID_LENGTH = 160;
    public static final int MAX_ACCOUNT_LABEL_LENGTH = 120;
    private static final String PLAYER = "PLAYER";
    private static final String SYSTEM = "SYSTEM";

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

    public WalletTransferResult transferIdempotent(int payerDbId, int payeeDbId, long amount, String reason,
            String currencyIdentifier, String pluginIdentifier, String correlationId) {
        if (payerDbId <= 0 || payeeDbId <= 0 || payerDbId == payeeDbId || amount <= 0) {
            return WalletTransferResult.failure(WalletErrorCode.INVALID_ARGUMENT,
                    "Payer and payee must differ and amount must be positive.");
        }
        String normalizedIdentifier = normalizeCurrencyIdentifier(currencyIdentifier);
        String normalizedPluginIdentifier = normalizeRequired(pluginIdentifier);
        String normalizedReason = normalizeReason(reason);
        String normalizedCorrelationId = normalizeCorrelationId(correlationId);
        if (normalizedIdentifier == null || normalizedPluginIdentifier == null || normalizedReason == null
                || normalizedCorrelationId == null) {
            return WalletTransferResult.failure(WalletErrorCode.INVALID_ARGUMENT,
                    "Currency, plugin, reason, and correlation id are required.");
        }
        try {
            Optional<WalletCurrency> currency = database.findCurrency(normalizedIdentifier);
            if (currency.isEmpty()) {
                return WalletTransferResult.failure(WalletErrorCode.UNKNOWN_CURRENCY, "Currency is not registered.");
            }
            return WalletTransferResult.success(database.transferIdempotent(payerDbId, payeeDbId, currency.get(), amount,
                    normalizedPluginIdentifier, normalizedReason, normalizedCorrelationId));
        } catch (InsufficientFundsException ex) {
            return WalletTransferResult.failure(WalletErrorCode.INSUFFICIENT_FUNDS,
                    "Wallet balance is too low for this transfer.");
        } catch (IdempotencyConflictException ex) {
            return WalletTransferResult.failure(WalletErrorCode.IDEMPOTENCY_CONFLICT,
                    "Correlation id is already bound to another transfer.");
        } catch (ArithmeticException ex) {
            return WalletTransferResult.failure(WalletErrorCode.INVALID_ARGUMENT,
                    "Wallet transfer amount exceeds supported balance range.");
        } catch (SQLException ex) {
            Wallet.logger().error("transferIdempotent failed: " + ex.getMessage());
            return WalletTransferResult.failure(WalletErrorCode.DATABASE_ERROR, "Wallet transfer failed.");
        }
    }

    public SystemAccountResult createSystemAccount(String accountId, String accountType, String displayName,
            String pluginIdentifier) {
        String normalizedAccountId = normalizeAccountId(accountId);
        String normalizedType = normalizeRequired(accountType);
        String normalizedDisplayName = normalizeLabel(displayName);
        String normalizedPlugin = normalizeRequired(pluginIdentifier);
        if (normalizedAccountId == null || normalizedType == null || normalizedDisplayName == null
                || normalizedPlugin == null) {
            return SystemAccountResult.failure(WalletErrorCode.INVALID_ARGUMENT,
                    "Account id, type, display name, and plugin identifier are required.");
        }
        normalizedType = normalizedType.toUpperCase(Locale.ROOT);
        try {
            Optional<SystemAccount> existing = database.findSystemAccount(normalizedAccountId);
            if (existing.isPresent()) {
                SystemAccount account = existing.get();
                if (!account.getOwnerPlugin().equals(normalizedPlugin)
                        || !account.getAccountType().equals(normalizedType)
                        || !account.getDisplayName().equals(normalizedDisplayName)) {
                    return SystemAccountResult.failure(WalletErrorCode.ACCOUNT_OWNERSHIP_CONFLICT,
                            "System account identity or metadata does not match the existing account.");
                }
                if (!account.isActive()) {
                    return SystemAccountResult.failure(WalletErrorCode.ACCOUNT_ARCHIVED,
                            "System account is archived.");
                }
                return SystemAccountResult.success(account);
            }
            return SystemAccountResult.success(database.insertSystemAccount(normalizedAccountId, normalizedPlugin,
                    normalizedType, normalizedDisplayName));
        } catch (SQLException ex) {
            Wallet.logger().error("createSystemAccount failed: " + ex.getMessage());
            return SystemAccountResult.failure(WalletErrorCode.DATABASE_ERROR, "System account creation failed.");
        }
    }

    public SystemAccountResult systemAccount(String accountId) {
        String normalizedAccountId = normalizeAccountId(accountId);
        if (normalizedAccountId == null) {
            return SystemAccountResult.failure(WalletErrorCode.INVALID_ARGUMENT, "Account id is required.");
        }
        try {
            return database.findSystemAccount(normalizedAccountId).map(SystemAccountResult::success)
                    .orElseGet(() -> SystemAccountResult.failure(WalletErrorCode.ACCOUNT_NOT_FOUND,
                            "System account was not found."));
        } catch (SQLException ex) {
            Wallet.logger().error("systemAccount failed: " + ex.getMessage());
            return SystemAccountResult.failure(WalletErrorCode.DATABASE_ERROR, "System account lookup failed.");
        }
    }

    public SystemAccountsResult listSystemAccounts(String search, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        String safeSearch = search == null ? "" : search.trim();
        try {
            return SystemAccountsResult.success(database.listSystemAccounts(safeSearch, safeOffset, safeLimit),
                    database.countSystemAccounts(safeSearch), safeOffset, safeLimit);
        } catch (SQLException ex) {
            Wallet.logger().error("listSystemAccounts failed: " + ex.getMessage());
            return SystemAccountsResult.failure(WalletErrorCode.DATABASE_ERROR, "System account list failed.");
        }
    }

    public SystemAccountBalancesResult systemAccountBalances(String accountId) {
        SystemAccountResult account = systemAccount(accountId);
        if (!account.success) return SystemAccountBalancesResult.failure(account.errorCode, account.message);
        try {
            return SystemAccountBalancesResult.success(database.listSystemBalances(account.account.getAccountId()));
        } catch (SQLException ex) {
            Wallet.logger().error("systemAccountBalances failed: " + ex.getMessage());
            return SystemAccountBalancesResult.failure(WalletErrorCode.DATABASE_ERROR,
                    "System account balances failed.");
        }
    }

    public SystemAccountTransactionsResult systemAccountTransactions(String accountId, int limit) {
        SystemAccountResult account = systemAccount(accountId);
        if (!account.success) return SystemAccountTransactionsResult.failure(account.errorCode, account.message);
        try {
            return SystemAccountTransactionsResult.success(database.listLatestSystemTransactions(
                    account.account.getAccountId(), Math.min(Math.max(limit, 1), 100)));
        } catch (SQLException ex) {
            Wallet.logger().error("systemAccountTransactions failed: " + ex.getMessage());
            return SystemAccountTransactionsResult.failure(WalletErrorCode.DATABASE_ERROR,
                    "System account transactions failed.");
        }
    }

    public SystemAccountResult archiveSystemAccount(String accountId, String pluginIdentifier) {
        String normalizedPlugin = normalizeRequired(pluginIdentifier);
        SystemAccountResult lookup = systemAccount(accountId);
        if (!lookup.success) return lookup;
        SystemAccount account = lookup.account;
        if (normalizedPlugin == null || !account.getOwnerPlugin().equals(normalizedPlugin)) {
            return SystemAccountResult.failure(WalletErrorCode.ACCOUNT_OWNERSHIP_CONFLICT,
                    "Only the owning plugin can archive a system account.");
        }
        if (!account.isActive()) return SystemAccountResult.success(account);
        try {
            if (database.hasNonZeroSystemBalance(account.getAccountId())) {
                return SystemAccountResult.failure(WalletErrorCode.ACCOUNT_NOT_EMPTY,
                        "System account still has a non-zero balance.");
            }
            database.archiveSystemAccount(account.getAccountId());
            return systemAccount(account.getAccountId());
        } catch (SQLException ex) {
            Wallet.logger().error("archiveSystemAccount failed: " + ex.getMessage());
            return SystemAccountResult.failure(WalletErrorCode.DATABASE_ERROR, "System account archive failed.");
        }
    }

    public SystemAccountResult updateSystemAccountDisplayName(String accountId, String displayName,
            String pluginIdentifier) {
        String normalizedDisplayName = normalizeLabel(displayName);
        String normalizedPlugin = normalizeRequired(pluginIdentifier);
        SystemAccountResult lookup = systemAccount(accountId);
        if (!lookup.success) return lookup;
        if (normalizedDisplayName == null || normalizedPlugin == null) {
            return SystemAccountResult.failure(WalletErrorCode.INVALID_ARGUMENT,
                    "Display name and plugin identifier are required.");
        }
        if (!lookup.account.getOwnerPlugin().equals(normalizedPlugin)) {
            return SystemAccountResult.failure(WalletErrorCode.ACCOUNT_OWNERSHIP_CONFLICT,
                    "Only the owning plugin can rename a system account.");
        }
        if (!lookup.account.isActive()) {
            return SystemAccountResult.failure(WalletErrorCode.ACCOUNT_ARCHIVED, "System account is archived.");
        }
        try {
            database.updateSystemAccountDisplayName(lookup.account.getAccountId(), normalizedDisplayName);
            return systemAccount(lookup.account.getAccountId());
        } catch (SQLException ex) {
            Wallet.logger().error("updateSystemAccountDisplayName failed: " + ex.getMessage());
            return SystemAccountResult.failure(WalletErrorCode.DATABASE_ERROR, "System account update failed.");
        }
    }

    public AccountTransferResult transferPlayerToSystemIdempotent(int payerDbId, String payeeAccountId, long amount,
            String reason, String currencyIdentifier, String pluginIdentifier, String correlationId) {
        if (payerDbId <= 0) return invalidAccountTransfer("Player database id must be positive.");
        SystemAccountResult payee = activeAccount(payeeAccountId);
        if (!payee.success) return AccountTransferResult.failure(payee.errorCode, payee.message);
        return transferAccount(PLAYER, Integer.toString(payerDbId), SYSTEM, payee.account.getAccountId(), amount,
                reason, currencyIdentifier, pluginIdentifier, correlationId);
    }

    public AccountTransferResult transferSystemToPlayerIdempotent(String payerAccountId, int payeeDbId, long amount,
            String reason, String currencyIdentifier, String pluginIdentifier, String correlationId) {
        if (payeeDbId <= 0) return invalidAccountTransfer("Player database id must be positive.");
        SystemAccountResult payer = ownedActiveAccount(payerAccountId, pluginIdentifier);
        if (!payer.success) return AccountTransferResult.failure(payer.errorCode, payer.message);
        return transferAccount(SYSTEM, payer.account.getAccountId(), PLAYER, Integer.toString(payeeDbId), amount,
                reason, currencyIdentifier, pluginIdentifier, correlationId);
    }

    public AccountTransferResult transferSystemToSystemIdempotent(String payerAccountId, String payeeAccountId,
            long amount, String reason, String currencyIdentifier, String pluginIdentifier, String correlationId) {
        SystemAccountResult payer = ownedActiveAccount(payerAccountId, pluginIdentifier);
        if (!payer.success) return AccountTransferResult.failure(payer.errorCode, payer.message);
        SystemAccountResult payee = activeAccount(payeeAccountId);
        if (!payee.success) return AccountTransferResult.failure(payee.errorCode, payee.message);
        if (payer.account.getAccountId().equals(payee.account.getAccountId())) {
            return invalidAccountTransfer("Payer and payee accounts must differ.");
        }
        return transferAccount(SYSTEM, payer.account.getAccountId(), SYSTEM, payee.account.getAccountId(), amount,
                reason, currencyIdentifier, pluginIdentifier, correlationId);
    }

    /**
     * Creates currency in a system account. This is deliberately limited to its
     * owning plugin and is permanently audited with an immutable correlation id.
     */
    public WalletTransactionResult creditSystemAccountIdempotent(String accountId, long amount, String reason,
            String currencyIdentifier, String pluginIdentifier, String correlationId) {
        SystemAccountResult account = ownedActiveAccount(accountId, pluginIdentifier);
        if (!account.success) return WalletTransactionResult.failure(account.errorCode, account.message);
        String normalizedCurrency = normalizeCurrencyIdentifier(currencyIdentifier);
        String normalizedReason = normalizeReason(reason);
        String normalizedPlugin = normalizeRequired(pluginIdentifier);
        String normalizedCorrelation = normalizeCorrelationId(correlationId);
        if (amount <= 0 || normalizedCurrency == null || normalizedReason == null || normalizedPlugin == null
                || normalizedCorrelation == null) {
            return WalletTransactionResult.failure(WalletErrorCode.INVALID_ARGUMENT,
                    "Positive amount, currency, reason, plugin, and correlation id are required.");
        }
        try {
            Optional<WalletCurrency> currency = database.findCurrency(normalizedCurrency);
            if (currency.isEmpty()) {
                return WalletTransactionResult.failure(WalletErrorCode.UNKNOWN_CURRENCY, "Currency is not registered.");
            }
            database.creditSystemAccountIdempotent(account.account.getAccountId(), currency.get(), amount,
                    normalizedPlugin, normalizedReason, normalizedCorrelation);
            return WalletTransactionResult.success(null);
        } catch (IdempotencyConflictException ex) {
            return WalletTransactionResult.failure(WalletErrorCode.IDEMPOTENCY_CONFLICT,
                    "Correlation id is already bound to another issuance.");
        } catch (ArithmeticException ex) {
            return WalletTransactionResult.failure(WalletErrorCode.INVALID_ARGUMENT,
                    "System account balance exceeds supported range.");
        } catch (SQLException ex) {
            Wallet.logger().error("creditSystemAccountIdempotent failed: " + ex.getMessage());
            return WalletTransactionResult.failure(WalletErrorCode.DATABASE_ERROR, "System account issuance failed.");
        }
    }

    public AccountTransferResult reverseAccountTransferIdempotent(String originalCorrelationId,
            String reversalCorrelationId, String reason, String pluginIdentifier) {
        String original = normalizeCorrelationId(originalCorrelationId);
        String reversal = normalizeCorrelationId(reversalCorrelationId);
        String normalizedReason = normalizeReason(reason);
        String normalizedPlugin = normalizeRequired(pluginIdentifier);
        if (original == null || reversal == null || normalizedReason == null || normalizedPlugin == null
                || original.equals(reversal)) {
            return invalidAccountTransfer("Original/reversal correlation ids, reason, and plugin are required.");
        }
        try {
            Optional<AccountTransfer> source = database.findAccountTransfer(original);
            if (source.isEmpty()) {
                return AccountTransferResult.failure(WalletErrorCode.TRANSFER_NOT_FOUND,
                        "Original account transfer was not found.");
            }
            AccountTransfer transfer = source.get();
            if (!transfer.getPluginIdentifier().equals(normalizedPlugin)) {
                return AccountTransferResult.failure(WalletErrorCode.ACCOUNT_OWNERSHIP_CONFLICT,
                        "Only the plugin that created a transfer can reverse it.");
            }
            return transferAccount(transfer.getPayeeKind(), transfer.getPayeeReference(), transfer.getPayerKind(),
                    transfer.getPayerReference(), transfer.getAmount(), normalizedReason,
                    transfer.getCurrency().getIdentifier(), normalizedPlugin, reversal);
        } catch (SQLException ex) {
            Wallet.logger().error("reverseAccountTransferIdempotent failed: " + ex.getMessage());
            return AccountTransferResult.failure(WalletErrorCode.DATABASE_ERROR, "Wallet reversal failed.");
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

    private AccountTransferResult transferAccount(String payerKind, String payerReference, String payeeKind,
            String payeeReference, long amount, String reason, String currencyIdentifier, String pluginIdentifier,
            String correlationId) {
        String normalizedCurrency = normalizeCurrencyIdentifier(currencyIdentifier);
        String normalizedPlugin = normalizeRequired(pluginIdentifier);
        String normalizedReason = normalizeReason(reason);
        String normalizedCorrelation = normalizeCorrelationId(correlationId);
        if (amount <= 0 || normalizedCurrency == null || normalizedPlugin == null || normalizedReason == null
                || normalizedCorrelation == null) {
            return invalidAccountTransfer("Positive amount, currency, plugin, reason, and correlation id are required.");
        }
        try {
            Optional<WalletCurrency> currency = database.findCurrency(normalizedCurrency);
            if (currency.isEmpty()) {
                return AccountTransferResult.failure(WalletErrorCode.UNKNOWN_CURRENCY, "Currency is not registered.");
            }
            return AccountTransferResult.success(database.transferAccountIdempotent(payerKind, payerReference,
                    payeeKind, payeeReference, currency.get(), amount, normalizedPlugin, normalizedReason,
                    normalizedCorrelation));
        } catch (InsufficientFundsException ex) {
            return AccountTransferResult.failure(WalletErrorCode.INSUFFICIENT_FUNDS,
                    "Wallet balance is too low for this transfer.");
        } catch (IdempotencyConflictException ex) {
            return AccountTransferResult.failure(WalletErrorCode.IDEMPOTENCY_CONFLICT,
                    "Correlation id is already bound to another transfer.");
        } catch (ArithmeticException ex) {
            return invalidAccountTransfer("Wallet transfer amount exceeds supported balance range.");
        } catch (SQLException ex) {
            Wallet.logger().error("system account transfer failed: " + ex.getMessage());
            return AccountTransferResult.failure(WalletErrorCode.DATABASE_ERROR, "Wallet transfer failed.");
        }
    }

    private SystemAccountResult activeAccount(String accountId) {
        SystemAccountResult lookup = systemAccount(accountId);
        if (!lookup.success || lookup.account.isActive()) return lookup;
        return SystemAccountResult.failure(WalletErrorCode.ACCOUNT_ARCHIVED, "System account is archived.");
    }

    private SystemAccountResult ownedActiveAccount(String accountId, String pluginIdentifier) {
        String normalizedPlugin = normalizeRequired(pluginIdentifier);
        SystemAccountResult lookup = activeAccount(accountId);
        if (!lookup.success) return lookup;
        if (normalizedPlugin == null || !lookup.account.getOwnerPlugin().equals(normalizedPlugin)) {
            return SystemAccountResult.failure(WalletErrorCode.ACCOUNT_OWNERSHIP_CONFLICT,
                    "Only the owning plugin can debit this system account.");
        }
        return lookup;
    }

    private static AccountTransferResult invalidAccountTransfer(String message) {
        return AccountTransferResult.failure(WalletErrorCode.INVALID_ARGUMENT, message);
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

    private static String normalizeCorrelationId(String correlationId) {
        String normalized = normalizeRequired(correlationId);
        return normalized == null || normalized.length() > 255 ? null : normalized;
    }

    private static String normalizeAccountId(String accountId) {
        String normalized = normalizeRequired(accountId);
        if (normalized == null || normalized.length() > MAX_ACCOUNT_ID_LENGTH) {
            return null;
        }
        return normalized;
    }

    private static String normalizeLabel(String label) {
        String normalized = normalizeRequired(label);
        if (normalized == null) return null;
        return normalized.length() <= MAX_ACCOUNT_LABEL_LENGTH ? normalized
                : normalized.substring(0, MAX_ACCOUNT_LABEL_LENGTH);
    }

    private static String normalizeRequired(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
