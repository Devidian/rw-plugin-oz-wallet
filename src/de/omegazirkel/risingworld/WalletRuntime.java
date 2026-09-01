package de.omegazirkel.risingworld;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;

import de.omegazirkel.risingworld.tools.Colors;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.OZLogger;
import de.omegazirkel.risingworld.tools.PlayerSettings;
import de.omegazirkel.risingworld.tools.db.SQLiteConnectionFactory;
import de.omegazirkel.risingworld.tools.settings.PlayerPluginAdminSettings;
import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ui.MenuItem;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginSettingsOverlay;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProviders;
import de.omegazirkel.risingworld.tools.ui.PluginMenuManager;
import de.omegazirkel.risingworld.tools.ui.PluginShortcutVisibility;
import de.omegazirkel.risingworld.wallet.PluginGUI;
import de.omegazirkel.risingworld.wallet.PluginSettings;
import de.omegazirkel.risingworld.wallet.WalletBalanceResult;
import de.omegazirkel.risingworld.wallet.WalletBalance;
import de.omegazirkel.risingworld.wallet.WalletCurrenciesResult;
import de.omegazirkel.risingworld.wallet.WalletCurrencyResult;
import de.omegazirkel.risingworld.wallet.WalletErrorCode;
import de.omegazirkel.risingworld.wallet.WalletDatabase;
import de.omegazirkel.risingworld.wallet.WalletPluginInfoStatusProvider;
import de.omegazirkel.risingworld.wallet.WalletService;
import de.omegazirkel.risingworld.wallet.WalletTransactionResult;
import de.omegazirkel.risingworld.wallet.WorldTreasuryService;
import de.omegazirkel.risingworld.wallet.WalletTransferResult;
import de.omegazirkel.risingworld.wallet.AccountTransferResult;
import de.omegazirkel.risingworld.wallet.SystemAccountBalancesResult;
import de.omegazirkel.risingworld.wallet.SystemAccountResult;
import de.omegazirkel.risingworld.wallet.SystemAccountsResult;
import de.omegazirkel.risingworld.wallet.SystemAccountTransactionsResult;
import de.omegazirkel.risingworld.wallet.ui.WalletCurrencyHud;
import de.omegazirkel.risingworld.wallet.ui.WalletPlayerPluginData;
import de.omegazirkel.risingworld.wallet.ui.WalletPlayerPluginSettings;
import net.risingworld.api.Server;
import net.risingworld.api.Plugin;
import net.risingworld.api.events.player.PlayerCommandEvent;
import net.risingworld.api.events.player.PlayerDisconnectEvent;
import net.risingworld.api.events.player.PlayerSpawnEvent;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.UITarget;

class WalletRuntime extends Plugin {
    static final Colors c = Colors.getInstance();
    private static final String WELCOME_BONUS_CLAIMED_KEY = "oz.wallet.welcomeBonusClaimed";
    private static final String WELCOME_BONUS_REASON = "Welcome bonus";
    private static I18n t = null;
    private static PluginSettings s = null;
    private static PluginGUI gui;
    private static WalletService walletService;
    private static PlayerSettings playerSettings;
    private static Connection sqliteCon;
    private static String worldSystemAccountId;
    public static String name;

    public static OZLogger logger() {
        return OZLogger.getInstance("OZ.Wallet");
    }

