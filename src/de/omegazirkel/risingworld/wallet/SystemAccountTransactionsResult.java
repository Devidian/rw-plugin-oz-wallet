package de.omegazirkel.risingworld.wallet;

import java.util.List;

/** Stable public result for system-account audit rows. */
public final class SystemAccountTransactionsResult {
    public final boolean success;
    public final WalletErrorCode errorCode;
    public final String message;
    public final List<SystemAccountTransaction> transactions;

    private SystemAccountTransactionsResult(boolean success, WalletErrorCode errorCode, String message,
            List<SystemAccountTransaction> transactions) {
        this.success = success;
        this.errorCode = errorCode;
        this.message = message;
        this.transactions = transactions;
    }

    public static SystemAccountTransactionsResult success(List<SystemAccountTransaction> transactions) {
        return new SystemAccountTransactionsResult(true, WalletErrorCode.NONE, "System transactions loaded.",
                List.copyOf(transactions));
    }

    public static SystemAccountTransactionsResult failure(WalletErrorCode errorCode, String message) {
        return new SystemAccountTransactionsResult(false, errorCode, message, List.of());
    }
}
