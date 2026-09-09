package com.woodpeckerbros.watchreminder.smartalarm;

import com.woodpeckerbros.watchreminder.reminder.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Finds a likely light-sleep / early-wake opportunity, not proof that the user is already awake.
 * The score compares a short recent window against this monitoring session's sleep baseline.
 */
public final class SmartWakeDetector {
    public static final long RECENT_WINDOW_MS = 75_000L;
    public static final long BASELINE_WINDOW_MS = 15 * 60_000L;
    // Leave the recent window out of the baseline, so this guarantees at least ten full minutes.
    public static final long BASELINE_MIN_DURATION_MS = 10 * 60_000L + RECENT_WINDOW_MS;
    public static final int CONFIRMED_WAKE_THRESHOLD = 23;
    public static final int IMMEDIATE_WAKE_THRESHOLD = 42;
    public static final int CANDIDATE_CONFIRMATION_THRESHOLD = 12;
    public static final long CANDIDATE_MEMORY_MS = 90_000L;
    private static final long CLEARLY_AWAKE_WINDOW_MS = 90_000L;
    private static final long FRESH_ACTIVITY_TRANSITION_MS = 120_000L;
    // Health Services activity updates can be brief while a user turns over.  Thirty seconds is
    // one normal scoring interval: prompt after a sustained state, without trusting one sample.
    private static final long SYSTEM_AWAKE_PERSISTENCE_MS = 30_000L;
    private static final long SYSTEM_AWAKE_OBSERVATION_WINDOW_MS = 90_000L;
    private static final long MOVEMENT_BUCKET_MS = 15_000L;
    private static final long COVERAGE_BUCKET_MS = 5_000L;

    public enum UserActivity { ASLEEP, PASSIVE, EXERCISE, UNKNOWN }

    private final Deque<TimedValue> heartRates = new ArrayDeque<>();
    private final Deque<TimedValue> accelerometer = new ArrayDeque<>();
    private final Deque<TimedValue> gyroscope = new ArrayDeque<>();
    private final Deque<Long> steps = new ArrayDeque<>();
    private final long startedAt;
    private UserActivity userActivity = UserActivity.UNKNOWN;
    private long awakeTransitionAt = Long.MIN_VALUE;
    private long systemNonAsleepStartedAt = Long.MIN_VALUE;
    private long lastSystemNonAsleepAt = Long.MIN_VALUE;
    private int systemNonAsleepObservations;
    private long candidateStartedAt = Long.MIN_VALUE;

    public SmartWakeDetector(long startedAt) { this.startedAt = startedAt; }

    public void addHeartRate(double bpm, long at) {
        if (bpm >= 30 && bpm <= 240) heartRates.addLast(new TimedValue(at, bpm));
    }
    public void addAccelerometerMotion(double magnitude, long at) {
        if (magnitude >= 0) accelerometer.addLast(new TimedValue(at, magnitude));
    }
    public void addGyroscopeMotion(double magnitude, long at) {
        if (magnitude >= 0) gyroscope.addLast(new TimedValue(at, magnitude));
    }
    public void addStep(long at) { steps.addLast(at); }

    /**
     * Supplies persisted context when a monitor starts, without pretending that it is a fresh
     * Health Services observation.  On OnePlus the last PASSIVE value can outlive the actual
     * awake period; starting a new session used to turn that one stale value into thirty seconds
     * of apparent persistence and wake with no candidate or clearly-awake evidence.
     */
    public void seedUserActivity(UserActivity value) {
        userActivity = value == null ? UserActivity.UNKNOWN : value;
        awakeTransitionAt = Long.MIN_VALUE;
        systemNonAsleepStartedAt = Long.MIN_VALUE;
        lastSystemNonAsleepAt = Long.MIN_VALUE;
        systemNonAsleepObservations = 0;
    }

    public void setUserActivity(UserActivity value, long receivedAt) {
        if (value == null) value = UserActivity.UNKNOWN;
        if (userActivity == UserActivity.ASLEEP
                && (value == UserActivity.PASSIVE || value == UserActivity.EXERCISE)) awakeTransitionAt = receivedAt;
        if (isSystemNonAsleep(value)) {
            boolean continuing = systemNonAsleepStartedAt != Long.MIN_VALUE
                    && receivedAt >= lastSystemNonAsleepAt
                    && receivedAt - lastSystemNonAsleepAt <= SYSTEM_AWAKE_OBSERVATION_WINDOW_MS;
            if (!continuing) {
                systemNonAsleepStartedAt = receivedAt;
                systemNonAsleepObservations = 0;
            }
            if (receivedAt >= lastSystemNonAsleepAt) {
                systemNonAsleepObservations++;
                lastSystemNonAsleepAt = receivedAt;
            }
        } else {
            // An explicit ASLEEP (or an unavailable/unknown state) breaks the persistence run.
            systemNonAsleepStartedAt = Long.MIN_VALUE;
            lastSystemNonAsleepAt = Long.MIN_VALUE;
            systemNonAsleepObservations = 0;
        }
        userActivity = value;
    }

