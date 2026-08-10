package de.omegazirkel.risingworld.wallet;

/** Issues the Wallet-owned annual world-treasury budget with immutable per-year audit records. */
public final class WorldTreasuryService {
    public static final long STANDARD_ANNUAL_CAPITAL = 10_000L;
    public static final float STANDARD_GAME_TIME_SPEED = 2.5f;
    public static final int STANDARD_DAYS_PER_MONTH = 30;
    private static final String ANNUAL_CORRELATION_PREFIX = "world-treasury:annual:";

    private final WalletService walletService;
    private final String walletPluginName;

    public WorldTreasuryService(WalletService walletService, String walletPluginName) {
        this.walletService = walletService;
        this.walletPluginName = walletPluginName;
    }

    public WalletTransactionResult reconcile(String worldAccountId, int currentYear, float gameTimeSpeed,
            int daysPerMonth, String currencyIdentifier) {
        if (currentYear <= 0) return WalletTransactionResult.success(null);
        int paidYears = walletService.countSystemIssuances(worldAccountId, correlationPrefix(worldAccountId));
        if (paidYears < 0) {
            return WalletTransactionResult.failure(WalletErrorCode.DATABASE_ERROR,
                    "Could not read annual world treasury issuances.");
        }
        long annualAmount = annualCapital(gameTimeSpeed, daysPerMonth);
        for (int year = paidYears + 1; year <= currentYear; year++) {
            WalletTransactionResult issued = walletService.creditSystemAccountIdempotent(worldAccountId, annualAmount,
                    "Guthaben für Jahr " + year, currencyIdentifier, walletPluginName,
                    correlationPrefix(worldAccountId) + year);
            if (!issued.success) return issued;
        }
        return WalletTransactionResult.success(null);
    }

    static long annualCapital(float gameTimeSpeed, int daysPerMonth) {
        float speed = gameTimeSpeed > 0f ? gameTimeSpeed : STANDARD_GAME_TIME_SPEED;
        int days = daysPerMonth > 0 ? daysPerMonth : STANDARD_DAYS_PER_MONTH;
        double amount = STANDARD_ANNUAL_CAPITAL * (speed / STANDARD_GAME_TIME_SPEED)
                * ((double) days / STANDARD_DAYS_PER_MONTH);
        if (!Double.isFinite(amount) || amount > Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.max(1L, Math.round(amount));
    }

    private static String correlationPrefix(String worldAccountId) {
        return ANNUAL_CORRELATION_PREFIX + Integer.toUnsignedString(worldAccountId.hashCode()) + ":";
    }
}
