package com.woodpeckerbros.watchreminder.smartalarm;

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

    @Test public void candidateMemoryDoesNotConfirmWhenTheOriginalEvidenceFades() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        addGentleWakeChange(detector, 610_000, true);
        SmartWakeDetector.Decision candidate = detector.evaluate(675_000);
        assertEquals(29, candidate.score);
        assertTrue(candidate.candidate);
        assertTrue(candidate.candidateActive);
        assertEquals("CREATED", candidate.candidateConfirmationStatus);
        assertFalse(candidate.shouldWake);

        // The candidate's movement has faded. Old HR alone must not be reused as confirmation.
        SmartWakeDetector.Decision weakened = detector.evaluate(705_000);
        assertEquals(15, weakened.score);
        assertFalse(weakened.candidateActive);
        assertEquals(30_000L, weakened.candidateAgeMs);
        assertEquals("EVIDENCE_WEAKENED", weakened.candidateConfirmationStatus);
        assertEquals("CANDIDATE_EVIDENCE_WEAKENED", weakened.continueReason);
        assertFalse(weakened.candidateConfirmed);
        assertFalse(weakened.shouldWake);
    }

    @Test public void realWorldCandidateThenFadingHeartRateOnlyDoesNotWake() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        addStrongHeartRateCandidateWithAccel(detector, 610_000);

        SmartWakeDetector.Decision candidate = detector.evaluate(675_000);
        assertEquals(36, candidate.score);
        assertEquals(2, candidate.evidenceGroups);
        assertTrue(candidate.candidateActive);
        assertFalse(candidate.shouldWake);

        // The original accelerometer cluster is now outside the current scoring window. The
        // remaining elevated HR (22 points, one group) is not fresh composite confirmation.
        SmartWakeDetector.Decision fading = detector.evaluate(705_000);
        assertEquals(22, fading.score);
        assertEquals(1, fading.evidenceGroups);
        assertFalse(fading.candidateActive);
        assertEquals("EVIDENCE_WEAKENED", fading.candidateConfirmationStatus);
        assertEquals("CANDIDATE_EVIDENCE_WEAKENED", fading.continueReason);
        assertFalse(fading.shouldWake);
    }

    @Test public void candidateConfirmsOnlyWithFreshSecondMultiGroupSample() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        addGentleWakeChange(detector, 610_000, true);
        assertTrue(detector.evaluate(675_000).candidateActive);

        addGentleWakeChange(detector, 680_000, true);
        SmartWakeDetector.Decision confirmation = detector.evaluate(745_000);
        assertTrue(confirmation.candidateActive);
        assertTrue(confirmation.candidateConfirmed);
        assertEquals("CONFIRMED_FRESH_EVIDENCE", confirmation.candidateConfirmationStatus);
        assertEquals("SCORE_STRENGTH_MAINTAINED", confirmation.candidateConfirmationSource);
        assertEquals("CANDIDATE_CONFIRMED_FRESH_EVIDENCE", confirmation.wakeReason);
        assertTrue(confirmation.shouldWake);
    }

    @Test public void candidateDoesNotDoubleCountOriginalCardioWhenOnlyFreshMovementArrives() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        addGentleWakeChange(detector, 610_000, true);
        assertTrue(detector.evaluate(675_000).candidateActive);

        addActiveAccelBucket(detector, 690_000);
        SmartWakeDetector.Decision waiting = detector.evaluate(705_000);
        assertTrue(waiting.candidateActive);
        assertFalse(waiting.candidateConfirmed);
        assertEquals("WAITING_FOR_FRESH_CONFIRMATION", waiting.candidateConfirmationStatus);
        assertEquals("CANDIDATE_WAITING_FOR_FRESH_CONFIRMATION", waiting.continueReason);
        assertFalse(waiting.shouldWake);
    }

    @Test public void candidateExpiresWithoutAffectingLaterEvaluations() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        addGentleWakeChange(detector, 610_000, true);
        assertTrue(detector.evaluate(675_000).candidateActive);

        SmartWakeDetector.Decision expired = detector.evaluate(766_000);
        assertFalse(expired.candidateActive);
        assertFalse(expired.candidateConfirmed);
        assertEquals("EXPIRED", expired.candidateConfirmationStatus);
        assertEquals("CANDIDATE_EXPIRED", expired.continueReason);
        assertFalse(expired.shouldWake);
    }

    @Test public void veryHighMultiSignalChangeWakesOnOneEvaluation() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        detector.setUserActivity(SmartWakeDetector.UserActivity.PASSIVE, 650_000);
        addStrongBaselineRelativeChange(detector, 610_000);
        SmartWakeDetector.Decision decision = detector.evaluate(675_000);
        assertTrue(decision.immediateCandidate);
        assertTrue(decision.shouldWake);
        assertEquals("SMART_SCORE_IMMEDIATE", decision.wakeReason);
    }

    @Test public void accelerometerAndGyroscopeAreOneMovementEvidenceGroup() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        addStrongBaselineRelativeChange(detector, 610_000);
        SmartWakeDetector.Decision decision = detector.evaluate(675_000);
        assertEquals(2, decision.evidenceGroups); // cardiovascular + movement, not three groups.
        assertTrue(decision.immediateCandidate); // Immediate now needs these exact two groups.
    }

    @Test public void persistentSystemNonAsleepWakesEvenWithoutSmartScore() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.PASSIVE, 610_000);
        detector.setUserActivity(SmartWakeDetector.UserActivity.PASSIVE, 640_000);
        detector.addStep(620_000);
        SmartWakeDetector.Decision decision = detector.evaluate(675_000);
        assertEquals(0, decision.score);
        assertTrue(decision.lateAwakeConfirmation);
        assertTrue(decision.systemAwakePersistent);
        assertEquals("SYSTEM_AWAKE_PERSISTENT", decision.wakeReason);
        assertTrue(decision.shouldWake);
    }

    @Test public void candidateDoesNotBlockClearlyAwakeRoute() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        addGentleWakeChange(detector, 610_000, true);
        assertTrue(detector.evaluate(675_000).candidateActive);

        for (int i = 0; i < 3; i++) detector.addStep(680_000L + i * 5_000L);
        addActiveAccelBucket(detector, 680_000L);
        addActiveAccelBucket(detector, 700_000L);
        SmartWakeDetector.Decision awake = detector.evaluate(715_000);
        assertTrue(awake.clearlyAwake);
        assertEquals("CLEARLY_AWAKE", awake.wakeReason);
        assertTrue(awake.shouldWake);
    }

    @Test public void freshPersistentSystemAwakeStillWakesWithCandidatePresent() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        addGentleWakeChange(detector, 610_000, true);
        assertTrue(detector.evaluate(675_000).candidateActive);

        detector.setUserActivity(SmartWakeDetector.UserActivity.PASSIVE, 680_000);
        detector.setUserActivity(SmartWakeDetector.UserActivity.PASSIVE, 700_000);
        SmartWakeDetector.Decision awake = detector.evaluate(715_000);
        assertTrue(awake.freshSystemNonAsleep);
        assertTrue(awake.systemAwakePersistent);
        assertEquals("SYSTEM_AWAKE_PERSISTENT", awake.wakeReason);
        assertTrue(awake.shouldWake);
    }

    @Test public void oneBriefSystemNonAsleepSampleDoesNotWakeBeforeItPersists() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        detector.setUserActivity(SmartWakeDetector.UserActivity.PASSIVE, 610_000);

        SmartWakeDetector.Decision pending = detector.evaluate(625_000);
        assertFalse(pending.systemAwakePersistent);
        assertFalse(pending.shouldWake);
        assertEquals("SYSTEM_AWAKE_NOT_YET_PERSISTENT", pending.continueReason);

        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 626_000);
        SmartWakeDetector.Decision returnedToSleep = detector.evaluate(660_000);
        assertFalse(returnedToSleep.systemAwakePersistent);
        assertFalse(returnedToSleep.shouldWake);
        assertEquals("NO_CANDIDATE", returnedToSleep.continueReason);
    }

    @Test public void secondSystemNonAsleepObservationConfirmsWakeWithoutWaitingForNextInterval() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.PASSIVE, 610_000);
        SmartWakeDetector.Decision pending = detector.evaluate(620_000);
        assertFalse(pending.systemAwakePersistent);

        detector.setUserActivity(SmartWakeDetector.UserActivity.PASSIVE, 625_000);
        SmartWakeDetector.Decision confirmed = detector.evaluate(626_000);
        assertTrue(confirmed.systemAwakePersistent);
        assertEquals(2, confirmed.systemNonAsleepObservations);
        assertEquals("SYSTEM_AWAKE_PERSISTENT", confirmed.wakeReason);
        assertTrue(confirmed.shouldWake);
    }

    @Test public void persistedPassiveContextIsNotCountedAsFreshAwakeObservation() {
        SmartWakeDetector detector = preparedDetector();
        detector.seedUserActivity(SmartWakeDetector.UserActivity.PASSIVE);

        SmartWakeDetector.Decision staleContext = detector.evaluate(675_000);
        assertEquals(0, staleContext.systemNonAsleepObservations);
        assertFalse(staleContext.systemAwakePersistent);
        assertFalse(staleContext.shouldWake);

        detector.setUserActivity(SmartWakeDetector.UserActivity.PASSIVE, 676_000);
        SmartWakeDetector.Decision liveObservation = detector.evaluate(706_000);
        assertEquals(1, liveObservation.systemNonAsleepObservations);
        assertFalse(liveObservation.systemAwakePersistent);
        assertFalse(liveObservation.shouldWake);

        detector.setUserActivity(SmartWakeDetector.UserActivity.PASSIVE, 700_000);
        SmartWakeDetector.Decision validatedObservation = detector.evaluate(706_000);
        assertEquals(2, validatedObservation.systemNonAsleepObservations);
        assertTrue(validatedObservation.systemAwakePersistent);
        assertEquals("SYSTEM_AWAKE_PERSISTENT", validatedObservation.wakeReason);
    }

    @Test public void stalePassiveContextCannotConfirmAnActiveCandidate() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        addGentleWakeChange(detector, 610_000, true);
        assertTrue(detector.evaluate(675_000).candidateActive);

        detector.seedUserActivity(SmartWakeDetector.UserActivity.PASSIVE);
        SmartWakeDetector.Decision stale = detector.evaluate(705_000);
        assertFalse(stale.freshSystemNonAsleep);
        assertFalse(stale.systemAwakePersistent);
        assertFalse(stale.candidateConfirmed);
        assertFalse(stale.shouldWake);
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
        assertEquals("timestamp=675000 → WAKE_SCORE=0 → groups=0 → candidate_active=false → candidate_age_ms=-1"
                + " → candidate_origin_score=-1 → candidate_origin_groups=-1"
                + " → candidate_confirmation_status=NONE → clearly_awake=false"
                + " → system_state=UNKNOWN → system_awake_fresh=false → system_awake_persistent=false"
                + " → decision=CONTINUE → DECISION_REASON=NO_CANDIDATE", summary);
        String telemetry = decision.telemetry();
        assertTrue(telemetry.contains("WAKE_SCORE="));
        assertTrue(telemetry.contains("EVIDENCE_GROUPS="));
        assertTrue(telemetry.contains("CANDIDATE_ACTIVE="));
        assertTrue(telemetry.contains("CANDIDATE_AGE="));
        assertTrue(telemetry.contains("CANDIDATE_ORIGIN_SCORE="));
        assertTrue(telemetry.contains("CANDIDATE_ORIGIN_GROUPS="));
        assertTrue(telemetry.contains("CANDIDATE_CONFIRMATION_STATUS="));
        assertTrue(telemetry.contains("CANDIDATE_CONFIRMATION_SOURCE="));
        assertTrue(telemetry.contains("CLEARLY_AWAKE="));
        assertTrue(telemetry.contains("CLEARLY_AWAKE_REASON="));
        assertTrue(telemetry.contains("ACTIVE_MOVEMENT_BUCKETS="));
        assertTrue(telemetry.contains("MOVEMENT_SPAN="));
        assertTrue(telemetry.contains("STEPS="));
        assertTrue(telemetry.contains("FRESH_USER_ACTIVITY_TRANSITION="));
        assertTrue(telemetry.contains("SYSTEM_AWAKE_PERSISTENT="));
        assertTrue(telemetry.contains("SYSTEM_AWAKE_FRESH="));
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

    private static void addStrongHeartRateCandidateWithAccel(SmartWakeDetector detector, long start) {
        detector.addHeartRate(70, start);
        detector.addHeartRate(70, start + 20_000L);
        detector.addHeartRate(70, start + 40_000L);
        for (int i = 0; i < 4; i++) {
            detector.addAccelerometerMotion(1.25, start + 5_000L + i * 4_000L);
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
