package de.omegazirkel.risingworld.wallet;

import de.omegazirkel.risingworld.Wallet;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProvider;
import net.risingworld.api.objects.Player;

public class WalletPluginInfoStatusProvider implements PluginInfoStatusProvider {
    private final Wallet plugin;
    private final String pluginName;
    private final String version;

    public WalletPluginInfoStatusProvider(Wallet plugin, String version) {
        this.plugin = plugin;
        this.pluginName = Wallet.name == null || Wallet.name.isBlank() ? "OZ - Wallet" : Wallet.name;
        this.version = version == null ? "" : version;
    }

    @Override
    public String getPluginName() {
        return pluginName;
    }

    @Override
    public String getInfo(Player player) {
        PluginSettings settings = PluginSettings.getInstance();
        return t().get("TC_WALLET_INFO_PANEL_INFO", player)
                .replace("PH_PLUGIN_NAME", pluginName)
                .replace("PH_VERSION", version)
                .replace("PH_PLUGIN_CMD", settings.walletCommand);
    }

    @Override
    public String getStatus(Player player) {
        PluginSettings settings = PluginSettings.getInstance();
        return t().get("TC_WALLET_INFO_PANEL_STATUS", player)
                .replace("PH_DATABASE_STATUS", plugin.databaseAvailable() ? "available" : "missing")
                .replace("PH_DEFAULT_CURRENCY", plugin.defaultCurrencyIdentifier())
                .replace("PH_DEFAULT_CURRENCY_NAME", settings.defaultCurrencyName)
                .replace("PH_WALLET_HUD", String.valueOf(Wallet.isWalletHudEnabled(player)))
                .replace("PH_WELCOME_BONUS", String.valueOf(settings.welcomeBonusEnabled))
                .replace("PH_WELCOME_BONUS_AMOUNT", String.valueOf(settings.welcomeBonusAmount))
                .replace("PH_AUDIT_LOG_LIMIT", String.valueOf(settings.auditLogLimit));
    }

    private I18n t() {
        return I18n.getInstance(plugin);
    }
}