    @Override
    public void onEnable() {
        name = this.getDescription("name");
        s = PluginSettings.getInstance((Wallet) this);
        t = I18n.getInstance(this);
        s.initSettings();

        try {
            sqliteCon = SQLiteConnectionFactory.open(this);
            WalletDatabase database = new WalletDatabase(sqliteCon);
            playerSettings = new PlayerSettings(sqliteCon);
            walletService = new WalletService(database);
            walletService.registerCurrency(
                    s.defaultCurrencyIdentifier,
                    s.defaultCurrencyName,
                    s.defaultCurrencyIcon,
                    name,
                    true);
            String worldName = Server.getOption("World_Name");
            if (worldName == null || worldName.isBlank()) worldName = "world";
            worldSystemAccountId = "world::" + worldName.trim();
            boolean newWorldAccount = walletService.systemAccount(worldSystemAccountId).errorCode
                    == WalletErrorCode.ACCOUNT_NOT_FOUND;
            SystemAccountResult worldAccount = walletService.createSystemAccount(worldSystemAccountId, "WORLD",
                    "World treasury: " + worldName.trim(), name);
            if (!worldAccount.success) {
                logger().error("Failed to initialize world system account: " + worldAccount.message);
            } else if (newWorldAccount && s.worldInitialCapital > 0L) {
                WalletTransactionResult seed = walletService.creditSystemAccountIdempotent(worldSystemAccountId,
                        s.worldInitialCapital, "Initial world treasury capital", s.defaultCurrencyIdentifier, name,
                        "world:" + worldName.trim() + ":initial-capital");
                if (!seed.success) logger().error("Failed to fund new world account: " + seed.message);
            }
            WalletTransactionResult annualCapital = new WorldTreasuryService(walletService, name).reconcile(
                    worldSystemAccountId, Server.getGameTime(net.risingworld.api.objects.Time.Unit.Years),
                    Server.getGameTimeSpeed(), daysPerMonth(), s.defaultCurrencyIdentifier);
            if (!annualCapital.success) logger().error("Failed to issue annual world treasury capital: "
                    + annualCapital.message);
        } catch (SQLException ex) {
            logger().error("Failed to initialize wallet database: " + ex.getMessage());
            ex.printStackTrace();
        }

        gui = PluginGUI.getInstance((Wallet) this, walletService);
        PluginShortcutVisibility.register(name, WalletPlayerPluginSettings::shortcutVisible);
        PluginMenuManager.registerPluginMenu(new MenuItem(name, "oz-wallet", "Wallet", p -> {
            p.hideRadialMenu(true);
            gui.openWallet(p);
        }));
        PlayerPluginSettingsOverlay.registerPlayerPluginSettings(new WalletPlayerPluginSettings(getDescription("version")));
        PlayerPluginSettingsOverlay.registerPlayerPluginData(new WalletPlayerPluginData(getDescription("version")));
        PlayerPluginSettingsOverlay.registerPlayerPluginAdminSettings(
                new PlayerPluginAdminSettings(name, getDescription("version"), () -> s.adminSettingsEntries(),
                        s::initSettings));
        PluginInfoStatusProviders.registerProvider(
                new WalletPluginInfoStatusProvider((Wallet) this, getDescription("version")));
        logger().info(this.getName() + " Plugin is enabled version:" + this.getDescription("version"));
    }

    private int daysPerMonth() {
        try {
            String configured = Server.getOption("Days_Per_Month");
            return configured == null ? WorldTreasuryService.STANDARD_DAYS_PER_MONTH
                    : Integer.parseInt(configured.trim());
        } catch (RuntimeException ex) {
            logger().warn("Could not read Days_Per_Month; using " + WorldTreasuryService.STANDARD_DAYS_PER_MONTH);
            return WorldTreasuryService.STANDARD_DAYS_PER_MONTH;
        }
    }

    @Override
    public void onDisable() {
        if (name != null) {
            PluginShortcutVisibility.unregister(name);
            PluginInfoStatusProviders.unregisterProvider(name);
        }
        for (Player player : Server.getAllPlayers()) {
            removeWalletHud(player);
        }

        if (sqliteCon != null) {
            try {
                sqliteCon.close();
            } catch (SQLException ex) {
                logger().error("Failed to close wallet database connection: " + ex.getMessage());
            }
        }
    }

    public void onSettingsChanged(Path settingsPath) {
        s.initSettings(settingsPath.toString());
        if (walletService != null) {
            walletService.registerCurrency(
                    s.defaultCurrencyIdentifier,
                    s.defaultCurrencyName,
                    s.defaultCurrencyIcon,
                    name,
                    true);
            refreshAllWalletHuds();
        }
    }

    public void onPlayerCommand(PlayerCommandEvent event) {
        Player player = event.getPlayer();
        String commandLine = event.getCommand();
        String[] cmdParts = commandLine.split(" ", 2);
        String command = cmdParts[0];

        if (command.equals("/" + s.walletCommand)) {
            if (cmdParts.length > 1
                    && (cmdParts[1].equalsIgnoreCase("status") || cmdParts[1].equalsIgnoreCase("info"))) {
                PluginInfoStatusProviders.show(player, name);
                return;
            }
            if (walletService == null) {
                player.sendTextMessage(c.error + this.getName() + ": wallet database is not available.");
                return;
            }
            gui.openWallet(player);
        }
    }

