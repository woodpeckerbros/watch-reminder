package com.woodpeckerbros.watchreminder.phone;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

class PendingPatchStore {
    private static final String PREFS_NAME = "pending_patch";
    private static final String KEY_OPERATIONS = "operations";

    private PendingPatchStore() {
    }

    static void upsertReminder(Context context, JSONObject reminder) {
        try {
            JSONObject operation = new JSONObject()
                    .put("action", "upsertReminder")
                    .put("reminder", reminder);
            put(context, operation);
        } catch (Exception ignored) {
        }
    }

    static void deleteReminder(Context context, String reminderId) {
        try {
            JSONObject operation = new JSONObject()
                    .put("action", "deleteReminder")
                    .put("reminderId", reminderId);
            put(context, operation);
        } catch (Exception ignored) {
        }
    }

    static void updateSettings(Context context, JSONObject root) {
        try {
            JSONObject operation = new JSONObject()
                    .put("action", "updateSettings")
                    .put("settings", root.optJSONObject("settings"))
                    .put("quietTimeRules", root.optJSONArray("quietTimeRules"))
                    .put("zmanimLocation", root.optJSONObject("zmanimLocation"));
            put(context, operation);
        } catch (Exception ignored) {
        }
    }

    static void replaceSmartAlarms(Context context, JSONArray smartAlarms) {
        try {
            put(context, new JSONObject().put("action", "replaceSmartAlarms")
                    .put("smartAlarms", smartAlarms));
        } catch (Exception ignored) { }
    }

    static boolean hasPending(Context context) {
        return operations(context).length() > 0;
    }

    static byte[] bytes(Context context) {
        JSONObject root = new JSONObject();
        try {
            root.put("type", "watch-reminder-patch")
                    .put("version", 1)
                    .put("createdAt", System.currentTimeMillis())
                    .put("operations", operations(context));
        } catch (Exception ignored) {
        }
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    static int count(Context context) {
        return operations(context).length();
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static void put(Context context, JSONObject operation) {
        JSONArray next = new JSONArray();
        String action = operation.optString("action");
        String reminderId = operation.optString("reminderId");
        JSONObject reminder = operation.optJSONObject("reminder");
        if (reminder != null) {
            reminderId = reminder.optString("id");
        }
        for (int i = 0; i < operations(context).length(); i++) {
            JSONObject existing = operations(context).optJSONObject(i);
            if (existing == null || replaces(existing, action, reminderId)) {
                continue;
            }
            next.put(existing);
        }
        next.put(operation);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_OPERATIONS, next.toString())
                .apply();
    }

    private static boolean replaces(JSONObject existing, String action, String reminderId) {
        if ("updateSettings".equals(action) && "updateSettings".equals(existing.optString("action"))) {
            return true;
        }
        if ("replaceSmartAlarms".equals(action) && "replaceSmartAlarms".equals(existing.optString("action"))) {
            return true;
        }
        JSONObject existingReminder = existing.optJSONObject("reminder");
        String existingId = existingReminder == null ? existing.optString("reminderId") : existingReminder.optString("id");
        return reminderId != null && !reminderId.isEmpty() && reminderId.equals(existingId);
    }

    private static JSONArray operations(Context context) {
        try {
            String raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_OPERATIONS, "[]");
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }
}
