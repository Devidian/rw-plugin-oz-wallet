package de.omegazirkel.risingworld;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;

import de.omegazirkel.risingworld.tools.Colors;
import de.omegazirkel.risingworld.tools.FileChangeListener;
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
import de.omegazirkel.risingworld.wallet.PluginGUI;
import de.omegazirkel.risingworld.wallet.PluginSettings;
import de.omegazirkel.risingworld.wallet.WalletBalanceResult;
import de.omegazirkel.risingworld.wallet.WalletBalance;
import de.omegazirkel.risingworld.wallet.WalletCurrenciesResult;
import de.omegazirkel.risingworld.wallet.WalletCurrencyResult;
import de.omegazirkel.risingworld.wallet.WalletDatabase;
import de.omegazirkel.risingworld.wallet.WalletPluginInfoStatusProvider;
import de.omegazirkel.risingworld.wallet.WalletService;
import de.omegazirkel.risingworld.wallet.WalletTransactionResult;
import de.omegazirkel.risingworld.wallet.ui.WalletCurrencyHud;
import de.omegazirkel.risingworld.wallet.ui.WalletPlayerPluginData;
import de.omegazirkel.risingworld.wallet.ui.WalletPlayerPluginSettings;
import net.risingworld.api.Server;
import net.risingworld.api.Plugin;
import net.risingworld.api.events.EventMethod;
import net.risingworld.api.events.Listener;
import net.risingworld.api.events.player.PlayerCommandEvent;
import net.risingworld.api.events.player.PlayerDisconnectEvent;
import net.risingworld.api.events.player.PlayerSpawnEvent;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.UITarget;

public class Wallet extends Plugin implements Listener, FileChangeListener {
    static final Colors c = Colors.getInstance();
    private static final String WELCOME_BONUS_CLAIMED_KEY = "oz.wallet.welcomeBonusClaimed";
    private static final String WELCOME_BONUS_REASON = "Welcome bonus";
    private static I18n t = null;
    private static PluginSettings s = null;
    private static PluginGUI gui;
    private static WalletService walletService;
    private static PlayerSettings playerSettings;
    private static Connection sqliteCon;
    public static String name;

    public static OZLogger logger() {
        return OZLogger.getInstance("OZ.Wallet");
    }

    @Override
    public void onEnable() {
        name = this.getDescription("name");
        s = PluginSettings.getInstance(this);
        t = I18n.getInstance(this);
        registerEventListener(this);
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
        } catch (SQLException ex) {
            logger().error("Failed to initialize wallet database: " + ex.getMessage());
            ex.printStackTrace();
        }

        gui = PluginGUI.getInstance(this, walletService);
        PluginMenuManager.registerPluginMenu(new MenuItem(AssetManager.getIcon("icon-ki-oz-wallet"), "Wallet", p -> {
            p.hideRadialMenu(true);
            gui.openWallet(p);
        }));
        PlayerPluginSettingsOverlay.registerPlayerPluginSettings(new WalletPlayerPluginSettings(getDescription("version")));
        PlayerPluginSettingsOverlay.registerPlayerPluginData(new WalletPlayerPluginData(getDescription("version")));
        PlayerPluginSettingsOverlay.registerPlayerPluginAdminSettings(
                new PlayerPluginAdminSettings(name, getDescription("version"), () -> s.adminSettingsEntries(),
                        s::initSettings));
        PluginInfoStatusProviders.registerProvider(new WalletPluginInfoStatusProvider(this, getDescription("version")));
        logger().info(this.getName() + " Plugin is enabled version:" + this.getDescription("version"));
    }

    @Override
    public void onDisable() {
        if (name != null) {
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

    @Override
    public void onSettingsChanged(Path settingsPath) {
        s.initSettings(settingsPath.toString());
        logger().setLevel(s.logLevel);
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

    @EventMethod
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

    @EventMethod
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
            String lang = player.getSystemLanguage();
            player.sendTextMessage(t.get("TC_MSG_PLUGIN_WELCOME", lang)
                    .replace("PH_PLUGIN_NAME", getDescription("name"))
                    .replace("PH_PLUGIN_CMD", s.walletCommand)
                    .replace("PH_PLUGIN_VERSION", getDescription("version")));
        }

        grantWelcomeBonus(player);
    }

    @EventMethod
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

        String lang = player.getSystemLanguage();
        player.sendTextMessage(t.get("TC_WALLET_WELCOME_BONUS_GRANTED", lang)
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
                t.get("TC_WALLET_INVENTORY_PANEL_TITLE", player),
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