    public void onPlayerSpawnEvent(PlayerSpawnEvent event) {
        Player player = event.getPlayer();
        if (playerSettings != null) {
            player.setAttribute(
                    WalletPlayerPluginSettings.SHOW_WALLET_HUD_KEY,
                    playerSettings.getBoolean(player.getDbID(), WalletPlayerPluginSettings.SHOW_WALLET_HUD_KEY)
                            .orElse(true));
        }
        syncWalletHud(player);

        if (s.enableWelcomeMessage) {
            String lang = de.omegazirkel.risingworld.OZTools.getPlayerLanguage(player);
            player.sendTextMessage(t.get("tc.msg.plugin.welcome", lang)
                    .replace("PH_PLUGIN_NAME", getDescription("name"))
                    .replace("PH_PLUGIN_CMD", s.walletCommand)
                    .replace("PH_PLUGIN_VERSION", getDescription("version")));
        }

        grantWelcomeBonus(player);
    }

    public void onPlayerDisconnectEvent(PlayerDisconnectEvent event) {
        removeWalletHud(event.getPlayer());
    }

    private void grantWelcomeBonus(Player player) {
        if (walletService == null || playerSettings == null || !s.welcomeBonusEnabled) {
            return;
        }
        if (!s.welcomeBonusAmountValid || s.welcomeBonusAmount <= 0) {
            logger().warn("Welcome bonus disabled for player " + player.getDbID()
                    + ": welcomeBonus.amount must be a positive whole number.");
            return;
        }

        int playerDbId = player.getDbID();
        boolean alreadyClaimed;
        try {
            alreadyClaimed = playerSettings.getBoolean(playerDbId, WELCOME_BONUS_CLAIMED_KEY).orElse(false);
        } catch (RuntimeException ex) {
            logger().warn("Could not read welcome bonus claim flag for player " + playerDbId + ": " + ex.getMessage());
            return;
        }
        if (alreadyClaimed) {
            return;
        }

        WalletTransactionResult result = depositDefault(playerDbId, s.welcomeBonusAmount, WELCOME_BONUS_REASON, name);
        if (!result.success) {
            logger().warn("Welcome bonus booking failed for player " + playerDbId + ": " + result.message);
            return;
        }

        try {
            playerSettings.setBoolean(playerDbId, WELCOME_BONUS_CLAIMED_KEY, true);
        } catch (RuntimeException ex) {
            logger().warn("Could not write welcome bonus claim flag for player " + playerDbId + ": " + ex.getMessage());
            return;
        }

        String lang = de.omegazirkel.risingworld.OZTools.getPlayerLanguage(player);
        player.sendTextMessage(t.get("tc.wallet.welcome.bonus.granted", lang)
                .replace("PH_AMOUNT", Long.toString(s.welcomeBonusAmount))
                .replace("PH_CURRENCY", defaultCurrencyIdentifier()));
    }

    public WalletCurrencyResult registerCurrency(
            String currencyIdentifier,
            String name,
            String icon,
            String pluginIdentifier) {
        if (walletService == null) {
            return WalletCurrencyResult.failure(
                    de.omegazirkel.risingworld.wallet.WalletErrorCode.DATABASE_ERROR,
                    "Wallet database is not available.");
        }
        WalletCurrencyResult result = walletService.registerCurrency(currencyIdentifier, name, icon, pluginIdentifier);
        if (result.success) {
            refreshAllWalletHuds();
        }
        return result;
    }

    public WalletTransactionResult deposit(
            int playerDbId,
            long value,
            String reason,
            String currencyIdentifier,
            String pluginIdentifier) {
        if (walletService == null) {
            return WalletTransactionResult.failure(
                    de.omegazirkel.risingworld.wallet.WalletErrorCode.DATABASE_ERROR,
                    "Wallet database is not available.");
        }
        WalletTransactionResult result = walletService.deposit(playerDbId, value, reason, currencyIdentifier,
                pluginIdentifier);
        refreshOnlineWalletHudAfterTransaction(playerDbId, result);
        return result;
    }

