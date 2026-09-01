package de.omegazirkel.risingworld.wallet;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import de.omegazirkel.risingworld.Wallet;
import de.omegazirkel.risingworld.tools.OZLogger;
import de.omegazirkel.risingworld.tools.settings.AdminSettingsEntry;
import de.omegazirkel.risingworld.tools.settings.AdminSettingsType;
import de.omegazirkel.risingworld.tools.settings.JsonSettingsFile;
import de.omegazirkel.risingworld.tools.settings.SettingsFileEditor;

public class PluginSettings {
    private static PluginSettings instance = null;
    private static Wallet plugin;

    public String defaultCurrencyIdentifier = "OZC";
    public String defaultCurrencyName = "Omega Zirkel Coin";
    public String defaultCurrencyIcon = "coin-omega-gold";
    public String walletCommand = "wallet";
    public boolean enableWelcomeMessage = false;
    public boolean welcomeBonusEnabled = true;
    public long welcomeBonusAmount = 100L;
    public long worldInitialCapital = 10_000L;
    public boolean welcomeBonusAmountValid = true;
    public int auditLogLimit = 50;
    public String auditLanguage = "en";

    private static OZLogger logger() {
        return Wallet.logger();
    }

    public static PluginSettings getInstance(Wallet p) {
        plugin = p;
        return getInstance();
    }

    public static PluginSettings getInstance() {
        if (instance == null) {
            instance = new PluginSettings();
        }
        return instance;
    }

    private PluginSettings() {
    }

    public void initSettings() {
        initSettings(JsonSettingsFile.worldSettingsFile(plugin.getPath() != null ? plugin.getPath() : ".").toString());
    }

