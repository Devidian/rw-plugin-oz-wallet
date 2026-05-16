package de.omegazirkel.risingworld.wallet;

public class WalletTransactionResult {
    public final boolean success;
    public final WalletErrorCode errorCode;
    public final String message;
    public final WalletTransaction transaction;

    private WalletTransactionResult(
            boolean success,
            WalletErrorCode errorCode,
            String message,
            WalletTransaction transaction) {
        this.success = success;
        this.errorCode = errorCode;
        this.message = message;
        this.transaction = transaction;
    }

    public static WalletTransactionResult success(WalletTransaction transaction) {
        return new WalletTransactionResult(true, WalletErrorCode.NONE, "Transaction completed.", transaction);
    }

    public static WalletTransactionResult failure(WalletErrorCode errorCode, String message) {
        return new WalletTransactionResult(false, errorCode, message, null);
    }
}
