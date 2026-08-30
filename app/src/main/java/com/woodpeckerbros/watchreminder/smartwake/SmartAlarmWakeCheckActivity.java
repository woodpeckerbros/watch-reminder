package com.woodpeckerbros.watchreminder.smartwake;

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

public final class SmartAlarmWakeCheckActivity extends Activity {
    private int alarmId;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setShowWhenLocked(true); setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        alarmId = getIntent().getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        boolean english = AppLanguage.isEnglish(this);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER); body.setPadding(dp(28), dp(42), dp(28), dp(28)); body.setBackgroundColor(0xFF071D2A);
        TextView title = new TextView(this); title.setText(english ? "Are you awake?" : "האם אתם ערים?");
        title.setTextColor(Color.WHITE); title.setTextSize(25); title.setGravity(Gravity.CENTER); body.addView(title);
        TextView hint = new TextView(this); hint.setText(english ? "Confirm within 30 seconds" : "יש לאשר בתוך 30 שניות");
        hint.setTextColor(0xFFFFD477); hint.setTextSize(14); hint.setGravity(Gravity.CENTER); hint.setPadding(0, dp(12), 0, dp(18)); body.addView(hint);
        Button awake = new Button(this); awake.setText(english ? "I'm awake" : "אני ער"); awake.setTextColor(Color.WHITE); awake.setTextSize(18);
        GradientDrawable background = new GradientDrawable(); background.setColor(0xFF287A64); background.setCornerRadius(dp(28)); background.setStroke(dp(2), 0xFFFFD477); awake.setBackground(background);
        awake.setOnClickListener(v -> { SmartAlarmWakeCheckReceiver.confirm(this, alarmId); finishAndRemoveTask(); });
        body.addView(awake, new LinearLayout.LayoutParams(-1, dp(58))); setContentView(body);
    }

    @Override public void onBackPressed() { }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
