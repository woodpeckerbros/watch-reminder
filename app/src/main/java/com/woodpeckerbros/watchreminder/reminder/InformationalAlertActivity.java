package com.woodpeckerbros.watchreminder.reminder;

import com.woodpeckerbros.watchreminder.*;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class InformationalAlertActivity extends JewishAlertBaseActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String key;
    private String title;
    private String message;
    private AlertFeedback feedback;
    private boolean closed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        key = getIntent().getStringExtra(InformationalAlertReceiver.EXTRA_KEY);
        title = getIntent().getStringExtra(InformationalAlertReceiver.EXTRA_TITLE);
        message = getIntent().getStringExtra(InformationalAlertReceiver.EXTRA_MESSAGE);
        if (key == null || title == null || message == null) { finish(); return; }
        InformationalAlertReceiver.dismissNotification(this, key);

        LinearLayout content = jewishAlertContent();
        LinearLayout card = jewishAlertCard(title);
        TextView body = jewishAlertText(message, 15, COLOR_MUTED);
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

        TextView snoozeTitle = jewishAlertText("אפשר לדחות", 13, COLOR_MUTED);
        snoozeTitle.setPadding(0, dp(8), 0, dp(3));
        card.addView(snoozeTitle);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.addView(snooze("15 דקות", 15));
        row.addView(snooze("30 דקות", 30));
        card.addView(row);

        content.addView(card, new LinearLayout.LayoutParams(-1, -2));
        setJewishAlertContent(content);
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
    private Button button(String label, int color) { return jewishAlertButton(label, color); }
}
