package com.woodpeckerbros.watchreminder;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MoonBlessingAlertActivity extends Activity {
    private static final int COLOR_BG = 0xFF061522;
    private static final int COLOR_SURFACE = 0xFF0B2133;
    private static final int COLOR_TEXT = 0xFFF4EBDD;
    private static final int COLOR_MUTED = 0xFFB8B7AE;
    private static final int COLOR_ACCENT = 0xFFC77B58;
    private static final int COLOR_ACTION = 0xFF66745D;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable autoCloseRunnable;
    private boolean actionClosed;
    private String monthKey;
    private long triggerAt;
    private AlertFeedback alertFeedback;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        MoonBlessingReceiver.cancelPreStartNotification(this);

        monthKey = getIntent().getStringExtra(MoonBlessingScheduler.EXTRA_MONTH_KEY);
        triggerAt = getIntent().getLongExtra(MoonBlessingScheduler.EXTRA_TRIGGER_AT,
                ReminderScheduler.floorToMinute(System.currentTimeMillis()));
        String messageText = getIntent().getStringExtra("moon_alert_message");
        if (messageText == null || messageText.trim().isEmpty()) {
            messageText = UiText.t(this, "הלילה יהיה אפשר להתחיל לברך ברכת הלבנה");
        }
        AppLog.d(this, "moon blessing pre-start alert open month=" + monthKey
                + " trigger=" + NextReminderCalculator.formatDateTime(triggerAt));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(14), dp(10), dp(14), dp(12));
        card.setBackground(rounded(COLOR_SURFACE, 20));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_alert_ringing_bell);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        card.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = text("ברכת הלבנה", 20, COLOR_TEXT);
        AppFont.bold(title);
        title.setPadding(0, dp(2), 0, dp(5));
        card.addView(title);

        TextView message = text(messageText, 14, COLOR_MUTED);
        message.setPadding(dp(4), 0, dp(4), dp(7));
        card.addView(message);

        Button done = button("בוצע", COLOR_ACTION);
        done.setOnClickListener(v -> finishDone());
        card.addView(done);

        TextView snoozeTitle = text("אפשר לדחות", 12, COLOR_MUTED);
        snoozeTitle.setPadding(0, dp(7), 0, dp(2));
        card.addView(snoozeTitle);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.addView(snoozeButton("15 דקות", 15));
        row.addView(snoozeButton("30 דקות", 30));
        card.addView(row);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setPadding(dp(14), dp(18), dp(14), dp(18));
        scroll.setClipToPadding(false);
        scroll.addView(card, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        alertFeedback = AlertFeedback.start(this, new ReminderSettings(this));
        scheduleAutoClose();
    }

    private Button snoozeButton(String label, int minutes) {
        Button button = button(label, COLOR_SURFACE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1f);
        params.setMargins(dp(3), dp(2), dp(3), dp(2));
        button.setLayoutParams(params);
        button.setOnClickListener(v -> snooze(minutes));
        return button;
    }

    private void finishDone() {
        actionClosed = true;
        stopFeedback();
        handler.removeCallbacksAndMessages(null);
        MoonBlessingScheduler.cancelRetry(this);
        MoonBlessingReceiver.cancelPreStartNotification(this);
        AppLog.d(this, "moon blessing pre-start alert done month=" + monthKey);
        finish();
    }

    private void snooze(int minutes) {
        actionClosed = true;
        stopFeedback();
        handler.removeCallbacksAndMessages(null);
        MoonBlessingScheduler.schedulePreStartRetry(this, monthKey, triggerAt, minutes);
        MoonBlessingReceiver.cancelPreStartNotification(this);
        AppLog.d(this, "moon blessing pre-start alert snooze minutes=" + minutes);
        finish();
    }

    private void scheduleAutoClose() {
        ReminderSettings settings = new ReminderSettings(this);
        autoCloseRunnable = () -> {
            if (!actionClosed) {
                AppLog.w(this, "moon blessing pre-start alert auto close; retry remains scheduled");
                finish();
            }
        };
        handler.postDelayed(autoCloseRunnable, settings.autoSnoozeDelayMs());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!actionClosed && autoCloseRunnable != null) {
            handler.removeCallbacks(autoCloseRunnable);
            handler.postDelayed(autoCloseRunnable, new ReminderSettings(this).autoSnoozeDelayMs());
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopFeedback();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }

    private void stopFeedback() {
        if (alertFeedback != null) {
            alertFeedback.stop();
            alertFeedback = null;
        } else {
            AlertFeedback.stopVibration(this);
        }
    }

    private Button button(String value, int color) {
        Button button = new Button(this);
        AppFont.apply(button);
        button.setText(UiText.t(this, value));
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(rounded(color, 18));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(160), dp(42));
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        AppFont.apply(view);
        view.setText(UiText.t(this, value));
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), 0x33747D63);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
