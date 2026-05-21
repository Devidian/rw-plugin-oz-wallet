package de.omegazirkel.risingworld.wallet.ui;

import java.util.List;

import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import de.omegazirkel.risingworld.wallet.WalletBalance;
import de.omegazirkel.risingworld.wallet.WalletCurrency;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.UIScrollView;
import net.risingworld.api.ui.UIScrollView.ScrollViewMode;
import net.risingworld.api.ui.UIScrollView.ScrollerVisibility;
import net.risingworld.api.ui.style.Font;
import net.risingworld.api.ui.style.Pivot;
import net.risingworld.api.ui.style.Position;
import net.risingworld.api.ui.style.ScaleMode;
import net.risingworld.api.ui.style.TextAnchor;
import net.risingworld.api.ui.style.Unit;

public class WalletCurrencyHud extends OZUIElement {
    public static final String ATTRIBUTE_KEY = "wallet.ui.currencyHud";

    private static final float PANEL_WIDTH = 280f;
    private static final float PANEL_HEIGHT = 50f;
    private static final float ROW_HEIGHT = 66f;
    private static final float ICON_SIZE = 28f;

    private final OZUIElement rows;
    private final UIScrollView scrollView;

    public WalletCurrencyHud(String titleText, List<WalletBalance> balances) {
        setPivot(Pivot.UpperLeft);
        style.position.set(Position.Absolute);
        style.left.set(78, Unit.Percent);
        style.top.set(20.5f, Unit.Percent);
        style.width.set(PANEL_WIDTH, Unit.Pixel);
        style.height.set(PANEL_HEIGHT, Unit.Percent);
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

        scrollView = new UIScrollView(ScrollViewMode.Vertical);
        scrollView.setPivot(Pivot.UpperLeft);
        scrollView.setPosition(14, 48, false);
        scrollView.style.width.set(252, Unit.Pixel);
        scrollView.style.height.set(212, Unit.Pixel);
        scrollView.setHorizontalScrollerVisibility(ScrollerVisibility.Hidden);
        scrollView.setVerticalScrollerVisibility(ScrollerVisibility.Hidden);
        scrollView.setMouseWheelScrollSize(24);
        scrollView.setBackgroundColor(0.08f, 0.08f, 0.08f, 0.55f);
        scrollView.setBorder(1);
        scrollView.setBorderColor(0.95f, 0.75f, 0.25f, 0.28f);
        addChild(scrollView);

        rows = new OZUIElement();
        rows.setPivot(Pivot.UpperLeft);
        rows.setAbsolute();
        rows.style.width.set(236, Unit.Pixel);
        rows.style.height.set(100, Unit.Percent);
        scrollView.addChild(rows);

        update(balances);
    }

    public void update(List<WalletBalance> balances) {
        rows.removeAllChilds();
        int visibleRows = balances == null || balances.isEmpty() ? 1 : Math.min(balances.size(), 8);
        float contentHeight = visibleRows * ROW_HEIGHT;
        int totalRows = balances == null || balances.isEmpty() ? 1 : balances.size();
        style.height.set(66 + contentHeight, Unit.Pixel);
        scrollView.style.height.set(contentHeight, Unit.Pixel);
        rows.style.height.set(totalRows * ROW_HEIGHT, Unit.Pixel);
        if (balances == null || balances.isEmpty()) {
            rows.addChild(emptyRow());
            return;
        }
        int index = 0;
        for (WalletBalance balance : balances) {
            rows.addChild(balanceRow(balance, index));
            index++;
        }
    }

    public void update(WalletCurrency currency, long amount) {
        update(List.of(new WalletBalance(0, currency, amount, 0L)));
    }

    private OZUIElement balanceRow(WalletBalance balance, int index) {
        WalletCurrency currency = balance.getCurrency();
        OZUIElement row = new OZUIElement();
        row.setPivot(Pivot.UpperLeft);
        row.setAbsolute();
        row.style.left.set(0, Unit.Pixel);
        row.style.top.set(index * ROW_HEIGHT, Unit.Pixel);
        row.style.width.set(100, Unit.Percent);
        row.style.height.set(ROW_HEIGHT, Unit.Pixel);

        OZUIElement icon = new OZUIElement();
        icon.setPivot(Pivot.UpperLeft);
        icon.style.position.set(Position.Absolute);
        icon.style.left.set(10, Unit.Pixel);
        icon.style.top.set(7, Unit.Pixel);
        icon.style.width.set(ICON_SIZE, Unit.Pixel);
        icon.style.height.set(ICON_SIZE, Unit.Pixel);
        icon.style.backgroundImage.set(AssetManager.getIcon(currency.getIconKey()));
        icon.style.backgroundImageScaleMode.set(ScaleMode.ScaleToFit);
        row.addChild(icon);

        UILabel amountLabel = new UILabel(balance.getBalance() + " " + currency.getIdentifier());
        amountLabel.setPivot(Pivot.UpperLeft);
        amountLabel.style.position.set(Position.Absolute);
        amountLabel.style.left.set(48, Unit.Pixel);
        amountLabel.style.top.set(4, Unit.Pixel);
        amountLabel.style.width.set(188, Unit.Pixel);
        amountLabel.style.height.set(22, Unit.Pixel);
        amountLabel.setFont(Font.DefaultBold);
        amountLabel.setFontSize(17);
        amountLabel.setFontColor(0xF5D36AFF);
        amountLabel.setTextAlign(TextAnchor.MiddleLeft);
        amountLabel.setTextWrap(false);
        row.addChild(amountLabel);

        UILabel nameLabel = new UILabel(currency.getName());
        nameLabel.setPivot(Pivot.UpperLeft);
        nameLabel.style.position.set(Position.Absolute);
        nameLabel.style.left.set(49, Unit.Pixel);
        nameLabel.style.top.set(25, Unit.Pixel);
        nameLabel.style.width.set(188, Unit.Pixel);
        nameLabel.style.height.set(15, Unit.Pixel);
        nameLabel.setFont(Font.Default);
        nameLabel.setFontSize(11);
        nameLabel.setFontColor(0xD8D8D8FF);
        nameLabel.setTextAlign(TextAnchor.MiddleLeft);
        nameLabel.setTextWrap(false);
        row.addChild(nameLabel);

        return row;
    }

    private UILabel emptyRow() {
        UILabel label = new UILabel("-");
        label.setPivot(Pivot.UpperLeft);
        label.style.width.set(100, Unit.Percent);
        label.style.height.set(30, Unit.Pixel);
        label.setFont(Font.Default);
        label.setFontSize(16);
        label.setFontColor(0xD8D8D8FF);
        label.setTextAlign(TextAnchor.MiddleLeft);
        return label;
    }
}
