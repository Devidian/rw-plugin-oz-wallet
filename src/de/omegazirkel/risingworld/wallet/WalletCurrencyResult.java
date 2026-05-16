package de.omegazirkel.risingworld.wallet;

public class WalletCurrencyResult {
    public final boolean success;
    public final WalletErrorCode errorCode;
    public final String message;
    public final WalletCurrency currency;

    private WalletCurrencyResult(boolean success, WalletErrorCode errorCode, String message, WalletCurrency currency) {
        this.success = success;
        this.errorCode = errorCode;
        this.message = message;
        this.currency = currency;
    }

    public static WalletCurrencyResult success(WalletCurrency currency) {
        return new WalletCurrencyResult(true, WalletErrorCode.NONE, "Currency registered.", currency);
    }

    public static WalletCurrencyResult failure(WalletErrorCode errorCode, String message) {
        return new WalletCurrencyResult(false, errorCode, message, null);
    }
}
