package com.woodpeckerbros.watchreminder.smartwake;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.woodpeckerbros.watchreminder.AppTextStyle;

public final class SmartAlarmSettingsActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); SmartAlarmStore store = new SmartAlarmStore(this);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setGravity(Gravity.CENTER_HORIZONTAL); content.setPadding(dp(24), dp(42), dp(24), dp(24));
        TextView title = text("Smart Alarm", 22); content.addView(title);
        TextView hint = text("מעיר בחלון מתאים עד 30 דקות לפני השעה. בשעת היעד ההתראה תפעל בכל מקרה.", 13); content.addView(hint);
        Switch enabled = new Switch(this); enabled.setText("פעיל"); enabled.setChecked(store.enabled()); content.addView(enabled);
        LinearLayout time = new LinearLayout(this); time.setGravity(Gravity.CENTER); time.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_LTR);
        NumberPicker hour = picker(0, 23, store.hour()); NumberPicker minute = picker(0, 59, store.minute()); time.addView(hour); time.addView(minute); content.addView(time);
        TextView capability = text("Health Services: ASLEEP + דופק חי אם נתמך. LIGHT / DEEP / REM אינם זמינים ב־API הנוכחי; נעשה שימוש בהערכת דופק ותנועה.", 12); content.addView(capability);
        Button save = new Button(this); save.setText("שמירה"); save.setOnClickListener(v -> {
            store.save(enabled.isChecked(), hour.getValue(), minute.getValue(), 30, 10);
            SmartAlarmScheduler.reschedule(this);
            requestSmartWakePermissionsIfNeeded(enabled.isChecked());
            Toast.makeText(this, "השעון המעורר נשמר", Toast.LENGTH_SHORT).show();
            if (!enabled.isChecked() || hasSmartWakePermissions()) finish();
        }); content.addView(save);
        Button cancel = new Button(this); cancel.setText("חזרה"); cancel.setOnClickListener(v -> finish()); content.addView(cancel);
        ScrollView scroll = new ScrollView(this); scroll.addView(content); AppTextStyle.apply(scroll); setContentView(scroll);
    }
    private NumberPicker picker(int min, int max, int value) { NumberPicker p = new NumberPicker(this); p.setMinValue(min); p.setMaxValue(max); p.setValue(value); p.setWrapSelectorWheel(true); return p; }
    private TextView text(String value, int size) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setGravity(Gravity.CENTER); t.setPadding(0, dp(7), 0, dp(9)); return t; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private boolean hasSmartWakePermissions() {
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) return false;
        if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return false;
        return Build.VERSION.SDK_INT < 33 || Build.VERSION.SDK_INT > 35
                || checkSelfPermission("android.permission.BODY_SENSORS_BACKGROUND") == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSmartWakePermissionsIfNeeded(boolean enabled) {
        if (!enabled || hasSmartWakePermissions()) return;
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BODY_SENSORS, Manifest.permission.ACTIVITY_RECOGNITION}, 81);
        } else if (Build.VERSION.SDK_INT >= 33 && Build.VERSION.SDK_INT <= 35) {
            requestPermissions(new String[]{"android.permission.BODY_SENSORS_BACKGROUND"}, 82);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 81) requestSmartWakePermissionsIfNeeded(true);
        else if (requestCode == 82) finish();
    }
}
