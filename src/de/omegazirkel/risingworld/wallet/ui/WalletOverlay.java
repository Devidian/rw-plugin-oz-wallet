package de.omegazirkel.risingworld.wallet.ui;

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
import de.omegazirkel.risingworld.wallet.PluginGUI;
import de.omegazirkel.risingworld.wallet.WalletBalance;
import de.omegazirkel.risingworld.wallet.WalletCurrency;
import de.omegazirkel.risingworld.wallet.WalletService;
import de.omegazirkel.risingworld.wallet.WalletTransaction;
import de.omegazirkel.risingworld.wallet.SystemAccount;
import de.omegazirkel.risingworld.wallet.SystemAccountBalance;
import de.omegazirkel.risingworld.wallet.SystemAccountBalancesResult;
import de.omegazirkel.risingworld.wallet.SystemAccountTransaction;
import de.omegazirkel.risingworld.wallet.SystemAccountTransactionsResult;
import de.omegazirkel.risingworld.wallet.SystemAccountsResult;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.PlayerDatabaseHelper;
import de.omegazirkel.risingworld.tools.PlayerDatabaseHelper.PlayerRecord;
import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ui.AdvancedButton;
import de.omegazirkel.risingworld.tools.ui.AdvancedButtonFactory;
import de.omegazirkel.risingworld.tools.ui.BasePluginOverlayWithTabs;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import de.omegazirkel.risingworld.tools.ui.table.TableCell;
import de.omegazirkel.risingworld.tools.ui.table.TableRow;
import de.omegazirkel.risingworld.tools.ui.table.TableScrollView;
import net.risingworld.api.Server;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.UIScrollView;
import net.risingworld.api.ui.UITextField;
import net.risingworld.api.ui.UIScrollView.ScrollViewMode;
import net.risingworld.api.ui.style.DisplayStyle;
import net.risingworld.api.ui.style.FlexDirection;
import net.risingworld.api.ui.style.Font;
import net.risingworld.api.ui.style.Pivot;
import net.risingworld.api.ui.style.ScaleMode;
import net.risingworld.api.ui.style.TextAnchor;
import net.risingworld.api.ui.style.Unit;
import net.risingworld.api.ui.style.Wrap;

public class WalletOverlay extends BasePluginOverlayWithTabs {
    private static final float TABLE_SCROLL_BODY_HEIGHT = 368f;

    private final Wallet plugin;
    private final WalletService service;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private String activeWalletTab = "balances";
    private String systemAccountSearch = "";
    private int systemAccountOffset;
    private String systemAccountDetailId;
    private static final int SYSTEM_ACCOUNT_PAGE_SIZE = 50;

    public WalletOverlay(Player player, Wallet plugin, WalletService service) {
        super(player, p -> { });
        this.plugin = plugin;
        this.service = service;
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        rebuild();
    }

    @Override
    protected I18n t() {
        return I18n.getInstance(Wallet.name);
    }

    @Override
    protected String titleText() {
        return t().get("TC_WALLET_TITLE", uiPlayer);
    }

    @Override
    protected String descriptionText() {
        return "";
    }

    @Override
    protected String legendText() {
        return "";
    }

    @Override
    protected void setupTabs() {
        setupTabContainer();
        addTab(t().get("TC_WALLET_TAB_BALANCES", uiPlayer), 150, "balances".equals(activeWalletTab), () -> selectTab("balances"));
        addTab(t().get("TC_WALLET_TAB_TRANSACTIONS", uiPlayer), 185, "transactions".equals(activeWalletTab), () -> selectTab("transactions"));
        if (uiPlayer.isAdmin()) {
            addTab(t().get("TC_WALLET_TAB_ADMIN_TRANSACTIONS", uiPlayer), 160, "adminTransactions".equals(activeWalletTab), true, () -> selectTab("adminTransactions"));
            addTab(t().get("TC_WALLET_TAB_GLOBAL_BALANCES", uiPlayer), 190, "globalBalances".equals(activeWalletTab), true, () -> selectTab("globalBalances"));
            addTab(t().get("TC_WALLET_TAB_TOP_BALANCES", uiPlayer), 160, "topBalances".equals(activeWalletTab), true, () -> selectTab("topBalances"));
            addTab(t().get("TC_WALLET_TAB_SYSTEM_ACCOUNTS", uiPlayer), 150,
                    "systemAccounts".equals(activeWalletTab), true, () -> selectTab("systemAccounts"));
        }
        showActiveTab();
    }

