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

import java.util.List;

public class DafYomiAlertActivity extends JewishAlertBaseActivity {
    private List<DafYomiHelper.Item> dueItems;
    private DafYomiHelper.Item currentItem;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable autoCloseRunnable;
    private boolean actionClosed;
    private AlertFeedback alertFeedback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLog.d(this, "daf yomi alert open");
        DafYomiReceiver.cancelNotification(this);
        dueItems = new DafYomiStore(this).dueItems(this);
        if (dueItems.isEmpty()) {
            AppLog.d(this, "daf yomi alert empty close");
            finish();
            return;
        }
        currentItem = dueItems.get(0);
        LinearLayout content = jewishAlertContent();
        LinearLayout card = jewishAlertCard("דף היומי");
        TextView question = jewishAlertText(questionText(), 19, COLOR_TEXT);
        question.setPadding(dp(4), dp(8), dp(4), dp(8));
        card.addView(question);

        if (dueItems.size() > 1) {
            TextView counter = jewishAlertText(AppLanguage.isEnglish(this) ? "Page 1 of " + dueItems.size() : "דף " + 1 + " מתוך " + dueItems.size(), 12, COLOR_MUTED);
            counter.setPadding(0, 0, 0, dp(4));
            card.addView(counter);
        }

        Button yes = button("כן", COLOR_ACTION);
        yes.setOnClickListener(v -> {
            actionClosed = true;
            handler.removeCallbacksAndMessages(null);
            stopVibration();
            AppLog.d(this, "daf yomi learned");
            new DafYomiStore(this).markLearned(currentItem);
            DafYomiScheduler.schedule(this);
            closeAndOpenNextIfNeeded();
        });
        Button no = button("לא", 0xFF7E2A35);
        no.setOnClickListener(v -> {
            actionClosed = true;
            handler.removeCallbacksAndMessages(null);
            stopVibration();
            AppLog.d(this, "daf yomi missed");
            new DafYomiStore(this).markMissed(currentItem);
            DafYomiScheduler.schedule(this);
            closeAndOpenNextIfNeeded();
        });
        Button retry = button("תזכר אותי לעוד שעה", COLOR_SURFACE);
        retry.setOnClickListener(v -> {
            actionClosed = true;
            handler.removeCallbacksAndMessages(null);
            stopVibration();
            AppLog.d(this, "daf yomi retry pressed");
            DafYomiScheduler.scheduleRetry(this, 60);
            close();
        });
        LinearLayout buttonArea = new LinearLayout(this);
        buttonArea.setOrientation(LinearLayout.VERTICAL);
        buttonArea.setGravity(Gravity.CENTER);
        buttonArea.addView(yes);
        buttonArea.addView(no);
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

    private String questionText() {
        if (AppLanguage.isEnglish(this)) {
            return "Did you learn " + currentItem.label + "?";
        }
        return "למדת " + currentItem.label + "?";
    }

    private void close() {
        stopVibration();
        finish();
    }

    private void closeAndOpenNextIfNeeded() {
        stopVibration();
        finish();
        if (new DafYomiStore(this).dueItems(this).isEmpty()) {
            return;
        }
        handler.postDelayed(() -> {
            android.content.Intent next = new android.content.Intent(this, DafYomiAlertActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(next);
        }, 250L);
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
            AppLog.w(this, "daf yomi auto retry minutes=" + minutes);
            DafYomiScheduler.scheduleRetry(this, minutes);
            close();
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
        params.setMargins(dp(3), dp(4), dp(3), dp(4));
        button.setLayoutParams(params);
        return button;
    }

}