    public Decision evaluate(long now) {
        prune(now);
        long recentStart = now - RECENT_WINDOW_MS;
        long baselineStart = Math.max(startedAt, now - BASELINE_WINDOW_MS);
        long baselineEnd = recentStart;
        boolean baselineTimeReady = now - startedAt >= BASELINE_MIN_DURATION_MS;

        HeartRateStats recentHr = heartRateStats(recentStart, now);
        HeartRateStats baselineHr = baselineHeartRateStats(baselineStart, baselineEnd);
        MotionStats recentAccel = motionStats(accelerometer, recentStart, now, .35, 1.15);
        MotionStats baselineAccel = baselineMotionStats(accelerometer, baselineStart, baselineEnd, .35, 1.15);
        MotionStats recentGyro = motionStats(gyroscope, recentStart, now, .55, 1.50);
        MotionStats baselineGyro = baselineMotionStats(gyroscope, baselineStart, baselineEnd, .55, 1.50);
        SampleQuality baselineHrQuality = sampleQuality(heartRates, baselineStart, baselineEnd);
        SampleQuality baselineAccelQuality = sampleQuality(accelerometer, baselineStart, baselineEnd);
        SampleQuality baselineGyroQuality = sampleQuality(gyroscope, baselineStart, baselineEnd);
        SampleQuality recentHrQuality = sampleQuality(heartRates, recentStart, now);
        MovementCoverage movementCoverage = movementCoverage(recentStart, now);
        // HR delivery is device-dependent and may be sparse. Motion is continuously sampled on
        // supported watches, so it is the minimum data-quality requirement for a usable baseline.
        boolean baselineHasMovement = baselineAccelQuality.count > 0 || baselineGyroQuality.count > 0;
        boolean baselineReady = baselineTimeReady && baselineHasMovement;
        String baselineStatus = !baselineTimeReady ? "COLLECTING_DURATION"
                : !baselineHasMovement ? "MISSING_MOVEMENT_SAMPLES" : "READY";
        MovementPattern movementPattern = movementPattern(now);
        int steps60Seconds = countStepsSince(now - 60_000L);
        int steps90Seconds = countStepsSince(now - CLEARLY_AWAKE_WINDOW_MS);
        boolean systemNonAsleep = isSystemNonAsleep(userActivity);
        long systemNonAsleepDurationMs = systemNonAsleep && systemNonAsleepStartedAt != Long.MIN_VALUE
                ? Math.max(0L, now - systemNonAsleepStartedAt) : 0L;
        boolean repeatedSystemNonAsleep = systemNonAsleepObservations >= 2
                && lastSystemNonAsleepAt != Long.MIN_VALUE
                && now - lastSystemNonAsleepAt <= SYSTEM_AWAKE_OBSERVATION_WINDOW_MS;
        boolean systemAwakePersistent = systemNonAsleep && (systemNonAsleepDurationMs >= SYSTEM_AWAKE_PERSISTENCE_MS
                || repeatedSystemNonAsleep);

        double hrAboveBaseline = recentHr.count >= 2 && baselineHr.count >= 4 ? recentHr.mean - baselineHr.mean : 0;
        double hrvAboveBaseline = recentHr.count >= 3 && baselineHr.count >= 4
                ? recentHr.variability - baselineHr.variability : 0;
        int hrRisePoints = hrAboveBaseline >= 6 ? 22 : hrAboveBaseline >= 3 ? 15 : 0;
        int hrvRisePoints = hrvAboveBaseline >= 2 ? 12 : hrvAboveBaseline >= 1 ? 8 : 0;

        int accelPoints = gentleMovementPoints(recentAccel, baselineAccel, 14, 5);
        int gyroPoints = gentleMovementPoints(recentGyro, baselineGyro, 12, 4);
        int combinedMotionPoints = accelPoints >= 14 && gyroPoints >= 12 ? 10 : 0;
        boolean transitionRecent = awakeTransitionAt != Long.MIN_VALUE && now >= awakeTransitionAt
                && now - awakeTransitionAt <= FRESH_ACTIVITY_TRANSITION_MS;
        // This is useful confirmation, but cannot wake on its own: it is often a little late.
        int transitionPoints = transitionRecent ? 10 : 0;

        // Keep these out of Wake Opportunity scoring; Clearly Awake evaluates them separately.
        boolean lateAwakeConfirmation = !steps.isEmpty() || userActivity == UserActivity.PASSIVE
                || userActivity == UserActivity.EXERCISE;
        boolean cardiovascularEvidence = hrRisePoints > 0 || hrvRisePoints > 0;
        // Accelerometer and gyroscope are two views of the same physical phenomenon.
        boolean movementEvidence = accelPoints > 0 || gyroPoints > 0;
        // ASLEEP is neutral context. Only a fresh transition creates activity-state evidence.
        boolean activityStateEvidence = transitionRecent;
        int evidenceGroups = (cardiovascularEvidence ? 1 : 0) + (movementEvidence ? 1 : 0)
                + (activityStateEvidence ? 1 : 0);
        int score = hrRisePoints + hrvRisePoints + accelPoints + gyroPoints + combinedMotionPoints + transitionPoints;
        boolean moderateCandidate = baselineReady && evidenceGroups >= 2 && score >= CONFIRMED_WAKE_THRESHOLD;
        boolean immediateCandidate = baselineReady && cardiovascularEvidence && movementEvidence
                && score >= IMMEDIATE_WAKE_THRESHOLD;

        if (candidateStartedAt != Long.MIN_VALUE && now - candidateStartedAt > CANDIDATE_MEMORY_MS) {
            candidateStartedAt = Long.MIN_VALUE;
        }
        boolean hadActiveCandidate = candidateStartedAt != Long.MIN_VALUE;
        boolean supportingSignal = cardiovascularEvidence || movementEvidence || activityStateEvidence;
        boolean candidateConfirmed = hadActiveCandidate && baselineReady
                && score >= CANDIDATE_CONFIRMATION_THRESHOLD && supportingSignal;
        if (!hadActiveCandidate && moderateCandidate) candidateStartedAt = now;
        boolean candidateActive = candidateStartedAt != Long.MIN_VALUE;
        long candidateAge = candidateActive ? Math.max(0, now - candidateStartedAt) : -1;

        ClearlyAwakeResult clearlyAwake = clearlyAwake(movementPattern, steps60Seconds,
                steps90Seconds, transitionRecent, cardiovascularEvidence);
        boolean smartScoreWake = immediateCandidate || candidateConfirmed;
        boolean shouldWake = systemAwakePersistent || smartScoreWake || clearlyAwake.detected;
        String wakeReason = systemAwakePersistent ? "SYSTEM_AWAKE_PERSISTENT"
                : smartScoreWake ? "SMART_SCORE"
                : clearlyAwake.detected ? "CLEARLY_AWAKE" : "NONE";
        String continueReason = shouldWake ? "NONE"
                : systemNonAsleep ? "SYSTEM_AWAKE_NOT_YET_PERSISTENT" : "NO_WAKE_SIGNAL";

        return new Decision(shouldWake, score, baselineReady, moderateCandidate, immediateCandidate,
                candidateActive, candidateConfirmed, candidateAge, evidenceGroups, recentHr, baselineHr,
                hrAboveBaseline, hrvAboveBaseline, recentAccel, baselineAccel, recentGyro, baselineGyro,
                steps90Seconds, steps60Seconds, userActivity,
                lateAwakeConfirmation, hrRisePoints, hrvRisePoints, accelPoints, gyroPoints,
                combinedMotionPoints, transitionPoints, clearlyAwake, movementPattern, transitionRecent,
                now, baselineStatus, baselineHrQuality, baselineAccelQuality, baselineGyroQuality,
                recentHrQuality, movementCoverage, systemAwakePersistent, systemNonAsleepDurationMs,
                systemNonAsleepObservations, wakeReason, continueReason);
    }

