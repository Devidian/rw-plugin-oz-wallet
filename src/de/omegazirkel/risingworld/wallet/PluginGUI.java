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
import de.omegazirkel.risingworld.tools.ui.OverlayBackPanel;
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

    public static final List<String> builtInIcons = Arrays.asList("icon-ki-coin-default", "icon-ki-coin-omega-silver",
            "icon-ki-coin-omega-gold", "icon-ki-oz-wallet");

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
        WalletOverlay overlay = new WalletOverlay(player, plugin);
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

    // TODO: refactor -> move into seperate file ui/WalletOverlay
    // TODO: refactor -> extend new BasePluginOverlayWithTabs from Tools to reduce code here
    private static class WalletOverlay extends OverlayBackPanel {
        private final Wallet plugin;
        private final WalletService service;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        private final OZUIElement panel;
        private final OZUIElement body;
        private final OZUIElement balancesTab;
        private final OZUIElement transactionsTab;
        private final OZUIElement adminTransactionsTab;
        private final OZUIElement globalBalancesTab;
        private final OZUIElement topBalancesTab;
        private String activeTab = "balances";

        WalletOverlay(Player player, Wallet plugin) {
            super(player);
            this.plugin = plugin;
            this.service = PluginGUI.getInstance().service;
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

            panel = new OZUIElement();
            panel.setPivot(Pivot.MiddleCenter);
            panel.setPosition(50, 50, true);
            panel.style.width.set(78, Unit.Percent);
            panel.style.height.set(560, Unit.Pixel);
            panel.setBackgroundColor(0, 0, 0, 0.86f);
            panel.setBorder(1);
            panel.setBorderColor(0.95f, 0.75f, 0.25f, 0.6f);
            addChild(panel);

            UILabel title = new UILabel(t().get("TC_WALLET_TITLE", uiPlayer));
            title.setFont(Font.DefaultBold);
            title.setFontSize(26);
            title.setTextAlign(TextAnchor.MiddleLeft);
            title.setPivot(Pivot.UpperLeft);
            title.setPosition(24, 20, false);
            title.setSize(360, 32, false);
            panel.addChild(title);

            balancesTab = tab(t().get("TC_WALLET_TAB_BALANCES", uiPlayer), 24, 82, 150, () -> showBalances());
            panel.addChild(balancesTab);

            transactionsTab = tab(t().get("TC_WALLET_TAB_TRANSACTIONS", uiPlayer), 174, 82, 185,
                    () -> showTransactions());
            panel.addChild(transactionsTab);

            if (uiPlayer.isAdmin()) {
                adminTransactionsTab = tab(t().get("TC_WALLET_TAB_ADMIN_TRANSACTIONS", uiPlayer), 359, 82, 160,
                        () -> showAdminTransactions());
                panel.addChild(adminTransactionsTab);

                globalBalancesTab = tab(t().get("TC_WALLET_TAB_GLOBAL_BALANCES", uiPlayer), 519, 82, 190,
                        () -> showGlobalBalances());
                panel.addChild(globalBalancesTab);

                topBalancesTab = tab(t().get("TC_WALLET_TAB_TOP_BALANCES", uiPlayer), 709, 82, 160,
                        () -> showTopBalances());
                panel.addChild(topBalancesTab);
            } else {
                adminTransactionsTab = null;
                globalBalancesTab = null;
                topBalancesTab = null;
            }

            OZUIElement closeButton = new OZUIElement();
            closeButton.setPivot(Pivot.UpperRight);
            closeButton.style.position.set(Position.Absolute);
            closeButton.style.right.set(0, Unit.Pixel);
            closeButton.style.top.set(20, Unit.Pixel);
            closeButton.setSize(34, 34, false);
            closeButton.setBorder(1);
            closeButton.setBorderColor(0.95f, 0.75f, 0.25f, 0.54f);
            closeButton.setBorderEdgeRadius(4, false);
            closeButton.setBackgroundColor(0.12f, 0.10f, 0.08f, 0.9f);
            closeButton.setHoverBackgroundColor(0x611F1AF2);
            closeButton.setClickable(true);
            closeButton.setClickAction(event -> PluginGUI.getInstance().closeWallet(player));
            UILabel closeLabel = new UILabel("X");
            closeLabel.setPivot(Pivot.MiddleCenter);
            closeLabel.setPosition(50, 50, true);
            closeLabel.setSize(100, 100, true);
            closeLabel.setFont(Font.DefaultBold);
            closeLabel.setFontSize(18);
            closeLabel.setTextAlign(TextAnchor.MiddleCenter);
            closeButton.addChild(closeLabel);
            panel.addChild(closeButton);

            body = new OZUIElement();
            body.setPivot(Pivot.UpperLeft);
            body.setPosition(24, 120, false);
            body.style.width.set(96, Unit.Percent);
            body.style.height.set(407, Unit.Pixel);
            body.setBackgroundColor(0.08f, 0.08f, 0.08f, 0.55f);
            body.setBorder(1);
            body.setBorderColor(0.95f, 0.75f, 0.25f, 0.48f);
            panel.addChild(body);

            showBalances();
        }

        private void showBalances() {
            activeTab = "balances";
            applyTabStyles();
            body.removeAllChilds();
            try {
                List<WalletBalance> balances = service.listBalancesForPlayer(
                        uiPlayer.getDbID(),
                        plugin.getSettings().defaultCurrencyIdentifier);
                if (balances.isEmpty()) {
                    body.addChild(message(t().get("TC_WALLET_EMPTY_BALANCES", uiPlayer)));
                    return;
                }

                UIScrollView scrollView = new UIScrollView(ScrollViewMode.Vertical);
                scrollView.setPivot(Pivot.UpperLeft);
                scrollView.setPosition(0, 0, false);
                scrollView.setSize(100, 100, true);
                scrollView.setMouseWheelScrollSize(32);

                OZUIElement content = new OZUIElement();
                content.setPivot(Pivot.UpperLeft);
                content.setPosition(0, 0, false);
                content.style.width.set(100, Unit.Percent);
                content.style.height.set(Math.max(407, ((balances.size() + 2) / 3) * 144), Unit.Pixel);
                content.style.display.set(DisplayStyle.Flex);
                content.style.flexDirection.set(FlexDirection.Row);
                content.style.flexWrap.set(Wrap.Wrap);
                scrollView.addChild(content);

                for (WalletBalance balance : balances) {
                    content.addChild(balanceCard(balance));
                }
                body.addChild(scrollView);
            } catch (SQLException ex) {
                Wallet.logger().error("Failed to render wallet balances: " + ex.getMessage());
                body.addChild(message(t().get("TC_WALLET_ERR_LOAD_BALANCES", uiPlayer)));
            }
        }

        private void showTransactions() {
            activeTab = "transactions";
            applyTabStyles();
            body.removeAllChilds();
            try {
                List<WalletTransaction> transactions = service.listLatestTransactions(uiPlayer.getDbID(), 100);
                if (transactions.isEmpty()) {
                    body.addChild(message(t().get("TC_WALLET_EMPTY_TRANSACTIONS", uiPlayer)));
                    return;
                }
                TableScrollView table = new TableScrollView(
                        Arrays.asList(
                                t().get("TC_WALLET_COL_AMOUNT", uiPlayer),
                                t().get("TC_WALLET_COL_CURRENCY", uiPlayer),
                                t().get("TC_WALLET_COL_SOURCE", uiPlayer),
                                t().get("TC_WALLET_COL_REASON", uiPlayer),
                                t().get("TC_WALLET_COL_DATE", uiPlayer)),
                        Arrays.asList(12f, 20f, 18f, 32f, 18f));
                table.setPosition(0, 0, false);
                table.style.width.set(100, Unit.Percent);
                table.setScrollBodyHeight(TABLE_SCROLL_BODY_HEIGHT);
                for (WalletTransaction tx : transactions) {
                    table.addRow(new TableRow(new ArrayList<>(Arrays.asList(
                            cell(formatDelta(tx.getDelta()), 12f),
                            cell(tx.getCurrency().getName(), 20f),
                            cell(tx.getPluginIdentifier(), 18f),
                            cell(tx.getReason(), 32f),
                            cell(dateFormat.format(new Date(tx.getCreatedAt())), 18f)))));
                }
                body.addChild(table.getRoot());
            } catch (SQLException ex) {
                Wallet.logger().error("Failed to render wallet transactions: " + ex.getMessage());
                body.addChild(message(t().get("TC_WALLET_ERR_LOAD_TRANSACTIONS", uiPlayer)));
            }
        }

        private void showAdminTransactions() {
            if (!uiPlayer.isAdmin()) {
                return;
            }
            activeTab = "adminTransactions";
            applyTabStyles();
            body.removeAllChilds();
            try {
                List<WalletTransaction> transactions = service.listLatestGlobalTransactions(plugin.getSettings().auditLogLimit);
                if (transactions.isEmpty()) {
                    body.addChild(message(t().get("TC_WALLET_EMPTY_TRANSACTIONS", uiPlayer)));
                    return;
                }
                TableScrollView table = new TableScrollView(
                        Arrays.asList(
                                t().get("TC_WALLET_COL_PLAYER", uiPlayer),
                                t().get("TC_WALLET_COL_AMOUNT", uiPlayer),
                                t().get("TC_WALLET_COL_CURRENCY", uiPlayer),
                                t().get("TC_WALLET_COL_SOURCE", uiPlayer),
                                t().get("TC_WALLET_COL_REASON", uiPlayer),
                                t().get("TC_WALLET_COL_DATE", uiPlayer)),
                        Arrays.asList(16f, 11f, 18f, 16f, 25f, 14f));
                table.setPosition(0, 0, false);
                table.style.width.set(100, Unit.Percent);
                table.setScrollBodyHeight(TABLE_SCROLL_BODY_HEIGHT);
                for (WalletTransaction tx : transactions) {
                    table.addRow(new TableRow(new ArrayList<>(Arrays.asList(
                            cell(playerName(tx.getPlayerDbId()), 16f),
                            cell(formatDelta(tx.getDelta()), 11f),
                            cell(tx.getCurrency().getName(), 18f),
                            cell(tx.getPluginIdentifier(), 16f),
                            cell(tx.getReason(), 25f),
                            cell(dateFormat.format(new Date(tx.getCreatedAt())), 14f)))));
                }
                body.addChild(table.getRoot());
            } catch (SQLException ex) {
                Wallet.logger().error("Failed to render wallet admin transactions: " + ex.getMessage());
                body.addChild(message(t().get("TC_WALLET_ERR_LOAD_TRANSACTIONS", uiPlayer)));
            }
        }

        private void showGlobalBalances() {
            if (!uiPlayer.isAdmin()) {
                return;
            }
            activeTab = "globalBalances";
            applyTabStyles();
            body.removeAllChilds();
            try {
                List<WalletBalance> balances = service.listGlobalBalances();
                if (balances.isEmpty()) {
                    body.addChild(message(t().get("TC_WALLET_EMPTY_GLOBAL_BALANCES", uiPlayer)));
                    return;
                }
                TableScrollView table = new TableScrollView(
                        Arrays.asList(
                                t().get("TC_WALLET_COL_CURRENCY", uiPlayer),
                                t().get("TC_WALLET_COL_IDENTIFIER", uiPlayer),
                                t().get("TC_WALLET_COL_GLOBAL_BALANCE", uiPlayer),
                                t().get("TC_WALLET_COL_SOURCE", uiPlayer)),
                        Arrays.asList(32f, 18f, 24f, 26f));
                table.setPosition(0, 0, false);
                table.style.width.set(100, Unit.Percent);
                table.setScrollBodyHeight(TABLE_SCROLL_BODY_HEIGHT);
                for (WalletBalance balance : balances) {
                    WalletCurrency currency = balance.getCurrency();
                    table.addRow(new TableRow(new ArrayList<>(Arrays.asList(
                            cell(currency.getName(), 32f),
                            cell(currency.getIdentifier(), 18f),
                            cell(Long.toString(balance.getBalance()), 24f),
                            cell(currency.getPluginIdentifier(), 26f)))));
                }
                body.addChild(table.getRoot());
            } catch (SQLException ex) {
                Wallet.logger().error("Failed to render wallet global balances: " + ex.getMessage());
                body.addChild(message(t().get("TC_WALLET_ERR_LOAD_BALANCES", uiPlayer)));
            }
        }

        private void showTopBalances() {
            if (!uiPlayer.isAdmin()) {
                return;
            }
            activeTab = "topBalances";
            applyTabStyles();
            body.removeAllChilds();
            try {
                List<WalletBalance> balances = service.listTopBalances(plugin.getSettings().defaultCurrencyIdentifier, 20);
                if (balances.isEmpty()) {
                    body.addChild(message(t().get("TC_WALLET_EMPTY_TOP_BALANCES", uiPlayer)));
                    return;
                }
                Set<Integer> playerDbIds = new LinkedHashSet<>();
                for (WalletBalance balance : balances) {
                    playerDbIds.add(balance.getPlayerDbId());
                }
                Map<Integer, PlayerRecord> playerRecords = PlayerDatabaseHelper.findPlayersByDbIds(plugin, playerDbIds);
                TableScrollView table = new TableScrollView(
                        Arrays.asList(
                                t().get("TC_WALLET_COL_RANK", uiPlayer),
                                t().get("TC_WALLET_COL_PLAYER", uiPlayer),
                                t().get("TC_WALLET_COL_AMOUNT", uiPlayer),
                                t().get("TC_WALLET_COL_CURRENCY", uiPlayer),
                                t().get("TC_WALLET_COL_IDENTIFIER", uiPlayer),
                                t().get("TC_WALLET_COL_SOURCE", uiPlayer)),
                        Arrays.asList(8f, 24f, 16f, 20f, 14f, 18f));
                table.setPosition(0, 0, false);
                table.style.width.set(100, Unit.Percent);
                table.setScrollBodyHeight(TABLE_SCROLL_BODY_HEIGHT);
                int rank = 1;
                for (WalletBalance balance : balances) {
                    WalletCurrency currency = balance.getCurrency();
                    table.addRow(new TableRow(new ArrayList<>(Arrays.asList(
                            cell("#" + rank, 8f),
                            cell(playerName(balance.getPlayerDbId(), playerRecords), 24f),
                            cell(Long.toString(balance.getBalance()), 16f),
                            cell(currency.getName(), 20f),
                            cell(currency.getIdentifier(), 14f),
                            cell(currency.getPluginIdentifier(), 18f)))));
                    rank++;
                }
                body.addChild(table.getRoot());
            } catch (SQLException ex) {
                Wallet.logger().error("Failed to render wallet top balances: " + ex.getMessage());
                body.addChild(message(t().get("TC_WALLET_ERR_LOAD_BALANCES", uiPlayer)));
            }
        }

        private OZUIElement tab(
                String text,
                float x,
                float y,
                float width,
                Runnable action) {
            OZUIElement tab = new OZUIElement();
            tab.setPivot(Pivot.UpperLeft);
            tab.setPosition(x, y, false);
            tab.setSize(width, 38, false);
            tab.setBorder(1);
            tab.setBorderEdgeRadius(4, false);
            tab.setClickable(true);
            tab.setClickAction(event -> action.run());

            UILabel label = new UILabel(text);
            label.setPivot(Pivot.MiddleCenter);
            label.setPosition(50, 50, true);
            label.setSize(100, 100, true);
            label.setFont(Font.DefaultBold);
            label.setFontSize(15);
            label.setTextAlign(TextAnchor.MiddleCenter);
            tab.addChild(label);
            return tab;
        }

        private void applyTabStyles() {
            styleTab(balancesTab, "balances".equals(activeTab));
            styleTab(transactionsTab, "transactions".equals(activeTab));
            styleAdminTab(adminTransactionsTab, "adminTransactions".equals(activeTab));
            styleAdminTab(globalBalancesTab, "globalBalances".equals(activeTab));
            styleAdminTab(topBalancesTab, "topBalances".equals(activeTab));
        }

        private void styleTab(OZUIElement tab, boolean active) {
            if (tab == null) {
                return;
            }
            if (active) {
                tab.setBackgroundColor(0.08f, 0.08f, 0.08f, 0.82f);
                tab.setBorderColor(0.95f, 0.75f, 0.25f, 0.74f);
            } else {
                tab.setBackgroundColor(0.10f, 0.10f, 0.10f, 0.38f);
                tab.setBorderColor(0.95f, 0.75f, 0.25f, 0.24f);
            }
        }

        private void styleAdminTab(OZUIElement tab, boolean active) {
            if (tab == null) {
                return;
            }
            if (active) {
                tab.setBackgroundColor(0.19f, 0.10f, 0.03f, 0.92f);
                tab.setBorderColor(1.0f, 0.48f, 0.12f, 0.86f);
            } else {
                tab.setBackgroundColor(0.16f, 0.07f, 0.03f, 0.58f);
                tab.setBorderColor(1.0f, 0.48f, 0.12f, 0.42f);
            }
        }

        private OZUIElement balanceCard(WalletBalance balance) {
            WalletCurrency currency = balance.getCurrency();
            OZUIElement card = new OZUIElement();
            card.setPivot(Pivot.UpperLeft);
            card.setSize(280, 124, false);
            card.setMargin(10f);
            card.setBackgroundColor(0.14f, 0.13f, 0.12f, 0.92f);
            card.setBorder(1);
            card.setBorderColor(0.95f, 0.75f, 0.25f, 0.26f);
            card.setBorderEdgeRadius(6, false);

            OZUIElement icon = new OZUIElement();
            icon.setPivot(Pivot.UpperLeft);
            icon.setPosition(16, 18, false);
            icon.setSize(54, 54, false);
            icon.style.backgroundImage.set(AssetManager.getIcon(currency.getIconKey()));
            icon.style.backgroundImageScaleMode.set(ScaleMode.ScaleToFit);
            card.addChild(icon);

            UILabel name = new UILabel(currency.getName());
            name.setPivot(Pivot.UpperLeft);
            name.setPosition(82, 14, false);
            name.style.width.set(178, Unit.Pixel);
            name.style.height.set(24, Unit.Pixel);
            name.setFont(Font.DefaultBold);
            name.setFontSize(17);
            name.setTextAlign(TextAnchor.MiddleLeft);
            name.setTextWrap(false);
            card.addChild(name);

            String sourceText = t().get("TC_WALLET_SOURCE_PREFIX", uiPlayer)
                    .replace("PH_SOURCE", currency.getPluginIdentifier());
            if (currency.isDefaultCurrency()) {
                sourceText += " " + t().get("TC_WALLET_DEFAULT_CURRENCY_HINT", uiPlayer);
            }
            UILabel source = new UILabel(sourceText);
            source.setPivot(Pivot.UpperLeft);
            source.setPosition(82, 43, false);
            source.style.width.set(178, Unit.Pixel);
            source.style.height.set(38, Unit.Pixel);
            source.setFont(Font.Default);
            source.setFontSize(13);
            source.setTextAlign(TextAnchor.MiddleLeft);
            source.setTextWrap(true);
            card.addChild(source);

            UILabel amount = new UILabel(balance.getBalance() + " " + currency.getIdentifier());
            amount.setPivot(Pivot.UpperLeft);
            amount.setPosition(82, 86, false);
            // amount.style.position.set(Position.Absolute);
            // amount.style.right.set(18, Unit.Pixel);
            // amount.style.bottom.set(16, Unit.Pixel);
            amount.style.width.set(178, Unit.Pixel);
            amount.style.height.set(30, Unit.Pixel);
            // amount.setBackgroundColor(0,0,0,1);
            amount.setFont(Font.DefaultBold);
            amount.setFontSize(20);
            amount.setTextAlign(TextAnchor.MiddleRight);
            amount.setTextWrap(false);
            card.addChild(amount);

            return card;
        }

        private TableCell cell(String text, float width) {
            UILabel label = new UILabel(text == null ? "" : text);
            label.setFont(Font.Default);
            label.setFontSize(13);
            label.setTextWrap(false);
            label.setTextAlign(TextAnchor.MiddleLeft);
            return new TableCell(label, width);
        }

        private UILabel message(String text) {
            UILabel label = new UILabel(text);
            label.setPivot(Pivot.MiddleCenter);
            label.setPosition(50, 50, true);
            label.setFont(Font.DefaultBold);
            label.setFontSize(18);
            label.setTextAlign(TextAnchor.MiddleCenter);
            return label;
        }

        private String formatDelta(long delta) {
            return delta > 0 ? "+" + delta : Long.toString(delta);
        }

        private String playerName(int playerDbId) {
            String name = Server.getLastKnownPlayerName(playerDbId);
            return name == null || name.isBlank() ? "#" + playerDbId : name;
        }

        private String playerName(int playerDbId, Map<Integer, PlayerRecord> playerRecords) {
            String serverName = Server.getLastKnownPlayerName(playerDbId);
            if (serverName != null && !serverName.isBlank()) {
                return serverName;
            }
            PlayerRecord record = playerRecords.get(playerDbId);
            if (record != null && record.name != null && !record.name.isBlank()) {
                return record.name;
            }
            return "#" + playerDbId;
        }
    }

}
