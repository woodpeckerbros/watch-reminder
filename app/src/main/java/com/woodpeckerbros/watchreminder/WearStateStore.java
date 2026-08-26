package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

public class WearStateStore {
    private static final String PREFS_NAME = "wear_state";
    private static final String KEY_ASLEEP = "asleep";
    private static final String KEY_OFF_BODY = "off_body";
    private static final String KEY_UPDATED_AT = "updated_at";
    private static final long STATE_TTL_MS = 6 * 60 * 60_000L;

    private final SharedPreferences prefs;

    public WearStateStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean asleep() {
        return fresh() && prefs.getBoolean(KEY_ASLEEP, false);
    }

    public boolean offBody() {
        return fresh() && prefs.getBoolean(KEY_OFF_BODY, false);
    }

    public boolean shouldDeferAlerts() {
        return asleep() || offBody();
    }

    public void setAsleep(boolean asleep) {
        prefs.edit()
                .putBoolean(KEY_ASLEEP, asleep)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply();
    }

    public void setOffBody(boolean offBody) {
        prefs.edit()
                .putBoolean(KEY_OFF_BODY, offBody)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply();
    }

    /**
     * A confirmed wake or on-body transition means alerts can be delivered now.
     * Clear both independently sourced flags atomically so one stale signal cannot
     * keep the deferred queue blocked after the other source confirms availability.
     */
    public void markAvailable() {
        prefs.edit()
                .putBoolean(KEY_ASLEEP, false)
                .putBoolean(KEY_OFF_BODY, false)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply();
    }

    private boolean fresh() {
        long updatedAt = prefs.getLong(KEY_UPDATED_AT, 0);
        return updatedAt > 0 && System.currentTimeMillis() - updatedAt < STATE_TTL_MS;
    }
}