    private static boolean isSystemNonAsleep(UserActivity activity) {
        return activity == UserActivity.PASSIVE || activity == UserActivity.EXERCISE;
    }

    private ClearlyAwakeResult clearlyAwake(MovementPattern movement, int steps60Seconds,
                                             int steps90Seconds, boolean transitionRecent,
                                             boolean physiologicalSupport) {
        if (steps60Seconds >= 3 && movement.activeBuckets60Seconds >= 2) {
            return ClearlyAwakeResult.yes("STEPS_3_PLUS_MOVEMENT");
        }
        // Step Detector alone is not trusted: wrist motion can occasionally be counted as steps.
        if (steps90Seconds >= 8 && (movement.activeBuckets > 0 || transitionRecent || physiologicalSupport)) {
            return ClearlyAwakeResult.yes("STEPS_8_PLUS_CONFIRMATION");
        }
        if (movement.dualStrongBuckets >= 3 && movement.dualStrongSpanMs >= 45_000L) {
            return ClearlyAwakeResult.yes("DUAL_SENSOR_SUSTAINED_STRONG_MOVEMENT");
        }
        boolean singleSensorSustained = (movement.accelStrongBuckets >= 4 && movement.accelStrongSpanMs >= 60_000L)
                || (movement.gyroStrongBuckets >= 4 && movement.gyroStrongSpanMs >= 60_000L);
        // One movement sensor needs a small independent confirmation to reject restless sleep.
        if (singleSensorSustained && (physiologicalSupport || steps90Seconds >= 1 || transitionRecent)) {
            return ClearlyAwakeResult.yes("SINGLE_SENSOR_SUSTAINED_STRONG_MOVEMENT_CONFIRMED");
        }
        if (transitionRecent && (movement.activeBuckets >= 2 || steps90Seconds >= 2)) {
            return ClearlyAwakeResult.yes("FRESH_ACTIVITY_TRANSITION_CONFIRMED");
        }
        if (transitionRecent && userActivity == UserActivity.EXERCISE && movement.activeBuckets >= 1) {
            return ClearlyAwakeResult.yes("FRESH_EXERCISE_WITH_MOVEMENT");
        }
        return ClearlyAwakeResult.no();
    }

