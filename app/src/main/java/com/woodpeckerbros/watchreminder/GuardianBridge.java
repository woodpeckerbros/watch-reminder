package com.woodpeckerbros.watchreminder;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** On-watch, cross-package failover bridge. No phone, network, or wearable transport is used. */
public final class GuardianBridge {
    static final String GUARDIAN_PACKAGE = "com.woodpeckerbros.watchreminder.guardian";
    static final String GUARDIAN_RECEIVER = GUARDIAN_PACKAGE + ".GuardianPlanReceiver";
    static final String ACTION_PLAN = "com.woodpeckerbros.watchreminder.guardian.PLAN";
    static final String ACTION_ACK = "com.woodpeckerbros.watchreminder.guardian.ACK";
    static final String ACTION_REQUEST_PLAN = "com.woodpeckerbros.watchreminder.guardian.REQUEST_PLAN";
    static final String ACTION_RECOVER = "com.woodpeckerbros.watchreminder.guardian.RECOVER";
    static final String EXTRA_PAYLOAD = "payload";
    static final String EXTRA_KEY = "key";

    private static final String PREFS = "guardian_bridge";
    private static final String KEY_LAST_PAYLOAD = "last_payload";
    private static final String KEY_LAST_SENT_AT = "last_sent_at";
    private static final long REFRESH_INTERVAL_MS = 6 * 60 * 60_000L;

    private GuardianBridge() { }

    public static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(GUARDIAN_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    public static void sync(Context context, boolean force) {
        Context appContext = context.getApplicationContext();
        if (!isInstalled(appContext)) return;
        String payload = payload(appContext);
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!force && payload.equals(prefs.getString(KEY_LAST_PAYLOAD, ""))
                && now - prefs.getLong(KEY_LAST_SENT_AT, 0L) < REFRESH_INTERVAL_MS) return;
        Intent intent = guardianIntent(ACTION_PLAN)
                .putExtra(EXTRA_PAYLOAD, payload)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        appContext.sendBroadcast(intent);
        prefs.edit().putString(KEY_LAST_PAYLOAD, payload).putLong(KEY_LAST_SENT_AT, now).apply();
        AppLog.d(appContext, "guardian plan sent occurrences=" + count(payload));
    }

    public static void acknowledgePopup(Context context, String reminderId, long scheduledAt) {
        if (!isInstalled(context) || reminderId == null || reminderId.isEmpty()) return;
        String key = occurrenceKey(reminderId, scheduledAt);
        context.sendBroadcast(guardianIntent(ACTION_ACK).putExtra(EXTRA_KEY, key)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES));
        AppLog.d(context, "guardian delivery ack key=" + key);
    }

    static String occurrenceKey(String reminderId, long scheduledAt) {
        return reminderId + ":" + ReminderScheduler.floorToMinute(scheduledAt);
    }

    private static Intent guardianIntent(String action) {
        return new Intent(action).setComponent(new ComponentName(GUARDIAN_PACKAGE, GUARDIAN_RECEIVER));
    }

    private static String payload(Context context) {
        JSONArray array = new JSONArray();
        Set<String> keys = new HashSet<>();
        try {
            for (ReminderScheduler.FallbackOccurrence occurrence
                    : ReminderScheduler.guardianOccurrences(context)) {
                add(array, keys, occurrence.reminderId, occurrence.reminderName,
                        occurrence.scheduledAt, occurrence.originalAt,
                        occurrence.day, occurrence.snooze);
            }
            ReminderStore reminderStore = new ReminderStore(context);
            for (ReminderSnoozeStore.Snooze snooze : new ReminderSnoozeStore(context).getAll()) {
                if (snooze.scheduledAt > System.currentTimeMillis()
                        && reminderStore.find(snooze.reminderId) != null) {
                    add(array, keys, snooze.reminderId, snooze.reminderName,
                            snooze.scheduledAt, snooze.originalScheduledAt, -1, true);
                }
            }
            return new JSONObject().put("version", 1).put("occurrences", array).toString();
        } catch (Exception error) {
            AppLog.e(context, "guardian plan build failed", error);
            return "{\"version\":1,\"occurrences\":[]}";
        }
    }

    private static void add(JSONArray array, Set<String> keys, String reminderId, String name,
                            long scheduledAt, long originalAt, int day, boolean snooze) throws Exception {
        String key = occurrenceKey(reminderId, scheduledAt);
        if (!keys.add(key)) return;
        array.put(new JSONObject()
                .put(EXTRA_KEY, key)
                .put("reminder_id", reminderId)
                .put("reminder_name", name == null || name.trim().isEmpty() ? "תזכורת" : name)
                .put("scheduled_at", scheduledAt)
                .put("original_at", originalAt)
                .put("day", day)
                .put("snooze", snooze));
    }

    private static int count(String payload) {
        try { return new JSONObject(payload).getJSONArray("occurrences").length(); }
        catch (Exception ignored) { return 0; }
    }
}
