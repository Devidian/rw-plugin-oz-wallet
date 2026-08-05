package de.omegazirkel.risingworld.wallet;

/** Immutable result of an idempotent transfer involving a system account. */
public final class AccountTransfer {
    private final String correlationId;
    private final String payerKind;
    private final String payerReference;
    private final String payeeKind;
    private final String payeeReference;
    private final WalletCurrency currency;
    private final long amount;
    private final long debitTransactionId;
    private final long creditTransactionId;
    private final String pluginIdentifier;
    private final String reason;
    private final long createdAt;

    public AccountTransfer(String correlationId, String payerKind, String payerReference, String payeeKind,
            String payeeReference, WalletCurrency currency, long amount, long debitTransactionId,
            long creditTransactionId, String pluginIdentifier, String reason, long createdAt) {
        this.correlationId = correlationId;
        this.payerKind = payerKind;
        this.payerReference = payerReference;
        this.payeeKind = payeeKind;
        this.payeeReference = payeeReference;
        this.currency = currency;
        this.amount = amount;
        this.debitTransactionId = debitTransactionId;
        this.creditTransactionId = creditTransactionId;
        this.pluginIdentifier = pluginIdentifier;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public String getCorrelationId() { return correlationId; }
    public String getPayerKind() { return payerKind; }
    public String getPayerReference() { return payerReference; }
    public String getPayeeKind() { return payeeKind; }
    public String getPayeeReference() { return payeeReference; }
    public WalletCurrency getCurrency() { return currency; }
    public long getAmount() { return amount; }
    public long getDebitTransactionId() { return debitTransactionId; }
    public long getCreditTransactionId() { return creditTransactionId; }
    public String getPluginIdentifier() { return pluginIdentifier; }
    public String getReason() { return reason; }
    public long getCreatedAt() { return createdAt; }
}
