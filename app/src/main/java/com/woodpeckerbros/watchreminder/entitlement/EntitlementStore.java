package com.woodpeckerbros.watchreminder.entitlement;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** Persistent local cache for trial timing and the last Play-verified lifetime entitlement. */
public final class EntitlementStore {
    private static final String PREFS = "zmanio_entitlement";
    private static final String KEY_TRIAL_STARTED_AT = "trial_started_at";
    private static final String KEY_HIGHEST_WALL_TIME = "highest_wall_time";
    private static final String KEY_LIFETIME_PURCHASED = "lifetime_purchased";
    private static final String KEY_LIFETIME_VERIFIED_AT = "lifetime_verified_at";

    private final SharedPreferences prefs;
    private final EntitlementClock clock;

    public EntitlementStore(Context context) {
        this(context, System::currentTimeMillis);
    }

    EntitlementStore(Context context, EntitlementClock clock) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.clock = clock;
    }

    public synchronized TrialPolicy.Snapshot snapshot() {
        long now = clock.wallTimeMillis();
        long startAt = prefs.getLong(KEY_TRIAL_STARTED_AT, 0L);
        long highest = prefs.getLong(KEY_HIGHEST_WALL_TIME, 0L);
        TrialPolicy.Snapshot snapshot = TrialPolicy.evaluate(startAt, highest, now,
                prefs.getBoolean(KEY_LIFETIME_PURCHASED, false));
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        if (startAt != snapshot.trialStartedAt) {
            editor.putLong(KEY_TRIAL_STARTED_AT, snapshot.trialStartedAt);
            changed = true;
        }
        if (highest != snapshot.effectiveNow) {
            editor.putLong(KEY_HIGHEST_WALL_TIME, snapshot.effectiveNow);
            changed = true;
        }
        if (changed) editor.commit();
        return snapshot;
    }

    public boolean hasFeatureAccess() {
        return snapshot().featureAccessGranted;
    }

    public synchronized void setLifetimePurchased(boolean purchased) {
        SharedPreferences.Editor editor = prefs.edit().putBoolean(KEY_LIFETIME_PURCHASED, purchased);
        if (purchased) editor.putLong(KEY_LIFETIME_VERIFIED_AT, Math.max(clock.wallTimeMillis(), 0L));
        else editor.remove(KEY_LIFETIME_VERIFIED_AT);
        editor.commit();
    }

    public JSONObject exportTrialMetadata() {
        TrialPolicy.Snapshot snapshot = snapshot();
        JSONObject metadata = new JSONObject();
        try {
            metadata.put("trialStartedAt", snapshot.trialStartedAt);
            metadata.put("highestSeenWallTime", snapshot.effectiveNow);
        } catch (Exception ignored) {
        }
        return metadata;
    }

    /** Keeps the earliest credible first activation when a watch backup is restored. */
    public synchronized void importTrialMetadata(JSONObject metadata) {
        if (metadata == null) return;
        long importedStart = metadata.optLong("trialStartedAt", 0L);
        if (importedStart <= 0L) return;
        long now = Math.max(0L, clock.wallTimeMillis());
        if (importedStart > now) return;
        long existing = prefs.getLong(KEY_TRIAL_STARTED_AT, 0L);
        long chosenStart = existing > 0L ? Math.min(existing, importedStart) : importedStart;
        long highest = Math.max(prefs.getLong(KEY_HIGHEST_WALL_TIME, 0L), now);
        prefs.edit().putLong(KEY_TRIAL_STARTED_AT, chosenStart)
                .putLong(KEY_HIGHEST_WALL_TIME, highest).commit();
    }

    public long trialStartedAt() {
        return snapshot().trialStartedAt;
    }

    public long lastLifetimeVerifiedAt() {
        return prefs.getLong(KEY_LIFETIME_VERIFIED_AT, 0L);
    }
}