    private void selectTab(String tab) {
        activeWalletTab = tab;
        rebuild();
    }

    private void showActiveTab() {
        if ("transactions".equals(activeWalletTab)) showTransactions();
        else if ("adminTransactions".equals(activeWalletTab)) showAdminTransactions();
        else if ("globalBalances".equals(activeWalletTab)) showGlobalBalances();
        else if ("topBalances".equals(activeWalletTab)) showTopBalances();
        else if ("systemAccounts".equals(activeWalletTab)) showSystemAccounts();
        else showBalances();
    }

    @Override
    protected void close() {
        PluginGUI.getInstance().closeWallet(uiPlayer);
    }

    private void showBalances() {
        activeWalletTab = "balances";
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
        activeWalletTab = "transactions";
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
        activeWalletTab = "adminTransactions";
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
        activeWalletTab = "globalBalances";
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
        activeWalletTab = "topBalances";
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

    private void showSystemAccounts() {
        if (!uiPlayer.isAdmin()) return;
        activeWalletTab = "systemAccounts";
        body.removeAllChilds();
        if (systemAccountDetailId != null) {
            showSystemAccountTransactions(systemAccountDetailId);
            return;
        }

        UITextField search = new UITextField(systemAccountSearch);
        search.setPivot(Pivot.UpperLeft);
        search.setPosition(12, 12, false);
        search.setSize(360, 34, false);
        search.setMaxCharacters(120);
        search.setBackgroundColor(0.02f, 0.02f, 0.02f, 0.78f);
        search.setBorder(1);
        search.setBorderColor(0.95f, 0.75f, 0.25f, 0.46f);
        body.addChild(search);

        AdvancedButton searchButton = actionButton(t().get("TC_WALLET_SEARCH", uiPlayer), () ->
                search.getCurrentText(uiPlayer, value -> {
                    systemAccountSearch = value == null ? "" : value.trim();
                    systemAccountOffset = 0;
                    rebuild();
                }));
        searchButton.setPosition(382, 12, false);
        searchButton.setSize(120, 34, false);
        body.addChild(searchButton);

        SystemAccountsResult accounts = service.listSystemAccounts(systemAccountSearch, systemAccountOffset,
                SYSTEM_ACCOUNT_PAGE_SIZE);
        if (!accounts.success) {
            body.addChild(message(t().get("TC_WALLET_ERR_LOAD_SYSTEM_ACCOUNTS", uiPlayer)));
            return;
        }
        if (accounts.accounts.isEmpty()) {
            body.addChild(message(t().get("TC_WALLET_EMPTY_SYSTEM_ACCOUNTS", uiPlayer)));
            return;
        }

        TableScrollView table = new TableScrollView(
                Arrays.asList(t().get("TC_WALLET_COL_ACCOUNT_ID", uiPlayer),
                        t().get("TC_WALLET_COL_ACCOUNT", uiPlayer),
                        t().get("TC_WALLET_COL_TYPE", uiPlayer), t().get("TC_WALLET_COL_SOURCE", uiPlayer),
                        t().get("TC_WALLET_COL_STATUS", uiPlayer), t().get("TC_WALLET_COL_AMOUNT", uiPlayer),
                        t().get("TC_WALLET_COL_ACTIONS", uiPlayer)),
                Arrays.asList(18f, 20f, 10f, 14f, 10f, 14f, 14f));
        table.setPosition(12, 56, false);
        table.style.width.set(98, Unit.Percent);
        table.setScrollBodyHeight(300f);
        for (SystemAccount account : accounts.accounts) {
            table.addRow(new TableRow(new ArrayList<>(Arrays.asList(
                    cell(account.getAccountId(), 18f), cell(account.getDisplayName(), 20f),
                    cell(account.getAccountType(), 10f), cell(account.getOwnerPlugin(), 14f),
                    cell(account.getStatus(), 10f), cell(systemBalanceSummary(account.getAccountId()), 14f),
                    new TableCell(detailButton(account.getAccountId()), 14f)))));
        }
        body.addChild(table.getRoot());

        int page = systemAccountOffset / SYSTEM_ACCOUNT_PAGE_SIZE + 1;
        int pages = Math.max(1, (accounts.total + SYSTEM_ACCOUNT_PAGE_SIZE - 1) / SYSTEM_ACCOUNT_PAGE_SIZE);
        if (pages <= 1) return;
        UILabel pageLabel = new UILabel(t().get("TC_WALLET_PAGE", uiPlayer)
                .replace("PH_PAGE", Integer.toString(page)).replace("PH_PAGES", Integer.toString(pages)));
        pageLabel.setPivot(Pivot.UpperCenter);
        pageLabel.setPosition(50, 0, true);
        pageLabel.style.top.set(398, Unit.Pixel);
        pageLabel.setSize(180, 32, false);
        pageLabel.setTextAlign(TextAnchor.MiddleCenter);
        body.addChild(pageLabel);
        if (systemAccountOffset > 0) {
            AdvancedButton previous = actionButton("<", () -> {
                systemAccountOffset = Math.max(0, systemAccountOffset - SYSTEM_ACCOUNT_PAGE_SIZE);
                rebuild();
            });
            previous.setPosition(38, 0, true);
            previous.style.top.set(398, Unit.Pixel);
            body.addChild(previous);
        }
        if (systemAccountOffset + SYSTEM_ACCOUNT_PAGE_SIZE < accounts.total) {
            AdvancedButton next = actionButton(">", () -> {
                systemAccountOffset += SYSTEM_ACCOUNT_PAGE_SIZE;
                rebuild();
            });
            next.setPosition(62, 0, true);
            next.style.top.set(398, Unit.Pixel);
            body.addChild(next);
        }
    }

    private void showSystemAccountTransactions(String accountId) {
        AdvancedButton back = actionButton(t().get("TC_WALLET_BACK", uiPlayer), () -> {
            systemAccountDetailId = null;
            rebuild();
        });
        back.setPosition(12, 12, false);
        back.setSize(120, 34, false);
        body.addChild(back);

        SystemAccountTransactionsResult result = service.systemAccountTransactions(accountId, 100);
        if (!result.success || result.transactions.isEmpty()) {
            body.addChild(message(t().get(result.success ? "TC_WALLET_EMPTY_SYSTEM_TRANSACTIONS"
                    : "TC_WALLET_ERR_LOAD_TRANSACTIONS", uiPlayer)));
            return;
        }
        TableScrollView table = new TableScrollView(
                Arrays.asList(t().get("TC_WALLET_COL_AMOUNT", uiPlayer),
                        t().get("TC_WALLET_COL_CURRENCY", uiPlayer), t().get("TC_WALLET_COL_SOURCE", uiPlayer),
                        t().get("TC_WALLET_COL_REASON", uiPlayer), t().get("TC_WALLET_COL_DATE", uiPlayer)),
                Arrays.asList(12f, 18f, 18f, 34f, 18f));
        table.setPosition(12, 56, false);
        table.style.width.set(98, Unit.Percent);
        table.setScrollBodyHeight(330f);
        for (SystemAccountTransaction tx : result.transactions) {
            table.addRow(new TableRow(new ArrayList<>(Arrays.asList(cell(formatDelta(tx.getDelta()), 12f),
                    cell(tx.getCurrency().getIdentifier(), 18f), cell(tx.getPluginIdentifier(), 18f),
                    cell(tx.getReason(), 34f), cell(dateFormat.format(new Date(tx.getCreatedAt())), 18f)))));
        }
        body.addChild(table.getRoot());
    }

    private String systemBalanceSummary(String accountId) {
        SystemAccountBalancesResult result = service.systemAccountBalances(accountId);
        if (!result.success || result.balances.isEmpty()) return "0";
        StringBuilder summary = new StringBuilder();
        for (SystemAccountBalance balance : result.balances) {
            if (summary.length() > 0) summary.append(", ");
            summary.append(balance.getBalance()).append(' ').append(balance.getCurrency().getIdentifier());
        }
        return summary.toString();
    }

    private AdvancedButton detailButton(String accountId) {
        AdvancedButton button = actionButton(t().get("TC_WALLET_SYSTEM_TRANSACTIONS", uiPlayer), () -> {
            systemAccountDetailId = accountId;
            rebuild();
        });
        button.setSize(112, 24, false);
        return button;
    }

    private AdvancedButton actionButton(String text, Runnable action) {
        AdvancedButton button = AdvancedButtonFactory.defaultButton(text, event -> action.run());
        button.setPivot(Pivot.UpperLeft);
        button.setSize(42, 28, false);
        button.setBorderEdgeRadius(3, false);
        return button;
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
        icon.style.backgroundImage.set(AssetManager.getIcon(uiPlayer, currency.getIconKey()));
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
