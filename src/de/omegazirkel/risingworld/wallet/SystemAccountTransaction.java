package de.omegazirkel.risingworld.wallet;

/** Immutable audit row for a system-account balance mutation. */
public final class SystemAccountTransaction {
    private final long id;
    private final String accountId;
    private final WalletCurrency currency;
    private final long delta;
    private final long resultingBalance;
    private final String pluginIdentifier;
    private final String reason;
    private final long createdAt;

    public SystemAccountTransaction(long id, String accountId, WalletCurrency currency, long delta,
            long resultingBalance, String pluginIdentifier, String reason, long createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.currency = currency;
        this.delta = delta;
        this.resultingBalance = resultingBalance;
        this.pluginIdentifier = pluginIdentifier;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public String getAccountId() { return accountId; }
    public WalletCurrency getCurrency() { return currency; }
    public long getDelta() { return delta; }
    public long getResultingBalance() { return resultingBalance; }
    public String getPluginIdentifier() { return pluginIdentifier; }
    public String getReason() { return reason; }
    public long getCreatedAt() { return createdAt; }
}
