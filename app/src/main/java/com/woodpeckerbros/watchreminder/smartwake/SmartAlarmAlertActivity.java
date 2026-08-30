package com.woodpeckerbros.watchreminder.smartwake;

import android.app.Activity;
import android.app.NotificationManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.woodpeckerbros.watchreminder.AppLanguage;
import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.AppTextStyle;
import com.woodpeckerbros.watchreminder.AlertFeedback;
import com.woodpeckerbros.watchreminder.R;
import com.woodpeckerbros.watchreminder.TopArcClockView;

import java.util.Calendar;
import java.lang.ref.WeakReference;

public final class SmartAlarmAlertActivity extends Activity {
    private static WeakReference<SmartAlarmAlertActivity> activeActivity;
    private long targetAt;
    private int alarmId = 1;
    private SmartAlarmStore settings;
    private boolean explicitlyHandled;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AlertFeedback alertFeedback;
    private long lastDismissTapAt;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        activeActivity = new WeakReference<>(this);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        targetAt = getIntent().getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
        alarmId = getIntent().getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        AppLog.d(this, "SmartAlarm alert activity onCreate id=" + alarmId
                + " target=" + targetAt + " reason=" + getIntent().getStringExtra("reason"));
        settings = new SmartAlarmStore(this, alarmId);
        setContentView(content());
        // Match regular reminders once the full-screen UI is visible. The service remains only
        // as a fallback for devices that decline to present the activity.
        SmartAlarmRingingService.stop(this);
        alertFeedback = AlertFeedback.startSmartAlarm(this, settings);
    }

    @Override protected void onResume() {
        super.onResume();
        AppLog.d(this, "SmartAlarm alert activity onResume id=" + alarmId + " target=" + targetAt);
    }

    private View content() {
        FrameLayout frame = new FrameLayout(this);
        ImageView background = new ImageView(this);
        background.setImageResource(selectedBackground());
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(background, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(this);
        scrim.setBackgroundColor(0x72000000);
        frame.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(24), dp(46), dp(24), dp(18));
        boolean english = AppLanguage.isEnglish(this);
        body.addView(label(english ? "Time to wake up" : "זמן להתעורר", 24, Color.WHITE));
        body.addView(label(english ? "Smart Alarm" : "שעון מעורר חכם", 15, 0xFFFFD27A));
        RingingAlarmClockView alarmClock = new RingingAlarmClockView(this);
        LinearLayout.LayoutParams alarmClockParams = new LinearLayout.LayoutParams(dp(64), dp(62));
        alarmClockParams.setMargins(0, dp(2), 0, dp(1));
        body.addView(alarmClock, alarmClockParams);

        int used = new SmartAlarmStateStore(this, alarmId).snoozeUsed();
        int remaining = Math.max(0, settings.snoozeCount() - used);
        if (settings.snoozeCount() > 0) {
            body.addView(label(english ? remaining + " snoozes remaining" : "נותרו " + remaining + " נודניקים", 13, 0xFFE8E8E8));
        }
        Button dismiss;
        String dismissMethod = settings.dismissMethod();
        int holdDurationSeconds = settings.dismissHoldSeconds();
        dismissMethod = getIntent().getStringExtra("preview_dismiss_method") == null
                ? dismissMethod : getIntent().getStringExtra("preview_dismiss_method");
        holdDurationSeconds = getIntent().getIntExtra("preview_hold_seconds", holdDurationSeconds);
        if (SmartAlarmStore.DISMISS_HOLD.equals(dismissMethod)) {
            HoldToDismissButton hold = new HoldToDismissButton(this);
            styleButton(hold, english ? "Hold to dismiss" : "לחצו לכיבוי", 0xFF7E2A35);
            hold.configure(holdDurationSeconds, this::dismissAlarm);
            dismiss = hold;
            body.addView(label(english ? "Hold for " + holdDurationSeconds + " seconds"
                    : "יש ללחוץ במשך " + holdDurationSeconds + " שניות", 11, 0xFFE8E8E8));
        } else {
            dismiss = button(english ? "Dismiss" : "כיבוי", 0xFF7E2A35);
            if (SmartAlarmStore.DISMISS_DOUBLE_TAP.equals(dismissMethod)) {
                dismiss.setText(english ? "Double tap to dismiss" : "לחצו פעמיים לכיבוי");
                dismiss.setOnClickListener(v -> {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastDismissTapAt <= 650L) dismissAlarm();
                    else lastDismissTapAt = now;
                });
            } else {
                dismiss.setOnClickListener(v -> dismissAlarm());
            }
        }
        body.addView(dismiss);
        if (remaining > 0) {
            Button snooze = button((english ? "Snooze " : "נודניק ") + settings.snoozeMinutes()
                    + (english ? " min" : " דקות"), 0xFF176B5B);
            snooze.setOnClickListener(v -> snooze());
            body.addView(snooze);
        }
        body.addView(new View(this), new LinearLayout.LayoutParams(1, dp(16)));
        AppTextStyle.apply(body);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -1));
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        TopArcClockView.addTo(frame);
        return frame;
    }

    private int backgroundForNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 11) return R.drawable.home_horizon_morning;
        if (hour >= 11 && hour < 16) return R.drawable.home_horizon_noon;
        if (hour >= 16 && hour < 19) return R.drawable.home_horizon_evening;
        return R.drawable.home_horizon_night;
    }

    private int selectedBackground() {
        String style = settings == null ? SmartAlarmStore.BACKGROUND_SUNRISE : settings.backgroundStyle();
        if (SmartAlarmStore.BACKGROUND_DYNAMIC.equals(style)) return backgroundForNow();
        if (SmartAlarmStore.BACKGROUND_MORNING.equals(style)) return R.drawable.home_horizon_morning;
        return R.drawable.smart_alarm_sunrise;
    }

    private void dismissAlarm() {
        explicitlyHandled = true;
        SmartAlarmScheduler.cancelAutoSnooze(this, alarmId);
        new SmartAlarmStateStore(this, alarmId).dismiss(targetAt);
        stopFeedback();
        SmartAlarmScheduler.scheduleNextAfterHandled(this, alarmId);
        close();
    }

    private void snooze() {
        explicitlyHandled = true;
        SmartAlarmScheduler.cancelAutoSnooze(this, alarmId);
        stopFeedback();
        SmartAlarmScheduler.scheduleSnooze(this, alarmId, targetAt, settings.snoozeMinutes());
        close();
    }

    private TextView label(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(size);
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(3), dp(5), dp(3), dp(5));
        return text;
    }

    private Button button(String value, int color) {
        Button button = new Button(this);
        styleButton(button, value, color);
        return button;
    }

    private void styleButton(Button button, String value, int color) {
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setAllCaps(false);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(24));
        shape.setStroke(dp(1), 0x99FFFFFF);
        button.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
        );
        params.setMargins(dp(2), dp(7), dp(2), 0);
        button.setLayoutParams(params);
    }

    private void close() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(0x534d5704 + alarmId);
        finishAndRemoveTask();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void stopFeedback() {
        SmartAlarmRingingService.stop(this);
        if (alertFeedback != null) {
            alertFeedback.stop();
            alertFeedback = null;
        }
    }
    @Override public void onBackPressed() { }

    static boolean closeAutoSnoozed(int alarmId, long targetAt) {
        SmartAlarmAlertActivity activity = activeActivity == null ? null : activeActivity.get();
        if (activity == null || activity.explicitlyHandled || activity.alarmId != alarmId || activity.targetAt != targetAt)
            return false;
        activity.handler.post(() -> {
            if (!activity.explicitlyHandled && activity.alarmId == alarmId && activity.targetAt == targetAt) {
                activity.explicitlyHandled = true;
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                activity.finishAndRemoveTask();
            }
        });
        return true;
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Activity destruction (for example when the user opens the app) is not an alarm action.
        // Feedback stops itself at the configured timeout unless an explicit action handles it.
        if (explicitlyHandled) stopFeedback();
        SmartAlarmAlertActivity active = activeActivity == null ? null : activeActivity.get();
        if (active == this) activeActivity = null;
        super.onDestroy();
    }
}
