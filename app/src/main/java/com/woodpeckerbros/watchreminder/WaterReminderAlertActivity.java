package com.woodpeckerbros.watchreminder;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class WaterReminderAlertActivity extends Activity {
    private static final int COLOR_BG = 0xFF061522;
    private static final int COLOR_SURFACE = 0xFF142A3A;
    private static final int COLOR_TEXT = 0xFFF4EBDD;
    private static final int COLOR_MUTED = 0xFFB8C5CA;
    private static final int COLOR_WATER = 0xFF59CBE8;
    private static final int COLOR_DRANK = 0xFF2F7F8F;

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable autoCloseRunnable;
    private AlertFeedback feedback;
    private boolean closed;
    private long triggerAt;
    private int amountMl;

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
        WaterReminderReceiver.cancelNotification(this);

        triggerAt = getIntent().getLongExtra(WaterReminderScheduler.EXTRA_TRIGGER_AT, 0L);
        amountMl = getIntent().getIntExtra(WaterReminderReceiver.EXTRA_AMOUNT_ML, 0);
        if (triggerAt <= 0L || amountMl <= 0 || new WaterReminderStore(this).isHandled(triggerAt)) {
            finish();
            return;
        }
        int consumedMl = getIntent().getIntExtra(WaterReminderReceiver.EXTRA_CONSUMED_ML, 0);
        int targetMl = getIntent().getIntExtra(WaterReminderReceiver.EXTRA_TARGET_ML, 0);
        ReminderSettings settings = new ReminderSettings(this);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(28));
        content.setBackgroundColor(COLOR_BG);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_water_drop);
        icon.setContentDescription(getString(R.string.water_icon_description));
        content.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text(getString(R.string.water_alert_title), 17, COLOR_WATER);
        AppFont.bold(title);
        content.addView(title);

        TextView amount = text(getString(R.string.water_alert_amount, amountMl), 24, COLOR_TEXT);
        AppFont.bold(amount);
        amount.setPadding(0, dp(2), 0, dp(2));
        content.addView(amount);

        String progressText = ReminderSettings.WATER_MODE_DAILY_TARGET.equals(settings.waterMode())
                ? getString(R.string.water_alert_progress, consumedMl, targetMl)
                : getString(R.string.water_alert_fixed_interval, settings.waterIntervalMinutes());
        TextView progress = text(progressText, 11, COLOR_MUTED);
        content.addView(progress);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.CENTER);
        Button skip = button(getString(R.string.water_skip), COLOR_SURFACE);
        Button drank = button(getString(R.string.water_drank), COLOR_DRANK);
        Button snooze = button(getString(R.string.water_snooze), COLOR_SURFACE);
        skip.setOnClickListener(v -> close(false));
        drank.setOnClickListener(v -> close(true));
        snooze.setOnClickListener(v -> snooze());
        actions.addView(skip);
        actions.addView(drank);
        actions.addView(snooze);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(4);
        content.addView(actions, actionsParams);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalFadingEdgeEnabled(false);
        scroll.setPadding(0, 0, 0, dp(16));
        scroll.setBackgroundColor(COLOR_BG);
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        AppTextStyle.apply(root);
        setContentView(root);
        feedback = AlertFeedback.start(this, settings);
        scheduleAutoClose(settings);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopFeedback();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!closed && autoCloseRunnable != null) {
            handler.removeCallbacks(autoCloseRunnable);
            handler.postDelayed(autoCloseRunnable, new ReminderSettings(this).autoSnoozeDelayMs());
        }
        return super.dispatchTouchEvent(event);
    }

    private void close(boolean drank) {
        if (closed) {
            return;
        }
        closed = true;
        handler.removeCallbacksAndMessages(null);
        stopFeedback();
        new WaterReminderStore(this).markHandled(triggerAt, amountMl, drank);
        WaterReminderReceiver.cancelNotification(this);
        WaterReminderScheduler.schedule(this);
        ComplicationRefresh.requestWater(this);
        finish();
    }

    private void snooze() {
        if (closed) return;
        closed = true;
        handler.removeCallbacksAndMessages(null);
        stopFeedback();
        WaterReminderReceiver.cancelNotification(this);
        WaterReminderScheduler.scheduleSnooze(this, 15);
        finish();
    }

    private void scheduleAutoClose(ReminderSettings settings) {
        autoCloseRunnable = () -> {
            if (!closed) {
                stopFeedback();
                finish();
            }
        };
        handler.postDelayed(autoCloseRunnable, settings.autoSnoozeDelayMs());
    }

    private void stopFeedback() {
        if (feedback != null) {
            feedback.stop();
            feedback = null;
        } else {
            AlertFeedback.stopVibration(this);
        }
    }

    private Button button(String value, int color) {
        Button button = new Button(this);
        AppFont.apply(button);
        button.setText(value);
        button.setTextColor(android.graphics.Color.WHITE);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(18));
        if (color == COLOR_SURFACE) {
            background.setStroke(dp(1), COLOR_WATER);
        }
        button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36));
        params.setMargins(dp(3), dp(2), dp(3), dp(2));
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        AppFont.apply(view);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTextDirection(AppLanguage.isRtl(this) ? TextView.TEXT_DIRECTION_RTL : TextView.TEXT_DIRECTION_LTR);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
