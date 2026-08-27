package com.woodpeckerbros.watchreminder.smartwake;

import android.content.Context;
import android.content.SharedPreferences;

public final class SmartAlarmStore {
    private static final String PREFS = "smart_alarm";
    private final SharedPreferences prefs;

    public SmartAlarmStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean enabled() { return prefs.getBoolean("enabled", false); }
    public int hour() { return prefs.getInt("hour", 6); }
    public int minute() { return prefs.getInt("minute", 30); }
    public int windowMinutes() { return prefs.getInt("window_minutes", 30); }
    public int snoozeMinutes() { return prefs.getInt("snooze_minutes", 10); }

    public void save(boolean enabled, int hour, int minute, int windowMinutes, int snoozeMinutes) {
        prefs.edit().putBoolean("enabled", enabled).putInt("hour", hour).putInt("minute", minute)
                .putInt("window_minutes", windowMinutes).putInt("snooze_minutes", snoozeMinutes).apply();
    }
}
