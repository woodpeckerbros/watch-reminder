package com.woodpeckerbros.watchreminder.smartwake;

import android.content.Context;
import android.content.SharedPreferences;

public final class SmartAlarmStore {
    private static final String PREFS = "smart_alarm";
    private static final String KEY_SNOOZE_DEFAULT_MIGRATED = "snooze_default_5_migrated";
    public static final int ALL_DAYS_MASK = 0xFE;
    public static final int DEFAULT_DAYS_MASK = ALL_DAYS_MASK & ~(1 << java.util.Calendar.SATURDAY);
    public static final String VIBRATION_GENTLE = "gentle";
    public static final String VIBRATION_NORMAL = "normal";
    public static final String VIBRATION_STRONG = "strong";
    public static final String VIBRATION_LONG = "long";
    private final SharedPreferences prefs;

    public SmartAlarmStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        migrateSnoozeDefaultOnce();
    }

    public boolean enabled() { return prefs.getBoolean("enabled", false); }
    public int hour() { return prefs.getInt("hour", 6); }
    public int minute() { return prefs.getInt("minute", 30); }
    public int windowMinutes() { return prefs.getInt("window_minutes", 30); }
    public int snoozeMinutes() { return prefs.getInt("snooze_minutes", 5); }
    public int daysMask() { return prefs.getInt("days_mask", DEFAULT_DAYS_MASK); }
    public int snoozeCount() { return prefs.getInt("snooze_count", 3); }
    public boolean vibrationEnabled() { return prefs.getBoolean("vibration_enabled", true); }
    public String vibrationStyle() { return prefs.getString("vibration_style", VIBRATION_NORMAL); }
    public int vibrationStrength() { return clamp(prefs.getInt("vibration_strength", 2), 1, 3); }
    public boolean soundEnabled() { return prefs.getBoolean("sound_enabled", true); }
    public int soundVolumePercent() { return clamp(prefs.getInt("sound_volume_percent", 80), 0, 100); }
    public String soundUri() { return prefs.getString("sound_uri", ""); }
    public int alertDurationSeconds() { return clamp(prefs.getInt("alert_duration_seconds", 30), 5, 120); }
    public boolean enabledOnDay(int calendarDay) { return (daysMask() & (1 << calendarDay)) != 0; }

    public void save(boolean enabled, int hour, int minute, int daysMask, int windowMinutes,
                     int snoozeMinutes, int snoozeCount, boolean vibrationEnabled,
                     String vibrationStyle, int vibrationStrength, boolean soundEnabled,
                     int soundVolumePercent, String soundUri, int alertDurationSeconds) {
        prefs.edit().putBoolean("enabled", enabled).putInt("hour", hour).putInt("minute", minute)
                .putInt("days_mask", daysMask).putInt("window_minutes", windowMinutes)
                .putInt("snooze_minutes", snoozeMinutes).putInt("snooze_count", snoozeCount)
                .putBoolean("vibration_enabled", vibrationEnabled).putString("vibration_style", vibrationStyle)
                .putInt("vibration_strength", vibrationStrength).putBoolean("sound_enabled", soundEnabled)
                .putInt("sound_volume_percent", soundVolumePercent).putString("sound_uri", soundUri == null ? "" : soundUri)
                .putInt("alert_duration_seconds", alertDurationSeconds).apply();
    }

    public void setSoundUri(String uri) { prefs.edit().putString("sound_uri", uri == null ? "" : uri).apply(); }

    private void migrateSnoozeDefaultOnce() {
        if (prefs.getBoolean(KEY_SNOOZE_DEFAULT_MIGRATED, false)) return;
        SharedPreferences.Editor editor = prefs.edit().putBoolean(KEY_SNOOZE_DEFAULT_MIGRATED, true);
        if (!prefs.contains("snooze_minutes") || prefs.getInt("snooze_minutes", 10) == 10) {
            editor.putInt("snooze_minutes", 5);
        }
        editor.apply();
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
