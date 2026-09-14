package com.woodpeckerbros.watchreminder.entitlement;

/** Stateless 14-day trial calculation, deliberately independent of Android storage. */
public final class TrialPolicy {
    public static final long TRIAL_DURATION_MS = 14L * 24L * 60L * 60L * 1000L;

    private TrialPolicy() { }

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
