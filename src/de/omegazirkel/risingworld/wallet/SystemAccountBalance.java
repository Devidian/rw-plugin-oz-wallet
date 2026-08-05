package de.omegazirkel.risingworld.wallet;

/** One currency balance held by a system account. */
public final class SystemAccountBalance {
    private final String accountId;
    private final WalletCurrency currency;
    private final long balance;
    private final long updatedAt;

    public SystemAccountBalance(String accountId, WalletCurrency currency, long balance, long updatedAt) {
        this.accountId = accountId;
        this.currency = currency;
        this.balance = balance;
        this.updatedAt = updatedAt;
    }

    public String getAccountId() { return accountId; }
    public WalletCurrency getCurrency() { return currency; }
    public long getBalance() { return balance; }
    public long getUpdatedAt() { return updatedAt; }
}
