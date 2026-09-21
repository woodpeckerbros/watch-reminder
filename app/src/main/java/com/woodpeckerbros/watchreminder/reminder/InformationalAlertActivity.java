package com.woodpeckerbros.watchreminder.reminder;

import com.woodpeckerbros.watchreminder.*;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class InformationalAlertActivity extends Activity {
    private static final int COLOR_BG = 0xFF061522;
    private static final int COLOR_SURFACE = 0xFF142A3A;
    private static final int COLOR_TEXT = 0xFFF4EBDD;
    private static final int COLOR_MUTED = 0xFFB8B7AE;
    private static final int COLOR_ACCENT = 0xFFE0C38D;
    private static final int COLOR_ACTION = 0xFF738368;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String key;
    private String title;
    private String message;
    private AlertFeedback feedback;
    private boolean closed;

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(AppLanguage.wrap(base)); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        key = getIntent().getStringExtra(InformationalAlertReceiver.EXTRA_KEY);
        title = getIntent().getStringExtra(InformationalAlertReceiver.EXTRA_TITLE);
        message = getIntent().getStringExtra(InformationalAlertReceiver.EXTRA_MESSAGE);
        if (key == null || title == null || message == null) { finish(); return; }
        InformationalAlertReceiver.dismissNotification(this, key);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(17), dp(42), dp(17), dp(22));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(9), dp(4), dp(9), dp(12));

        FrameLayout iconBadge = new FrameLayout(this);
        iconBadge.setBackground(iconBadgeBackground());
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_jewish_alert);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int iconInset = dp(8);
        icon.setPadding(iconInset, iconInset, iconInset, iconInset);
        iconBadge.addView(icon, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(56), dp(56));
        iconParams.setMargins(0, 0, 0, dp(4));
        card.addView(iconBadge, iconParams);

        TextView heading = text(title, 22, COLOR_TEXT);
        AppFont.bold(heading);
        heading.setPadding(dp(4), dp(1), dp(4), dp(5));
        heading.setShadowLayer(dp(2), 0, 0, 0xAAFFF3D5);
        card.addView(heading);
        TextView body = text(message, 15, COLOR_MUTED);
        body.setLineSpacing(dp(2), 1f);
        body.setPadding(dp(5), 0, dp(5), dp(9));
        card.addView(body);

        Button done = button("הבנתי", COLOR_ACTION);
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(-1, dp(48));
        doneParams.setMargins(dp(3), dp(4), dp(3), dp(3));
        done.setLayoutParams(doneParams);
        done.setTextSize(18);
        done.setShadowLayer(dp(2), 0, 0, 0xAAFFF3D5);
        done.setOnClickListener(v -> complete());
        card.addView(done);

        TextView snoozeTitle = text("אפשר לדחות", 13, COLOR_MUTED);
        snoozeTitle.setPadding(0, dp(8), 0, dp(3));
        card.addView(snoozeTitle);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.addView(snooze("15 דקות", 15));
        row.addView(snooze("30 דקות", 30));
        card.addView(row);

        content.addView(card, new LinearLayout.LayoutParams(-1, -2));
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
        feedback = AlertFeedback.start(this, new ReminderSettings(this));
        handler.postDelayed(this::autoClose, new ReminderSettings(this).autoSnoozeDelayMs());
    }

    private void complete() {
        closed = true;
        InformationalAlertReceiver.complete(this, key);
        stopFeedback();
        finish();
    }

    private Button snooze(String label, int minutes) {
        Button button = button(label, COLOR_SURFACE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        button.setLayoutParams(params);
        button.setOnClickListener(v -> {
            closed = true;
            InformationalAlertReceiver.complete(this, key);
            InformationalAlertReceiver.scheduleRetry(this, key, title, message, minutes);
            stopFeedback();
            finish();
        });
        return button;
    }

    private void autoClose() { if (!closed) { InformationalAlertReceiver.dismissNotification(this, key); finish(); } }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); stopFeedback(); super.onDestroy(); }
    private void stopFeedback() { if (feedback != null) { feedback.stop(); feedback = null; } }
    private Button button(String label, int color) { Button b = new Button(this); AppFont.apply(b); b.setText(UiText.t(this, label)); b.setTextColor(COLOR_TEXT); b.setTextSize(14); b.setAllCaps(false); b.setBackground(new DepthButtonDrawable(color, dp(20))); b.setPadding(0, 0, 0, 0); return b; }
    private TextView text(String value, int size, int color) { TextView v = new TextView(this); AppFont.apply(v); v.setText(UiText.t(this, value)); v.setTextSize(size); v.setTextColor(color); v.setGravity(Gravity.CENTER); v.setTextDirection(AppLanguage.isRtl(this) ? TextView.TEXT_DIRECTION_RTL : TextView.TEXT_DIRECTION_LTR); return v; }
    private GradientDrawable iconBadgeBackground() { GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF28526A, 0xFF0B2537}); d.setShape(GradientDrawable.OVAL); d.setStroke(dp(1), COLOR_ACCENT); return d; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