    public void initSettings(String filePath) {
        Path settingsFile = Paths.get(filePath);
        Path defaultSettingsFile = settingsFile.resolveSibling("settings.default.json");
        Path legacySettingsFile = settingsFile.resolveSibling("settings.properties");

        try {
            if (JsonSettingsFile.migrateLegacyProperties(legacySettingsFile, settingsFile))
                logger().info("Migrated legacy settings.properties to " + settingsFile.getFileName());
            if (Files.notExists(settingsFile) && Files.exists(defaultSettingsFile))
                JsonSettingsFile.writeFlatAtomically(settingsFile, JsonSettingsFile.loadFlat(defaultSettingsFile));
            Properties settings = loadSettings(settingsFile);
            if (settings.isEmpty()) {
                logger().warn("Neither settings.properties nor settings.default.properties found. Using default values.");
            }

            defaultCurrencyIdentifier = settings.getProperty("defaultCurrency.identifier", defaultCurrencyIdentifier);
            defaultCurrencyName = settings.getProperty("defaultCurrency.name", defaultCurrencyName);
            defaultCurrencyIcon = settings.getProperty("defaultCurrency.icon", defaultCurrencyIcon);
            walletCommand = settings.getProperty("walletCommand", walletCommand);
            enableWelcomeMessage = settings.getProperty("sendPluginWelcome", "false").contentEquals("true");
            welcomeBonusEnabled = settings.getProperty("welcomeBonus.enabled", "true").contentEquals("true");
            welcomeBonusAmountValid = true;
            String welcomeBonusAmountValue = settings.getProperty("welcomeBonus.amount", "100");
            try {
                welcomeBonusAmount = Long.parseLong(welcomeBonusAmountValue.trim());
            } catch (NumberFormatException ex) {
                welcomeBonusAmount = 0L;
                welcomeBonusAmountValid = false;
            }
            String worldInitialCapitalValue = settings.getProperty("worldInitialCapital", "10000");
            try {
                worldInitialCapital = Math.max(0L, Long.parseLong(worldInitialCapitalValue.trim()));
            } catch (NumberFormatException ex) {
                worldInitialCapital = 10_000L;
                logger().warn("Invalid worldInitialCapital " + worldInitialCapitalValue + ", using default 10000.");
            }
            String auditLogLimitValue = settings.getProperty("auditLogLimit", "50");
            try {
                int configuredAuditLogLimit = Integer.parseInt(auditLogLimitValue.trim());
                if (configuredAuditLogLimit >= 0) {
                    auditLogLimit = configuredAuditLogLimit;
                } else {
                    auditLogLimit = 50;
                    logger().warn("Invalid auditLogLimit " + auditLogLimitValue + ", using default 50.");
                }
            } catch (NumberFormatException ex) {
                auditLogLimit = 50;
                logger().warn("Invalid auditLogLimit " + auditLogLimitValue + ", using default 50.");
            }
            String configuredAuditLanguage = settings.getProperty("wallet-audit-language", "en").trim().toLowerCase();
            auditLanguage = configuredAuditLanguage.equals("de") ? "de" : "en";

            logger().info(plugin.getName() + " Plugin settings loaded");
            logger().info("Default currency is " + defaultCurrencyIdentifier + " (" + defaultCurrencyName + ")");
            logger().info("Wallet command is /" + walletCommand);
            logger().info("Welcome bonus is " + (welcomeBonusEnabled ? "enabled" : "disabled")
                    + " with amount " + welcomeBonusAmount + " " + defaultCurrencyIdentifier);
            logger().info("Audit log limit is " + (auditLogLimit == 0 ? "unlimited" : auditLogLimit));
        } catch (IOException ex) {
            logger().error("IOException on initSettings: " + ex.getMessage());
            ex.printStackTrace();
        } catch (NumberFormatException ex) {
            logger().error("NumberFormatException on initSettings: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public java.util.List<AdminSettingsEntry> adminSettingsEntries() {
        return java.util.List.of(
                AdminSettingsEntry.group("general", "General", "Command and welcome behavior."),
                entry("walletCommand", "Wallet command", "Chat command used to open the wallet.", walletCommand,
                        "wallet", AdminSettingsType.STRING),
                entry("sendPluginWelcome", "Welcome message", "Shows a short wallet message when a player joins.",
                        enableWelcomeMessage, "false", AdminSettingsType.BOOLEAN),
                AdminSettingsEntry.group("currency", "Default currency", "Default Wallet currency registration."),
                entry("defaultCurrency.identifier", "Currency identifier", "Identifier for the default Wallet currency.",
                        defaultCurrencyIdentifier, "OZC", AdminSettingsType.STRING),
                entry("defaultCurrency.name", "Currency name", "Display name for the default Wallet currency.",
                        defaultCurrencyName, "Omega Zirkel Coin", AdminSettingsType.STRING),
                entry("defaultCurrency.icon", "Currency icon", "Asset icon used for the default Wallet currency.",
                        defaultCurrencyIcon, "coin-omega-gold", AdminSettingsType.STRING),
                AdminSettingsEntry.group("welcomeBonus", "Welcome bonus", "First-join Wallet bonus behavior."),
                entry("welcomeBonus.enabled", "Welcome bonus", "Enables the first-join welcome bonus.",
                        welcomeBonusEnabled, "true", AdminSettingsType.BOOLEAN),
                entry("welcomeBonus.amount", "Welcome bonus amount", "Amount paid for the first-join welcome bonus.",
                        welcomeBonusAmount, "100", AdminSettingsType.INTEGER),
                entry("worldInitialCapital", "World initial capital",
                        "Initial world-account capital, issued once when a new world account is created.",
                        worldInitialCapital, "10000", AdminSettingsType.INTEGER),
                AdminSettingsEntry.group("adminOverview", "Admin overview", "Admin wallet overview display behavior."),
                selectEntry("wallet-audit-language", "System account audit language",
                        "Language used for system-account transaction reasons.", auditLanguage, "en"),
                entry("auditLogLimit", "Audit log limit", "Maximum number of wallet audit rows shown in UI.",
                        auditLogLimit, "50", AdminSettingsType.INTEGER));
    }

    private AdminSettingsEntry selectEntry(String key, String label, String description, String value, String defaultValue) {
        return new AdminSettingsEntry(key, label, description, value, defaultValue, AdminSettingsType.SELECT, false,
                newValue -> SettingsFileEditor.writeValue(settingsPath(), key, newValue), java.util.List.of("en", "de"));
    }

    private AdminSettingsEntry entry(String key, String label, String description, Object value, String defaultValue,
            AdminSettingsType type) {
        return new AdminSettingsEntry(
                key,
                label,
                description,
                String.valueOf(value),
                defaultValue,
                type,
                false,
                newValue -> SettingsFileEditor.writeValue(settingsPath(), key, newValue));
    }

    private Path settingsPath() {
        return JsonSettingsFile.worldSettingsFile(plugin.getPath() != null ? plugin.getPath() : ".");
    }

    private Properties loadSettings(Path file) throws IOException {
        if (!file.getFileName().toString().endsWith(".properties")) return JsonSettingsFile.loadProperties(file);
        Properties properties = new Properties();
        if (Files.exists(file)) try (FileInputStream input = new FileInputStream(file.toFile())) {
            properties.load(new InputStreamReader(input, "UTF8"));
        }
        return properties;
    }
}
