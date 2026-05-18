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

    private static final float PANEL_WIDTH = 280f;
    private static final float PANEL_HEIGHT = 118f;
    private static final float ICON_SIZE = 42f;

    private final UILabel amountLabel;
    private final UILabel currencyLabel;
    private final OZUIElement icon;

    public WalletCurrencyHud(String titleText, WalletCurrency currency, long amount) {
        setPivot(Pivot.UpperLeft);
        style.position.set(Position.Absolute);
        style.left.set(78, Unit.Percent);
        style.top.set(20.5f, Unit.Percent);
        style.width.set(PANEL_WIDTH, Unit.Pixel);
        style.height.set(PANEL_HEIGHT, Unit.Pixel);
        setBackgroundColor(0f, 0f, 0f, 0.74f);
        setBorder(1);
        setBorderColor(0.95f, 0.75f, 0.25f, 0.42f);

        UILabel title = new UILabel(titleText);
        title.setPivot(Pivot.UpperLeft);
        title.setPosition(18, 12, false);
        title.style.width.set(244, Unit.Pixel);
        title.style.height.set(26, Unit.Pixel);
        title.setFont(Font.DefaultBold);
        title.setFontSize(20);
        title.setFontColor(0xFFFFFFFF);
        title.setTextAlign(TextAnchor.MiddleLeft);
        title.setTextWrap(false);
        addChild(title);

        OZUIElement content = new OZUIElement();
        content.setPivot(Pivot.UpperLeft);
        content.setPosition(16, 48, false);
        content.style.width.set(248, Unit.Pixel);
        content.style.height.set(52, Unit.Pixel);
        content.setBackgroundColor(0.08f, 0.08f, 0.08f, 0.55f);
        content.setBorder(1);
        content.setBorderColor(0.95f, 0.75f, 0.25f, 0.28f);
        addChild(content);

        icon = new OZUIElement();
        icon.setPivot(Pivot.UpperLeft);
        icon.style.position.set(Position.Absolute);
        icon.style.left.set(10, Unit.Pixel);
        icon.style.top.set(5, Unit.Pixel);
        icon.style.width.set(ICON_SIZE, Unit.Pixel);
        icon.style.height.set(ICON_SIZE, Unit.Pixel);
        icon.style.backgroundImageScaleMode.set(ScaleMode.ScaleToFit);
        content.addChild(icon);

        amountLabel = new UILabel(Long.toString(amount));
        amountLabel.setPivot(Pivot.UpperLeft);
        amountLabel.style.position.set(Position.Absolute);
        amountLabel.style.left.set(66, Unit.Pixel);
        amountLabel.style.top.set(5, Unit.Pixel);
        amountLabel.style.width.set(168, Unit.Pixel);
        amountLabel.style.height.set(27, Unit.Pixel);
        amountLabel.setFont(Font.DefaultBold);
        amountLabel.setFontSize(22);
        amountLabel.setFontColor(0xF5D36AFF);
        amountLabel.setTextAlign(TextAnchor.MiddleLeft);
        amountLabel.setTextWrap(false);
        content.addChild(amountLabel);

        currencyLabel = new UILabel("");
        currencyLabel.setPivot(Pivot.UpperLeft);
        currencyLabel.style.position.set(Position.Absolute);
        currencyLabel.style.left.set(67, Unit.Pixel);
        currencyLabel.style.top.set(31, Unit.Pixel);
        currencyLabel.style.width.set(168, Unit.Pixel);
        currencyLabel.style.height.set(16, Unit.Pixel);
        currencyLabel.setFont(Font.Default);
        currencyLabel.setFontSize(11);
        currencyLabel.setFontColor(0xD8D8D8FF);
        currencyLabel.setTextAlign(TextAnchor.MiddleLeft);
        currencyLabel.setTextWrap(false);
        content.addChild(currencyLabel);

        update(currency, amount);
    }

    public void update(WalletCurrency currency, long amount) {
        amountLabel.setText(Long.toString(amount));
        currencyLabel.setText(currency.getIdentifier());
        icon.style.backgroundImage.set(AssetManager.getIcon(currency.getIconKey()));
    }
}
