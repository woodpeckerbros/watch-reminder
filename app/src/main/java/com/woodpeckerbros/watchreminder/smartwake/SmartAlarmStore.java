package com.woodpeckerbros.watchreminder.smartwake;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class SmartAlarmStore {
    private static final String PREFS = "smart_alarm";
    private static final String REGISTRY = "smart_alarm_registry";
    private static final String KEY_SNOOZE_DEFAULT_MIGRATED = "snooze_default_5_migrated";
    private static final String KEY_VIBRATION_LEVELS_MIGRATED = "vibration_10_levels_migrated";
    public static final int ALL_DAYS_MASK = 0xFE;
    public static final int DEFAULT_DAYS_MASK = ALL_DAYS_MASK & ~(1 << java.util.Calendar.SATURDAY);
    public static final String VIBRATION_GENTLE = "gentle";
    public static final String VIBRATION_NORMAL = "normal";
    public static final String VIBRATION_STRONG = "strong";
    public static final String VIBRATION_LONG = "long";
    public static final String BACKGROUND_SUNRISE = "sunrise";
    public static final String BACKGROUND_MORNING = "morning";
    public static final String BACKGROUND_DYNAMIC = "dynamic";
    public static final String DISMISS_TAP = "tap";
    public static final String DISMISS_HOLD = "hold";
    public static final String DISMISS_DOUBLE_TAP = "double_tap";
    private final SharedPreferences prefs;
    private final Context context;
    private final int id;

    public SmartAlarmStore(Context context) {
        this(context, 1);
    }

    public SmartAlarmStore(Context context, int id) {
        this.context = context.getApplicationContext();
        this.id = Math.max(1, id);
        prefs = this.context.getSharedPreferences(prefsName(this.id), Context.MODE_PRIVATE);
        migrateSnoozeDefaultOnce();
        migrateVibrationLevelsOnce();
    }

    public int id() { return id; }

    public boolean enabled() { return prefs.getBoolean("enabled", false); }
    public int hour() { return prefs.getInt("hour", 6); }
    public int minute() { return prefs.getInt("minute", 30); }
    public int windowMinutes() { return prefs.getInt("window_minutes", 30); }
    public int snoozeMinutes() { return prefs.getInt("snooze_minutes", 5); }
    public int daysMask() { return prefs.getInt("days_mask", DEFAULT_DAYS_MASK); }
    public int snoozeCount() { return prefs.getInt("snooze_count", 3); }
    public boolean vibrationEnabled() { return prefs.getBoolean("vibration_enabled", true); }
    public String vibrationStyle() { return prefs.getString("vibration_style", VIBRATION_NORMAL); }
    public int vibrationStrength() { return clamp(prefs.getInt("vibration_strength", 6), 1, 10); }
    public boolean soundEnabled() { return prefs.getBoolean("sound_enabled", true); }
    public int soundVolumePercent() { return clamp(prefs.getInt("sound_volume_percent", 80), 0, 100); }
    public String soundUri() { return prefs.getString("sound_uri", ""); }
    public int alertDurationSeconds() { return clamp(prefs.getInt("alert_duration_seconds", 30), 5, 120); }
    public String backgroundStyle() { return prefs.getString("background_style", BACKGROUND_SUNRISE); }
    public String dismissMethod() { return prefs.getString("dismiss_method", DISMISS_TAP); }
    public int dismissHoldSeconds() { return clamp(prefs.getInt("dismiss_hold_seconds", 3), 1, 10); }
    public boolean enabledOnDay(int calendarDay) { return (daysMask() & (1 << calendarDay)) != 0; }

    public void save(boolean enabled, int hour, int minute, int daysMask, int windowMinutes,
                     int snoozeMinutes, int snoozeCount, boolean vibrationEnabled,
                     String vibrationStyle, int vibrationStrength, boolean soundEnabled,
                     int soundVolumePercent, String soundUri, int alertDurationSeconds, String backgroundStyle,
                     String dismissMethod, int dismissHoldSeconds) {
        prefs.edit().putBoolean("enabled", enabled).putInt("hour", hour).putInt("minute", minute)
                .putInt("days_mask", daysMask).putInt("window_minutes", windowMinutes)
                .putInt("snooze_minutes", snoozeMinutes).putInt("snooze_count", snoozeCount)
                .putBoolean("vibration_enabled", vibrationEnabled).putString("vibration_style", vibrationStyle)
                .putInt("vibration_strength", vibrationStrength).putBoolean("sound_enabled", soundEnabled)
                .putInt("sound_volume_percent", soundVolumePercent).putString("sound_uri", soundUri == null ? "" : soundUri)
                .putInt("alert_duration_seconds", alertDurationSeconds)
                .putString("background_style", backgroundStyle == null ? BACKGROUND_SUNRISE : backgroundStyle)
                .putString("dismiss_method", dismissMethod == null ? DISMISS_TAP : dismissMethod)
                .putInt("dismiss_hold_seconds", dismissHoldSeconds).apply();
        register(context, id);
    }

    public void setSoundUri(String uri) { prefs.edit().putString("sound_uri", uri == null ? "" : uri).apply(); }
    public void setEnabled(boolean enabled) { prefs.edit().putBoolean("enabled", enabled).apply(); register(context, id); }

    public static ArrayList<Integer> ids(Context context) {
        migrateLegacy(context);
        Set<String> values = context.getApplicationContext().getSharedPreferences(REGISTRY, Context.MODE_PRIVATE)
                .getStringSet("ids", Collections.emptySet());
        ArrayList<Integer> result = new ArrayList<>();
        for (String value : values) try { result.add(Integer.parseInt(value)); } catch (NumberFormatException ignored) { }
        Collections.sort(result);
        return result;
    }

    public static int create(Context context) {
        SharedPreferences registry = context.getApplicationContext().getSharedPreferences(REGISTRY, Context.MODE_PRIVATE);
        int id = Math.max(1, registry.getInt("next_id", 1));
        registry.edit().putInt("next_id", id + 1).apply();
        register(context, id);
        new SmartAlarmStore(context, id).setEnabled(true);
        return id;
    }

    public static void delete(Context context, int id) {
        context.getApplicationContext().getSharedPreferences(prefsName(id), Context.MODE_PRIVATE).edit().clear().apply();
        SharedPreferences registry = context.getApplicationContext().getSharedPreferences(REGISTRY, Context.MODE_PRIVATE);
        Set<String> ids = new HashSet<>(registry.getStringSet("ids", Collections.emptySet()));
        ids.remove(String.valueOf(id));
        registry.edit().putStringSet("ids", ids).apply();
        new SmartAlarmStateStore(context, id).clear();
    }

    public static JSONArray toJson(Context context) throws Exception {
        JSONArray result = new JSONArray();
        for (int id : ids(context)) {
            SmartAlarmStore alarm = new SmartAlarmStore(context, id);
            result.put(new JSONObject().put("id", id).put("enabled", alarm.enabled())
                    .put("hour", alarm.hour()).put("minute", alarm.minute()).put("daysMask", alarm.daysMask())
                    .put("windowMinutes", alarm.windowMinutes()).put("snoozeMinutes", alarm.snoozeMinutes())
                    .put("snoozeCount", alarm.snoozeCount()).put("vibrationEnabled", alarm.vibrationEnabled())
                    .put("vibrationStyle", alarm.vibrationStyle()).put("vibrationStrength", alarm.vibrationStrength())
                    .put("soundEnabled", alarm.soundEnabled()).put("soundVolumePercent", alarm.soundVolumePercent())
                    .put("soundUri", alarm.soundUri()).put("alertDurationSeconds", alarm.alertDurationSeconds())
                    .put("backgroundStyle", alarm.backgroundStyle()).put("dismissMethod", alarm.dismissMethod())
                    .put("dismissHoldSeconds", alarm.dismissHoldSeconds()));
        }
        return result;
    }

    public static void restoreJson(Context context, JSONArray values) {
        for (int id : ids(context)) delete(context, id);
        if (values == null) return;
        int highest = 0;
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.optJSONObject(index);
            if (value == null) continue;
            int id = Math.max(1, value.optInt("id", index + 1)); highest = Math.max(highest, id);
            SmartAlarmStore alarm = new SmartAlarmStore(context, id);
            alarm.save(value.optBoolean("enabled", false), value.optInt("hour", 6), value.optInt("minute", 30),
                    value.optInt("daysMask", DEFAULT_DAYS_MASK), value.optInt("windowMinutes", 30),
                    value.optInt("snoozeMinutes", 5), value.optInt("snoozeCount", 3),
                    value.optBoolean("vibrationEnabled", true), value.optString("vibrationStyle", VIBRATION_NORMAL),
                    value.optInt("vibrationStrength", 6), value.optBoolean("soundEnabled", true),
                    value.optInt("soundVolumePercent", 80), value.optString("soundUri", ""),
                    value.optInt("alertDurationSeconds", 30),
                    value.optString("backgroundStyle", BACKGROUND_SUNRISE),
                    value.optString("dismissMethod", DISMISS_TAP), value.optInt("dismissHoldSeconds", 3));
        }
        context.getApplicationContext().getSharedPreferences(REGISTRY, Context.MODE_PRIVATE).edit()
                .putInt("next_id", highest + 1).apply();
    }

    private static String prefsName(int id) { return id == 1 ? PREFS : PREFS + "_" + id; }

    private static void migrateLegacy(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences registry = app.getSharedPreferences(REGISTRY, Context.MODE_PRIVATE);
        if (registry.getBoolean("legacy_checked", false)) return;
        SharedPreferences legacy = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> ids = new HashSet<>(registry.getStringSet("ids", Collections.emptySet()));
        if (legacy.contains("enabled") || legacy.contains("hour")) ids.add("1");
        registry.edit().putStringSet("ids", ids).putInt("next_id", ids.contains("1") ? 2 : 1)
                .putBoolean("legacy_checked", true).apply();
    }

    private static void register(Context context, int id) {
        SharedPreferences registry = context.getApplicationContext().getSharedPreferences(REGISTRY, Context.MODE_PRIVATE);
        Set<String> ids = new HashSet<>(registry.getStringSet("ids", Collections.emptySet()));
        ids.add(String.valueOf(id));
        registry.edit().putStringSet("ids", ids).putInt("next_id", Math.max(registry.getInt("next_id", 1), id + 1)).apply();
    }

    private void migrateSnoozeDefaultOnce() {
        if (prefs.getBoolean(KEY_SNOOZE_DEFAULT_MIGRATED, false)) return;
        SharedPreferences.Editor editor = prefs.edit().putBoolean(KEY_SNOOZE_DEFAULT_MIGRATED, true);
        if (!prefs.contains("snooze_minutes") || prefs.getInt("snooze_minutes", 10) == 10) {
            editor.putInt("snooze_minutes", 5);
        }
        editor.apply();
    }

    private void migrateVibrationLevelsOnce() {
        if (prefs.getBoolean(KEY_VIBRATION_LEVELS_MIGRATED, false)) return;
        int oldStrength = clamp(prefs.getInt("vibration_strength", 2), 1, 3);
        int migratedStrength = oldStrength == 1 ? 3 : oldStrength == 2 ? 6 : 10;
        prefs.edit().putInt("vibration_strength", migratedStrength)
                .putBoolean(KEY_VIBRATION_LEVELS_MIGRATED, true).apply();
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
