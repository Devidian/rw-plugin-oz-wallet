package de.omegazirkel.risingworld.wallet;

/** Immutable public description of a non-player Wallet account. */
public final class SystemAccount {
    private final String accountId;
    private final String ownerPlugin;
    private final String accountType;
    private final String displayName;
    private final String status;
    private final long createdAt;
    private final long updatedAt;

    public SystemAccount(String accountId, String ownerPlugin, String accountType, String displayName, String status,
            long createdAt, long updatedAt) {
        this.accountId = accountId;
        this.ownerPlugin = ownerPlugin;
        this.accountType = accountType;
        this.displayName = displayName;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getAccountId() { return accountId; }
    public String getOwnerPlugin() { return ownerPlugin; }
    public String getAccountType() { return accountType; }
    public String getDisplayName() { return displayName; }
    public String getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public boolean isActive() { return "ACTIVE".equals(status); }
}
