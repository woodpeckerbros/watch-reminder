package com.woodpeckerbros.watchreminder.smartalarm;

import com.woodpeckerbros.watchreminder.reminder.*;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists only observed delivery cadence, never health values. */
final class SmartWakeSamplingProfile {
    private static final String PREFS = "smart_wake_sampling_profile";
    private static final String KEY_OBSERVED_HR_INTERVAL_MS = "observed_hr_interval_ms";
    private static final String KEY_LAST_HR_SAMPLE_AT = "last_hr_sample_at";
    static final long DEFAULT_START_BUFFER_MS = 5 * 60_000L;
    private static final long MAX_START_BUFFER_MS = 15 * 60_000L;
    private static final long MIN_VALID_INTERVAL_MS = 15_000L;
    private static final long MAX_VALID_INTERVAL_MS = 10 * 60_000L;

    private SmartWakeSamplingProfile() {}

    static long monitorLeadTime(Context context) {
        return SmartWakeDetector.BASELINE_MIN_DURATION_MS + startBufferMs(observedHrIntervalMs(context));
    }

    static long observedHrIntervalMs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_OBSERVED_HR_INTERVAL_MS, 0L);
    }

    static void recordHeartRateDelivery(Context context, long receivedAt) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long previous = prefs.getLong(KEY_LAST_HR_SAMPLE_AT, 0L);
        SharedPreferences.Editor editor = prefs.edit().putLong(KEY_LAST_HR_SAMPLE_AT, receivedAt);
        if (previous > 0L) {
            long interval = receivedAt - previous;
            if (interval >= MIN_VALID_INTERVAL_MS && interval <= MAX_VALID_INTERVAL_MS) {
                long existing = prefs.getLong(KEY_OBSERVED_HR_INTERVAL_MS, 0L);
                // Keep the slower observed cadence for the following night, then reduce it slowly.
                long updated = existing == 0L ? interval : Math.max(interval, existing - 15_000L);
                editor.putLong(KEY_OBSERVED_HR_INTERVAL_MS, updated);
            }
        }
        editor.apply();
    }

    static long startBufferMs(long observedHrIntervalMs) {
        long adaptive = observedHrIntervalMs <= 0L ? DEFAULT_START_BUFFER_MS
                : Math.min(MAX_START_BUFFER_MS, observedHrIntervalMs * 2L);
        return Math.max(DEFAULT_START_BUFFER_MS, adaptive);
    }
}
