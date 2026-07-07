package com.woodpeckerbros.watchreminder.phone;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

class LocalReminderDocument {
    private static final String PREFS_NAME = "local_reminder_document";
    private static final String KEY_TEXT = "text";
    private static final String KEY_UPDATED_AT = "updated_at";

    private LocalReminderDocument() {
    }

    static void save(Context context, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TEXT, text)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply();
    }

    static String text(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String text = prefs.getString(KEY_TEXT, "");
        if (!text.isEmpty()) {
            return text;
        }
        try {
            byte[] bytes = BackupStorage.lastBackup(context);
            text = new String(bytes, StandardCharsets.UTF_8);
            save(context, text);
            return text;
        } catch (Exception ignored) {
            return "";
        }
    }

    static long updatedAt(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_UPDATED_AT, 0);
    }

    static JSONObject root(Context context) {
        try {
            String text = text(context);
            return text.isEmpty() ? emptyRoot() : new JSONObject(text);
        } catch (Exception ignored) {
            return emptyRoot();
        }
    }

    static JSONArray reminders(Context context) {
        return root(context).optJSONArray("reminders");
    }

    static void saveRoot(Context context, JSONObject root) {
        try {
            root.put("type", "watch-reminder-backup");
            root.put("version", root.optInt("version", 1));
            root.put("exportedAt", System.currentTimeMillis());
            save(context, root.toString(2));
        } catch (Exception ignored) {
        }
    }

    static byte[] bytes(Context context) {
        return text(context).getBytes(StandardCharsets.UTF_8);
    }

    private static JSONObject emptyRoot() {
        JSONObject root = new JSONObject();
        try {
            root.put("type", "watch-reminder-backup")
                    .put("version", 1)
                    .put("exportedAt", System.currentTimeMillis())
                    .put("reminders", new JSONArray())
                    .put("settings", new JSONObject()
                            .put("serviceEnabled", false)
                            .put("checkIntervalSeconds", 300)
                            .put("autoSnoozeDelaySeconds", 30)
                            .put("autoSnoozeMinutes", 5)
                            .put("vibrationStyle", "normal")
                            .put("vibrationDurationMs", 1800)
                            .put("alertDurationMs", 1800)
                            .put("vibrationEnabled", true)
                            .put("alertSoundEnabled", false)
                            .put("alertSoundUri", "")
                            .put("alertVolumePercent", 80)
                            .put("quietMinchaMaariv", false)
                            .put("blessingReminderMinutes", 65)
                            .put("moonBlessingEnabled", false)
                            .put("dafYomiEnabled", false)
                            .put("dafYomiHour", 21)
                            .put("dafYomiMinute", 30)
                            .put("omerEnabled", false)
                            .put("omerOffsetMinutes", 15)
                            .put("language", "auto")
                            .put("jewishMode", false))
                    .put("quietTimeRules", new JSONArray())
                    .put("zmanimLocation", new JSONObject()
                            .put("name", "פתח תקווה, ישראל")
                            .put("latitude", 32.084)
                            .put("longitude", 34.8878)
                            .put("elevation", 0)
                            .put("timeZone", "Asia/Jerusalem"));
        } catch (Exception ignored) {
        }
        return root;
    }
}
