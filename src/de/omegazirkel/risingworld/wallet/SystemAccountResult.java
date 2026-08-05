package de.omegazirkel.risingworld.wallet;

/** Stable public result for system-account mutations and lookup. */
public final class SystemAccountResult {
    public final boolean success;
    public final WalletErrorCode errorCode;
    public final String message;
    public final SystemAccount account;

    private SystemAccountResult(boolean success, WalletErrorCode errorCode, String message, SystemAccount account) {
        this.success = success;
        this.errorCode = errorCode;
        this.message = message;
        this.account = account;
    }

    public static SystemAccountResult success(SystemAccount account) {
        return new SystemAccountResult(true, WalletErrorCode.NONE, "System account available.", account);
    }

    public static SystemAccountResult failure(WalletErrorCode errorCode, String message) {
        return new SystemAccountResult(false, errorCode, message, null);
    }
}
