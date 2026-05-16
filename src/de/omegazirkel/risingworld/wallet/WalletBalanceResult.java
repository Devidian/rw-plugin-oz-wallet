package de.omegazirkel.risingworld.wallet;

public class WalletBalanceResult {
    public final boolean success;
    public final WalletErrorCode errorCode;
    public final String message;
    public final WalletBalance balance;

    private WalletBalanceResult(boolean success, WalletErrorCode errorCode, String message, WalletBalance balance) {
        this.success = success;
        this.errorCode = errorCode;
        this.message = message;
        this.balance = balance;
    }

    public static WalletBalanceResult success(WalletBalance balance) {
        return new WalletBalanceResult(true, WalletErrorCode.NONE, "Balance loaded.", balance);
    }

    public static WalletBalanceResult failure(WalletErrorCode errorCode, String message) {
        return new WalletBalanceResult(false, errorCode, message, null);
    }
}
