package com.woodpeckerbros.watchreminder.guardian;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

final class GuardianStore {
    private static final String PREFS = "guardian_plan";
    private static final String KEY_PAYLOAD = "payload";
    private static final String KEY_SCHEDULED = "scheduled_keys";
    private static final String KEY_ACKED = "acked_keys";
    // Give the primary receiver a few seconds to acknowledge a healthy delivery. If it does
    // not, recovery starts while the reminder is still timely rather than minutes later.
    private static final long FALLBACK_DELAY_MS = 15_000L;
    private static final long LATE_LIMIT_MS = 24 * 60 * 60_000L;

    private GuardianStore() { }

    static synchronized void apply(Context context, String payload) {
        if (payload == null || payload.isEmpty()) return;
        try {
            JSONArray occurrences = new JSONObject(payload).optJSONArray("occurrences");
            if (occurrences == null) return;
            SharedPreferences prefs = prefs(context);
            Set<String> oldKeys = copy(prefs.getStringSet(KEY_SCHEDULED, new HashSet<>()));
            Set<String> acked = copy(prefs.getStringSet(KEY_ACKED, new HashSet<>()));
            Set<String> nextKeys = new HashSet<>();
            long now = System.currentTimeMillis();

            for (int i = 0; i < occurrences.length(); i++) {
                JSONObject item = occurrences.optJSONObject(i);
                if (item == null) continue;
                String key = item.optString(GuardianContract.EXTRA_KEY, "");
                long scheduledAt = item.optLong(GuardianContract.EXTRA_SCHEDULED_AT, 0L);
                if (key.isEmpty() || scheduledAt <= 0L || scheduledAt < now - LATE_LIMIT_MS) continue;
                nextKeys.add(key);
                if (!acked.contains(key)) schedule(context, item);
            }
            for (String oldKey : oldKeys) {
                if (!nextKeys.contains(oldKey)) cancel(context, oldKey);
            }
            acked.retainAll(nextKeys);
            prefs.edit().putString(KEY_PAYLOAD, payload).putStringSet(KEY_SCHEDULED, nextKeys)
                    .putStringSet(KEY_ACKED, acked).commit();
            android.util.Log.d("ZmanioGuardian", "plan applied occurrences=" + nextKeys.size());
        } catch (Exception error) {
            android.util.Log.e("ZmanioGuardian", "plan apply failed", error);
        }
    }

    static synchronized void acknowledge(Context context, String key) {
        if (key == null || key.isEmpty()) return;
        cancel(context, key);
        SharedPreferences prefs = prefs(context);
        Set<String> acked = copy(prefs.getStringSet(KEY_ACKED, new HashSet<>()));
        acked.add(key);
        prefs.edit().putStringSet(KEY_ACKED, acked).commit();
        android.util.Log.d("ZmanioGuardian", "delivery acknowledged key=" + key);
    }

    static synchronized void rescheduleStored(Context context) {
        String payload = prefs(context).getString(KEY_PAYLOAD, "");
        if (!payload.isEmpty()) apply(context, payload);
    }

    static int plannedCount(Context context) {
        return prefs(context).getStringSet(KEY_SCHEDULED, new HashSet<>()).size();
    }

    private static void schedule(Context context, JSONObject item) {
        String key = item.optString(GuardianContract.EXTRA_KEY, "");
        long scheduledAt = item.optLong(GuardianContract.EXTRA_SCHEDULED_AT, 0L);
        long triggerAt = Math.max(System.currentTimeMillis() + 1_000L,
                scheduledAt + FALLBACK_DELAY_MS);
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) return;
        Intent intent = new Intent(context, GuardianFallbackReceiver.class)
                .putExtra(GuardianContract.EXTRA_KEY, key)
                .putExtra(GuardianContract.EXTRA_REMINDER_ID,
                        item.optString(GuardianContract.EXTRA_REMINDER_ID, ""))
                .putExtra(GuardianContract.EXTRA_REMINDER_NAME,
                        item.optString(GuardianContract.EXTRA_REMINDER_NAME, "תזכורת"))
                .putExtra(GuardianContract.EXTRA_SCHEDULED_AT, scheduledAt)
                .putExtra(GuardianContract.EXTRA_ORIGINAL_AT,
                        item.optLong(GuardianContract.EXTRA_ORIGINAL_AT, scheduledAt))
                .putExtra(GuardianContract.EXTRA_DAY,
                        item.optInt(GuardianContract.EXTRA_DAY, -1))
                .putExtra(GuardianContract.EXTRA_SNOOZE,
                        item.optBoolean(GuardianContract.EXTRA_SNOOZE, false));
        PendingIntent operation = PendingIntent.getBroadcast(context, key.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent showIntent = new Intent(context, GuardianAlertActivity.class)
                .putExtra(GuardianContract.EXTRA_KEY, key)
                .putExtra(GuardianContract.EXTRA_REMINDER_NAME,
                        item.optString(GuardianContract.EXTRA_REMINDER_NAME, "תזכורת"));
        PendingIntent show = PendingIntent.getActivity(context, key.hashCode(), showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            manager.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerAt, show), operation);
        } catch (SecurityException error) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation);
        }
    }

    static void scheduleGuardianAlert(Context context, Intent source) {
        String key = source.getStringExtra(GuardianContract.EXTRA_KEY);
        if (key == null) return;
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) return;
        Intent alert = new Intent(context, GuardianAlertReceiver.class)
                .putExtra(GuardianContract.EXTRA_KEY, key)
                .putExtra(GuardianContract.EXTRA_REMINDER_NAME,
                        source.getStringExtra(GuardianContract.EXTRA_REMINDER_NAME));
        PendingIntent pending = PendingIntent.getBroadcast(context, alertRequestCode(key), alert,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 8_000L, pending);
    }

    private static void cancel(Context context, String key) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager != null) {
            PendingIntent fallback = PendingIntent.getBroadcast(context, key.hashCode(),
                    new Intent(context, GuardianFallbackReceiver.class),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (fallback != null) manager.cancel(fallback);
            PendingIntent alert = PendingIntent.getBroadcast(context, alertRequestCode(key),
                    new Intent(context, GuardianAlertReceiver.class),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (alert != null) manager.cancel(alert);
        }
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications != null) notifications.cancel(key.hashCode());
    }

    private static int alertRequestCode(String key) { return (key + ":guardian-alert").hashCode(); }
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    private static Set<String> copy(Set<String> source) { return new HashSet<>(source); }
}