    public WalletTransactionResult withdraw(
            int playerDbId,
            long value,
            String reason,
            String currencyIdentifier,
            String pluginIdentifier) {
        if (walletService == null) {
            return WalletTransactionResult.failure(
                    de.omegazirkel.risingworld.wallet.WalletErrorCode.DATABASE_ERROR,
                    "Wallet database is not available.");
        }
        WalletTransactionResult result = walletService.withdraw(playerDbId, value, reason, currencyIdentifier,
                pluginIdentifier);
        refreshOnlineWalletHudAfterTransaction(playerDbId, result);
        return result;
    }

    /** Public v1 atomic, idempotent transfer contract for cross-plugin sagas. */
    public WalletTransferResult transferIdempotent(
            int payerDbId,
            int payeeDbId,
            long value,
            String reason,
            String currencyIdentifier,
            String pluginIdentifier,
            String correlationId) {
        if (walletService == null) {
            return WalletTransferResult.failure(
                    de.omegazirkel.risingworld.wallet.WalletErrorCode.DATABASE_ERROR,
                    "Wallet database is not available.");
        }
        WalletTransferResult result = walletService.transferIdempotent(payerDbId, payeeDbId, value, reason,
                currencyIdentifier, pluginIdentifier, correlationId);
        if (result.success) {
            refreshOnlineWalletHud(payerDbId);
            refreshOnlineWalletHud(payeeDbId);
        }
        return result;
    }

    public WalletBalanceResult balance(int playerDbId, String currencyIdentifier) {
        if (walletService == null) {
            return WalletBalanceResult.failure(
                    de.omegazirkel.risingworld.wallet.WalletErrorCode.DATABASE_ERROR,
                    "Wallet database is not available.");
        }
        return walletService.balance(playerDbId, currencyIdentifier);
    }

    public String defaultCurrencyIdentifier() {
        if (walletService == null) {
            return s.defaultCurrencyIdentifier;
        }
        String identifier = walletService.defaultCurrencyIdentifier();
        return identifier == null ? s.defaultCurrencyIdentifier : identifier;
    }

    /** Language selected by administrators for system-account audit reasons. */
    public String walletAuditLanguage() {
        return s == null ? "en" : s.auditLanguage;
    }

    public boolean databaseAvailable() {
        return walletService != null;
    }

    public WalletCurrencyResult defaultCurrency() {
        if (walletService == null) {
            return WalletCurrencyResult.failure(
                    de.omegazirkel.risingworld.wallet.WalletErrorCode.DATABASE_ERROR,
                    "Wallet database is not available.");
        }
        return walletService.defaultCurrency();
    }

    public WalletCurrenciesResult listCurrencies() {
        if (walletService == null) {
            return WalletCurrenciesResult.failure(
                    de.omegazirkel.risingworld.wallet.WalletErrorCode.DATABASE_ERROR,
                    "Wallet database is not available.");
        }
        return walletService.listCurrencies();
    }

    public WalletTransactionResult depositDefault(int playerDbId, long value, String reason, String pluginIdentifier) {
        return deposit(playerDbId, value, reason, s.defaultCurrencyIdentifier, pluginIdentifier);
    }

    public WalletTransactionResult withdrawDefault(int playerDbId, long value, String reason, String pluginIdentifier) {
        return withdraw(playerDbId, value, reason, s.defaultCurrencyIdentifier, pluginIdentifier);
    }

    public WalletBalanceResult balanceDefault(int playerDbId) {
        return balance(playerDbId, s.defaultCurrencyIdentifier);
    }

    public String worldSystemAccountId() {
        return worldSystemAccountId;
    }

    public SystemAccountResult createSystemAccount(String accountId, String accountType, String displayName,
            String pluginIdentifier) {
        if (walletService == null) return systemAccountDatabaseFailure();
        return walletService.createSystemAccount(accountId, accountType, displayName, pluginIdentifier);
    }

    public SystemAccountResult systemAccount(String accountId) {
        if (walletService == null) return systemAccountDatabaseFailure();
        return walletService.systemAccount(accountId);
    }

    public SystemAccountsResult listSystemAccounts(String search, int offset, int limit) {
        if (walletService == null) {
            return SystemAccountsResult.failure(
                    de.omegazirkel.risingworld.wallet.WalletErrorCode.DATABASE_ERROR,
                    "Wallet database is not available.");
        }
        return walletService.listSystemAccounts(search, offset, limit);
    }