    private static int gentleMovementPoints(MotionStats recent, MotionStats baseline, int gentlePoints, int strongPoints) {
        boolean aboveBaseline = recent.energyPerSecond > baseline.energyPerSecond * 2 + .02
                || recent.bursts > baseline.bursts + 1;
        if (!aboveBaseline || !recent.active) return 0;
        // A short/moderate cluster is the useful light-sleep signal. Very strong sustained
        // movement is retained as weak confirmation only because it often means fully awake.
        return recent.strong ? strongPoints : gentlePoints;
    }

    private void prune(long now) {
        long earliest = now - BASELINE_WINDOW_MS;
        pruneValues(heartRates, earliest); pruneValues(accelerometer, earliest); pruneValues(gyroscope, earliest);
        while (!steps.isEmpty() && steps.peekFirst() < now - CLEARLY_AWAKE_WINDOW_MS) steps.removeFirst();
    }

    private int countStepsSince(long start) {
        int count = 0;
        for (Long step : steps) if (step >= start) count++;
        return count;
    }
    private static void pruneValues(Deque<TimedValue> values, long earliest) {
        while (!values.isEmpty() && values.peekFirst().at < earliest) values.removeFirst();
    }

    private static SampleQuality sampleQuality(Deque<TimedValue> values, long start, long end) {
        int count = 0; long latest = Long.MIN_VALUE;
        for (TimedValue value : values) if (value.at >= start && value.at <= end) {
            count++; latest = Math.max(latest, value.at);
        }
        return new SampleQuality(count, latest);
    }

    private MovementCoverage movementCoverage(long start, long end) {
        int buckets = Math.max(1, (int) Math.ceil((end - start) / (double) COVERAGE_BUCKET_MS));
        boolean[] covered = new boolean[buckets];
        markCoverage(covered, accelerometer, start, end);
        markCoverage(covered, gyroscope, start, end);
        int count = 0; for (boolean present : covered) if (present) count++;
        return new MovementCoverage(count, buckets);
    }

    private static void markCoverage(boolean[] covered, Deque<TimedValue> values, long start, long end) {
        for (TimedValue value : values) if (value.at >= start && value.at <= end) {
            int bucket = (int) ((value.at - start) / COVERAGE_BUCKET_MS);
            covered[Math.min(covered.length - 1, Math.max(0, bucket))] = true;
        }
    }

    private HeartRateStats heartRateStats(long start, long end) {
        double sum = 0; int count = 0;
        for (TimedValue value : heartRates) if (value.at >= start && value.at <= end) { sum += value.value; count++; }
        if (count == 0) return new HeartRateStats(0, 0, 0);
        double mean = sum / count, variance = 0;
        for (TimedValue value : heartRates) if (value.at >= start && value.at <= end) { double d = value.value - mean; variance += d * d; }
        return new HeartRateStats(mean, Math.sqrt(variance / count), count);
    }

    /** A 10% trimmed HR mean/spread rejects short wake or check-the-watch spikes. */
    private HeartRateStats baselineHeartRateStats(long start, long end) {
        List<Double> samples = new ArrayList<>();
        for (TimedValue value : heartRates) if (value.at >= start && value.at <= end) samples.add(value.value);
        if (samples.isEmpty()) return new HeartRateStats(0, 0, 0);
        Collections.sort(samples);
        int trim = (int) Math.floor(samples.size() * .10);
        int from = trim, until = samples.size() - trim;
        double sum = 0; for (int i = from; i < until; i++) sum += samples.get(i);
        double mean = sum / (until - from), variance = 0;
        for (int i = from; i < until; i++) { double delta = samples.get(i) - mean; variance += delta * delta; }
        return new HeartRateStats(mean, Math.sqrt(variance / (until - from)), samples.size());
    }
    private static MotionStats motionStats(Deque<TimedValue> values, long start, long end,
                                           double activeThreshold, double burstThreshold) {
        double energy = 0; int bursts = 0;
        for (TimedValue value : values) if (value.at >= start && value.at <= end) {
            if (value.value > activeThreshold) energy += Math.min(3.0, value.value);
            if (value.value > burstThreshold) bursts++;
        }
        double seconds = Math.max(1, (end - start) / 1000.0);
        boolean strong = bursts >= 50 || energy >= 75;
        boolean active = strong || bursts >= 2 || energy >= 4;
        return new MotionStats(energy, energy / seconds, bursts, active, strong);
    }

