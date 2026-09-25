package com.woodpeckerbros.watchreminder.reminder;

import com.woodpeckerbros.watchreminder.*;

import android.content.Context;
import android.content.SharedPreferences;

public class WearStateStore {
    private static final String PREFS_NAME = "wear_state";
    private static final String KEY_ASLEEP = "asleep";
    private static final String KEY_OFF_BODY = "off_body";
    private static final String KEY_UPDATED_AT = "updated_at";
    private static final String KEY_USER_ACTIVITY_STATE = "user_activity_state";
    private static final String KEY_USER_ACTIVITY_STATE_CHANGE_AT = "user_activity_state_change_at";
    private static final String KEY_USER_ACTIVITY_CALLBACK_RECEIVED_AT = "user_activity_callback_received_at";
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

    public String userActivityState() {
        return fresh() ? prefs.getString(KEY_USER_ACTIVITY_STATE, "UNKNOWN") : "UNKNOWN";
    }

    /**
     * Stores the latest Health Services episode identity, not a synthesized callback count.
     * Older or time-ambiguous deliveries are logged by the caller but must not roll persistent
     * state back before a future Smart Wake service restart.
     */
    public boolean setUserActivityState(String state, long stateChangeAt, long callbackReceivedAt) {
        long now = System.currentTimeMillis();
        String normalized = state == null ? "UNKNOWN" : state;
        String previousState = prefs.getString(KEY_USER_ACTIVITY_STATE, "UNKNOWN");
        long previousStateChangeAt = prefs.getLong(KEY_USER_ACTIVITY_STATE_CHANGE_AT, 0L);
        if (previousStateChangeAt > 0L && (stateChangeAt < previousStateChangeAt
                || (stateChangeAt == previousStateChangeAt && !normalized.equals(previousState)))) {
            return false;
        }
        prefs.edit()
                .putString(KEY_USER_ACTIVITY_STATE, normalized)
                .putLong(KEY_USER_ACTIVITY_STATE_CHANGE_AT, stateChangeAt)
                .putLong(KEY_USER_ACTIVITY_CALLBACK_RECEIVED_AT, callbackReceivedAt)
                .putLong(KEY_UPDATED_AT, now)
                .apply();
        return true;
    }

    public long userActivityStateChangeAt() {
        return prefs.getLong(KEY_USER_ACTIVITY_STATE_CHANGE_AT, 0L);
    }

    public long userActivityCallbackReceivedAt() {
        return prefs.getLong(KEY_USER_ACTIVITY_CALLBACK_RECEIVED_AT, 0L);
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
