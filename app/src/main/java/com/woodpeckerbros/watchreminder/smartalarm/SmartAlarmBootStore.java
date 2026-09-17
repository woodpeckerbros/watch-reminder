package com.woodpeckerbros.watchreminder.smartalarm;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Minimal device-protected shadow of armed Smart Alarm occurrences.
 *
 * Smart Alarm settings remain credential protected.  This store contains no health data and no
 * user-facing configuration. It exists so LOCKED_BOOT_COMPLETED can restore an already armed
 * safety deadline and, when applicable, the bounded Smart Wake lead-in before unlock.
 */
final class SmartAlarmBootStore {
    private static final String PREFS = "smart_alarm_boot_safety";
    private static final String TARGET_PREFIX = "target_";
    private static final String MONITOR_AT_PREFIX = "monitor_at_";
    private static final String EARLIEST_WAKE_PREFIX = "earliest_wake_";
    private static final String SMART_WAKE_ENABLED_PREFIX = "smart_wake_enabled_";
    private static final String DELIVERED_PREFIX = "delivered_";

    static final class Entry {
        final int alarmId;
        final long targetAt;
        final long monitoringStartAt;
        final long earliestWakeAt;
        final boolean smartWakeEnabled;
        final boolean delivered;

        Entry(int alarmId, long targetAt, long monitoringStartAt, long earliestWakeAt,
              boolean smartWakeEnabled, boolean delivered) {
            this.alarmId = alarmId;
            this.targetAt = targetAt;
            this.monitoringStartAt = monitoringStartAt;
            this.earliestWakeAt = earliestWakeAt;
            this.smartWakeEnabled = smartWakeEnabled;
            this.delivered = delivered;
        }
    }

    private SmartAlarmBootStore() {}

    static synchronized void arm(Context context, int alarmId, long targetAt,
                                 long monitoringStartAt, long earliestWakeAt,
                                 boolean smartWakeEnabled) {
        SharedPreferences prefs = prefs(context);
        long previousTarget = prefs.getLong(targetKey(alarmId), 0L);
        SharedPreferences.Editor editor = prefs.edit()
                .putLong(targetKey(alarmId), targetAt)
                .putLong(monitorAtKey(alarmId), monitoringStartAt)
                .putLong(earliestWakeKey(alarmId), earliestWakeAt)
                .putBoolean(smartWakeEnabledKey(alarmId), smartWakeEnabled);
        if (previousTarget != targetAt) {
            editor.putBoolean(deliveredKey(alarmId), false);
        }
        editor.commit();
    }

    static synchronized void disarm(Context context, int alarmId, long targetAt) {
        SharedPreferences prefs = prefs(context);
        if (targetAt != 0L && prefs.getLong(targetKey(alarmId), 0L) != targetAt) return;
        prefs.edit().remove(targetKey(alarmId)).remove(monitorAtKey(alarmId))
                .remove(earliestWakeKey(alarmId)).remove(smartWakeEnabledKey(alarmId))
                .remove(deliveredKey(alarmId)).commit();
    }

    static synchronized boolean matches(Context context, int alarmId, long targetAt) {
        return targetAt > 0L && prefs(context).getLong(targetKey(alarmId), 0L) == targetAt;
    }

    static synchronized boolean supportsSmartWake(Context context, int alarmId, long targetAt) {
        SharedPreferences prefs = prefs(context);
        return prefs.getLong(targetKey(alarmId), 0L) == targetAt
                && prefs.getBoolean(smartWakeEnabledKey(alarmId), false)
                && prefs.getLong(monitorAtKey(alarmId), 0L) > 0L
                && prefs.getLong(earliestWakeKey(alarmId), 0L) > 0L;
    }

    static synchronized boolean delivered(Context context, int alarmId, long targetAt) {
        SharedPreferences prefs = prefs(context);
        return prefs.getLong(targetKey(alarmId), 0L) == targetAt
                && prefs.getBoolean(deliveredKey(alarmId), false);
    }

    static synchronized boolean markDirectBootDelivered(Context context, int alarmId, long targetAt) {
        SharedPreferences prefs = prefs(context);
        if (prefs.getLong(targetKey(alarmId), 0L) != targetAt
                || prefs.getBoolean(deliveredKey(alarmId), false)) {
            return false;
        }
        return prefs.edit().putBoolean(deliveredKey(alarmId), true).commit();
    }

    static synchronized List<Entry> entries(Context context) {
        SharedPreferences prefs = prefs(context);
        List<Entry> result = new ArrayList<>();
        for (Map.Entry<String, ?> item : prefs.getAll().entrySet()) {
            if (!item.getKey().startsWith(TARGET_PREFIX) || !(item.getValue() instanceof Long)) continue;
            try {
                int alarmId = Integer.parseInt(item.getKey().substring(TARGET_PREFIX.length()));
                long targetAt = (Long) item.getValue();
                result.add(new Entry(alarmId, targetAt,
                        prefs.getLong(monitorAtKey(alarmId), 0L),
                        prefs.getLong(earliestWakeKey(alarmId), 0L),
                        prefs.getBoolean(smartWakeEnabledKey(alarmId), false),
                        prefs.getBoolean(deliveredKey(alarmId), false)));
            } catch (NumberFormatException ignored) {
            }
        }
        Collections.sort(result, (left, right) -> Integer.compare(left.alarmId, right.alarmId));
        return result;
    }

    static String diagnosticSummary(Context context) {
        StringBuilder result = new StringBuilder();
        for (Entry entry : entries(context)) {
            result.append("id=").append(entry.alarmId)
                    .append(" target=").append(entry.targetAt)
                    .append(" monitoringStart=").append(entry.monitoringStartAt)
                    .append(" earliestWake=").append(entry.earliestWakeAt)
                    .append(" smartWakeEnabled=").append(entry.smartWakeEnabled)
                    .append(" directBootDelivered=").append(entry.delivered).append('\n');
        }
        return result.length() == 0 ? "none\n" : result.toString();
    }

    private static SharedPreferences prefs(Context context) {
        Context deviceContext = context.getApplicationContext().createDeviceProtectedStorageContext();
        return deviceContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String targetKey(int alarmId) { return TARGET_PREFIX + alarmId; }
    private static String monitorAtKey(int alarmId) { return MONITOR_AT_PREFIX + alarmId; }
    private static String earliestWakeKey(int alarmId) { return EARLIEST_WAKE_PREFIX + alarmId; }
    private static String smartWakeEnabledKey(int alarmId) { return SMART_WAKE_ENABLED_PREFIX + alarmId; }
    private static String deliveredKey(int alarmId) { return DELIVERED_PREFIX + alarmId; }
}
