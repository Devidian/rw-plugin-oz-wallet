package de.omegazirkel.risingworld.wallet;

public class WalletCurrency {
    private final String identifier;
    private final String name;
    private final String iconKey;
    private final String pluginIdentifier;
    private final long registeredAt;
    private final boolean defaultCurrency;

    public WalletCurrency(
            String identifier,
            String name,
            String iconKey,
            String pluginIdentifier,
            long registeredAt,
            boolean defaultCurrency) {
        this.identifier = identifier;
        this.name = name;
        this.iconKey = iconKey;
        this.pluginIdentifier = pluginIdentifier;
        this.registeredAt = registeredAt;
        this.defaultCurrency = defaultCurrency;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getName() {
        return name;
    }

    public String getIconKey() {
        if (iconKey == null)
            return "icon-ki-coin-default";
        return iconKey;
    }

    public String getPluginIdentifier() {
        return pluginIdentifier;
    }

    public long getRegisteredAt() {
        return registeredAt;
    }

    public boolean isDefaultCurrency() {
        return defaultCurrency;
    }
}
