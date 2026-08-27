package com.woodpeckerbros.watchreminder.smartwake;

import android.content.Context;
import android.content.SharedPreferences;

public final class SmartAlarmStateStore {
    private final SharedPreferences prefs;

    public SmartAlarmStateStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("smart_alarm_state", Context.MODE_PRIVATE);
    }

    public synchronized void begin(long targetAt) {
        prefs.edit().putLong("target_at", targetAt).putBoolean("fired", false).putBoolean("dismissed", false).apply();
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
}
