package de.omegazirkel.risingworld.wallet;

public enum WalletErrorCode {
    NONE,
    INVALID_ARGUMENT,
    CURRENCY_ALREADY_REGISTERED,
    UNKNOWN_CURRENCY,
    INSUFFICIENT_FUNDS,
    IDEMPOTENCY_CONFLICT,
    DATABASE_ERROR
}