    public SystemAccountBalancesResult systemAccountBalances(String accountId) {
        if (walletService == null) {
            return SystemAccountBalancesResult.failure(
                    de.omegazirkel.risingworld.wallet.WalletErrorCode.DATABASE_ERROR,
                    "Wallet database is not available.");
        }
        return walletService.systemAccountBalances(accountId);
    }

    public SystemAccountTransactionsResult systemAccountTransactions(String accountId, int limit) {
        if (walletService == null) {
            return SystemAccountTransactionsResult.failure(
                    de.omegazirkel.risingworld.wallet.WalletErrorCode.DATABASE_ERROR,
                    "Wallet database is not available.");
        }
        return walletService.systemAccountTransactions(accountId, limit);
    }

    public SystemAccountResult archiveSystemAccount(String accountId, String pluginIdentifier) {
        if (walletService == null) return systemAccountDatabaseFailure();
        return walletService.archiveSystemAccount(accountId, pluginIdentifier);
    }

    public SystemAccountResult updateSystemAccountDisplayName(String accountId, String displayName,
            String pluginIdentifier) {
        if (walletService == null) return systemAccountDatabaseFailure();
        return walletService.updateSystemAccountDisplayName(accountId, displayName, pluginIdentifier);
    }

    public AccountTransferResult transferPlayerToSystemIdempotent(int payerDbId, String payeeAccountId, long value,
            String reason, String currencyIdentifier, String pluginIdentifier, String correlationId) {
        if (walletService == null) return accountTransferDatabaseFailure();
        AccountTransferResult result = walletService.transferPlayerToSystemIdempotent(payerDbId, payeeAccountId,
                value, reason, currencyIdentifier, pluginIdentifier, correlationId);
        if (result.success) refreshOnlineWalletHud(payerDbId);
        return result;
    }

    public AccountTransferResult transferSystemToPlayerIdempotent(String payerAccountId, int payeeDbId, long value,
            String reason, String currencyIdentifier, String pluginIdentifier, String correlationId) {
        if (walletService == null) return accountTransferDatabaseFailure();
        AccountTransferResult result = walletService.transferSystemToPlayerIdempotent(payerAccountId, payeeDbId,
                value, reason, currencyIdentifier, pluginIdentifier, correlationId);
        if (result.success) refreshOnlineWalletHud(payeeDbId);
        return result;
    }

    public AccountTransferResult transferSystemToSystemIdempotent(String payerAccountId, String payeeAccountId,
            long value, String reason, String currencyIdentifier, String pluginIdentifier, String correlationId) {
        if (walletService == null) return accountTransferDatabaseFailure();
        return walletService.transferSystemToSystemIdempotent(payerAccountId, payeeAccountId, value, reason,
                currencyIdentifier, pluginIdentifier, correlationId);
    }

    public AccountTransferResult transferWorldToSystemIdempotent(String payeeAccountId, long value, String reason,
            String currencyIdentifier, String pluginIdentifier, String correlationId) {
        if (walletService == null || worldSystemAccountId == null) return accountTransferDatabaseFailure();
        return walletService.transferWorldToSystemIdempotent(worldSystemAccountId, payeeAccountId, value, reason,
                currencyIdentifier, pluginIdentifier, correlationId);
    }

    public WalletTransactionResult creditSystemAccountIdempotent(String accountId, long value, String reason,
            String currencyIdentifier, String pluginIdentifier, String correlationId) {
        if (walletService == null) {
            return WalletTransactionResult.failure(de.omegazirkel.risingworld.wallet.WalletErrorCode.DATABASE_ERROR,
                    "Wallet database is not available.");
        }
        return walletService.creditSystemAccountIdempotent(accountId, value, reason, currencyIdentifier,
                pluginIdentifier, correlationId);
    }

    public AccountTransferResult transferPlayerToWorldIdempotent(int payerDbId, long value, String reason,
            String currencyIdentifier, String pluginIdentifier, String correlationId) {
        if (worldSystemAccountId == null) return accountTransferDatabaseFailure();
        return transferPlayerToSystemIdempotent(payerDbId, worldSystemAccountId, value, reason, currencyIdentifier,
                pluginIdentifier, correlationId);
    }

