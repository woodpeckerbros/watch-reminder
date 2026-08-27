package com.woodpeckerbros.watchreminder.smartwake;

import android.app.Activity;
import android.app.NotificationManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class SmartAlarmAlertActivity extends Activity {
    private long targetAt;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setShowWhenLocked(true); setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        targetAt = getIntent().getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(34), dp(28), dp(28)); root.setBackgroundColor(Color.rgb(7, 17, 22));
        TextView title = label("זמן להתעורר", 25); root.addView(title);
        TextView reason = label("Smart Alarm", 15); reason.setTextColor(Color.rgb(214, 183, 102)); root.addView(reason);
        Button dismiss = button("כיבוי"); dismiss.setOnClickListener(v -> dismissAlarm()); root.addView(dismiss);
        Button snooze = button("נודניק " + new SmartAlarmStore(this).snoozeMinutes() + " דקות");
        snooze.setOnClickListener(v -> snooze()); root.addView(snooze);
        setContentView(root);
    }

    private void dismissAlarm() {
        new SmartAlarmStateStore(this).dismiss(targetAt); stopFeedback(); SmartAlarmScheduler.scheduleNextAfterHandled(this); close();
    }
    private void snooze() {
        stopFeedback(); SmartAlarmScheduler.scheduleSnooze(this, targetAt, new SmartAlarmStore(this).snoozeMinutes()); close();
    }
    private void close() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE); if (manager != null) manager.cancel(0x534d5704);
        finishAndRemoveTask();
    }
    private TextView label(String value, int size) { TextView text = new TextView(this); text.setText(value); text.setTextColor(Color.WHITE); text.setTextSize(size); text.setGravity(Gravity.CENTER); text.setPadding(4, dp(8), 4, dp(12)); return text; }
    private Button button(String value) { Button button = new Button(this); button.setText(value); button.setTextSize(16); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(180), dp(50)); p.setMargins(0, dp(8), 0, 0); button.setLayoutParams(p); return button; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void stopFeedback() { SmartAlarmRingingService.stop(this); }
    @Override public void onBackPressed() { /* Alarm requires an explicit dismiss or snooze action. */ }
    @Override protected void onDestroy() { stopFeedback(); super.onDestroy(); }
}
