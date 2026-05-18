package de.omegazirkel.risingworld.wallet.ui;

import de.omegazirkel.risingworld.Wallet;
import de.omegazirkel.risingworld.tools.ui.BasePlayerPluginDataPanel;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginData;
import net.risingworld.api.objects.Player;

public class WalletPlayerPluginData extends PlayerPluginData {

    public WalletPlayerPluginData(String pluginVersion) {
        this.pluginLabel = Wallet.name;
        this.pluginVersion = pluginVersion;
    }

    @Override
    public BasePlayerPluginDataPanel createPlayerPluginDataUIElement(Player uiPlayer) {
        return new BasePlayerPluginDataPanel(uiPlayer, pluginLabel) {
            @Override
            protected void redrawContent() {
                flexWrapper.removeAllChilds();
                flexWrapper.addChild(defaultEmptyStateLabel());
            }
        };
    }
}
