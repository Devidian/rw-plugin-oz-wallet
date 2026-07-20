package de.omegazirkel.risingworld.wallet;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import de.omegazirkel.risingworld.Wallet;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.PlayerDatabaseHelper;
import de.omegazirkel.risingworld.tools.PlayerDatabaseHelper.PlayerRecord;
import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ui.CursorManager;
import de.omegazirkel.risingworld.wallet.ui.WalletOverlay;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import de.omegazirkel.risingworld.tools.ui.table.TableCell;
import de.omegazirkel.risingworld.tools.ui.table.TableRow;
import de.omegazirkel.risingworld.tools.ui.table.TableScrollView;
import net.risingworld.api.Server;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.UIScrollView;
import net.risingworld.api.ui.UIScrollView.ScrollViewMode;
import net.risingworld.api.ui.style.Align;
import net.risingworld.api.ui.style.DisplayStyle;
import net.risingworld.api.ui.style.FlexDirection;
import net.risingworld.api.ui.style.Font;
import net.risingworld.api.ui.style.Justify;
import net.risingworld.api.ui.style.Pivot;
import net.risingworld.api.ui.style.Position;
import net.risingworld.api.ui.style.ScaleMode;
import net.risingworld.api.ui.style.TextAnchor;
import net.risingworld.api.ui.style.Unit;
import net.risingworld.api.ui.style.Wrap;

public class PluginGUI {
    private static final String OVERLAY_ATTRIBUTE = "wallet.ui.overlay";
    private static final float TABLE_SCROLL_BODY_HEIGHT = 368f;
    private static PluginGUI instance = null;
    private Wallet plugin;
    private WalletService service;

    public static final List<String> builtInIcons = Arrays.asList("coin-default", "coin-omega-silver",
            "coin-omega-gold", "oz-wallet");

    private PluginGUI() {
    }

    public static PluginGUI getInstance(Wallet p, WalletService service) {
        for (String key : builtInIcons) {
            try {
                AssetManager.loadIconFromPlugin(p, key);

            } catch (Exception ex) {
                Wallet.logger().error("Failed to load icon <" + key + ">: " + ex.getMessage());
            }
        }

        PluginGUI gui = getInstance();
        gui.plugin = p;
        gui.service = service;
        return gui;
    }

    public static PluginGUI getInstance() {
        if (instance == null) {
            instance = new PluginGUI();
        }
        return instance;
    }

    public void openWallet(Player player) {
        if (plugin == null || service == null) {
            player.sendTextMessage(t().get("TC_WALLET_ERR_DATABASE_UNAVAILABLE", player));
            return;
        }
        closeWallet(player);
        WalletOverlay overlay = new WalletOverlay(player, plugin, service);
        CursorManager.show(player);
        player.addUIElement(overlay);
        player.setAttribute(OVERLAY_ATTRIBUTE, overlay);
    }

    public void closeWallet(Player player) {
        Object existing = player.getAttribute(OVERLAY_ATTRIBUTE);
        if (existing instanceof WalletOverlay overlay) {
            player.removeUIElement(overlay);
            player.deleteAttribute(OVERLAY_ATTRIBUTE);
            CursorManager.hide(player);
        }
    }

    private static I18n t() {
        return I18n.getInstance(Wallet.name);
    }


}
