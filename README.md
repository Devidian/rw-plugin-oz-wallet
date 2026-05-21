# OZ - Wallet

Wallet and economy state plugin for Rising World.

## Responsibilities

- currency registry
- player balances
- transaction history
- standalone wallet UI opened with `/wallet`
- public API for optional integrations from sibling plugins

`rw-plugin-oz-tools` is a hard runtime dependency.

## Settings

The plugin copies `settings.default.properties` to `settings.properties` on first run.

```properties
defaultCurrency.identifier=OZC
defaultCurrency.name=Omega Zirkel Coin
defaultCurrency.icon=icon-ki-coin-omega-gold
walletCommand=wallet
sendPluginWelcome=false
welcomeBonus.enabled=true
welcomeBonus.amount=100
auditLogLimit=50
logLevel=ALL
```

The standalone wallet UI also appears in the OZ Tools radial plugin menu. Balances are shown as currency cards with their icon, amount, source plugin, and default-currency marker; transaction history remains in a table for scanning recent activity. Transaction timestamps are rendered as GMT in the wallet table. Admin players also see admin-only tabs for the latest global transactions, total world balances grouped by currency, and the top 20 positive standard-currency player balances. The top list resolves names through the shared player lookup helper and falls back to database ids for unknown players. `auditLogLimit` controls how many global audit-log rows are shown to admins; the default is `50`, and `0` disables the limit.

Player-specific settings are stored through `rw-plugin-oz-tools` player settings. The existing `showWalletHud` key defaults to `true` for compatibility and is exposed in the OZ Tools player plugin settings overlay as an inventory wallet toggle. When enabled, the player sees a compact `Geldbörse` / `Wallet` side panel in the inventory with up to five currencies sorted by descending balance; it updates after successful wallet bookings, currency registration, or settings reload and is removed immediately when the player disables the setting.

`welcomeBonus.enabled` controls the one-time welcome bonus for new players. When enabled, the first spawn without the internal `oz.wallet.welcomeBonusClaimed` player setting deposits `welcomeBonus.amount` in the current default currency and then stores the claim flag through `rw-plugin-oz-tools` player settings. Set `welcomeBonus.enabled=false` to disable the bonus without setting claim flags. Amounts of `0`, negative values, or invalid numbers do not grant a bonus and leave the claim flag unset for a later valid configuration.

## Public API

Other plugins can look up `OZ - Wallet` and call these methods on the main plugin class:

```java
public WalletCurrencyResult registerCurrency(
    String currencyIdentifier,
    String name,
    String icon,
    String pluginIdentifier
);

public WalletTransactionResult deposit(
    int playerDbId,
    long value,
    String reason,
    String currencyIdentifier,
    String pluginIdentifier
);

public WalletTransactionResult withdraw(
    int playerDbId,
    long value,
    String reason,
    String currencyIdentifier,
    String pluginIdentifier
);

public WalletBalanceResult balance(
    int playerDbId,
    String currencyIdentifier
);

public String defaultCurrencyIdentifier();

public WalletCurrencyResult defaultCurrency();

public WalletTransactionResult depositDefault(
    int playerDbId,
    long value,
    String reason,
    String pluginIdentifier
);

public WalletTransactionResult withdrawDefault(
    int playerDbId,
    long value,
    String reason,
    String pluginIdentifier
);

public WalletBalanceResult balanceDefault(
    int playerDbId
);
```

Currency identifiers are trimmed and normalized to uppercase. Amounts are whole-number positive `long` values. Unknown currencies return `UNKNOWN_CURRENCY`, invalid inputs return `INVALID_ARGUMENT`, duplicate currency identifiers from another plugin return `CURRENCY_ALREADY_REGISTERED`, and withdrawals never allow negative balances. The `*Default` convenience methods use the configured default currency.

## Optional integration from other plugins

Plugins that only optionally use economy features should avoid a hard compile-time dependency on `rw-plugin-oz-wallet`. Use the Rising World plugin lookup and reflection pattern instead:

