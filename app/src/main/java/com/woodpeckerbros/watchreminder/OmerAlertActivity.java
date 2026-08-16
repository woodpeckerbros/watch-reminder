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

public class OmerAlertActivity extends Activity {
    private static final int COLOR_BG = 0xFF061522;
    private static final int COLOR_SURFACE = 0xFF142A3A;
    private static final int COLOR_TEXT = 0xFFF4EBDD;
    private static final int COLOR_MUTED = 0xFFB8B7AE;
    private static final int COLOR_ACCENT = 0xFFC77B58;
    private static final int COLOR_ACCENT_DARK = 0xFF66745D;

    private OmerHelper.Item item;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable autoCloseRunnable;
    private boolean actionClosed;
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
        AppLog.d(this, "omer alert open");
        OmerReceiver.cancelNotification(this);

        long triggerAt = getIntent().getLongExtra(OmerScheduler.EXTRA_TRIGGER_AT, 0);
        item = triggerAt > 0 ? OmerHelper.itemForTrigger(this, triggerAt) : OmerHelper.dueNow(this, new ReminderSettings(this).omerOffsetMinutes());
        if (item == null || new OmerStore(this).isHandled(item.key)) {
            AppLog.d(this, "omer alert empty close");
            finish();
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(12), dp(14), dp(12), dp(14));
        content.setBackgroundColor(COLOR_BG);

        LinearLayout textArea = new LinearLayout(this);
        textArea.setOrientation(LinearLayout.VERTICAL);
        textArea.setGravity(Gravity.CENTER);

        TextView title = text("ספירת העומר", 19, COLOR_ACCENT);
        AppFont.bold(title);
        textArea.addView(title);

        TextView count = text(item.label, 18, COLOR_TEXT);
        count.setPadding(dp(4), dp(8), dp(4), dp(4));
        textArea.addView(count);

        TextView hint = text(AppLanguage.isEnglish(this) ? "Tonight is day " + item.day + " of the Omer" : "סופרים הערב יום " + item.day + " לעומר", 12, COLOR_MUTED);
        hint.setPadding(dp(4), 0, dp(4), dp(4));
        textArea.addView(hint);
        content.addView(textArea, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                0.65f
        ));

        Button counted = button("ספרתי", COLOR_ACCENT_DARK);
        counted.setOnClickListener(v -> {
            actionClosed = true;
            handler.removeCallbacksAndMessages(null);
            stopVibration();
            AppLog.d(this, "omer counted day=" + item.day);
            new OmerStore(this).markHandled(item.key);
            OmerScheduler.schedule(this);
            finish();
        });
        Button retry = button("תזכר אותי לעוד שעה", COLOR_SURFACE);
        retry.setOnClickListener(v -> {
            actionClosed = true;
            handler.removeCallbacksAndMessages(null);
            stopVibration();
            AppLog.d(this, "omer retry pressed day=" + item.day);
            OmerScheduler.scheduleRetry(this, 60);
            finish();
        });
        LinearLayout buttonArea = new LinearLayout(this);
        buttonArea.setOrientation(LinearLayout.VERTICAL);
        buttonArea.setGravity(Gravity.CENTER);
        buttonArea.addView(counted);
        buttonArea.addView(retry);
        content.addView(buttonArea, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.35f
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
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        resetAutoCloseTimer();
        return super.dispatchTouchEvent(event);
    }

    private void scheduleAutoClose() {
        ReminderSettings settings = new ReminderSettings(this);
        autoCloseRunnable = () -> {
            if (actionClosed) {
                return;
            }
            actionClosed = true;
            stopVibration();
            int minutes = new ReminderSettings(this).autoSnoozeMinutes();
            AppLog.w(this, "omer auto retry minutes=" + minutes + " day=" + item.day);
            OmerScheduler.scheduleRetry(this, minutes);
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
        button.setTextSize(value.length() > 9 ? 11 : 13);
        button.setAllCaps(false);
        button.setBackground(rounded(color));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
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