    /**
     * Uses the median of 30-second motion buckets. A brief turn-over affects one or two buckets,
     * so it cannot materially raise the sleep baseline unless movement occupies most of it.
     */
    private static MotionStats baselineMotionStats(Deque<TimedValue> values, long start, long end,
                                                   double activeThreshold, double burstThreshold) {
        List<Double> energyRates = new ArrayList<>();
        List<Double> burstCounts = new ArrayList<>();
        for (long bucketStart = start; bucketStart < end; bucketStart += 30_000L) {
            MotionStats bucket = motionStats(values, bucketStart, Math.min(end, bucketStart + 30_000L),
                    activeThreshold, burstThreshold);
            energyRates.add(bucket.energyPerSecond);
            burstCounts.add((double) bucket.bursts);
        }
        if (energyRates.isEmpty()) return new MotionStats(0, 0, 0, false, false);
        double rate = median(energyRates);
        int bursts = (int) Math.round(median(burstCounts));
        double energy = rate * 30;
        boolean strong = bursts >= 50 || energy >= 75;
        boolean active = strong || bursts >= 2 || energy >= 4;
        return new MotionStats(energy, rate, bursts, active, strong);
    }

    private static double median(List<Double> values) {
        Collections.sort(values);
        int middle = values.size() / 2;
        return values.size() % 2 == 0 ? (values.get(middle - 1) + values.get(middle)) / 2 : values.get(middle);
    }

    private MovementPattern movementPattern(long now) {
        long start = now - CLEARLY_AWAKE_WINDOW_MS;
        int bucketCount = (int) (CLEARLY_AWAKE_WINDOW_MS / MOVEMENT_BUCKET_MS);
        BucketSeries accel = bucketSeries(accelerometer, start, now, bucketCount, .35, 1.15);
        BucketSeries gyro = bucketSeries(gyroscope, start, now, bucketCount, .55, 1.50);
        int active = 0, active60 = 0, dualStrong = 0;
        int firstActive = -1, lastActive = -1, firstDual = -1, lastDual = -1;
        for (int i = 0; i < bucketCount; i++) {
            boolean bucketActive = accel.active[i] || gyro.active[i];
            if (bucketActive) {
                active++;
                if (i >= bucketCount - 4) active60++;
                if (firstActive < 0) firstActive = i;
                lastActive = i;
            }
            if (accel.strong[i] && gyro.strong[i]) {
                dualStrong++;
                if (firstDual < 0) firstDual = i;
                lastDual = i;
            }
        }
        return new MovementPattern(active, active60, span(firstActive, lastActive), accel.activeCount,
                gyro.activeCount, accel.strongCount, gyro.strongCount, accel.strongSpanMs,
                gyro.strongSpanMs, dualStrong, span(firstDual, lastDual));
    }

    private static BucketSeries bucketSeries(Deque<TimedValue> values, long start, long end,
                                             int bucketCount, double activeThreshold,
                                             double burstThreshold) {
        double[] energy = new double[bucketCount];
        int[] bursts = new int[bucketCount];
        for (TimedValue value : values) {
            if (value.at < start || value.at > end) continue;
            int bucket = (int) ((value.at - start) / MOVEMENT_BUCKET_MS);
            if (bucket == bucketCount) bucket--;
            if (value.value > activeThreshold) energy[bucket] += Math.min(3.0, value.value);
            if (value.value > burstThreshold) bursts[bucket]++;
        }
        boolean[] active = new boolean[bucketCount];
        boolean[] strong = new boolean[bucketCount];
        int activeCount = 0, strongCount = 0, firstStrong = -1, lastStrong = -1;
        for (int i = 0; i < bucketCount; i++) {
            active[i] = bursts[i] >= 2 || energy[i] >= 4;
            // Scale the detector's existing 75-second strong-motion rule to a 15-second bucket.
            strong[i] = bursts[i] >= 10 || energy[i] >= 15;
            if (active[i]) activeCount++;
            if (strong[i]) {
                strongCount++;
                if (firstStrong < 0) firstStrong = i;
                lastStrong = i;
            }
        }
        return new BucketSeries(active, strong, activeCount, strongCount, span(firstStrong, lastStrong));
    }

