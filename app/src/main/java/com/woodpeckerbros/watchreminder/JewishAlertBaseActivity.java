package com.woodpeckerbros.watchreminder;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.woodpeckerbros.watchreminder.reminder.ReminderAlertFrameView;

/**
 * Shared, scrollable shell for every Jewish-time alert. Subclasses provide only their
 * message-specific views and actions; the visual hierarchy is deliberately built here.
 */
public abstract class JewishAlertBaseActivity extends Activity {
    protected static final int COLOR_BG = 0xFF061522;
    protected static final int COLOR_SURFACE = 0xFF142A3A;
    protected static final int COLOR_TEXT = 0xFFF4EBDD;
    protected static final int COLOR_MUTED = 0xFFB8B7AE;
    protected static final int COLOR_ACCENT = 0xFFE0C38D;
    protected static final int COLOR_ACTION = 0xFF738368;

    @Override protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
    }

    protected final LinearLayout jewishAlertContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(17), dp(42), dp(17), dp(22));
        return content;
    }

    /** Creates the icon and heading that must remain identical on all Jewish alert screens. */
    protected final LinearLayout jewishAlertCard(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(9), dp(4), dp(9), dp(12));

        FrameLayout iconBadge = new FrameLayout(this);
        iconBadge.setBackground(iconBadgeBackground());
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_jewish_alert);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        iconBadge.addView(icon, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(56), dp(56));
        iconParams.setMargins(0, 0, 0, dp(4));
        card.addView(iconBadge, iconParams);

        TextView heading = jewishAlertText(title, 22, COLOR_TEXT);
        AppFont.bold(heading);
        heading.setPadding(dp(4), dp(1), dp(4), dp(5));
        heading.setShadowLayer(dp(2), 0, 0, 0xAAFFF3D5);
        card.addView(heading);
        return card;
    }

    protected final TextView jewishAlertText(String value, int sp, int color) {
        TextView view = new TextView(this);
        AppFont.apply(view);
        view.setText(UiText.t(this, value));
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTextDirection(AppLanguage.isRtl(this)
                ? TextView.TEXT_DIRECTION_RTL : TextView.TEXT_DIRECTION_LTR);
        return view;
    }

    protected final Button jewishAlertButton(String label, int color) {
        Button button = new Button(this);
        AppFont.apply(button);
        button.setText(UiText.t(this, label));
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(new DepthButtonDrawable(color, dp(20)));
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    /** Installs the common decorative frame and the always-scrollable content area. */
    protected final void setJewishAlertContent(LinearLayout content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        root.addView(new ReminderAlertFrameView(this), new FrameLayout.LayoutParams(-1, -1));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        TopArcClockView clock = TopArcClockView.addTo(root);
        clock.setTranslationY(dp(4));
        root.addView(new ReminderAlertFrameView(this, false), new FrameLayout.LayoutParams(-1, -1));
        AppTextStyle.apply(root);
        setContentView(root);
    }

    protected final int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable iconBadgeBackground() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF28526A, 0xFF0B2537});
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setStroke(dp(1), COLOR_ACCENT);
        return drawable;
    }
}
