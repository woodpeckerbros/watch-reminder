package com.woodpeckerbros.watchreminder.smartalarm;

import com.woodpeckerbros.watchreminder.reminder.*;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.woodpeckerbros.watchreminder.AppLanguage;
import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.AppTextStyle;

import java.lang.ref.WeakReference;

public final class SmartAlarmWakeCheckActivity extends Activity {
    private static WeakReference<SmartAlarmWakeCheckActivity> activeActivity;
    private int alarmId;

    static void closeIfShowing(int expectedAlarmId) {
        SmartAlarmWakeCheckActivity activity = activeActivity == null ? null : activeActivity.get();
        if (activity != null && activity.alarmId == expectedAlarmId
                && !activity.isFinishing() && !activity.isDestroyed()) {
            activity.finishAndRemoveTask();
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        activeActivity = new WeakReference<>(this);
        setShowWhenLocked(true); setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        alarmId = getIntent().getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        AppLog.d(this, "SmartAlarm wake check activity shown id=" + alarmId);
        boolean english = AppLanguage.isEnglish(this);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER); body.setPadding(dp(28), dp(42), dp(28), dp(28)); body.setBackgroundColor(0xFF071D2A);
        TextView title = new TextView(this); title.setText(english ? "Are you awake?" : "האם אתם ערים?");
        title.setTextColor(Color.WHITE); title.setTextSize(25); title.setGravity(Gravity.CENTER); body.addView(title);
        TextView hint = new TextView(this); hint.setText(english ? "Confirm within 30 seconds" : "יש לאשר בתוך 30 שניות");
        hint.setTextColor(0xFFFFD477); hint.setTextSize(14); hint.setGravity(Gravity.CENTER); hint.setPadding(0, dp(12), 0, dp(18)); body.addView(hint);
        Button awake = new Button(this); awake.setText(english ? "I'm awake" : "אני ער"); awake.setTextColor(Color.WHITE); awake.setTextSize(18);
        GradientDrawable background = new GradientDrawable(); background.setColor(0xFF287A64); background.setCornerRadius(dp(28)); background.setStroke(dp(2), 0xFFFFD477); awake.setBackground(background);
        awake.setOnClickListener(v -> {
            AppLog.d(this, "SmartAlarm wake check confirmed id=" + alarmId);
            SmartAlarmWakeCheckReceiver.confirm(this, alarmId);
            finishAndRemoveTask();
        });
        body.addView(awake, new LinearLayout.LayoutParams(-1, dp(58)));
        AppTextStyle.apply(body);
        setContentView(body);
    }

    @Override public void onBackPressed() { }
    @Override protected void onDestroy() {
        SmartAlarmWakeCheckActivity active = activeActivity == null ? null : activeActivity.get();
        if (active == this) activeActivity = null;
        super.onDestroy();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
