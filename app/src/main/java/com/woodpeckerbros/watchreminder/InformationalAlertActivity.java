package com.woodpeckerbros.watchreminder;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class InformationalAlertActivity extends Activity {
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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        key = getIntent().getStringExtra(InformationalAlertReceiver.EXTRA_KEY);
        title = getIntent().getStringExtra(InformationalAlertReceiver.EXTRA_TITLE);
        message = getIntent().getStringExtra(InformationalAlertReceiver.EXTRA_MESSAGE);
        if (key == null || title == null || message == null) { finish(); return; }
        InformationalAlertReceiver.dismissNotification(this, key);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(14), dp(10), dp(14), dp(12));
        card.setBackground(rounded(0xFF0B2133));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_alert_ringing_bell);
        card.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView heading = text(title, 20, 0xFFF4EBDD);
        AppFont.bold(heading);
        card.addView(heading);
        TextView body = text(message, 14, 0xFFB8B7AE);
        body.setPadding(dp(3), dp(5), dp(3), dp(8));
        card.addView(body);
        Button done = button("הבנתי");
        done.setOnClickListener(v -> complete());
        card.addView(done);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.addView(snooze("15 דקות", 15));
        row.addView(snooze("30 דקות", 30));
        card.addView(row);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setPadding(dp(14), dp(18), dp(14), dp(18));
        scroll.setBackgroundColor(0xFF061522);
        scroll.addView(card, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
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
        Button button = button(label);
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
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); stopFeedback(); getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); super.onDestroy(); }
    private void stopFeedback() { if (feedback != null) { feedback.stop(); feedback = null; } }
    private Button button(String label) { Button b = new Button(this); AppFont.apply(b); b.setText(UiText.t(this, label)); b.setTextColor(0xFFF4EBDD); b.setTextSize(14); b.setAllCaps(false); b.setBackground(rounded(0xFF66745D)); b.setLayoutParams(new LinearLayout.LayoutParams(dp(160), dp(42))); return b; }
    private TextView text(String value, int size, int color) { TextView v = new TextView(this); AppFont.apply(v); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setGravity(Gravity.CENTER); return v; }
    private GradientDrawable rounded(int color) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(18)); d.setStroke(dp(1), 0x33747D63); return d; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
