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
    /** Independent groups may combine only while both are still current/recent. */
    public static final long TEMPORAL_GROUP_FRESHNESS_MS = RECENT_WINDOW_MS;
    public static final long BASELINE_WINDOW_MS = 15 * 60_000L;
    // Leave the recent window out of the baseline, so this guarantees at least ten full minutes.
    public static final long BASELINE_MIN_DURATION_MS = 10 * 60_000L + RECENT_WINDOW_MS;
    public static final int CONFIRMED_WAKE_THRESHOLD = 23;
    public static final int IMMEDIATE_WAKE_THRESHOLD = 42;
    public static final int TREND_INTERESTING_THRESHOLD = 12;
    public static final int TREND_CANDIDATE_THRESHOLD = 20;
    public static final int CANDIDATE_CONFIRMATION_THRESHOLD = 12;
    public static final long CANDIDATE_MEMORY_MS = 150_000L;
    private static final long MIN_CANDIDATE_CONFIRMATION_MS = 20_000L;
    private static final long WAKEABILITY_TREND_WINDOW_MS = 4 * 60_000L;
    // This is deliberately shorter than the trend history. It is the bounded interval in which
    // repeated fresh HR and movement updates can themselves be a wake opportunity.
    private static final long TEMPORAL_CONFIRMATION_WINDOW_MS = 150_000L;
    private static final long HEART_RATE_DYNAMICS_WINDOW_MS = 4 * 60_000L;
    private static final long CLEARLY_AWAKE_WINDOW_MS = 90_000L;
    private static final long FRESH_ACTIVITY_TRANSITION_MS = 120_000L;
    // Health Services stateChangeTime is an epoch timestamp. Keep system-state evidence bounded
    // without mistaking a delayed callback for a newly observed transition.
    static final long SYSTEM_AWAKE_OBSERVATION_WINDOW_MS = 30 * 60_000L;
    private static final long MOVEMENT_BUCKET_MS = 15_000L;
    private static final long COVERAGE_BUCKET_MS = 5_000L;
    private static final int EVIDENCE_CARDIOVASCULAR = 1;
    private static final int EVIDENCE_MOVEMENT = 1 << 1;
    private static final int EVIDENCE_ACTIVITY_STATE = 1 << 2;

    public enum UserActivity { ASLEEP, PASSIVE, EXERCISE, UNKNOWN }
    public enum WakeabilityState {
        STABLE_OR_LOW_WAKEABILITY, NORMAL_SLEEP, WAKEABILITY_RISING, WAKE_OPPORTUNITY, AWAKE
    }

    private final Deque<TimedValue> heartRates = new ArrayDeque<>();
    private final Deque<TimedValue> accelerometer = new ArrayDeque<>();
    private final Deque<TimedValue> gyroscope = new ArrayDeque<>();
    private final Deque<TimedStep> steps = new ArrayDeque<>();
    private final Deque<WakeabilityFrame> wakeabilityFrames = new ArrayDeque<>();
    private final long startedAt;
    private UserActivity userActivity = UserActivity.UNKNOWN;
    private long awakeTransitionAt = Long.MIN_VALUE;
    private long userActivityStateChangeAt = Long.MIN_VALUE;
    private long userActivityCallbackReceivedAt = Long.MIN_VALUE;
    private String userActivityEpisodeId = "NONE";
    private boolean duplicateUserActivityEpisodeCallback;
    private long candidateStartedAt = Long.MIN_VALUE;
    private int candidateOriginScore = -1;
    private int candidateOriginGroups = -1;
    private int candidateWeakEvaluations;
    private long lastEvaluationAt = Long.MIN_VALUE;
    // A rolling baseline describes stable sleep only. Once an interesting change begins, retain
    // the pre-change baseline until the detector has observed measured quiet again.
    private BaselineSnapshot frozenBaseline;
    private SelfStimulusContamination.Snapshot selfStimulus = new SelfStimulusContamination.Snapshot(false, null);

    public SmartWakeDetector(long startedAt) { this.startedAt = startedAt; }

    public void addHeartRate(double bpm, long at) {
        addHeartRate(bpm, at, false);
    }
    void addHeartRate(double bpm, long at, boolean tainted) {
        if (bpm >= 30 && bpm <= 240) heartRates.addLast(new TimedValue(at, bpm, tainted));
    }
    public void addAccelerometerMotion(double magnitude, long at) {
        addAccelerometerMotion(magnitude, at, false);
    }
    void addAccelerometerMotion(double magnitude, long at, boolean tainted) {
        if (magnitude >= 0) accelerometer.addLast(new TimedValue(at, magnitude, tainted));
    }
    public void addGyroscopeMotion(double magnitude, long at) {
        addGyroscopeMotion(magnitude, at, false);
    }
    void addGyroscopeMotion(double magnitude, long at, boolean tainted) {
        if (magnitude >= 0) gyroscope.addLast(new TimedValue(at, magnitude, tainted));
    }
    public void addStep(long at) { addStep(at, false); }
    void addStep(long at, boolean tainted) { steps.addLast(new TimedStep(at, tainted)); }

    void setSelfStimulus(SelfStimulusContamination.Snapshot snapshot) {
        selfStimulus = snapshot == null ? new SelfStimulusContamination.Snapshot(false, null) : snapshot;
    }

    /** Supplies persisted context without turning an old state into a fresh transition. */
    public void seedUserActivity(UserActivity value) {
        seedUserActivity(value, Long.MIN_VALUE, Long.MIN_VALUE);
    }

    public void seedUserActivity(UserActivity value, long stateChangeAt, long callbackReceivedAt) {
        userActivity = value == null ? UserActivity.UNKNOWN : value;
        awakeTransitionAt = Long.MIN_VALUE;
        userActivityStateChangeAt = stateChangeAt > 0L ? stateChangeAt : Long.MIN_VALUE;
        userActivityCallbackReceivedAt = callbackReceivedAt > 0L ? callbackReceivedAt : Long.MIN_VALUE;
        userActivityEpisodeId = activityEpisodeId(userActivity, userActivityStateChangeAt);
        duplicateUserActivityEpisodeCallback = false;
    }

    /** Compatibility helper for deterministic unit tests; production supplies both timestamps. */
    public void setUserActivity(UserActivity value, long at) {
        setUserActivity(value, at, at);
    }

    /**
     * Applies a Health Services episode identified solely by state + stateChangeTime. Receipt
     * time is retained for diagnostics but never establishes a transition or wake persistence.
     */
    public void setUserActivity(UserActivity value, long stateChangeAt, long callbackReceivedAt) {
        if (value == null) value = UserActivity.UNKNOWN;
        long normalizedStateChangeAt = stateChangeAt > 0L ? stateChangeAt : Long.MIN_VALUE;
        userActivityCallbackReceivedAt = callbackReceivedAt > 0L ? callbackReceivedAt : Long.MIN_VALUE;
        duplicateUserActivityEpisodeCallback = value == userActivity
                && normalizedStateChangeAt == userActivityStateChangeAt;
        if (duplicateUserActivityEpisodeCallback) return;

        // A delayed, older callback cannot roll the detector back to a prior episode merely
        // because it arrived after a newer Health Services update.
        if (userActivityStateChangeAt != Long.MIN_VALUE
                && normalizedStateChangeAt < userActivityStateChangeAt) return;
        // Different states with the exact same state-change instant have no trustworthy order.
        // Retain receipt diagnostics above, but keep the already accepted episode for safety.
        if (userActivityStateChangeAt != Long.MIN_VALUE
                && normalizedStateChangeAt == userActivityStateChangeAt && value != userActivity) return;

        UserActivity previous = userActivity;
        userActivity = value;
        userActivityStateChangeAt = normalizedStateChangeAt;
        userActivityEpisodeId = activityEpisodeId(value, normalizedStateChangeAt);
        if (previous == UserActivity.ASLEEP
                && (value == UserActivity.PASSIVE || value == UserActivity.EXERCISE)
                && normalizedStateChangeAt >= startedAt) {
            awakeTransitionAt = normalizedStateChangeAt;
        }
        if (value == UserActivity.ASLEEP || value == UserActivity.UNKNOWN) awakeTransitionAt = Long.MIN_VALUE;
    }

    public Decision evaluate(long now) {
        prune(now);
        long recentStart = now - TEMPORAL_GROUP_FRESHNESS_MS;
        long baselineStart = Math.max(startedAt, now - BASELINE_WINDOW_MS);
        long baselineEnd = recentStart;
        boolean baselineTimeReady = now - startedAt >= BASELINE_MIN_DURATION_MS;

        HeartRateStats recentHr = heartRateStats(recentStart, now);
        HeartRateStats rollingBaselineHr = baselineHeartRateStats(baselineStart, baselineEnd);
        MotionStats recentAccel = motionStats(accelerometer, recentStart, now, .35, 1.15);
        MotionStats rollingBaselineAccel = baselineMotionStats(accelerometer, baselineStart, baselineEnd, .35, 1.15);
        MotionStats recentGyro = motionStats(gyroscope, recentStart, now, .55, 1.50);
        MotionStats rollingBaselineGyro = baselineMotionStats(gyroscope, baselineStart, baselineEnd, .55, 1.50);
        SampleQuality rollingBaselineHrQuality = sampleQuality(heartRates, baselineStart, baselineEnd);
        SampleQuality rollingBaselineAccelQuality = sampleQuality(accelerometer, baselineStart, baselineEnd);
        SampleQuality rollingBaselineGyroQuality = sampleQuality(gyroscope, baselineStart, baselineEnd);
        BaselineSnapshot rollingBaseline = new BaselineSnapshot(rollingBaselineHr, rollingBaselineAccel,
                rollingBaselineGyro, rollingBaselineHrQuality, rollingBaselineAccelQuality,
                rollingBaselineGyroQuality);
        BaselineSnapshot appliedBaseline = frozenBaseline != null ? frozenBaseline : rollingBaseline;
        HeartRateStats baselineHr = appliedBaseline.heartRate;
        MotionStats baselineAccel = appliedBaseline.accelerometer;
        MotionStats baselineGyro = appliedBaseline.gyroscope;
        SampleQuality baselineHrQuality = appliedBaseline.heartRateQuality;
        SampleQuality baselineAccelQuality = appliedBaseline.accelerometerQuality;
        SampleQuality baselineGyroQuality = appliedBaseline.gyroscopeQuality;
        SampleQuality recentHrQuality = sampleQuality(heartRates, recentStart, now);
        SampleQuality recentAccelQuality = sampleQuality(accelerometer, recentStart, now);
        SampleQuality recentGyroQuality = sampleQuality(gyroscope, recentStart, now);
        MovementCoverage movementCoverage = movementCoverage(recentStart, now);
        // HR delivery is device-dependent and may be sparse. Motion is continuously sampled on
        // supported watches, so it is the minimum data-quality requirement for a usable baseline.
        boolean baselineHasMovement = baselineAccelQuality.count > 0 || baselineGyroQuality.count > 0;
        boolean baselineReady = baselineTimeReady && baselineHasMovement;
        String baselineStatus = !baselineTimeReady ? "COLLECTING_DURATION"
                : !baselineHasMovement ? "MISSING_MOVEMENT_SAMPLES"
                : frozenBaseline != null ? "READY_FROZEN" : "READY";
        MovementPattern movementPattern = movementPattern(now);
        int steps60Seconds = countStepsSince(now - 60_000L);
        int steps90Seconds = countStepsSince(now - CLEARLY_AWAKE_WINDOW_MS);
        boolean stepEvidenceTainted = hasTaintedSteps(now - 60_000L, now);
        boolean movementEvidenceTainted = hasTaintedValues(accelerometer, recentStart, now)
                || hasTaintedValues(gyroscope, recentStart, now);
        boolean cardioEvidenceTainted = hasTaintedValues(heartRates, recentStart, now);
        boolean systemNonAsleep = isSystemNonAsleep(userActivity);
        long healthStateAgeMs = userActivityStateChangeAt == Long.MIN_VALUE
                ? -1L : Math.max(0L, now - userActivityStateChangeAt);
        // stateChangeTime, not callback delivery, is the only timing identity of a Health
        // Services episode. A state already in effect before this monitoring session is context,
        // never a fresh wake transition.
        boolean systemAwakeEvidence = systemNonAsleep
                && userActivityStateChangeAt != Long.MIN_VALUE
                && userActivityStateChangeAt >= startedAt
                && userActivityStateChangeAt <= now
                && healthStateAgeMs <= SYSTEM_AWAKE_OBSERVATION_WINDOW_MS;
        long systemNonAsleepDurationMs = systemAwakeEvidence ? healthStateAgeMs : 0L;

        double hrAboveBaseline = recentHr.count >= 2 && baselineHr.count >= 4 ? recentHr.mean - baselineHr.mean : 0;
        // BPM spread is not HRV. Real HRV needs beat-to-beat/RR data, which this device/API does
        // not expose to Zmanio. Keep BPM variability as a diagnostic proxy only and never score it
        // as an independent physiological signal.
        double hrvAboveBaseline = 0;
        int hrRisePoints = hrAboveBaseline >= 6 ? 22 : hrAboveBaseline >= 3 ? 15 : 0;
        HeartRateDynamics hrDynamics = heartRateDynamics(now - HEART_RATE_DYNAMICS_WINDOW_MS,
                now, baselineHr.count >= 4 ? baselineHr.mean : Double.NaN);
        int hrTrendPoints = hrDynamics.sustainedElevation && hrDynamics.slopeBpmPerMinute >= 1.0 ? 10
                : hrDynamics.sustainedElevation ? 6 : 0;
        int hrvRisePoints = 0;

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
        boolean cardiovascularEvidence = hrRisePoints > 0 || hrTrendPoints > 0;
        // A four-minute slope can be diagnostically useful, but it cannot make a current
        // multi-group sample with motion that happened minutes ago. HR must itself be fresh.
        boolean cardiovascularCurrentEvidence = cardiovascularEvidence && recentHrQuality.count > 0;
        // Accelerometer and gyroscope are two views of the same physical phenomenon.
        boolean movementEvidence = accelPoints > 0 || gyroPoints > 0;
        // ASLEEP is neutral context. Only a fresh transition creates activity-state evidence.
        boolean activityStateEvidence = transitionRecent;
        int evidenceMask = (cardiovascularCurrentEvidence ? EVIDENCE_CARDIOVASCULAR : 0)
                | (movementEvidence ? EVIDENCE_MOVEMENT : 0)
                | (activityStateEvidence ? EVIDENCE_ACTIVITY_STATE : 0);
        int evidenceGroups = Integer.bitCount(evidenceMask);
        // Delta and slope are two views of the same cardiovascular source, so do not double-count.
        int cardiovascularPoints = Math.max(hrRisePoints, hrTrendPoints);
        int score = cardiovascularPoints + accelPoints + gyroPoints + combinedMotionPoints + transitionPoints;
        long latestMovementEvidenceAt = latestMovementEvidenceAt(recentStart, now);
        int freshEvaluationMask = lastEvaluationAt == Long.MIN_VALUE ? 0
                : ((cardiovascularCurrentEvidence && recentHrQuality.latestAt > lastEvaluationAt
                    ? EVIDENCE_CARDIOVASCULAR : 0)
                | (movementEvidence && latestMovementEvidenceAt > lastEvaluationAt
                    ? EVIDENCE_MOVEMENT : 0)
                | (activityStateEvidence && awakeTransitionAt > lastEvaluationAt
                    ? EVIDENCE_ACTIVITY_STATE : 0));
        WakeabilityTrend trend = updateWakeabilityTrend(now, score, evidenceMask,
                recentHrQuality.latestAt, latestMovementEvidenceAt, baselineReady);
        boolean trendCandidate = trend.rising && evidenceGroups >= 2
                && score >= TREND_CANDIDATE_THRESHOLD;
        boolean moderateCandidate = baselineReady && evidenceGroups >= 2
                && (score >= CONFIRMED_WAKE_THRESHOLD || trendCandidate);
        // A very strong single sample is still only a candidate. The old one-evaluation wake path
        // was vulnerable to a turn-over plus transient HR spike and defeated temporal confirmation.
        boolean immediateCandidate = baselineReady && cardiovascularCurrentEvidence && movementEvidence
                && score >= IMMEDIATE_WAKE_THRESHOLD;

        boolean candidateExpired = false;
        boolean candidateWeakened = false;
        long candidateAge = -1L;
        int loggedCandidateOriginScore = -1;
        int loggedCandidateOriginGroups = -1;
        if (candidateStartedAt != Long.MIN_VALUE) {
            candidateAge = Math.max(0L, now - candidateStartedAt);
            loggedCandidateOriginScore = candidateOriginScore;
            loggedCandidateOriginGroups = candidateOriginGroups;
            if (candidateAge > CANDIDATE_MEMORY_MS) {
                clearCandidate();
                candidateExpired = true;
            }
        }

        boolean hadActiveCandidate = candidateStartedAt != Long.MIN_VALUE;
        boolean freshCardiovascularEvidence = cardiovascularCurrentEvidence
                && hasFreshCardiovascularEvidence(candidateStartedAt, now, baselineHr);
        boolean freshMovementEvidence = movementEvidence && hasFreshMovementEvidence(candidateStartedAt, now,
                baselineAccel, baselineGyro);
        boolean candidateConfirmed = false;
        String candidateConfirmationStatus = candidateExpired ? "EXPIRED" : "NONE";
        String candidateConfirmationSource = "NONE";
        if (hadActiveCandidate) {
            // A remembered candidate supplies context only. Confirmation needs a new, currently
            // strong multi-group sample with BOTH cardiovascular and movement evidence refreshed
            // after candidate creation. Candidate memory can never confirm itself.
            candidateConfirmed = baselineReady && moderateCandidate
                    && candidateAge >= MIN_CANDIDATE_CONFIRMATION_MS
                    && freshCardiovascularEvidence && freshMovementEvidence;
            if (candidateConfirmed) {
                candidateConfirmationStatus = "CONFIRMED_FRESH_EVIDENCE";
                candidateConfirmationSource = "TEMPORAL_CARDIO_AND_MOVEMENT";
            } else if (evidenceGroups < candidateOriginGroups && score >= CANDIDATE_CONFIRMATION_THRESHOLD) {
                // The 36/2 -> 22/1 regression: remaining single-group evidence is not confirmation.
                clearCandidate();
                candidateWeakened = true;
                candidateConfirmationStatus = "EVIDENCE_WEAKENED";
            } else if (score < CANDIDATE_CONFIRMATION_THRESHOLD || evidenceGroups == 0) {
                // Give one quiet evaluation room for batching jitter, then let an isolated event
                // decay naturally. This context still never contributes wake evidence.
                candidateWeakEvaluations++;
                if (candidateWeakEvaluations >= 2) {
                    clearCandidate();
                    candidateWeakened = true;
                    candidateConfirmationStatus = "EVIDENCE_DECAYED";
                } else {
                    candidateConfirmationStatus = "DECAYING_WAITING_FOR_FRESH_CONFIRMATION";
                }
            } else {
                candidateWeakEvaluations = 0;
                candidateConfirmationStatus = "WAITING_FOR_FRESH_CONFIRMATION";
            }
        } else if (!candidateExpired && moderateCandidate) {
            beginCandidate(now, score, evidenceGroups);
            candidateAge = 0L;
            loggedCandidateOriginScore = candidateOriginScore;
            loggedCandidateOriginGroups = candidateOriginGroups;
            candidateConfirmationStatus = "CREATED";
        }
        boolean candidateActive = candidateStartedAt != Long.MIN_VALUE;
        boolean measuredQuiet = recentAccelQuality.count > 0 || recentGyroQuality.count > 0;
        boolean interestingWakeability = score >= TREND_INTERESTING_THRESHOLD && evidenceGroups > 0;
        if (frozenBaseline == null && baselineReady && interestingWakeability) {
            // Capture the rolling values that existed before this interesting sample can age into
            // the baseline. The current 75-second exclusion already protects the first sample.
            frozenBaseline = rollingBaseline;
        } else if (frozenBaseline != null && !candidateActive && !trend.rising && score == 0
                && measuredQuiet) {
            // Never interpret missing sensor data as quiet. A genuine quiet evaluation resumes
            // normal rolling adaptation after an isolated event or a decayed transition.
            frozenBaseline = null;
        }

        ClearlyAwakeResult clearlyAwake = clearlyAwake(movementPattern, steps60Seconds,
                steps90Seconds, transitionRecent, cardiovascularEvidence);
        String clearlyAwakeBlockedReason = !clearlyAwake.detected
                && (stepEvidenceTainted || movementEvidenceTainted || cardioEvidenceTainted)
                ? "SELF_STIMULUS_CONTAMINATION" : "NONE";
        SystemAwakeCorroboration systemAwakeCorroboration = systemAwakeCorroboration(
                systemAwakeEvidence, clearlyAwake, steps60Seconds, movementPattern,
                cardiovascularCurrentEvidence, movementEvidence);
        boolean systemAwakeCorroborated = systemAwakeCorroboration.corroborated;
        // A candidate still needs fresh post-candidate confirmation. Separately, a short,
        // independently confirmed trend may wake immediately: its multiple fresh frames already
        // supply the temporal confirmation that an isolated candidate lacks.
        boolean temporalTrendWake = trend.temporallyConfirmed;
        boolean smartScoreWake = candidateConfirmed || temporalTrendWake;
        boolean shouldWake = smartScoreWake || systemAwakeCorroborated || clearlyAwake.detected;
        String wakeReason = (candidateConfirmed || temporalTrendWake)
                ? (userActivity == UserActivity.ASLEEP
                    ? "WAKE_LIGHT_SLEEP_OPPORTUNITY"
                    : "WAKE_TEMPORAL_MULTI_SENSOR_CONFIRMATION")
                : systemAwakeCorroborated ? "WAKE_ALREADY_AWAKE"
                : clearlyAwake.detected ? "WAKE_CLEARLY_AWAKE" : "NONE";
        WakeabilityState wakeabilityState = (candidateConfirmed || temporalTrendWake) ? WakeabilityState.WAKE_OPPORTUNITY
                : (systemAwakeCorroborated || clearlyAwake.detected) ? WakeabilityState.AWAKE
                : trend.rising || candidateActive ? WakeabilityState.WAKEABILITY_RISING
                : baselineReady && score == 0 ? WakeabilityState.STABLE_OR_LOW_WAKEABILITY
                : WakeabilityState.NORMAL_SLEEP;
        String continueReason = shouldWake ? "NONE"
                : systemAwakeEvidence ? "SYSTEM_AWAKE_UNCORROBORATED"
                : candidateWeakened || candidateExpired ? "CONTINUE_CANDIDATE_DECAYED"
                : candidateActive || trend.rising ? "CONTINUE_INSUFFICIENT_WAKEABILITY"
                : score > 0 ? "CONTINUE_ISOLATED_EVENT"
                : baselineReady && (recentAccelQuality.count > 0 || recentGyroQuality.count > 0)
                    ? "CONTINUE_STABLE_SLEEP" : "CONTINUE_INSUFFICIENT_WAKEABILITY";

        int movementClusters = movementPattern.movementClusters;
        int microMovementCount = Math.max(recentAccel.activeSamples, recentGyro.activeSamples);
        long timeSinceLastMovementMs = latestMovementEvidenceAt == Long.MIN_VALUE
                ? -1L : Math.max(0L, now - latestMovementEvidenceAt);
        String movementDataStatus = recentAccel.samples == 0 && recentGyro.samples == 0
                ? "NO_DATA" : movementEvidence ? "MEASURED_ACTIVITY" : "MEASURED_QUIET";
        lastEvaluationAt = now;

        return new Decision(shouldWake, score, baselineReady, moderateCandidate, immediateCandidate,
                candidateActive, candidateConfirmed, candidateAge, evidenceGroups, recentHr, baselineHr,
                hrAboveBaseline, hrvAboveBaseline, recentAccel, baselineAccel, recentGyro, baselineGyro,
                steps90Seconds, steps60Seconds, userActivity,
                lateAwakeConfirmation, hrRisePoints, hrTrendPoints, hrvRisePoints, accelPoints, gyroPoints,
                combinedMotionPoints, transitionPoints, clearlyAwake, movementPattern, transitionRecent,
                now, baselineStatus, baselineHrQuality, baselineAccelQuality, baselineGyroQuality,
                recentHrQuality, movementCoverage, systemAwakeEvidence, systemAwakeCorroborated,
                systemAwakeCorroboration.source, systemNonAsleepDurationMs, loggedCandidateOriginScore,
                loggedCandidateOriginGroups, candidateConfirmationStatus, candidateConfirmationSource,
                wakeReason, continueReason, wakeabilityState, trend, Integer.bitCount(freshEvaluationMask),
                hrDynamics, healthStateAgeMs, userActivityStateChangeAt, userActivityCallbackReceivedAt,
                userActivityEpisodeId, duplicateUserActivityEpisodeCallback, movementClusters, microMovementCount,
                timeSinceLastMovementMs, movementDataStatus, selfStimulus,
                stepEvidenceTainted, movementEvidenceTainted, cardioEvidenceTainted,
                clearlyAwakeBlockedReason);
    }

    private void beginCandidate(long now, int score, int groups) {
        candidateStartedAt = now;
        candidateOriginScore = score;
        candidateOriginGroups = groups;
        candidateWeakEvaluations = 0;
    }

    private void clearCandidate() {
        candidateStartedAt = Long.MIN_VALUE;
        candidateOriginScore = -1;
        candidateOriginGroups = -1;
        candidateWeakEvaluations = 0;
    }

    private boolean hasFreshMovementEvidence(long since, long now, MotionStats baselineAccel,
                                              MotionStats baselineGyro) {
        long freshStart = since == Long.MIN_VALUE ? now + 1L : since + 1L;
        MotionStats freshAccel = motionStats(accelerometer, freshStart, now, .35, 1.15);
        MotionStats freshGyro = motionStats(gyroscope, freshStart, now, .55, 1.50);
        return gentleMovementPoints(freshAccel, baselineAccel, 14, 5) > 0
                || gentleMovementPoints(freshGyro, baselineGyro, 12, 4) > 0;
    }

    private boolean hasFreshCardiovascularEvidence(long since, long now, HeartRateStats baselineHr) {
        if (baselineHr.count < 4) return false;
        long freshStart = since == Long.MIN_VALUE ? now + 1L : since + 1L;
        HeartRateStats fresh = heartRateStats(freshStart, now);
        return fresh.count > 0 && fresh.mean - baselineHr.mean >= 3.0;
    }

    private long latestMovementEvidenceAt(long start, long end) {
        long latest = latestAbove(accelerometer, start, end, .35);
        return Math.max(latest, latestAbove(gyroscope, start, end, .55));
    }

    private static long latestAbove(Deque<TimedValue> values, long start, long end, double threshold) {
        long latest = Long.MIN_VALUE;
        for (TimedValue value : values) {
            if (!value.tainted && value.at >= start && value.at <= end && value.value > threshold) latest = value.at;
        }
        return latest;
    }

    private HeartRateDynamics heartRateDynamics(long start, long end, double baseline) {
        List<TimedValue> samples = new ArrayList<>();
        for (TimedValue value : heartRates) if (!value.tainted && value.at >= start && value.at <= end) samples.add(value);
        if (samples.isEmpty()) return HeartRateDynamics.empty();
        double meanX = 0, meanY = 0;
        int elevated = 0;
        StringBuilder values = new StringBuilder();
        for (TimedValue sample : samples) {
            double minutes = (sample.at - samples.get(0).at) / 60_000.0;
            meanX += minutes;
            meanY += sample.value;
            if (!Double.isNaN(baseline) && sample.value >= baseline + 2.0) elevated++;
            if (values.length() > 0) values.append(',');
            values.append(String.format(Locale.US, "%.0f", sample.value));
        }
        meanX /= samples.size();
        meanY /= samples.size();
        double numerator = 0, denominator = 0;
        for (TimedValue sample : samples) {
            double x = (sample.at - samples.get(0).at) / 60_000.0;
            numerator += (x - meanX) * (sample.value - meanY);
            denominator += (x - meanX) * (x - meanX);
        }
        long span = samples.get(samples.size() - 1).at - samples.get(0).at;
        double slope = denominator <= 0 ? 0 : numerator / denominator;
        boolean sustained = samples.size() >= 3 && span >= 60_000L
                && elevated >= Math.min(3, samples.size() - 1);
        return new HeartRateDynamics(slope, samples.size(), span, elevated, sustained, values.toString());
    }

    private WakeabilityTrend updateWakeabilityTrend(long now, int score, int evidenceMask,
                                                      long latestHrAt, long latestMovementAt,
                                                      boolean baselineReady) {
        wakeabilityFrames.addLast(new WakeabilityFrame(now, score, evidenceMask,
                latestHrAt, latestMovementAt));
        while (!wakeabilityFrames.isEmpty()
                && wakeabilityFrames.peekFirst().at < now - WAKEABILITY_TREND_WINDOW_MS) {
            wakeabilityFrames.removeFirst();
        }
        int interesting = 0, unionMask = 0, firstScore = score;
        int cardioUpdates = 0, movementUpdates = 0;
        int recentInteresting = 0, recentUnionMask = 0, recentFirstScore = score;
        int recentCardioUpdates = 0, recentMovementUpdates = 0;
        long countedHrAt = Long.MIN_VALUE, countedMovementAt = Long.MIN_VALUE;
        long recentCountedHrAt = Long.MIN_VALUE, recentCountedMovementAt = Long.MIN_VALUE;
        boolean foundFirst = false;
        boolean foundRecentFirst = false;
        for (WakeabilityFrame frame : wakeabilityFrames) {
            if (frame.score < TREND_INTERESTING_THRESHOLD || frame.evidenceMask == 0) continue;
            if (!foundFirst) { firstScore = frame.score; foundFirst = true; }
            interesting++;
            unionMask |= frame.evidenceMask;
            if ((frame.evidenceMask & EVIDENCE_CARDIOVASCULAR) != 0 && frame.latestHrAt > countedHrAt) {
                cardioUpdates++;
                countedHrAt = frame.latestHrAt;
            }
            if ((frame.evidenceMask & EVIDENCE_MOVEMENT) != 0
                    && frame.latestMovementAt > countedMovementAt) {
                movementUpdates++;
                countedMovementAt = frame.latestMovementAt;
            }
            if (frame.at < now - TEMPORAL_CONFIRMATION_WINDOW_MS) continue;
            if (!foundRecentFirst) { recentFirstScore = frame.score; foundRecentFirst = true; }
            recentInteresting++;
            recentUnionMask |= frame.evidenceMask;
            if ((frame.evidenceMask & EVIDENCE_CARDIOVASCULAR) != 0
                    && frame.latestHrAt > recentCountedHrAt) {
                recentCardioUpdates++;
                recentCountedHrAt = frame.latestHrAt;
            }
            if ((frame.evidenceMask & EVIDENCE_MOVEMENT) != 0
                    && frame.latestMovementAt > recentCountedMovementAt) {
                recentMovementUpdates++;
                recentCountedMovementAt = frame.latestMovementAt;
            }
        }
        boolean hasCardioAndMovement = (unionMask & EVIDENCE_CARDIOVASCULAR) != 0
                && (unionMask & EVIDENCE_MOVEMENT) != 0;
        int delta = foundFirst ? score - firstScore : 0;
        boolean rising = baselineReady && score >= TREND_CANDIDATE_THRESHOLD
                && interesting >= 2 && hasCardioAndMovement
                && cardioUpdates >= 2 && movementUpdates >= 2
                && delta >= -3;
        boolean currentMultiGroup = Integer.bitCount(evidenceMask) >= 2;
        boolean recentHasCardioAndMovement = (recentUnionMask & EVIDENCE_CARDIOVASCULAR) != 0
                && (recentUnionMask & EVIDENCE_MOVEMENT) != 0;
        int recentDelta = foundRecentFirst ? score - recentFirstScore : 0;
        // Unlike candidate context, this route has already observed a progression of at least
        // three interesting frames and two distinct current/recent updates from both sources.
        // The last frame must still itself be a current multi-group opportunity.
        boolean temporallyConfirmed = baselineReady && currentMultiGroup
                && score >= CONFIRMED_WAKE_THRESHOLD && recentInteresting >= 3
                && recentHasCardioAndMovement && recentCardioUpdates >= 2
                && recentMovementUpdates >= 2 && recentDelta >= -3;
        String label = temporallyConfirmed ? "CONFIRMED" : rising ? "RISING"
                : interesting > 0 ? "OBSERVING" : "FLAT";
        String evidence = String.format(Locale.US,
                "samples=%d,cardio_updates=%d,movement_updates=%d,score_delta=%+d,groups=%d,"
                        + "recent_%dms_samples=%d,cardio_updates=%d,movement_updates=%d,score_delta=%+d,confirmed=%s",
                interesting, cardioUpdates, movementUpdates, delta, Integer.bitCount(unionMask),
                TEMPORAL_CONFIRMATION_WINDOW_MS, recentInteresting, recentCardioUpdates,
                recentMovementUpdates, recentDelta, temporallyConfirmed);
        return new WakeabilityTrend(rising, delta, interesting, cardioUpdates, movementUpdates,
                temporallyConfirmed, label, evidence);
    }

    private static boolean isSystemNonAsleep(UserActivity activity) {
        return activity == UserActivity.PASSIVE || activity == UserActivity.EXERCISE;
    }

    private static String activityEpisodeId(UserActivity activity, long stateChangeAt) {
        return activity == null || stateChangeAt == Long.MIN_VALUE ? "NONE"
                : activity.name() + "@" + stateChangeAt;
    }

    /**
     * PASSIVE is useful system context, but OnePlus may repeat-deliver one long-lived PASSIVE
     * episode. It may wake only with an independent current signal. A fresh EXERCISE episode is
     * materially stronger and is itself sufficient; both rules depend on stateChangeTime.
     */
    private SystemAwakeCorroboration systemAwakeCorroboration(boolean systemAwakeEvidence,
                                                               ClearlyAwakeResult clearlyAwake,
                                                               int steps60Seconds,
                                                               MovementPattern movement,
                                                               boolean cardiovascularCurrentEvidence,
                                                               boolean movementEvidence) {
        if (!systemAwakeEvidence) return SystemAwakeCorroboration.none();
        if (userActivity == UserActivity.EXERCISE) {
            return SystemAwakeCorroboration.yes("FRESH_EXERCISE_STATE");
        }
        if (clearlyAwake.detected) return SystemAwakeCorroboration.yes("CLEARLY_AWAKE");
        if (steps60Seconds >= 1) return SystemAwakeCorroboration.yes("FRESH_STEPS");
        if (movement.activeBuckets >= 2) return SystemAwakeCorroboration.yes("REPEATED_MOVEMENT");
        if (cardiovascularCurrentEvidence && movementEvidence) {
            return SystemAwakeCorroboration.yes("CURRENT_CARDIOVASCULAR_AND_MOVEMENT");
        }
        return SystemAwakeCorroboration.none();
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
        while (!steps.isEmpty() && steps.peekFirst().at < now - CLEARLY_AWAKE_WINDOW_MS) steps.removeFirst();
    }

    private int countStepsSince(long start) {
        int count = 0;
        for (TimedStep step : steps) if (!step.tainted && step.at >= start) count++;
        return count;
    }
    private boolean hasTaintedSteps(long start, long end) {
        for (TimedStep step : steps) if (step.tainted && step.at >= start && step.at <= end) return true;
        return false;
    }
    private static boolean hasTaintedValues(Deque<TimedValue> values, long start, long end) {
        for (TimedValue value : values) if (value.tainted && value.at >= start && value.at <= end) return true;
        return false;
    }
    private static void pruneValues(Deque<TimedValue> values, long earliest) {
        while (!values.isEmpty() && values.peekFirst().at < earliest) values.removeFirst();
    }

    private static SampleQuality sampleQuality(Deque<TimedValue> values, long start, long end) {
        int count = 0; long latest = Long.MIN_VALUE;
        for (TimedValue value : values) if (!value.tainted && value.at >= start && value.at <= end) {
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
        for (TimedValue value : values) if (!value.tainted && value.at >= start && value.at <= end) {
            int bucket = (int) ((value.at - start) / COVERAGE_BUCKET_MS);
            covered[Math.min(covered.length - 1, Math.max(0, bucket))] = true;
        }
    }

    private HeartRateStats heartRateStats(long start, long end) {
        double sum = 0; int count = 0;
        for (TimedValue value : heartRates) if (!value.tainted && value.at >= start && value.at <= end) { sum += value.value; count++; }
        if (count == 0) return new HeartRateStats(0, 0, 0);
        double mean = sum / count, variance = 0;
        for (TimedValue value : heartRates) if (!value.tainted && value.at >= start && value.at <= end) { double d = value.value - mean; variance += d * d; }
        return new HeartRateStats(mean, Math.sqrt(variance / count), count);
    }

    /** A 10% trimmed HR mean/spread rejects short wake or check-the-watch spikes. */
    private HeartRateStats baselineHeartRateStats(long start, long end) {
        List<Double> samples = new ArrayList<>();
        for (TimedValue value : heartRates) if (!value.tainted && value.at >= start && value.at <= end) samples.add(value.value);
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
        double energy = 0; int bursts = 0, samples = 0, activeSamples = 0;
        for (TimedValue value : values) if (!value.tainted && value.at >= start && value.at <= end) {
            samples++;
            if (value.value > activeThreshold) {
                energy += Math.min(3.0, value.value);
                activeSamples++;
            }
            if (value.value > burstThreshold) bursts++;
        }
        double seconds = Math.max(1, (end - start) / 1000.0);
        boolean strong = bursts >= 50 || energy >= 75;
        boolean active = strong || bursts >= 2 || energy >= 4;
        return new MotionStats(energy, energy / seconds, bursts, samples, activeSamples, active, strong);
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
        if (energyRates.isEmpty()) return new MotionStats(0, 0, 0, 0, 0, false, false);
        double rate = median(energyRates);
        int bursts = (int) Math.round(median(burstCounts));
        double energy = rate * 30;
        boolean strong = bursts >= 50 || energy >= 75;
        boolean active = strong || bursts >= 2 || energy >= 4;
        return new MotionStats(energy, rate, bursts, 0, 0, active, strong);
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
        int active = 0, active60 = 0, dualStrong = 0, movementClusters = 0;
        int firstActive = -1, lastActive = -1, firstDual = -1, lastDual = -1;
        boolean previousActive = false;
        for (int i = 0; i < bucketCount; i++) {
            boolean bucketActive = accel.active[i] || gyro.active[i];
            if (bucketActive) {
                if (!previousActive) movementClusters++;
                active++;
                if (i >= bucketCount - 4) active60++;
                if (firstActive < 0) firstActive = i;
                lastActive = i;
            }
            previousActive = bucketActive;
            if (accel.strong[i] && gyro.strong[i]) {
                dualStrong++;
                if (firstDual < 0) firstDual = i;
                lastDual = i;
            }
        }
        return new MovementPattern(active, active60, movementClusters, span(firstActive, lastActive), accel.activeCount,
                gyro.activeCount, accel.strongCount, gyro.strongCount, accel.strongSpanMs,
                gyro.strongSpanMs, dualStrong, span(firstDual, lastDual));
    }

    private static BucketSeries bucketSeries(Deque<TimedValue> values, long start, long end,
                                             int bucketCount, double activeThreshold,
                                             double burstThreshold) {
        double[] energy = new double[bucketCount];
        int[] bursts = new int[bucketCount];
        for (TimedValue value : values) {
            if (value.tainted || value.at < start || value.at > end) continue;
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

    private static final class TimedValue {
        final long at; final double value; final boolean tainted;
        TimedValue(long at, double value, boolean tainted) { this.at = at; this.value = value; this.tainted = tainted; }
    }
    private static final class TimedStep {
        final long at; final boolean tainted;
        TimedStep(long at, boolean tainted) { this.at = at; this.tainted = tainted; }
    }
    private static final class WakeabilityFrame {
        final long at, latestHrAt, latestMovementAt;
        final int score, evidenceMask;
        WakeabilityFrame(long at, int score, int evidenceMask, long latestHrAt, long latestMovementAt) {
            this.at = at; this.score = score; this.evidenceMask = evidenceMask;
            this.latestHrAt = latestHrAt; this.latestMovementAt = latestMovementAt;
        }
    }
    private static final class HeartRateDynamics {
        final double slopeBpmPerMinute;
        final int samples, elevatedSamples;
        final long spanMs;
        final boolean sustainedElevation;
        final String recentSamples;
        HeartRateDynamics(double slopeBpmPerMinute, int samples, long spanMs, int elevatedSamples,
                          boolean sustainedElevation, String recentSamples) {
            this.slopeBpmPerMinute = slopeBpmPerMinute; this.samples = samples; this.spanMs = spanMs;
            this.elevatedSamples = elevatedSamples; this.sustainedElevation = sustainedElevation;
            this.recentSamples = recentSamples;
        }
        static HeartRateDynamics empty() {
            return new HeartRateDynamics(0, 0, 0, 0, false, "NO_DATA");
        }
    }
    private static final class WakeabilityTrend {
        final boolean rising, temporallyConfirmed;
        final int scoreDelta, samples, cardioUpdates, movementUpdates;
        final String label, evidence;
        WakeabilityTrend(boolean rising, int scoreDelta, int samples, int cardioUpdates,
                         int movementUpdates, boolean temporallyConfirmed, String label, String evidence) {
            this.rising = rising; this.scoreDelta = scoreDelta; this.samples = samples;
            this.cardioUpdates = cardioUpdates; this.movementUpdates = movementUpdates;
            this.temporallyConfirmed = temporallyConfirmed;
            this.label = label; this.evidence = evidence;
        }
    }
    private static final class SampleQuality {
        final int count; final long latestAt;
        SampleQuality(int count, long latestAt) { this.count = count; this.latestAt = latestAt; }
    }
    private static final class BaselineSnapshot {
        final HeartRateStats heartRate;
        final MotionStats accelerometer, gyroscope;
        final SampleQuality heartRateQuality, accelerometerQuality, gyroscopeQuality;
        BaselineSnapshot(HeartRateStats heartRate, MotionStats accelerometer, MotionStats gyroscope,
                         SampleQuality heartRateQuality, SampleQuality accelerometerQuality,
                         SampleQuality gyroscopeQuality) {
            this.heartRate = heartRate; this.accelerometer = accelerometer; this.gyroscope = gyroscope;
            this.heartRateQuality = heartRateQuality;
            this.accelerometerQuality = accelerometerQuality;
            this.gyroscopeQuality = gyroscopeQuality;
        }
    }
    private static final class MovementCoverage {
        final int coveredBuckets, totalBuckets;
        MovementCoverage(int coveredBuckets, int totalBuckets) { this.coveredBuckets = coveredBuckets; this.totalBuckets = totalBuckets; }
    }
    private static final class HeartRateStats { final double mean, variability; final int count; HeartRateStats(double mean, double variability, int count) { this.mean = mean; this.variability = variability; this.count = count; } }
    public static final class MotionStats {
        public final double energy, energyPerSecond;
        public final int bursts, samples, activeSamples;
        public final boolean active, strong;
        MotionStats(double energy, double energyPerSecond, int bursts, int samples, int activeSamples,
                    boolean active, boolean strong) {
            this.energy = energy; this.energyPerSecond = energyPerSecond; this.bursts = bursts;
            this.samples = samples; this.activeSamples = activeSamples;
            this.active = active; this.strong = strong;
        }
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
        final int activeBuckets, activeBuckets60Seconds, movementClusters, accelActiveBuckets, gyroActiveBuckets;
        final int accelStrongBuckets, gyroStrongBuckets, dualStrongBuckets;
        final long movementSpanMs, accelStrongSpanMs, gyroStrongSpanMs, dualStrongSpanMs;
        MovementPattern(int activeBuckets, int activeBuckets60Seconds, int movementClusters, long movementSpanMs,
                        int accelActiveBuckets, int gyroActiveBuckets, int accelStrongBuckets,
                        int gyroStrongBuckets, long accelStrongSpanMs, long gyroStrongSpanMs,
                        int dualStrongBuckets, long dualStrongSpanMs) {
            this.activeBuckets = activeBuckets; this.activeBuckets60Seconds = activeBuckets60Seconds;
            this.movementClusters = movementClusters; this.movementSpanMs = movementSpanMs;
            this.accelActiveBuckets = accelActiveBuckets;
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
    private static final class SystemAwakeCorroboration {
        final boolean corroborated;
        final String source;
        private SystemAwakeCorroboration(boolean corroborated, String source) {
            this.corroborated = corroborated; this.source = source;
        }
        static SystemAwakeCorroboration yes(String source) {
            return new SystemAwakeCorroboration(true, source);
        }
        static SystemAwakeCorroboration none() {
            return new SystemAwakeCorroboration(false, "NONE");
        }
    }

    public static final class Decision {
        public final boolean shouldWake, candidate, immediateCandidate, baselineReady, lateAwakeConfirmation;
        public final boolean candidateActive, candidateConfirmed, clearlyAwake, freshUserActivityTransition;
        public final boolean systemAwakeEvidence, systemAwakeCorroborated, duplicateActivityEpisodeCallback;
        public final int score, evidenceGroups, freshEvidenceGroups, steps, steps60Seconds;
        public final long candidateAgeMs, movementSpanMs, systemNonAsleepDurationMs, systemStateAgeMs;
        public final long healthStateChangeTimeEpochMs, healthCallbackReceivedAtEpochMs;
        public final int candidateOriginScore, candidateOriginGroups;
        public final String candidateConfirmationStatus, candidateConfirmationSource, wakeReason, continueReason;
        public final String healthEpisodeId, systemAwakeCorroborationSource;
        public final int activeMovementBuckets, accelActiveBuckets, gyroActiveBuckets;
        public final int accelStrongBuckets, gyroStrongBuckets, dualStrongBuckets;
        public final String clearlyAwakeReason;
        public final WakeabilityState wakeabilityState;
        public final String wakeabilityTrend, wakeabilityEvidence, movementDataStatus;
        public final String baselineStatus;
        public final int baselineHeartRateSamples, baselineAccelerometerSamples, baselineGyroscopeSamples;
        public final long heartRateSampleAgeMs, hrvSampleAgeMs;
        public final double heartRateRecentSlope;
        public final int heartRateRecentSamples, heartRateElevatedSamples;
        public final String heartRateRecentValues;
        public final int movementCoverageBuckets, movementCoverageTotalBuckets;
        public final int movementClusterCount, microMovementCount;
        public final long timeSinceLastMovementMs;
        public final boolean selfStimulusActive, stepEvidenceTainted, movementEvidenceTainted, cardioEvidenceTainted;
        public final String selfStimulusSource, selfStimulusType, clearlyAwakeBlockedReason;
        public final long selfStimulusStartedAt, selfStimulusEndedAt;
        public final double heartRateMean, heartRateVariability, heartRateBaseline, hrvBaseline, hrAboveBaseline, hrvAboveBaseline;
        public final MotionStats accelerometer, accelerometerBaseline, gyroscope, gyroscopeBaseline;
        public final UserActivity userActivity;
        private final int hrRisePoints, hrTrendPoints, hrvRisePoints, accelPoints, gyroPoints;
        private final int combinedMotionPoints, transitionPoints;

        Decision(boolean shouldWake, int score, boolean baselineReady, boolean candidate, boolean immediateCandidate,
                 boolean candidateActive, boolean candidateConfirmed, long candidateAgeMs, int evidenceGroups,
                 HeartRateStats recentHr, HeartRateStats baselineHr,
                 double hrAboveBaseline, double hrvAboveBaseline, MotionStats accelerometer, MotionStats accelerometerBaseline,
                 MotionStats gyroscope, MotionStats gyroscopeBaseline, int steps, int steps60Seconds,
                 UserActivity userActivity,
                 boolean lateAwakeConfirmation, int hrRisePoints, int hrTrendPoints, int hrvRisePoints,
                 int accelPoints, int gyroPoints,
                 int combinedMotionPoints, int transitionPoints, ClearlyAwakeResult clearlyAwake,
                 MovementPattern movementPattern, boolean freshUserActivityTransition,
                 long evaluatedAt, String baselineStatus, SampleQuality baselineHrQuality, SampleQuality baselineAccelQuality,
                 SampleQuality baselineGyroQuality, SampleQuality recentHrQuality,
                 MovementCoverage movementCoverage, boolean systemAwakeEvidence, boolean systemAwakeCorroborated,
                 String systemAwakeCorroborationSource, long systemNonAsleepDurationMs,
                 int candidateOriginScore, int candidateOriginGroups, String candidateConfirmationStatus,
                 String candidateConfirmationSource, String wakeReason, String continueReason,
                 WakeabilityState wakeabilityState, WakeabilityTrend wakeabilityTrend,
                 int freshEvidenceGroups, HeartRateDynamics hrDynamics, long systemStateAgeMs,
                 long healthStateChangeTimeEpochMs, long healthCallbackReceivedAtEpochMs,
                 String healthEpisodeId, boolean duplicateActivityEpisodeCallback,
                 int movementClusterCount, int microMovementCount, long timeSinceLastMovementMs,
                 String movementDataStatus, SelfStimulusContamination.Snapshot selfStimulus,
                 boolean stepEvidenceTainted, boolean movementEvidenceTainted, boolean cardioEvidenceTainted,
                 String clearlyAwakeBlockedReason) {
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
            this.hrRisePoints = hrRisePoints; this.hrTrendPoints = hrTrendPoints;
            this.hrvRisePoints = hrvRisePoints; this.accelPoints = accelPoints;
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
            this.hrvSampleAgeMs = -1L;
            this.movementCoverageBuckets = movementCoverage.coveredBuckets;
            this.movementCoverageTotalBuckets = movementCoverage.totalBuckets;
            this.systemAwakeEvidence = systemAwakeEvidence;
            this.systemAwakeCorroborated = systemAwakeCorroborated;
            this.systemAwakeCorroborationSource = systemAwakeCorroborationSource;
            this.systemNonAsleepDurationMs = systemNonAsleepDurationMs;
            this.candidateOriginScore = candidateOriginScore;
            this.candidateOriginGroups = candidateOriginGroups;
            this.candidateConfirmationStatus = candidateConfirmationStatus;
            this.candidateConfirmationSource = candidateConfirmationSource;
            this.wakeReason = wakeReason;
            this.continueReason = continueReason;
            this.wakeabilityState = wakeabilityState;
            this.wakeabilityTrend = wakeabilityTrend.label;
            this.wakeabilityEvidence = wakeabilityTrend.evidence;
            this.freshEvidenceGroups = freshEvidenceGroups;
            this.heartRateRecentSlope = hrDynamics.slopeBpmPerMinute;
            this.heartRateRecentSamples = hrDynamics.samples;
            this.heartRateElevatedSamples = hrDynamics.elevatedSamples;
            this.heartRateRecentValues = hrDynamics.recentSamples;
            this.systemStateAgeMs = systemStateAgeMs;
            this.healthStateChangeTimeEpochMs = healthStateChangeTimeEpochMs;
            this.healthCallbackReceivedAtEpochMs = healthCallbackReceivedAtEpochMs;
            this.healthEpisodeId = healthEpisodeId;
            this.duplicateActivityEpisodeCallback = duplicateActivityEpisodeCallback;
            this.movementClusterCount = movementClusterCount;
            this.microMovementCount = microMovementCount;
            this.timeSinceLastMovementMs = timeSinceLastMovementMs;
            this.movementDataStatus = movementDataStatus;
            this.selfStimulusActive = selfStimulus.active;
            SelfStimulusContamination.Stimulus source = selfStimulus.source;
            this.selfStimulusSource = source == null || source.sourceOccurrenceId.isEmpty()
                    ? "NONE" : source.sourceOccurrenceId;
            this.selfStimulusType = source == null ? "NONE" : source.type;
            this.selfStimulusStartedAt = source == null ? -1L : source.startedEpochAt;
            this.selfStimulusEndedAt = source == null || source.endedEpochAt == Long.MIN_VALUE
                    ? -1L : source.endedEpochAt;
            this.stepEvidenceTainted = stepEvidenceTainted;
            this.movementEvidenceTainted = movementEvidenceTainted;
            this.cardioEvidenceTainted = cardioEvidenceTainted;
            this.clearlyAwakeBlockedReason = clearlyAwakeBlockedReason;
        }

        private long ageAt(long evaluatedAt, long timestamp) { return timestamp == Long.MIN_VALUE ? -1L : Math.max(0L, evaluatedAt - timestamp); }

        /** One grep-friendly record per scoring evaluation for reviewing an entire night. */
        public String summary(long timestamp) {
            return String.format(Locale.US,
                    "timestamp=%d → system_state=%s → health_episode=%s → system_state_age_ms=%d"
                            + " → WAKE_SCORE=%d → groups=%d → fresh_groups=%d"
                            + " → candidate_active=%s → candidate_age_ms=%d"
                            + " → candidate_origin_score=%d → candidate_origin_groups=%d"
                            + " → candidate_confirmation_status=%s → wakeability_state=%s"
                            + " → wakeability_trend=%s → clearly_awake=%s"
                            + " → self_stimulus_active=%s"
                            + " → system_awake_evidence=%s → system_awake_corroborated=%s"
                            + " → decision=%s → DECISION_REASON=%s",
                    timestamp, userActivity, healthEpisodeId, systemStateAgeMs, score, evidenceGroups, freshEvidenceGroups,
                    candidateActive, candidateAgeMs,
                    candidateOriginScore, candidateOriginGroups, candidateConfirmationStatus,
                    wakeabilityState, wakeabilityTrend, clearlyAwake, selfStimulusActive,
                    systemAwakeEvidence, systemAwakeCorroborated,
                    shouldWake ? "WAKE" : "CONTINUE",
                    shouldWake ? wakeReason : continueReason);
        }

        /** One persisted debug record per 30-second scoring evaluation. */
        public String telemetry() {
            return String.format(Locale.US,
                    "HEALTH_ACTIVITY_STATE=%s HEALTH_STATE_CHANGE_TIME=%d HEALTH_CALLBACK_RECEIVED_AT=%d HEALTH_STATE_AGE=%dms HEALTH_EPISODE_ID=%s HEALTH_DUPLICATE_EPISODE_CALLBACK=%s\n"
                            + "SYSTEM_AWAKE_EVIDENCE=%s SYSTEM_AWAKE_CORROBORATED=%s SYSTEM_AWAKE_CORROBORATION_SOURCE=%s\n"
                            + "BASELINE_READY=%s BASELINE_STATUS=%s BASELINE_SAMPLES[HR=%d,ACCEL=%d,GYRO=%d] METHOD=HR_TRIMMED_MEAN,MOTION_MEDIAN_30S_BUCKETS\n"
                            + "WAKE_SCORE=%d EVIDENCE_GROUPS=%d FRESH_EVIDENCE_GROUPS=%d\n"
                            + "SELF_STIMULUS_ACTIVE=%s SELF_STIMULUS_SOURCE=%s SELF_STIMULUS_TYPE=%s SELF_STIMULUS_STARTED_AT=%d SELF_STIMULUS_ENDED_AT=%d\n"
                            + "STEP_EVIDENCE_TAINTED=%s MOVEMENT_EVIDENCE_TAINTED=%s CARDIO_EVIDENCE_TAINTED=%s EVIDENCE_EXCLUDED_SELF_STIMULUS=%s\n"
                            + "HR=%.1f HR_AGE=%dms HR_BASELINE=%.1f HR_DELTA=%+.1f HR_RECENT_SLOPE=%+.2f_bpm_per_min HR_RECENT_SAMPLES=%s HR_SAMPLE_COUNT=%d HR_ELEVATED_SAMPLES=%d (+%d_delta,+%d_trend)\n"
                            + "HR_BPM_VARIABILITY_PROXY=%.1f BASELINE_PROXY=%.1f HRV=NO_DATA HRV_AGE=-1ms HRV_BASELINE=NO_DATA HRV_DELTA=NO_DATA SOURCE=UNAVAILABLE\n"
                            + "MOVEMENT_DATA_STATUS=%s MOVEMENT_WINDOW_COVERAGE=%d/%d\n"
                            + "ACCEL_SAMPLES=%d ACCEL_ACTIVITY_INDEX=%.3f/s ACCEL_BASELINE=%.3f/s ACCEL_BURSTS=%d (+%d)\n"
                            + "GYRO_SAMPLES=%d GYRO_ACTIVITY_INDEX=%.3f/s GYRO_BASELINE=%.3f/s GYRO_BURSTS=%d (+%d)\n"
                            + "MICRO_MOVEMENT_COUNT=%d MOVEMENT_CLUSTER_COUNT=%d TIME_SINCE_LAST_MOVEMENT=%dms ACTIVE_MOVEMENT_BUCKETS=%d MOVEMENT_SPAN=%dms\n"
                            + "ACCEL_ACTIVE_BUCKETS=%d GYRO_ACTIVE_BUCKETS=%d ACCEL_STRONG_BUCKETS=%d GYRO_STRONG_BUCKETS=%d DUAL_STRONG_BUCKETS=%d MOTION_CONFIRMATION=%s (+%d)\n"
                            + "STEPS_RECENT=%d STEP_DELTA_60S=%d ASLEEP_TO_NON_ASLEEP=%s (+%d)\n"
                            + "CANDIDATE_ACTIVE=%s CANDIDATE_AGE=%dms CANDIDATE_ORIGIN=%d/%d CANDIDATE_CONFIRMATION_STATUS=%s CANDIDATE_CONFIRMATION_SOURCE=%s CANDIDATE_CONFIRMED=%s\n"
                            + "WAKEABILITY_STATE=%s WAKEABILITY_TREND=%s WAKEABILITY_EVIDENCE=%s\n"
                            + "CLEARLY_AWAKE=%s CLEARLY_AWAKE_REASON=%s CLEARLY_AWAKE_BLOCKED_REASON=%s ALREADY_AWAKE=%s\n"
                            + "CANDIDATE_THRESHOLD=%d TREND_CANDIDATE_THRESHOLD=%d CANDIDATE_CONFIRMATION_THRESHOLD=%d SINGLE_SAMPLE_STRONG_THRESHOLD=%d\n"
                            + "CANDIDATE=%s SINGLE_SAMPLE_STRONG=%s DECISION=%s DECISION_REASON=%s CONTINUE_REASON=%s",
                    userActivity, healthStateChangeTimeEpochMs, healthCallbackReceivedAtEpochMs,
                    systemStateAgeMs, healthEpisodeId, duplicateActivityEpisodeCallback,
                    systemAwakeEvidence, systemAwakeCorroborated, systemAwakeCorroborationSource,
                    baselineReady, baselineStatus, baselineHeartRateSamples, baselineAccelerometerSamples,
                    baselineGyroscopeSamples, score, evidenceGroups, freshEvidenceGroups,
                    selfStimulusActive, selfStimulusSource, selfStimulusType, selfStimulusStartedAt,
                    selfStimulusEndedAt, stepEvidenceTainted, movementEvidenceTainted, cardioEvidenceTainted,
                    stepEvidenceTainted || movementEvidenceTainted || cardioEvidenceTainted,
                    heartRateMean, heartRateSampleAgeMs, heartRateBaseline, hrAboveBaseline,
                    heartRateRecentSlope, heartRateRecentValues, heartRateRecentSamples,
                    heartRateElevatedSamples, hrRisePoints, hrTrendPoints,
                    heartRateVariability, hrvBaseline, movementDataStatus,
                    movementCoverageBuckets, movementCoverageTotalBuckets,
                    accelerometer.samples, accelerometer.energyPerSecond,
                    accelerometerBaseline.energyPerSecond, accelerometer.bursts, accelPoints,
                    gyroscope.samples, gyroscope.energyPerSecond, gyroscopeBaseline.energyPerSecond,
                    gyroscope.bursts, gyroPoints, microMovementCount, movementClusterCount,
                    timeSinceLastMovementMs, activeMovementBuckets, movementSpanMs,
                    accelActiveBuckets, gyroActiveBuckets, accelStrongBuckets, gyroStrongBuckets,
                    dualStrongBuckets, accelPoints >= 14 && gyroPoints >= 12, combinedMotionPoints,
                    steps, steps60Seconds, freshUserActivityTransition, transitionPoints,
                    candidateActive, candidateAgeMs, candidateOriginScore, candidateOriginGroups,
                    candidateConfirmationStatus, candidateConfirmationSource, candidateConfirmed,
                    wakeabilityState, wakeabilityTrend, wakeabilityEvidence,
                    clearlyAwake, clearlyAwakeReason, clearlyAwakeBlockedReason, systemAwakeCorroborated || clearlyAwake,
                    CONFIRMED_WAKE_THRESHOLD, TREND_CANDIDATE_THRESHOLD,
                    CANDIDATE_CONFIRMATION_THRESHOLD, IMMEDIATE_WAKE_THRESHOLD,
                    candidate, immediateCandidate, shouldWake ? "WAKE" : "CONTINUE",
                    shouldWake ? wakeReason : continueReason, continueReason);
        }
    }
}
