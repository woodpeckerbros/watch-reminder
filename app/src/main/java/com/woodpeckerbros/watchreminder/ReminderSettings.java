package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

public class ReminderSettings {
    public static final String LANGUAGE_AUTO = "auto";
    public static final String LANGUAGE_HEBREW = "he";
    public static final String LANGUAGE_ENGLISH = "en";

    public static final String VIBRATION_GENTLE = "gentle";
    public static final String VIBRATION_NORMAL = "normal";
    public static final String VIBRATION_STRONG = "strong";
    public static final String VIBRATION_LONG = "long";
    public static final String VIBRATION_OFF = "off";

    public static final int DEFAULT_CHECK_INTERVAL_SECONDS = 3 * 60;
    public static final int DEFAULT_AUTO_SNOOZE_DELAY_SECONDS = 30;
    public static final int DEFAULT_AUTO_SNOOZE_MINUTES = 5;
    public static final int DEFAULT_VIBRATION_DURATION_MS = 1800;
    public static final int DEFAULT_BLESSING_REMINDER_MINUTES = 65;
    public static final int DEFAULT_ALERT_VOLUME_PERCENT = 80;

    private static final String PREFS_NAME = "reminder_settings";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String KEY_CHECK_INTERVAL_SECONDS = "check_interval_seconds";
    private static final String KEY_AUTO_SNOOZE_DELAY_SECONDS = "auto_snooze_delay_seconds";
    private static final String KEY_AUTO_SNOOZE_MINUTES = "auto_snooze_minutes";
    private static final String KEY_VIBRATION_STYLE = "vibration_style";
    private static final String KEY_VIBRATION_DURATION_MS = "vibration_duration_ms";
    private static final String KEY_VIBRATION_ENABLED = "vibration_enabled";
    private static final String KEY_ALERT_SOUND_ENABLED = "alert_sound_enabled";
    private static final String KEY_ALERT_SOUND_URI = "alert_sound_uri";
    private static final String KEY_ALERT_VOLUME_PERCENT = "alert_volume_percent";
    private static final String KEY_QUIET_MINCHA_MAARIV = "quiet_mincha_maariv";
    private static final String KEY_BLESSING_REMINDER_MINUTES = "blessing_reminder_minutes";
    private static final String KEY_POWER_SAVE_DEFAULT_APPLIED = "power_save_default_applied";
    private static final String KEY_DAF_YOMI_ENABLED = "daf_yomi_enabled";
    private static final String KEY_DAF_YOMI_HOUR = "daf_yomi_hour";
    private static final String KEY_DAF_YOMI_MINUTE = "daf_yomi_minute";
    private static final String KEY_MOON_BLESSING_ENABLED = "moon_blessing_enabled";
    private static final String KEY_OMER_ENABLED = "omer_enabled";
    private static final String KEY_OMER_OFFSET_MINUTES = "omer_offset_minutes";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_JEWISH_MODE = "jewish_mode";
    private static final String KEY_JEWISH_DAY_REMINDERS_ENABLED = "jewish_day_reminders_enabled";

    private final SharedPreferences prefs;
    private final boolean defaultJewishMode;

