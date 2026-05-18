package de.omegazirkel.risingworld.wallet;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.logging.log4j.Level;

import de.omegazirkel.risingworld.Wallet;
import de.omegazirkel.risingworld.tools.OZLogger;

public class PluginSettings {
    private static PluginSettings instance = null;
    private static Wallet plugin;

    public String defaultCurrencyIdentifier = "OZC";
    public String defaultCurrencyName = "Omega Zirkel Coin";
    public String defaultCurrencyIcon = "icon-ki-coin-omega-gold";
    public String walletCommand = "wallet";
    public boolean enableWelcomeMessage = false;
    public boolean welcomeBonusEnabled = true;
    public long welcomeBonusAmount = 100L;
    public boolean welcomeBonusAmountValid = true;
    public int auditLogLimit = 50;
    public String logLevel = Level.ALL.name();

    private static OZLogger logger() {
        return OZLogger.getInstance("OZ.Wallet.Settings");
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
        initSettings((plugin.getPath() != null ? plugin.getPath() : ".") + "/settings.properties");
    }

    public void initSettings(String filePath) {
        Path settingsFile = Paths.get(filePath);
        Path defaultSettingsFile = settingsFile.resolveSibling("settings.default.properties");

        try {
            if (Files.notExists(settingsFile) && Files.exists(defaultSettingsFile)) {
                logger().info("settings.properties not found, copying from settings.default.properties...");
                Files.copy(defaultSettingsFile, settingsFile);
            }

            Properties settings = new Properties();
            if (Files.exists(settingsFile)) {
                try (FileInputStream in = new FileInputStream(settingsFile.toFile())) {
                    settings.load(new InputStreamReader(in, "UTF8"));
                }
            } else {
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
            logLevel = settings.getProperty("logLevel", "ALL");

            logger().info(plugin.getName() + " Plugin settings loaded");
            logger().info("Default currency is " + defaultCurrencyIdentifier + " (" + defaultCurrencyName + ")");
            logger().info("Wallet command is /" + walletCommand);
            logger().info("Welcome bonus is " + (welcomeBonusEnabled ? "enabled" : "disabled")
                    + " with amount " + welcomeBonusAmount + " " + defaultCurrencyIdentifier);
            logger().info("Audit log limit is " + (auditLogLimit == 0 ? "unlimited" : auditLogLimit));
            logger().info("Loglevel is set to " + logLevel);
            logger().setLevel(logLevel);
        } catch (IOException ex) {
            logger().error("IOException on initSettings: " + ex.getMessage());
            ex.printStackTrace();
        } catch (NumberFormatException ex) {
            logger().error("NumberFormatException on initSettings: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
