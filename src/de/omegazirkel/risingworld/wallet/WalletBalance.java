package de.omegazirkel.risingworld.wallet;

public class WalletBalance {
    private final int playerDbId;
    private final WalletCurrency currency;
    private final long balance;
    private final long updatedAt;

    public WalletBalance(int playerDbId, WalletCurrency currency, long balance, long updatedAt) {
        this.playerDbId = playerDbId;
        this.currency = currency;
        this.balance = balance;
        this.updatedAt = updatedAt;
    }

    public int getPlayerDbId() {
        return playerDbId;
    }

    public WalletCurrency getCurrency() {
        return currency;
    }

    public long getBalance() {
        return balance;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
