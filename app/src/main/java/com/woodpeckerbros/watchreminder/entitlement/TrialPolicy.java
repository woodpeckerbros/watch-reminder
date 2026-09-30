package com.woodpeckerbros.watchreminder.entitlement;

import java.util.Calendar;
import java.util.TimeZone;

/** Stateless 14-day trial calculation, deliberately independent of Android storage. */
public final class TrialPolicy {
    public static final long TRIAL_DURATION_MS = 14L * 24L * 60L * 60L * 1000L;
    public static final long EXPIRY_WARNING_BEFORE_MS = 24L * 60L * 60L * 1000L;

    private TrialPolicy() { }

    public static long expiryWarningAt(Snapshot trial) {
        return expiryWarningAt(trial, TimeZone.getDefault());
    }

    static long expiryWarningAt(Snapshot trial, TimeZone zone) {
        long expiryAt = trial.trialStartedAt + TRIAL_DURATION_MS;
        Calendar expiry = Calendar.getInstance(zone);
        expiry.setTimeInMillis(expiryAt);
        int expiryHour = expiry.get(Calendar.HOUR_OF_DAY);
        if (expiryHour >= 9 && expiryHour < 19) {
            return expiryAt - EXPIRY_WARNING_BEFORE_MS;
        }
        // A warning at night is easily missed. For an evening/overnight expiry, warn
        // roughly 12 hours ahead, constrained to the watch's local daytime (09:00–18:00).
        Calendar warning = Calendar.getInstance(zone);
        warning.setTimeInMillis(expiryAt - 12L * 60L * 60L * 1000L);
        int warningHour = warning.get(Calendar.HOUR_OF_DAY);
        if (warningHour < 9) {
            warning.set(Calendar.HOUR_OF_DAY, 9);
            warning.set(Calendar.MINUTE, 0);
            warning.set(Calendar.SECOND, 0);
            warning.set(Calendar.MILLISECOND, 0);
        } else if (warningHour >= 18) {
            warning.set(Calendar.HOUR_OF_DAY, 18);
            warning.set(Calendar.MINUTE, 0);
            warning.set(Calendar.SECOND, 0);
            warning.set(Calendar.MILLISECOND, 0);
        }
        return warning.getTimeInMillis();
    }

    public static boolean inExpiryWarningWindow(Snapshot trial) {
        return inExpiryWarningWindow(trial, TimeZone.getDefault());
    }

    static boolean inExpiryWarningWindow(Snapshot trial, TimeZone zone) {
        return !trial.lifetimePurchased && trial.featureAccessGranted
                && trial.effectiveNow >= expiryWarningAt(trial, zone);
    }

    public static Snapshot evaluate(long storedStartAt, long highestSeenWallTime,
                                    long currentWallTime, boolean lifetimePurchased) {
        long safeNow = Math.max(0L, currentWallTime);
        long effectiveNow = Math.max(safeNow, highestSeenWallTime);
        long startAt = storedStartAt > 0L ? storedStartAt : effectiveNow;
        if (startAt > effectiveNow) {
            // A corrupted/future value must never create extra trial time.
            startAt = effectiveNow;
        }
        long elapsed = Math.max(0L, effectiveNow - startAt);
        boolean active = lifetimePurchased || elapsed < TRIAL_DURATION_MS;
        long remaining = lifetimePurchased ? Long.MAX_VALUE
                : Math.max(0L, TRIAL_DURATION_MS - elapsed);
        return new Snapshot(startAt, effectiveNow, remaining, lifetimePurchased, active);
    }

    public static final class Snapshot {
        public final long trialStartedAt;
        public final long effectiveNow;
        public final long remainingMillis;
        public final boolean lifetimePurchased;
        public final boolean featureAccessGranted;

        Snapshot(long trialStartedAt, long effectiveNow, long remainingMillis,
                 boolean lifetimePurchased, boolean featureAccessGranted) {
            this.trialStartedAt = trialStartedAt;
            this.effectiveNow = effectiveNow;
            this.remainingMillis = remainingMillis;
            this.lifetimePurchased = lifetimePurchased;
            this.featureAccessGranted = featureAccessGranted;
        }
    }
}