- call `plugin.getPluginByName("OZ - Wallet")` during initialization
- optionally check `Class.forName("de.omegazirkel.risingworld.Wallet")` before invoking methods
- invoke the public API methods by reflection
- keep economy features disabled when Wallet is not installed or not loaded

`rw-plugin-oz-wallet` still has to be installed at runtime when those economy features are enabled. Reflection only keeps the consuming plugin loadable without Wallet.

Example wrapper for a consuming plugin:

```java
package de.omegazirkel.risingworld.example;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.risingworld.api.Plugin;

public final class WalletConnect {

    private static Plugin walletPlugin;

    private WalletConnect() {
    }

    public static void init(Plugin plugin) {
        walletPlugin = plugin.getPluginByName("OZ - Wallet");
    }

    public static boolean isAvailable() {
        try {
            Class.forName("de.omegazirkel.risingworld.Wallet");
            return walletPlugin != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static Object registerCurrency(
            String currencyIdentifier,
            String name,
            String icon,
            String pluginIdentifier) {
        return callWalletMethod(
                "registerCurrency",
                new Class<?>[] { String.class, String.class, String.class, String.class },
                new Object[] { currencyIdentifier, name, icon, pluginIdentifier });
    }

    public static Object deposit(
            int playerDbId,
            long value,
            String reason,
            String currencyIdentifier,
            String pluginIdentifier) {
        return callWalletMethod(
                "deposit",
                new Class<?>[] { int.class, long.class, String.class, String.class, String.class },
                new Object[] { playerDbId, value, reason, currencyIdentifier, pluginIdentifier });
    }

    public static Object withdraw(
            int playerDbId,
            long value,
            String reason,
            String currencyIdentifier,
            String pluginIdentifier) {
        return callWalletMethod(
                "withdraw",
                new Class<?>[] { int.class, long.class, String.class, String.class, String.class },
                new Object[] { playerDbId, value, reason, currencyIdentifier, pluginIdentifier });
    }

    public static Object balance(int playerDbId, String currencyIdentifier) {
        return callWalletMethod(
                "balance",
                new Class<?>[] { int.class, String.class },
                new Object[] { playerDbId, currencyIdentifier });
    }

    public static Object depositDefault(
            int playerDbId,
            long value,
            String reason,
            String pluginIdentifier) {
        return callWalletMethod(
                "depositDefault",
                new Class<?>[] { int.class, long.class, String.class, String.class },
                new Object[] { playerDbId, value, reason, pluginIdentifier });
    }

    public static boolean isSuccess(Object result) {
        Object success = getResultField(result, "success");
        return success instanceof Boolean && (Boolean) success;
    }

    public static String message(Object result) {
        Object message = getResultField(result, "message");
        return message instanceof String ? (String) message : "";
    }

    private static Object callWalletMethod(String methodName, Class<?>[] paramTypes, Object[] args) {
        if (!isAvailable()) {
            return null;
        }

        try {
            Method method = walletPlugin.getClass().getMethod(methodName, paramTypes);
            return method.invoke(walletPlugin, args);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object getResultField(Object result, String fieldName) {
        if (result == null) {
            return null;
        }

        try {
            Field field = result.getClass().getField(fieldName);
            return field.get(result);
        } catch (Exception e) {
            return null;
        }
    }
}
```

## Persistence

The plugin stores wallet data in the plugin world SQLite database through `rw-plugin-oz-tools` connection helpers.

- `wallet_currencies`
- `wallet_balances`
- `wallet_transactions`

Deposits and withdrawals update balances and write the transaction row in one SQLite transaction.

## Validation

- `mvn -B -DskipTests package`
- `scripts/verify-plugin-api.sh --summary`
- `scripts/verify-plugin-api.sh --class <RisingWorldApiClass>` or `--method <RisingWorldApiClass#method>` for new API usage
- `mvn -B test` when tests exist
