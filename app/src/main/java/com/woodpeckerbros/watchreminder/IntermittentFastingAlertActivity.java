package com.woodpeckerbros.watchreminder;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class IntermittentFastingAlertActivity extends Activity {
    private static final int COLOR_BG = 0xFF061522;
    private static final int COLOR_SURFACE = 0xFF142A3A;
    private static final int COLOR_TEXT = 0xFFF4EBDD;
    private static final int COLOR_MUTED = 0xFFB8B7AE;
    private static final int COLOR_ACCENT = 0xFFC77B58;
    private static final int COLOR_ACCENT_DARK = 0xFF66745D;

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable autoCloseRunnable;
    private boolean actionClosed;
    private String eventType;
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
        IntermittentFastingReceiver.cancelNotification(this);

        eventType = getIntent().getStringExtra(IntermittentFastingScheduler.EXTRA_EVENT_TYPE);
        triggerAt = getIntent().getLongExtra(IntermittentFastingScheduler.EXTRA_TRIGGER_AT, 0L);
        if (triggerAt <= 0L) {
            triggerAt = ReminderScheduler.floorToMinute(System.currentTimeMillis());
        }
        IntermittentFastingStore store = new IntermittentFastingStore(this);
        if (eventType == null || store.isAlertAcknowledged(eventType, triggerAt)) {
            AppLog.d(this, "fasting alert close empty/acknowledged");
            finish();
            return;
        }

        String titleText = getIntent().getStringExtra("fasting_alert_title");
        String messageText = getIntent().getStringExtra("fasting_alert_message");
        if (titleText == null || titleText.trim().isEmpty()) {
            titleText = "צום לסירוגין";
        }
        if (messageText == null || messageText.trim().isEmpty()) {
            messageText = "יש לך תזכורת לצום לסירוגין.";
        }
        AppLog.d(this, "fasting alert open type=" + eventType + " trigger=" + NextReminderCalculator.formatDateTime(triggerAt));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(10), dp(10), dp(10), dp(10));
        content.setBackgroundColor(COLOR_BG);

        LinearLayout textArea = new LinearLayout(this);
        textArea.setOrientation(LinearLayout.VERTICAL);
        textArea.setGravity(Gravity.CENTER);

        TextView title = text(titleText, 17, COLOR_ACCENT);
        AppFont.bold(title);
        textArea.addView(title);

        TextView message = text(messageText, 14, COLOR_TEXT);
        message.setPadding(dp(4), dp(6), dp(4), dp(3));
        textArea.addView(message);

        TextView hint = text("תחזור עד שתלחץ הבנתי.", 11, COLOR_MUTED);
        hint.setPadding(dp(4), dp(1), dp(4), dp(3));
        textArea.addView(hint);
        content.addView(textArea, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.25f
        ));

        Button understood = button("הבנתי", COLOR_ACCENT_DARK);
        understood.setOnClickListener(v -> acknowledge());
        LinearLayout buttonArea = new LinearLayout(this);
        buttonArea.setGravity(Gravity.CENTER);
        buttonArea.addView(understood);
        content.addView(buttonArea, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                0.75f
        ));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        root.addView(content, new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        setContentView(root);
        startVibration(new ReminderSettings(this));
        scheduleAutoClose();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopVibration();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        resetAutoCloseTimer();
        return super.dispatchTouchEvent(event);
    }

    private void acknowledge() {
        actionClosed = true;
        handler.removeCallbacksAndMessages(null);
        stopVibration();
        new IntermittentFastingStore(this).acknowledgeAlert(eventType, triggerAt);
        IntermittentFastingScheduler.cancelRetry(this);
        IntermittentFastingReceiver.cancelNotification(this);
        IntermittentFastingScheduler.schedule(this);
        AppLog.d(this, "fasting alert acknowledged type=" + eventType + " trigger=" + NextReminderCalculator.formatDateTime(triggerAt));
        finish();
    }

    private void scheduleAutoClose() {
        ReminderSettings settings = new ReminderSettings(this);
        autoCloseRunnable = () -> {
            if (actionClosed) {
                return;
            }
            AppLog.w(this, "fasting alert auto close type=" + eventType + " trigger=" + NextReminderCalculator.formatDateTime(triggerAt));
            finish();
        };
        handler.postDelayed(autoCloseRunnable, settings.autoSnoozeDelayMs());
    }

    private void resetAutoCloseTimer() {
        if (actionClosed || autoCloseRunnable == null) {
            return;
        }
        ReminderSettings settings = new ReminderSettings(this);
        handler.removeCallbacks(autoCloseRunnable);
        handler.postDelayed(autoCloseRunnable, settings.autoSnoozeDelayMs());
    }

    private void startVibration(ReminderSettings settings) {
        stopVibration();
        alertFeedback = AlertFeedback.start(this, settings);
    }

    private void stopVibration() {
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
        button.setTextColor(android.graphics.Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(rounded(color));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        params.setMargins(dp(10), dp(5), dp(10), dp(5));
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
        view.setTextDirection(AppLanguage.isRtl(this) ? TextView.TEXT_DIRECTION_RTL : TextView.TEXT_DIRECTION_LTR);
        return view;
    }

    private android.graphics.drawable.GradientDrawable rounded(int color) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(18));
        if (color == COLOR_SURFACE) drawable.setStroke(dp(1), COLOR_ACCENT);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
