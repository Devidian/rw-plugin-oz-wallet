package de.omegazirkel.risingworld.wallet;

/** Stable public result for transfers involving a system account. */
public final class AccountTransferResult {
    public final boolean success;
    public final WalletErrorCode errorCode;
    public final String message;
    public final AccountTransfer transfer;

    private AccountTransferResult(boolean success, WalletErrorCode errorCode, String message,
            AccountTransfer transfer) {
        this.success = success;
        this.errorCode = errorCode;
        this.message = message;
        this.transfer = transfer;
    }

    public static AccountTransferResult success(AccountTransfer transfer) {
        return new AccountTransferResult(true, WalletErrorCode.NONE, "Transfer completed.", transfer);
    }

    public static AccountTransferResult failure(WalletErrorCode errorCode, String message) {
        return new AccountTransferResult(false, errorCode, message, null);
    }
}
