package com.woodpeckerbros.watchreminder.smartalarm;

/** Fixed feedback expiry and a separately renewable, silent interaction grace period. */
final class SmartAlarmPresentationTiming {
    static final long INTERACTION_GRACE_MS = 5_000L;

    private SmartAlarmPresentationTiming() {}

    static long graceRemaining(long now, long lastProgressAt) {
        return lastProgressAt <= 0L ? 0L
                : Math.max(0L, lastProgressAt + INTERACTION_GRACE_MS - now);
    }

    static int feedbackRemaining(long now, long feedbackEndAt, int configuredDurationMs) {
        return (int) Math.max(0L, Math.min(configuredDurationMs, feedbackEndAt - now));
    }
}
