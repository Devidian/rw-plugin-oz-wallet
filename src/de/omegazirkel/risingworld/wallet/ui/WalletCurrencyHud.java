package de.omegazirkel.risingworld.wallet.ui;

import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import de.omegazirkel.risingworld.wallet.WalletCurrency;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.style.Font;
import net.risingworld.api.ui.style.Pivot;
import net.risingworld.api.ui.style.Position;
import net.risingworld.api.ui.style.ScaleMode;
import net.risingworld.api.ui.style.TextAnchor;
import net.risingworld.api.ui.style.Unit;

public class WalletCurrencyHud extends OZUIElement {
    public static final String ATTRIBUTE_KEY = "wallet.ui.currencyHud";

    private static final float HUD_WIDTH = 128f;
    private static final float HUD_HEIGHT = 32f;
    private static final float HUD_RIGHT = 10f;
    private static final float HUD_BOTTOM = 60f;
    private static final float ICON_SIZE = 22f;

    private final UILabel amountLabel;
    private final OZUIElement icon;

    public WalletCurrencyHud(WalletCurrency currency, long amount) {
        setPivot(Pivot.LowerLeft);
        style.position.set(Position.Absolute);
        style.right.set(HUD_RIGHT, Unit.Pixel);
        style.bottom.set(HUD_BOTTOM, Unit.Pixel);
        // setSize(HUD_WIDTH, HUD_HEIGHT, false);
        style.width.set(HUD_WIDTH, Unit.Pixel);
        style.height.set(HUD_HEIGHT, Unit.Pixel);

        amountLabel = new UILabel(Long.toString(amount));
        amountLabel.setPivot(Pivot.UpperLeft);
        amountLabel.style.position.set(Position.Absolute);
        amountLabel.style.right.set(40, Unit.Pixel);
        amountLabel.style.top.set(0, Unit.Pixel);
        amountLabel.style.width.set(80, Unit.Pixel);
        amountLabel.style.height.set(HUD_HEIGHT, Unit.Pixel);
        amountLabel.setFont(Font.DefaultBold);
        amountLabel.setFontSize(18);
        amountLabel.setFontColor(0xF5D36AFF);
        amountLabel.setTextAlign(TextAnchor.MiddleRight);
        amountLabel.setTextWrap(false);
        addChild(amountLabel);

        icon = new OZUIElement();
        icon.setPivot(Pivot.UpperLeft);
        icon.style.position.set(Position.Absolute);
        icon.style.right.set(0, Unit.Pixel);
        // icon.style.top.set(5, Unit.Pixel);
        icon.setMargin(5f);
        // icon.setSize(ICON_SIZE, ICON_SIZE, false);
        icon.style.width.set(ICON_SIZE, Unit.Pixel);
        icon.style.height.set(ICON_SIZE, Unit.Pixel);
        icon.style.backgroundImageScaleMode.set(ScaleMode.ScaleToFit);
        addChild(icon);

        // setBackgroundColor(0x000000FF);
        // amountLabel.setBackgroundColor(0x008000FF);
        // icon.setBackgroundColor(0x800000FF);

        update(currency, amount);
    }

    public void update(WalletCurrency currency, long amount) {
        amountLabel.setText(Long.toString(amount));
        icon.style.backgroundImage.set(AssetManager.getIcon(currency.getIconKey()));
    }
}