    public AccountTransferResult reverseAccountTransferIdempotent(String originalCorrelationId,
            String reversalCorrelationId, String reason, String pluginIdentifier) {
        if (walletService == null) return accountTransferDatabaseFailure();
        AccountTransferResult result = walletService.reverseAccountTransferIdempotent(originalCorrelationId,
                reversalCorrelationId, reason, pluginIdentifier);
        if (result.success && result.transfer != null) {
            if ("PLAYER".equals(result.transfer.getPayerKind())) {
                refreshOnlineWalletHud(Integer.parseInt(result.transfer.getPayerReference()));
            }
            if ("PLAYER".equals(result.transfer.getPayeeKind())) {
                refreshOnlineWalletHud(Integer.parseInt(result.transfer.getPayeeReference()));
            }
        }
        return result;
    }

    private static SystemAccountResult systemAccountDatabaseFailure() {
        return SystemAccountResult.failure(de.omegazirkel.risingworld.wallet.WalletErrorCode.DATABASE_ERROR,
                "Wallet database is not available.");
    }

    private static AccountTransferResult accountTransferDatabaseFailure() {
        return AccountTransferResult.failure(de.omegazirkel.risingworld.wallet.WalletErrorCode.DATABASE_ERROR,
                "Wallet database is not available.");
    }

    public static PlayerSettings playerSettings() {
        return playerSettings;
    }

    public static boolean isWalletHudEnabled(Player player) {
        if (player.hasAttribute(WalletPlayerPluginSettings.SHOW_WALLET_HUD_KEY)) {
            Object value = player.getAttribute(WalletPlayerPluginSettings.SHOW_WALLET_HUD_KEY);
            return value instanceof Boolean ? (Boolean) value : true;
        }
        if (playerSettings == null) {
            return true;
        }
        return playerSettings.getBoolean(player.getDbID(), WalletPlayerPluginSettings.SHOW_WALLET_HUD_KEY).orElse(true);
    }

    public static void syncWalletHud(Player player) {
        if (player == null || walletService == null || playerSettings == null || !player.isConnected()
                || !player.isSpawned() || !isWalletHudEnabled(player)) {
            removeWalletHud(player);
            return;
        }

        List<WalletBalance> balances;
        try {
            balances = walletService.listBalancesForPlayer(player.getDbID(), s.defaultCurrencyIdentifier).stream()
                    .sorted(Comparator.comparingLong(WalletBalance::getBalance).reversed()
                            .thenComparing(balance -> !balance.getCurrency().isDefaultCurrency())
                            .thenComparing(balance -> balance.getCurrency().getIdentifier()))
                    .limit(5)
                    .toList();
        } catch (SQLException ex) {
            logger().warn("Could not load wallet HUD balances for player " + player.getDbID() + ": "
                    + ex.getMessage());
            return;
        }

        Object existing = player.getAttribute(WalletCurrencyHud.ATTRIBUTE_KEY);
        if (existing instanceof WalletCurrencyHud hud) {
            hud.update(balances);
            return;
        }

        WalletCurrencyHud hud = new WalletCurrencyHud(
                player,
                t.get("tc.wallet.inventory.panel.title", player),
                balances);
        player.addUIElement(hud, UITarget.Inventory);
        player.setAttribute(WalletCurrencyHud.ATTRIBUTE_KEY, hud);
    }

    public static void removeWalletHud(Player player) {
        if (player == null) {
            return;
        }
        Object existing = player.getAttribute(WalletCurrencyHud.ATTRIBUTE_KEY);
        if (existing instanceof WalletCurrencyHud hud) {
            player.removeUIElement(hud);
        }
        player.deleteAttribute(WalletCurrencyHud.ATTRIBUTE_KEY);
    }

    private static void refreshOnlineWalletHudAfterTransaction(int playerDbId, WalletTransactionResult result) {
        if (!result.success) {
            return;
        }
        refreshOnlineWalletHud(playerDbId);
    }

    private static void refreshOnlineWalletHud(int playerDbId) {
        Player player = Server.getPlayerByDbID(playerDbId);
        if (player != null && player.isConnected() && player.isSpawned() && isWalletHudEnabled(player)) {
            syncWalletHud(player);
        }
    }

    private static void refreshAllWalletHuds() {
        for (Player player : Server.getAllPlayers()) {
            syncWalletHud(player);
        }
    }

    public PluginSettings getSettings() {
        return s;
    }
}
