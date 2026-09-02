package com.woodpeckerbros.watchreminder.smartwake;

import android.content.Context;
import android.content.SharedPreferences;

public final class SmartAlarmStateStore {
    private final SharedPreferences prefs;

    public SmartAlarmStateStore(Context context) {
        this(context, 1);
    }

    public SmartAlarmStateStore(Context context, int alarmId) {
        prefs = context.getApplicationContext().getSharedPreferences(
                alarmId == 1 ? "smart_alarm_state" : "smart_alarm_state_" + alarmId, Context.MODE_PRIVATE);
    }

    public synchronized void begin(long targetAt) {
        prefs.edit().putLong("target_at", targetAt).putBoolean("fired", false).putBoolean("dismissed", false)
                .putInt("snooze_used", 0).apply();
    }

    public synchronized void beginSnooze(long targetAt, int snoozeUsed) {
        prefs.edit().putLong("target_at", targetAt).putBoolean("fired", false).putBoolean("dismissed", false)
                .putInt("snooze_used", snoozeUsed).apply();
    }

    public long targetAt() { return prefs.getLong("target_at", 0L); }
    public boolean fired(long targetAt) { return targetAt == targetAt() && prefs.getBoolean("fired", false); }
    public boolean dismissed(long targetAt) { return targetAt == targetAt() && prefs.getBoolean("dismissed", false); }

    public synchronized boolean claimFire(long targetAt) {
        if (targetAt != targetAt() || fired(targetAt) || dismissed(targetAt)) return false;
        prefs.edit().putBoolean("fired", true).apply();
        return true;
    }

    public void dismiss(long targetAt) {
        if (targetAt == targetAt()) prefs.edit().putBoolean("dismissed", true).apply();
    }

    public int snoozeUsed() { return prefs.getInt("snooze_used", 0); }
    public long systemAlarmTargetAt() { return prefs.getLong("system_alarm_target_at", 0L); }
    public void setSystemAlarmTargetAt(long targetAt) {
        prefs.edit().putLong("system_alarm_target_at", targetAt).apply();
    }
    public void clear() { prefs.edit().clear().apply(); }
}