    private static long span(int firstBucket, int lastBucket) {
        return firstBucket < 0 ? 0 : (lastBucket - firstBucket + 1L) * MOVEMENT_BUCKET_MS;
    }

    private static final class TimedValue { final long at; final double value; TimedValue(long at, double value) { this.at = at; this.value = value; } }
    private static final class SampleQuality {
        final int count; final long latestAt;
        SampleQuality(int count, long latestAt) { this.count = count; this.latestAt = latestAt; }
    }
    private static final class MovementCoverage {
        final int coveredBuckets, totalBuckets;
        MovementCoverage(int coveredBuckets, int totalBuckets) { this.coveredBuckets = coveredBuckets; this.totalBuckets = totalBuckets; }
    }
    private static final class HeartRateStats { final double mean, variability; final int count; HeartRateStats(double mean, double variability, int count) { this.mean = mean; this.variability = variability; this.count = count; } }
    public static final class MotionStats {
        public final double energy, energyPerSecond; public final int bursts; public final boolean active, strong;
        MotionStats(double energy, double energyPerSecond, int bursts, boolean active, boolean strong) { this.energy = energy; this.energyPerSecond = energyPerSecond; this.bursts = bursts; this.active = active; this.strong = strong; }
    }
    private static final class BucketSeries {
        final boolean[] active, strong;
        final int activeCount, strongCount;
        final long strongSpanMs;
        BucketSeries(boolean[] active, boolean[] strong, int activeCount, int strongCount, long strongSpanMs) {
            this.active = active; this.strong = strong; this.activeCount = activeCount;
            this.strongCount = strongCount; this.strongSpanMs = strongSpanMs;
        }
    }
    private static final class MovementPattern {
        final int activeBuckets, activeBuckets60Seconds, accelActiveBuckets, gyroActiveBuckets;
        final int accelStrongBuckets, gyroStrongBuckets, dualStrongBuckets;
        final long movementSpanMs, accelStrongSpanMs, gyroStrongSpanMs, dualStrongSpanMs;
        MovementPattern(int activeBuckets, int activeBuckets60Seconds, long movementSpanMs,
                        int accelActiveBuckets, int gyroActiveBuckets, int accelStrongBuckets,
                        int gyroStrongBuckets, long accelStrongSpanMs, long gyroStrongSpanMs,
                        int dualStrongBuckets, long dualStrongSpanMs) {
            this.activeBuckets = activeBuckets; this.activeBuckets60Seconds = activeBuckets60Seconds;
            this.movementSpanMs = movementSpanMs; this.accelActiveBuckets = accelActiveBuckets;
            this.gyroActiveBuckets = gyroActiveBuckets; this.accelStrongBuckets = accelStrongBuckets;
            this.gyroStrongBuckets = gyroStrongBuckets; this.accelStrongSpanMs = accelStrongSpanMs;
            this.gyroStrongSpanMs = gyroStrongSpanMs; this.dualStrongBuckets = dualStrongBuckets;
            this.dualStrongSpanMs = dualStrongSpanMs;
        }
    }
    private static final class ClearlyAwakeResult {
        final boolean detected; final String reason;
        private ClearlyAwakeResult(boolean detected, String reason) { this.detected = detected; this.reason = reason; }
        static ClearlyAwakeResult yes(String reason) { return new ClearlyAwakeResult(true, reason); }
        static ClearlyAwakeResult no() { return new ClearlyAwakeResult(false, "NONE"); }
    }

    public static final class Decision {
        public final boolean shouldWake, candidate, immediateCandidate, baselineReady, lateAwakeConfirmation;
        public final boolean candidateActive, candidateConfirmed, clearlyAwake, freshUserActivityTransition;
        public final boolean systemAwakePersistent;
        public final int score, evidenceGroups, steps, steps60Seconds;
        public final long candidateAgeMs, movementSpanMs, systemNonAsleepDurationMs;
        public final int systemNonAsleepObservations;
        public final String wakeReason, continueReason;
        public final int activeMovementBuckets, accelActiveBuckets, gyroActiveBuckets;
        public final int accelStrongBuckets, gyroStrongBuckets, dualStrongBuckets;
        public final String clearlyAwakeReason;
        public final String baselineStatus;
        public final int baselineHeartRateSamples, baselineAccelerometerSamples, baselineGyroscopeSamples;
        public final long heartRateSampleAgeMs, hrvSampleAgeMs;
        public final int movementCoverageBuckets, movementCoverageTotalBuckets;
        public final double heartRateMean, heartRateVariability, heartRateBaseline, hrvBaseline, hrAboveBaseline, hrvAboveBaseline;
        public final MotionStats accelerometer, accelerometerBaseline, gyroscope, gyroscopeBaseline;
        public final UserActivity userActivity;
        private final int hrRisePoints, hrvRisePoints, accelPoints, gyroPoints, combinedMotionPoints, transitionPoints;

