package de.omegazirkel.risingworld.wallet;

import java.util.List;

public class WalletCurrenciesResult {
    public final boolean success;
    public final WalletErrorCode errorCode;
    public final String message;
    public final List<WalletCurrency> currencies;

    private WalletCurrenciesResult(
            boolean success,
            WalletErrorCode errorCode,
            String message,
            List<WalletCurrency> currencies) {
        this.success = success;
        this.errorCode = errorCode;
        this.message = message;
        this.currencies = currencies == null ? List.of() : List.copyOf(currencies);
    }

    public static WalletCurrenciesResult success(List<WalletCurrency> currencies) {
        return new WalletCurrenciesResult(true, WalletErrorCode.NONE, "Currencies loaded.", currencies);
    }

    public static WalletCurrenciesResult failure(WalletErrorCode errorCode, String message) {
        return new WalletCurrenciesResult(false, errorCode, message, List.of());
    }
}
