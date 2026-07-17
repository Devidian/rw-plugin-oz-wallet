package de.omegazirkel.risingworld.wallet;

/** Stable public result for {@code Wallet.transferIdempotent}. */
public final class WalletTransferResult {
    public final boolean success;
    public final WalletErrorCode errorCode;
    public final String message;
    public final WalletTransfer transfer;

    private WalletTransferResult(boolean success, WalletErrorCode errorCode, String message, WalletTransfer transfer) {
        this.success = success;
        this.errorCode = errorCode;
        this.message = message;
        this.transfer = transfer;
    }

    public static WalletTransferResult success(WalletTransfer transfer) {
        return new WalletTransferResult(true, WalletErrorCode.NONE, "Transfer completed.", transfer);
    }

    public static WalletTransferResult failure(WalletErrorCode errorCode, String message) {
        return new WalletTransferResult(false, errorCode, message, null);
    }
}
