package de.omegazirkel.risingworld.wallet;

public class WalletTransaction {
    private final long id;
    private final int playerDbId;
    private final WalletCurrency currency;
    private final long delta;
    private final long resultingBalance;
    private final String pluginIdentifier;
    private final String reason;
    private final long createdAt;

    public WalletTransaction(
            long id,
            int playerDbId,
            WalletCurrency currency,
            long delta,
            long resultingBalance,
            String pluginIdentifier,
            String reason,
            long createdAt) {
        this.id = id;
        this.playerDbId = playerDbId;
        this.currency = currency;
        this.delta = delta;
        this.resultingBalance = resultingBalance;
        this.pluginIdentifier = pluginIdentifier;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public int getPlayerDbId() {
        return playerDbId;
    }

    public WalletCurrency getCurrency() {
        return currency;
    }

    public long getDelta() {
        return delta;
    }

    public long getResultingBalance() {
        return resultingBalance;
    }

    public String getPluginIdentifier() {
        return pluginIdentifier;
    }

    public String getReason() {
        return reason;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
