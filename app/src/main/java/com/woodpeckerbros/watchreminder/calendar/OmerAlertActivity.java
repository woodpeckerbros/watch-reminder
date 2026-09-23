package com.woodpeckerbros.watchreminder.calendar;

import com.woodpeckerbros.watchreminder.reminder.*;

import com.woodpeckerbros.watchreminder.*;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OmerAlertActivity extends JewishAlertBaseActivity {

    private OmerHelper.Item item;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable autoCloseRunnable;
    private boolean actionClosed;
    private AlertFeedback alertFeedback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLog.d(this, "omer alert open");
        OmerReceiver.cancelNotification(this);

        long triggerAt = getIntent().getLongExtra(OmerScheduler.EXTRA_TRIGGER_AT, 0);
        item = triggerAt > 0 ? OmerHelper.itemForTrigger(this, triggerAt) : OmerHelper.dueNow(this, new ReminderSettings(this).omerOffsetMinutes());
        if (item == null || new OmerStore(this).isHandled(item.key)) {
            AppLog.d(this, "omer alert empty close");
            finish();
            return;
        }

        LinearLayout content = jewishAlertContent();
        LinearLayout card = jewishAlertCard("ספירת העומר");
        TextView count = jewishAlertText(item.label, 18, COLOR_TEXT);
        count.setPadding(dp(4), dp(8), dp(4), dp(4));
        card.addView(count);

        TextView hint = jewishAlertText(AppLanguage.isEnglish(this) ? "Tonight is day " + item.day + " of the Omer" : "סופרים הערב יום " + item.day + " לעומר", 12, COLOR_MUTED);
        hint.setPadding(dp(4), 0, dp(4), dp(4));
        card.addView(hint);

        Button counted = button("ספרתי", COLOR_ACTION);
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
        card.addView(buttonArea, new LinearLayout.LayoutParams(-1, -2));
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));
        setJewishAlertContent(content);
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
        Button button = jewishAlertButton(value, color);
        button.setTextSize(value.length() > 9 ? 11 : 13);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(dp(10), dp(5), dp(10), dp(5));
        button.setLayoutParams(params);
        return button;
    }

}