    public ReminderSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        defaultJewishMode = defaultJewishMode();
    }

    public boolean serviceEnabled() {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false);
    }

    public void setServiceEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply();
    }

    public int checkIntervalSeconds() {
        return clamp(prefs.getInt(KEY_CHECK_INTERVAL_SECONDS, DEFAULT_CHECK_INTERVAL_SECONDS), 10, 3600);
    }

    public void setCheckIntervalSeconds(int seconds) {
        prefs.edit().putInt(KEY_CHECK_INTERVAL_SECONDS, clamp(seconds, 10, 3600)).apply();
    }

    public int autoSnoozeDelaySeconds() {
        return clamp(prefs.getInt(KEY_AUTO_SNOOZE_DELAY_SECONDS, DEFAULT_AUTO_SNOOZE_DELAY_SECONDS), 5, 600);
    }

    public void setAutoSnoozeDelaySeconds(int seconds) {
        prefs.edit().putInt(KEY_AUTO_SNOOZE_DELAY_SECONDS, clamp(seconds, 5, 600)).apply();
    }

    public int autoSnoozeMinutes() {
        return clamp(prefs.getInt(KEY_AUTO_SNOOZE_MINUTES, DEFAULT_AUTO_SNOOZE_MINUTES), 1, 240);
    }

    public void setAutoSnoozeMinutes(int minutes) {
        prefs.edit().putInt(KEY_AUTO_SNOOZE_MINUTES, clamp(minutes, 1, 240)).apply();
    }

    public String vibrationStyle() {
        return prefs.getString(KEY_VIBRATION_STYLE, VIBRATION_NORMAL);
    }

    public void setVibrationStyle(String style) {
        prefs.edit().putString(KEY_VIBRATION_STYLE, style).apply();
    }

    public int vibrationDurationMs() {
        return alertDurationMs();
    }

    public void setVibrationDurationMs(int durationMs) {
        setAlertDurationMs(durationMs);
    }

    public int alertDurationMs() {
        return clamp(prefs.getInt(KEY_VIBRATION_DURATION_MS, DEFAULT_VIBRATION_DURATION_MS), 200, 10_000);
    }

    public void setAlertDurationMs(int durationMs) {
        prefs.edit().putInt(KEY_VIBRATION_DURATION_MS, clamp(durationMs, 200, 10_000)).apply();
    }

    public boolean vibrationEnabled() {
        return prefs.getBoolean(KEY_VIBRATION_ENABLED, !VIBRATION_OFF.equals(vibrationStyle()));
    }

    public void setVibrationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply();
    }

    public boolean alertSoundEnabled() {
        return prefs.getBoolean(KEY_ALERT_SOUND_ENABLED, false);
    }

    public void setAlertSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ALERT_SOUND_ENABLED, enabled).apply();
    }

    public String alertSoundUri() {
        return prefs.getString(KEY_ALERT_SOUND_URI, "");
    }

    public void setAlertSoundUri(String uri) {
        prefs.edit().putString(KEY_ALERT_SOUND_URI, uri == null ? "" : uri).apply();
    }

    public int alertVolumePercent() {
        return clamp(prefs.getInt(KEY_ALERT_VOLUME_PERCENT, DEFAULT_ALERT_VOLUME_PERCENT), 0, 100);
    }

    public void setAlertVolumePercent(int percent) {
        prefs.edit().putInt(KEY_ALERT_VOLUME_PERCENT, clamp(percent, 0, 100)).apply();
    }

    public int alertVolumeLevel() {
        return clamp(Math.max(1, Math.round(alertVolumePercent() / 10f)), 1, 10);
    }

    public void setAlertVolumeLevel(int level) {
        setAlertVolumePercent(clamp(level, 1, 10) * 10);
    }

    public boolean quietMinchaMaariv() {
        return prefs.getBoolean(KEY_QUIET_MINCHA_MAARIV, false);
    }

    public void setQuietMinchaMaariv(boolean enabled) {
        prefs.edit().putBoolean(KEY_QUIET_MINCHA_MAARIV, enabled).apply();
    }

    public int blessingReminderMinutes() {
        return clamp(prefs.getInt(KEY_BLESSING_REMINDER_MINUTES, DEFAULT_BLESSING_REMINDER_MINUTES), 1, 71);
    }

    public void setBlessingReminderMinutes(int minutes) {
        prefs.edit().putInt(KEY_BLESSING_REMINDER_MINUTES, clamp(minutes, 1, 71)).apply();
    }

    public boolean dafYomiEnabled() {
        return prefs.getBoolean(KEY_DAF_YOMI_ENABLED, false);
    }

    public void setDafYomiEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DAF_YOMI_ENABLED, enabled).apply();
    }

    public int dafYomiHour() {
        return clamp(prefs.getInt(KEY_DAF_YOMI_HOUR, 21), 0, 23);
    }

    public int dafYomiMinute() {
        return clamp(prefs.getInt(KEY_DAF_YOMI_MINUTE, 30), 0, 59);
    }

    public void setDafYomiTime(int hour, int minute) {
        prefs.edit()
                .putInt(KEY_DAF_YOMI_HOUR, clamp(hour, 0, 23))
                .putInt(KEY_DAF_YOMI_MINUTE, clamp(minute, 0, 59))
                .apply();
    }

    public boolean moonBlessingEnabled() {
        return prefs.getBoolean(KEY_MOON_BLESSING_ENABLED, false);
    }

    public void setMoonBlessingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MOON_BLESSING_ENABLED, enabled).apply();
    }

    public boolean omerEnabled() {
        return prefs.getBoolean(KEY_OMER_ENABLED, false);
    }

    public void setOmerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_OMER_ENABLED, enabled).apply();
    }

    public int omerOffsetMinutes() {
        return clamp(prefs.getInt(KEY_OMER_OFFSET_MINUTES, 15), 0, 240);
    }

    public void setOmerOffsetMinutes(int minutes) {
        prefs.edit().putInt(KEY_OMER_OFFSET_MINUTES, clamp(minutes, 0, 240)).apply();
    }

    public boolean jewishDayRemindersEnabled() {
        return jewishMode() && prefs.getBoolean(KEY_JEWISH_DAY_REMINDERS_ENABLED, true);
    }

    public void setJewishDayRemindersEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_JEWISH_DAY_REMINDERS_ENABLED, enabled).apply();
    }

    public String language() {
        String value = prefs.getString(KEY_LANGUAGE, LANGUAGE_AUTO);
        if (LANGUAGE_HEBREW.equals(value) || LANGUAGE_ENGLISH.equals(value)) {
            return value;
        }
        return LANGUAGE_AUTO;
    }

    public void setLanguage(String language) {
        if (!LANGUAGE_HEBREW.equals(language) && !LANGUAGE_ENGLISH.equals(language)) {
            language = LANGUAGE_AUTO;
        }
        SharedPreferences.Editor editor = prefs.edit().putString(KEY_LANGUAGE, language);
        if (AppLanguage.isHebrewLanguageSetting(language)) {
            editor.putBoolean(KEY_JEWISH_MODE, true);
        }
        editor.apply();
    }

    public boolean jewishMode() {
        if (AppLanguage.isHebrewLanguageSetting(language())) {
            return true;
        }
        if (prefs.contains(KEY_JEWISH_MODE)) {
            return prefs.getBoolean(KEY_JEWISH_MODE, defaultJewishMode);
        }
        return defaultJewishMode;
    }

    public void setJewishMode(boolean enabled) {
        SharedPreferences.Editor editor = prefs.edit().putBoolean(KEY_JEWISH_MODE, enabled);
        if (enabled && !prefs.contains(KEY_JEWISH_DAY_REMINDERS_ENABLED)) {
            editor.putBoolean(KEY_JEWISH_DAY_REMINDERS_ENABLED, true);
        }
        editor.apply();
    }

    private boolean defaultJewishMode() {
        String language = language();
        if (LANGUAGE_HEBREW.equals(language)) {
            return true;
        }
        if (LANGUAGE_ENGLISH.equals(language)) {
            return false;
        }
        return AppLanguage.isHebrewLocale(AppLanguage.deviceLocale());
    }

    public void applyPowerSaveDefaultOnce() {
        if (prefs.getBoolean(KEY_POWER_SAVE_DEFAULT_APPLIED, false)) {
            return;
        }
        prefs.edit()
                .putBoolean(KEY_SERVICE_ENABLED, false)
                .putBoolean(KEY_POWER_SAVE_DEFAULT_APPLIED, true)
                .apply();
    }

    public long checkIntervalMs() {
        return checkIntervalSeconds() * 1000L;
    }

    public long dueLookbackMs() {
        return checkIntervalMs() + 45_000L;
    }

    public long autoSnoozeDelayMs() {
        return autoSnoozeDelaySeconds() * 1000L;
    }

    public long[] vibrationPattern() {
        if (!vibrationEnabled()) {
            return new long[]{0};
        }
        return vibrationPattern(vibrationStyle(), vibrationDurationMs());
    }

    public static long[] vibrationPattern(String style, int durationMs) {
        int duration = Math.max(200, Math.min(10_000, durationMs));
        if (VIBRATION_OFF.equals(style)) {
            return new long[]{0};
        }
        if (VIBRATION_GENTLE.equals(style)) {
            return repeatingPattern(duration, 170, 120);
        }
        if (VIBRATION_STRONG.equals(style)) {
            return repeatingPattern(duration, 450, 120);
        }
        if (VIBRATION_LONG.equals(style)) {
            return repeatingPattern(duration, 900, 180);
        }
        return repeatingPattern(duration, 350, 180);
    }

    private static long[] repeatingPattern(int durationMs, int pulseMs, int gapMs) {
        long remaining = durationMs;
        java.util.ArrayList<Long> values = new java.util.ArrayList<>();
        values.add(0L);
        while (remaining > 0) {
            long pulse = Math.min(pulseMs, remaining);
            values.add(pulse);
            remaining -= pulse;
            if (remaining > 0) {
                long gap = Math.min(gapMs, remaining);
                values.add(gap);
                remaining -= gap;
            }
        }
        long[] pattern = new long[values.size()];
        for (int i = 0; i < values.size(); i++) {
            pattern[i] = values.get(i);
        }
        return pattern;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
