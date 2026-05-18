package de.omegazirkel.risingworld.wallet.ui;

import de.omegazirkel.risingworld.Wallet;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.BasePlayerPluginSettingsPanel;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginSettings;
import net.risingworld.api.objects.Player;

public class WalletPlayerPluginSettings extends PlayerPluginSettings {
    public static final String SHOW_WALLET_HUD_KEY = "oz.wallet.showWalletHud";

    public WalletPlayerPluginSettings(String pluginVersion) {
        this.pluginLabel = Wallet.name;
        this.pluginVersion = pluginVersion;
    }

    private I18n t() {
        return I18n.getInstance(Wallet.name);
    }

    @Override
    public BasePlayerPluginSettingsPanel createPlayerPluginSettingsUIElement(Player uiPlayer) {
        return new BasePlayerPluginSettingsPanel(uiPlayer, pluginLabel) {
            @Override
            protected void redrawContent() {
                flexWrapper.removeAllChilds();
                flexWrapper.addChild(playerSettingShowWalletHud(uiPlayer));
            }

            protected OZUIElement playerSettingShowWalletHud(Player uiPlayer) {
                OZUIElement element = defaultSettingsContainer();
                element.addChild(defaultSettingsLabel(t().get("TC_LABEL_SHOW_WALLET_HUD", uiPlayer)));

                boolean currentValue = Wallet.isWalletHudEnabled(uiPlayer);
                uiPlayer.setAttribute(SHOW_WALLET_HUD_KEY, currentValue);

                element.addChild(switchButtons(uiPlayer, currentValue, event -> {
                    boolean nextValue = !Wallet.isWalletHudEnabled(uiPlayer);
                    if (Wallet.playerSettings() != null) {
                        Wallet.playerSettings().setBoolean(uiPlayer.getDbID(), SHOW_WALLET_HUD_KEY, nextValue);
                    }
                    uiPlayer.setAttribute(SHOW_WALLET_HUD_KEY, nextValue);
                    Wallet.syncWalletHud(uiPlayer);
                    redrawContent();
                }, t().get("TC_BTN_WALLET_HUD_OFF", uiPlayer), t().get("TC_BTN_WALLET_HUD_ON", uiPlayer)));
                return element;
            }
        };
    }
}
