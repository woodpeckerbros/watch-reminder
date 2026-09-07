package com.woodpeckerbros.watchreminder.smartwake;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SmartWakeDetectorTest {
    @Test public void asleepIsNeutralContextNotNegativeEvidence() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        addGentleWakeChange(detector, 610_000, false);
        SmartWakeDetector.Decision decision = detector.evaluate(675_000);
        assertEquals(15, decision.score); // HR rise alone is intentionally not enough without motion.
        assertFalse(decision.shouldWake);
    }

    @Test public void lightSleepBeginningToWakeUsesCandidateMemoryAndConfirmation() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        addGentleWakeChange(detector, 610_000, true);
        SmartWakeDetector.Decision candidate = detector.evaluate(675_000);
        assertEquals(29, candidate.score);
        assertTrue(candidate.candidate);
        assertTrue(candidate.candidateActive);
        assertFalse(candidate.shouldWake);

        // The original movement has left the short scoring window, but a supporting HR signal
        // confirms the remembered candidate rather than forcing it to start from zero again.
        SmartWakeDetector.Decision confirmation = detector.evaluate(705_000);
        assertEquals(15, confirmation.score);
        assertTrue(confirmation.candidateActive);
        assertEquals(30_000L, confirmation.candidateAgeMs);
        assertTrue(confirmation.candidateConfirmed);
        assertTrue(confirmation.shouldWake);
    }

    @Test public void veryHighMultiSignalChangeWakesOnOneEvaluation() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        detector.setUserActivity(SmartWakeDetector.UserActivity.PASSIVE, 650_000);
        addStrongBaselineRelativeChange(detector, 610_000);
        SmartWakeDetector.Decision decision = detector.evaluate(675_000);
        assertTrue(decision.immediateCandidate);
        assertTrue(decision.shouldWake);
    }

    @Test public void accelerometerAndGyroscopeAreOneMovementEvidenceGroup() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        addStrongBaselineRelativeChange(detector, 610_000);
        SmartWakeDetector.Decision decision = detector.evaluate(675_000);
        assertEquals(2, decision.evidenceGroups); // cardiovascular + movement, not three groups.
        assertTrue(decision.immediateCandidate); // Immediate now needs these exact two groups.
    }

    @Test public void stepsAndPassiveDoNotCreateWakeScore() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.PASSIVE, 610_000);
        detector.addStep(620_000);
        SmartWakeDetector.Decision decision = detector.evaluate(675_000);
        assertEquals(0, decision.score);
        assertTrue(decision.lateAwakeConfirmation);
        assertFalse(decision.shouldWake);
    }

    @Test public void deepSleepSingleTurnOverDoesNotWake() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        for (int i = 0; i < 12; i++) {
            long at = 620_000L + i * 500L;
            detector.addAccelerometerMotion(1.6, at);
            detector.addGyroscopeMotion(1.8, at);
        }

        SmartWakeDetector.Decision decision = detector.evaluate(675_000);
        assertFalse(decision.candidate);
        assertFalse(decision.clearlyAwake);
        assertFalse(decision.shouldWake);
        assertEquals(1, decision.activeMovementBuckets);
    }

    @Test public void userAlreadyUpAndWalkingWakesThroughClearlyAwakeRoute() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 590_000);
        for (int i = 0; i < 8; i++) detector.addStep(625_000L + i * 5_000L);
        addActiveAccelBucket(detector, 635_000L);
        addActiveAccelBucket(detector, 650_000L);

        SmartWakeDetector.Decision decision = detector.evaluate(675_000);
        assertEquals(14, decision.score); // Only normal movement scoring; steps add no wake points.
        assertFalse(decision.candidate);
        assertTrue(decision.clearlyAwake);
        assertEquals("STEPS_3_PLUS_MOVEMENT", decision.clearlyAwakeReason);
        assertTrue(decision.shouldWake);
    }

    @Test public void eightStepsAloneNeedIndependentConfirmation() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 590_000);
        for (int i = 0; i < 8; i++) detector.addStep(625_000L + i * 5_000L);

        SmartWakeDetector.Decision decision = detector.evaluate(675_000);
        assertFalse(decision.clearlyAwake);
        assertFalse(decision.shouldWake);
    }

    @Test public void oneSustainedMovementSensorNeedsIndependentConfirmation() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 590_000);
        addStrongAccelBucket(detector, 600_000L);
        addStrongAccelBucket(detector, 615_000L);
        addStrongAccelBucket(detector, 630_000L);
        addStrongAccelBucket(detector, 645_000L);

        SmartWakeDetector.Decision unconfirmed = detector.evaluate(675_000);
        assertFalse(unconfirmed.clearlyAwake);
        assertFalse(unconfirmed.shouldWake);

        detector.addStep(674_000L);
        SmartWakeDetector.Decision confirmed = detector.evaluate(675_000);
        assertTrue(confirmed.clearlyAwake);
        assertEquals("SINGLE_SENSOR_SUSTAINED_STRONG_MOVEMENT_CONFIRMED", confirmed.clearlyAwakeReason);
        assertTrue(confirmed.shouldWake);
    }

    @Test public void telemetryIncludesBothDecisionRoutesAndSupportingMeasurements() {
        SmartWakeDetector.Decision decision = preparedDetector().evaluate(675_000);
        String summary = decision.summary(675_000);
        assertTrue(summary.contains("timestamp=675000"));
        assertTrue(summary.contains("BASELINE_READY=true"));
        assertTrue(summary.contains("BASELINE_SAMPLES["));
        assertTrue(summary.contains("HR_SAMPLE_AGE="));
        assertTrue(summary.contains("HRV_SAMPLE_AGE="));
        assertTrue(summary.contains("MOVEMENT_WINDOW_COVERAGE="));
        assertTrue(summary.contains("WAKE_SCORE=0"));
        assertTrue(summary.contains("EVIDENCE_GROUPS=0"));
        assertTrue(summary.contains("CANDIDATE=false"));
        assertTrue(summary.contains("CLEARLY_AWAKE=false"));
        assertTrue(summary.contains("DECISION=CONTINUE"));
        String telemetry = decision.telemetry();
        assertTrue(telemetry.contains("WAKE_SCORE="));
        assertTrue(telemetry.contains("EVIDENCE_GROUPS="));
        assertTrue(telemetry.contains("CANDIDATE_ACTIVE="));
        assertTrue(telemetry.contains("CANDIDATE_AGE="));
        assertTrue(telemetry.contains("CLEARLY_AWAKE="));
        assertTrue(telemetry.contains("CLEARLY_AWAKE_REASON="));
        assertTrue(telemetry.contains("ACTIVE_MOVEMENT_BUCKETS="));
        assertTrue(telemetry.contains("MOVEMENT_SPAN="));
        assertTrue(telemetry.contains("STEPS="));
        assertTrue(telemetry.contains("FRESH_USER_ACTIVITY_TRANSITION="));
    }

    @Test public void baselineIsRequiredBeforeEarlyWake() {
        SmartWakeDetector detector = new SmartWakeDetector(0);
        addStrongBaselineRelativeChange(detector, 20_000);
        SmartWakeDetector.Decision decision = detector.evaluate(90_000);
        assertFalse(decision.baselineReady);
        assertFalse(decision.shouldWake);
    }

    @Test public void shortHeartRateSpikeDoesNotRaiseTrimmedBaseline() {
        SmartWakeDetector detector = preparedDetector();
        detector.addHeartRate(125, 300_000); // A brief check-the-watch / arousal spike.
        addGentleWakeChange(detector, 610_000, false);
        SmartWakeDetector.Decision decision = detector.evaluate(675_000);
        assertEquals(60.53, decision.heartRateBaseline, .02);
        assertEquals(.50, decision.hrvBaseline, .02);
    }

    @Test public void shortStrongMotionDoesNotRaiseMedianMotionBaseline() {
        SmartWakeDetector detector = preparedDetector();
        for (int i = 0; i < 20; i++) { // One short 30-second turn-over bucket out of twenty.
            long at = 300_000L + i * 1_000L;
            detector.addAccelerometerMotion(2.5, at);
            detector.addGyroscopeMotion(2.5, at);
        }
        SmartWakeDetector.Decision decision = detector.evaluate(675_000);
        assertEquals(0, decision.accelerometerBaseline.energyPerSecond, .0001);
        assertEquals(0, decision.gyroscopeBaseline.energyPerSecond, .0001);
    }

    private static SmartWakeDetector preparedDetector() {
        SmartWakeDetector detector = new SmartWakeDetector(0);
        // Ten minutes of quiet personal baseline: HR around 60 and no motion above noise thresholds.
        for (int minute = 0; minute < 10; minute++) {
            long at = minute * 60_000L + 5_000L;
            detector.addHeartRate(60 + (minute % 2), at);
            detector.addHeartRate(61 - (minute % 2), at + 10_000L);
            detector.addAccelerometerMotion(.08, at);
            detector.addGyroscopeMotion(.10, at);
        }
        return detector;
    }

    private static void addGentleWakeChange(SmartWakeDetector detector, long start, boolean includeMotion) {
        detector.addHeartRate(64, start);
        detector.addHeartRate(65, start + 20_000L);
        detector.addHeartRate(66, start + 40_000L);
        if (includeMotion) {
            for (int i = 0; i < 4; i++) {
                long at = start + 5_000L + i * 4_000L;
                detector.addAccelerometerMotion(1.25, at);
            }
        }
    }

    private static void addStrongBaselineRelativeChange(SmartWakeDetector detector, long start) {
        detector.addHeartRate(66, start);
        detector.addHeartRate(69, start + 20_000L);
        detector.addHeartRate(72, start + 40_000L);
        for (int i = 0; i < 7; i++) {
            long at = start + 4_000L + i * 5_000L;
            detector.addAccelerometerMotion(1.4, at);
            detector.addGyroscopeMotion(1.8, at);
        }
    }

    private static void addActiveAccelBucket(SmartWakeDetector detector, long start) {
        for (int i = 0; i < 4; i++) detector.addAccelerometerMotion(1.2, start + i * 1_000L);
    }

    private static void addStrongAccelBucket(SmartWakeDetector detector, long start) {
        for (int i = 0; i < 10; i++) detector.addAccelerometerMotion(1.4, start + i * 500L);
    }
}