        Decision(boolean shouldWake, int score, boolean baselineReady, boolean candidate, boolean immediateCandidate,
                 boolean candidateActive, boolean candidateConfirmed, long candidateAgeMs, int evidenceGroups,
                 HeartRateStats recentHr, HeartRateStats baselineHr,
                 double hrAboveBaseline, double hrvAboveBaseline, MotionStats accelerometer, MotionStats accelerometerBaseline,
                 MotionStats gyroscope, MotionStats gyroscopeBaseline, int steps, int steps60Seconds,
                 UserActivity userActivity,
                 boolean lateAwakeConfirmation, int hrRisePoints, int hrvRisePoints, int accelPoints, int gyroPoints,
                 int combinedMotionPoints, int transitionPoints, ClearlyAwakeResult clearlyAwake,
                 MovementPattern movementPattern, boolean freshUserActivityTransition,
                 long evaluatedAt, String baselineStatus, SampleQuality baselineHrQuality, SampleQuality baselineAccelQuality,
                 SampleQuality baselineGyroQuality, SampleQuality recentHrQuality,
                 MovementCoverage movementCoverage, boolean systemAwakePersistent, long systemNonAsleepDurationMs,
                 int systemNonAsleepObservations, String wakeReason, String continueReason) {
            this.shouldWake = shouldWake; this.score = score; this.baselineReady = baselineReady; this.candidate = candidate;
            this.immediateCandidate = immediateCandidate; this.candidateActive = candidateActive;
            this.candidateConfirmed = candidateConfirmed; this.candidateAgeMs = candidateAgeMs;
            this.evidenceGroups = evidenceGroups; this.heartRateMean = recentHr.mean; this.heartRateVariability = recentHr.variability;
            this.heartRateBaseline = baselineHr.mean; this.hrvBaseline = baselineHr.variability;
            this.hrAboveBaseline = hrAboveBaseline; this.hrvAboveBaseline = hrvAboveBaseline;
            this.accelerometer = accelerometer; this.accelerometerBaseline = accelerometerBaseline;
            this.gyroscope = gyroscope; this.gyroscopeBaseline = gyroscopeBaseline; this.steps = steps;
            this.steps60Seconds = steps60Seconds;
            this.userActivity = userActivity; this.lateAwakeConfirmation = lateAwakeConfirmation;
            this.hrRisePoints = hrRisePoints; this.hrvRisePoints = hrvRisePoints; this.accelPoints = accelPoints;
            this.gyroPoints = gyroPoints; this.combinedMotionPoints = combinedMotionPoints; this.transitionPoints = transitionPoints;
            this.clearlyAwake = clearlyAwake.detected; this.clearlyAwakeReason = clearlyAwake.reason;
            this.freshUserActivityTransition = freshUserActivityTransition;
            this.activeMovementBuckets = movementPattern.activeBuckets;
            this.accelActiveBuckets = movementPattern.accelActiveBuckets;
            this.gyroActiveBuckets = movementPattern.gyroActiveBuckets;
            this.accelStrongBuckets = movementPattern.accelStrongBuckets;
            this.gyroStrongBuckets = movementPattern.gyroStrongBuckets;
            this.dualStrongBuckets = movementPattern.dualStrongBuckets;
            this.movementSpanMs = movementPattern.movementSpanMs;
            this.baselineStatus = baselineStatus;
            this.baselineHeartRateSamples = baselineHrQuality.count;
            this.baselineAccelerometerSamples = baselineAccelQuality.count;
            this.baselineGyroscopeSamples = baselineGyroQuality.count;
            this.heartRateSampleAgeMs = ageAt(evaluatedAt, recentHrQuality.latestAt);
            this.hrvSampleAgeMs = recentHr.count >= 3 ? ageAt(evaluatedAt, recentHrQuality.latestAt) : -1L;
            this.movementCoverageBuckets = movementCoverage.coveredBuckets;
            this.movementCoverageTotalBuckets = movementCoverage.totalBuckets;
            this.systemAwakePersistent = systemAwakePersistent;
            this.systemNonAsleepDurationMs = systemNonAsleepDurationMs;
            this.systemNonAsleepObservations = systemNonAsleepObservations;
            this.wakeReason = wakeReason;
            this.continueReason = continueReason;
        }

        private long ageAt(long evaluatedAt, long timestamp) { return timestamp == Long.MIN_VALUE ? -1L : Math.max(0L, evaluatedAt - timestamp); }

