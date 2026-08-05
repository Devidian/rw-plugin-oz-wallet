package de.omegazirkel.risingworld.wallet;

import java.util.List;

/** Stable public paged result for system-account listings. */
public final class SystemAccountsResult {
    public final boolean success;
    public final WalletErrorCode errorCode;
    public final String message;
    public final List<SystemAccount> accounts;
    public final int total;
    public final int offset;
    public final int limit;

    private SystemAccountsResult(boolean success, WalletErrorCode errorCode, String message,
            List<SystemAccount> accounts, int total, int offset, int limit) {
        this.success = success;
        this.errorCode = errorCode;
        this.message = message;
        this.accounts = accounts;
        this.total = total;
        this.offset = offset;
        this.limit = limit;
    }

    public static SystemAccountsResult success(List<SystemAccount> accounts, int total, int offset, int limit) {
        return new SystemAccountsResult(true, WalletErrorCode.NONE, "System accounts loaded.",
                List.copyOf(accounts), total, offset, limit);
    }

    public static SystemAccountsResult failure(WalletErrorCode errorCode, String message) {
        return new SystemAccountsResult(false, errorCode, message, List.of(), 0, 0, 0);
    }
}
