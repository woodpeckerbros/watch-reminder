package com.woodpeckerbros.watchreminder.calendar;

import com.woodpeckerbros.watchreminder.reminder.*;

import com.woodpeckerbros.watchreminder.*;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MoonBlessingAlertActivity extends JewishAlertBaseActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable autoCloseRunnable;
    private boolean actionClosed;
    private String monthKey;
    private String kind;
    private long triggerAt;
    private AlertFeedback alertFeedback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MoonBlessingReceiver.cancelNotification(this);

        monthKey = getIntent().getStringExtra(MoonBlessingScheduler.EXTRA_MONTH_KEY);
        kind = getIntent().getStringExtra(MoonBlessingScheduler.EXTRA_KIND);
        triggerAt = getIntent().getLongExtra(MoonBlessingScheduler.EXTRA_TRIGGER_AT,
                ReminderScheduler.floorToMinute(System.currentTimeMillis()));
        String messageText = getIntent().getStringExtra("moon_alert_message");
        if (messageText == null || messageText.trim().isEmpty()) {
            messageText = UiText.t(this, "הלילה יהיה אפשר להתחיל לברך ברכת הלבנה");
        }
        AppLog.d(this, "moon blessing pre-start alert open month=" + monthKey
                + " trigger=" + NextReminderCalculator.formatDateTime(triggerAt));

        LinearLayout content = jewishAlertContent();
        LinearLayout card = jewishAlertCard("ברכת הלבנה");

        TextView message = jewishAlertText(messageText, 15, COLOR_MUTED);
        message.setLineSpacing(dp(2), 1f);
        message.setPadding(dp(5), 0, dp(5), dp(9));
        card.addView(message);

        if (MoonBlessingScheduler.KIND_PRE_START.equals(kind)) {
            Button done = button("בוצע", COLOR_ACTION);
            LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(-1, dp(48));
            doneParams.setMargins(dp(3), dp(4), dp(3), dp(3));
            done.setLayoutParams(doneParams);
            done.setTextSize(18);
            done.setShadowLayer(dp(2), 0, 0, 0xAAFFF3D5);
            done.setOnClickListener(v -> finishDone());
            card.addView(done);
        } else {
            LinearLayout answerRow = new LinearLayout(this);
            answerRow.setGravity(Gravity.CENTER);
            Button yes = button("כן", COLOR_ACTION);
            Button no = button("לא", COLOR_SURFACE);
            LinearLayout.LayoutParams answerParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
            answerParams.setMargins(dp(3), dp(3), dp(3), dp(3));
            yes.setLayoutParams(answerParams);
            LinearLayout.LayoutParams noParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
            noParams.setMargins(dp(3), dp(3), dp(3), dp(3));
            no.setLayoutParams(noParams);
            yes.setOnClickListener(v -> answer(true));
            no.setOnClickListener(v -> answer(false));
            answerRow.addView(yes);
            answerRow.addView(no);
            card.addView(answerRow);
        }

        TextView snoozeTitle = jewishAlertText("אפשר לדחות", 13, COLOR_MUTED);
        snoozeTitle.setPadding(0, dp(8), 0, dp(3));
        card.addView(snoozeTitle);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.addView(snoozeButton("15 דקות", 15));
        row.addView(snoozeButton("30 דקות", 30));
        card.addView(row);

        content.addView(card, new LinearLayout.LayoutParams(-1, -2));
        setJewishAlertContent(content);

        alertFeedback = AlertFeedback.start(this, new ReminderSettings(this));
        scheduleAutoClose();
    }

    private Button snoozeButton(String label, int minutes) {
        Button button = button(label, COLOR_SURFACE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
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
        MoonBlessingScheduler.scheduleRetry(this, kind, monthKey, triggerAt, minutes);
        MoonBlessingReceiver.cancelPreStartNotification(this);
        AppLog.d(this, "moon blessing pre-start alert snooze minutes=" + minutes);
        finish();
    }

    private void answer(boolean blessed) {
        actionClosed = true;
        stopFeedback();
        handler.removeCallbacksAndMessages(null);
        MoonBlessingScheduler.cancelRetry(this);
        MoonBlessingReceiver.cancelNotification(this);
        if (blessed) new MoonBlessingStore(this).markHandled(monthKey);
        MoonBlessingScheduler.schedule(this);
        AppLog.d(this, "moon blessing alert answer blessed=" + blessed + " month=" + monthKey);
        finish();
    }

    private void scheduleAutoClose() {
        ReminderSettings settings = new ReminderSettings(this);
        autoCloseRunnable = () -> {
            if (!actionClosed) {
                AppLog.w(this, "moon blessing alert auto close; retry remains scheduled kind=" + kind);
                MoonBlessingReceiver.cancelNotification(this);
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
        return jewishAlertButton(value, color);
    }
}
