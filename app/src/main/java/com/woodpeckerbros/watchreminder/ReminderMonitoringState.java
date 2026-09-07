package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

/** Small, service-owned state. It is intentionally not the source of reminder truth. */
public final class ReminderMonitoringState {
    private static final String PREFS = "reminder_monitoring";
    private static final String STARTED_AT = "started_at";
    private static final String CHECK_COUNT = "check_count";
    private static final String REPAIR_COUNT = "repair_count";
    private static final String LAST_CHECK_AT = "last_check_at";
    private static final String NEXT_CHECK_AT = "next_check_at";
    private static final String ALARM_REQUEST_CODE = "alarm_request_code";
    private static final String ALARM_TRIGGER_AT = "alarm_trigger_at";

    private final SharedPreferences prefs;

    public ReminderMonitoringState(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public long startedAt() { return prefs.getLong(STARTED_AT, 0L); }
    public long lastCheckAt() { return prefs.getLong(LAST_CHECK_AT, 0L); }
    public long nextCheckAt() { return prefs.getLong(NEXT_CHECK_AT, 0L); }
    public int checkCount() { return prefs.getInt(CHECK_COUNT, 0); }
    public int repairCount() { return prefs.getInt(REPAIR_COUNT, 0); }
    public int alarmRequestCode() { return prefs.getInt(ALARM_REQUEST_CODE, Integer.MIN_VALUE); }
    public long alarmTriggerAt() { return prefs.getLong(ALARM_TRIGGER_AT, 0L); }

    public void markStarted(long now) {
        prefs.edit().putLong(STARTED_AT, now).apply();
    }

    public void recordCheck(long now, long nextAt, boolean repaired) {
        SharedPreferences.Editor editor = prefs.edit()
                .putLong(LAST_CHECK_AT, now)
                .putLong(NEXT_CHECK_AT, nextAt)
                .putInt(CHECK_COUNT, checkCount() + 1);
        if (repaired) editor.putInt(REPAIR_COUNT, repairCount() + 1);
        editor.apply();
    }

    public void recordScheduledAlarm(int requestCode, long triggerAt) {
        prefs.edit().putInt(ALARM_REQUEST_CODE, requestCode).putLong(ALARM_TRIGGER_AT, triggerAt).apply();
    }

    public void clearScheduledAlarm() {
        prefs.edit().remove(ALARM_REQUEST_CODE).remove(ALARM_TRIGGER_AT).apply();
    }

    public void clearMaintenanceSchedule() {
        prefs.edit().remove(NEXT_CHECK_AT).apply();
    }
}
