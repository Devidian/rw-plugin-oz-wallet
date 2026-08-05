package de.omegazirkel.risingworld.wallet;

import java.util.List;

/** Stable public result for system-account balance queries. */
public final class SystemAccountBalancesResult {
    public final boolean success;
    public final WalletErrorCode errorCode;
    public final String message;
    public final List<SystemAccountBalance> balances;

    private SystemAccountBalancesResult(boolean success, WalletErrorCode errorCode, String message,
            List<SystemAccountBalance> balances) {
        this.success = success;
        this.errorCode = errorCode;
        this.message = message;
        this.balances = balances;
    }

    public static SystemAccountBalancesResult success(List<SystemAccountBalance> balances) {
        return new SystemAccountBalancesResult(true, WalletErrorCode.NONE, "System balances loaded.",
                List.copyOf(balances));
    }

    public static SystemAccountBalancesResult failure(WalletErrorCode errorCode, String message) {
        return new SystemAccountBalancesResult(false, errorCode, message, List.of());
    }
}