        /** One grep-friendly record per scoring evaluation for reviewing an entire night. */
        public String summary(long timestamp) {
            return String.format(Locale.US,
                    "timestamp=%d → WAKE_SCORE=%d → groups=%d → candidate=%s → clearly_awake=%s → decision=%s",
                    timestamp, score, evidenceGroups, candidateActive, clearlyAwake,
                    shouldWake ? "WAKE" : "CONTINUE");
        }

        /** One persisted debug record per 30-second scoring evaluation. */
        public String telemetry() {
            return String.format(Locale.US,
                    "BASELINE_READY=%s BASELINE_STATUS=%s BASELINE_SAMPLES[HR=%d,ACCEL=%d,GYRO=%d] METHOD=HR_TRIMMED_MEAN_STD,MOTION_MEDIAN_30S_BUCKETS\nHR_SAMPLE_AGE=%dms HRV_SAMPLE_AGE=%dms MOVEMENT_WINDOW_COVERAGE=%d/%d\nHR=%.1f BASELINE=%.1f DELTA=%+.1f (+%d)\n"
                            + "HRV=%.1f BASELINE=%.1f DELTA=%+.1f (+%d)\n"
                            + "ACCEL_ACTIVITY=%.3f/s BASELINE=%.3f/s bursts=%d (+%d)\n"
                            + "WRIST_MOVEMENT=%.3f/s BASELINE=%.3f/s bursts=%d (+%d)\n"
                            + "MOTION_CONFIRMATION=%s (+%d)\nUSER_ACTIVITY=%s (+0) SYSTEM_NON_ASLEEP_DURATION=%dms SYSTEM_NON_ASLEEP_OBSERVATIONS=%d SYSTEM_AWAKE_PERSISTENT=%s\n"
                            + "ASLEEP_TO_NON_ASLEEP=%s (+%d)\nSTEP_DETECTOR=%d (confirmation=%s, +0)\n"
                            + "WAKE_SCORE=%d\nEVIDENCE_GROUPS=%d\nCANDIDATE_ACTIVE=%s\nCANDIDATE_AGE=%dms\n"
                            + "CANDIDATE_CONFIRMED=%s\nCLEARLY_AWAKE=%s\nCLEARLY_AWAKE_REASON=%s\n"
                            + "ACTIVE_MOVEMENT_BUCKETS=%d ACCEL_ACTIVE_BUCKETS=%d GYRO_ACTIVE_BUCKETS=%d\n"
                            + "ACCEL_STRONG_BUCKETS=%d GYRO_STRONG_BUCKETS=%d DUAL_STRONG_BUCKETS=%d\n"
                            + "MOVEMENT_SPAN=%dms\nSTEPS=%d STEPS_60S=%d\nFRESH_USER_ACTIVITY_TRANSITION=%s\n"
                            + "CANDIDATE_THRESHOLD=%d CANDIDATE_CONFIRMATION_THRESHOLD=%d IMMEDIATE_THRESHOLD=%d\n"
                            + "CANDIDATE=%s IMMEDIATE=%s SHOULD_WAKE=%s WAKE_REASON=%s CONTINUE_REASON=%s",
                    baselineReady, baselineStatus, baselineHeartRateSamples, baselineAccelerometerSamples,
                    baselineGyroscopeSamples, heartRateSampleAgeMs, hrvSampleAgeMs,
                    movementCoverageBuckets, movementCoverageTotalBuckets,
                    heartRateMean, heartRateBaseline, hrAboveBaseline, hrRisePoints,
                    heartRateVariability, hrvBaseline, hrvAboveBaseline, hrvRisePoints,
                    accelerometer.energyPerSecond, accelerometerBaseline.energyPerSecond, accelerometer.bursts, accelPoints,
                    gyroscope.energyPerSecond, gyroscopeBaseline.energyPerSecond, gyroscope.bursts, gyroPoints,
                    accelPoints >= 14 && gyroPoints >= 12, combinedMotionPoints, userActivity, systemNonAsleepDurationMs,
                    systemNonAsleepObservations, systemAwakePersistent, transitionPoints > 0,
                    transitionPoints, steps, lateAwakeConfirmation, score, evidenceGroups, candidateActive,
                    candidateAgeMs, candidateConfirmed, clearlyAwake, clearlyAwakeReason, activeMovementBuckets,
                    accelActiveBuckets, gyroActiveBuckets, accelStrongBuckets, gyroStrongBuckets,
                    dualStrongBuckets, movementSpanMs, steps, steps60Seconds, freshUserActivityTransition,
                    CONFIRMED_WAKE_THRESHOLD, CANDIDATE_CONFIRMATION_THRESHOLD, IMMEDIATE_WAKE_THRESHOLD,
                    candidate, immediateCandidate, shouldWake, wakeReason, continueReason);
        }
    }
}
