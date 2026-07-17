package de.omegazirkel.risingworld.wallet;

/** Immutable result of one idempotent, atomic Wallet transfer. */
public final class WalletTransfer {
    private final String correlationId;
    private final int payerDbId;
    private final int payeeDbId;
    private final WalletCurrency currency;
    private final long amount;
    private final long debitTransactionId;
    private final long creditTransactionId;
    private final String pluginIdentifier;
    private final String reason;
    private final long createdAt;

    public WalletTransfer(String correlationId, int payerDbId, int payeeDbId, WalletCurrency currency, long amount,
            long debitTransactionId, long creditTransactionId, String pluginIdentifier, String reason, long createdAt) {
        this.correlationId = correlationId;
        this.payerDbId = payerDbId;
        this.payeeDbId = payeeDbId;
        this.currency = currency;
        this.amount = amount;
        this.debitTransactionId = debitTransactionId;
        this.creditTransactionId = creditTransactionId;
        this.pluginIdentifier = pluginIdentifier;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public String getCorrelationId() { return correlationId; }
    public int getPayerDbId() { return payerDbId; }
    public int getPayeeDbId() { return payeeDbId; }
    public WalletCurrency getCurrency() { return currency; }
    public long getAmount() { return amount; }
    public long getDebitTransactionId() { return debitTransactionId; }
    public long getCreditTransactionId() { return creditTransactionId; }
    public String getPluginIdentifier() { return pluginIdentifier; }
    public String getReason() { return reason; }
    public long getCreatedAt() { return createdAt; }
}
