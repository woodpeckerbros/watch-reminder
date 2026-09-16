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
        assertEquals(15, decision.score); // HR delta/slope remain one cardiovascular evidence group.
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
        assertEquals("CONTINUE_CANDIDATE_DECAYED", weakened.continueReason);
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
        assertEquals("CONTINUE_CANDIDATE_DECAYED", fading.continueReason);
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
        assertEquals("TEMPORAL_CARDIO_AND_MOVEMENT", confirmation.candidateConfirmationSource);
        assertEquals("WAKE_LIGHT_SLEEP_OPPORTUNITY", confirmation.wakeReason);
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
        assertEquals("CONTINUE_INSUFFICIENT_WAKEABILITY", waiting.continueReason);
        assertFalse(waiting.shouldWake);
    }

    @Test public void freshLowHeartRateCannotConfirmOldHighHeartRateWithFreshMovement() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000L);
        addStrongHeartRateCandidateWithAccel(detector, 610_000L);
        assertTrue(detector.evaluate(675_000L).candidateActive);

        detector.addHeartRate(60, 680_000L);
        addActiveAccelBucket(detector, 685_000L);
        SmartWakeDetector.Decision decision = detector.evaluate(705_000L);

        assertFalse(decision.candidateConfirmed);
        assertFalse(decision.shouldWake);
    }

    @Test public void candidateExpiresWithoutAffectingLaterEvaluations() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        addGentleWakeChange(detector, 610_000, true);
        assertTrue(detector.evaluate(675_000).candidateActive);

        SmartWakeDetector.Decision expired = detector.evaluate(826_000);
        assertFalse(expired.candidateActive);
        assertFalse(expired.candidateConfirmed);
        assertEquals("EXPIRED", expired.candidateConfirmationStatus);
        assertEquals("CONTINUE_CANDIDATE_DECAYED", expired.continueReason);
        assertFalse(expired.shouldWake);
    }

    @Test public void veryHighMultiSignalChangeStillNeedsTemporalConfirmation() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        addStrongBaselineRelativeChange(detector, 610_000);
        SmartWakeDetector.Decision decision = detector.evaluate(675_000);
        assertTrue(decision.immediateCandidate);
        assertTrue(decision.candidateActive);
        assertFalse(decision.shouldWake);
    }

    @Test public void accelerometerAndGyroscopeAreOneMovementEvidenceGroup() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        addStrongBaselineRelativeChange(detector, 610_000);
        SmartWakeDetector.Decision decision = detector.evaluate(675_000);
        assertEquals(2, decision.evidenceGroups); // cardiovascular + movement, not three groups.
        assertTrue(decision.immediateCandidate); // Strong sample, but still only candidate context.
        assertFalse(decision.shouldWake);
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
        assertEquals("WAKE_ALREADY_AWAKE", decision.wakeReason);
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
        assertEquals("WAKE_CLEARLY_AWAKE", awake.wakeReason);
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
        assertEquals("WAKE_ALREADY_AWAKE", awake.wakeReason);
        assertTrue(awake.shouldWake);
    }

    @Test public void oneBriefSystemNonAsleepSampleDoesNotWakeBeforeItPersists() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000);
        detector.setUserActivity(SmartWakeDetector.UserActivity.PASSIVE, 610_000);

        SmartWakeDetector.Decision pending = detector.evaluate(625_000);
        assertFalse(pending.systemAwakePersistent);
        assertFalse(pending.shouldWake);
        assertEquals("CONTINUE_ISOLATED_EVENT", pending.continueReason);

        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 626_000);
        SmartWakeDetector.Decision returnedToSleep = detector.evaluate(660_000);
        assertFalse(returnedToSleep.systemAwakePersistent);
        assertFalse(returnedToSleep.shouldWake);
        assertEquals("CONTINUE_INSUFFICIENT_WAKEABILITY", returnedToSleep.continueReason);
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
        assertEquals("WAKE_ALREADY_AWAKE", confirmed.wakeReason);
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
        assertEquals("WAKE_ALREADY_AWAKE", validatedObservation.wakeReason);
    }

    @Test public void twoRecentPersistedCallbacksPreserveValidatedAwakeStateAcrossStartup() {
        SmartWakeDetector detector = new SmartWakeDetector(675_000L);
        detector.seedValidatedUserActivity(SmartWakeDetector.UserActivity.PASSIVE,
                610_000L, 650_000L);

        SmartWakeDetector.Decision decision = detector.evaluate(675_000L);
        assertEquals(2, decision.systemNonAsleepObservations);
        assertTrue(decision.systemAwakePersistent);
        assertEquals("WAKE_ALREADY_AWAKE", decision.wakeReason);
        assertTrue(decision.shouldWake);
    }

    @Test public void stalePersistedCallbackPairDoesNotWakeAStartedMonitor() {
        SmartWakeDetector detector = new SmartWakeDetector(2_500_000L);
        detector.seedValidatedUserActivity(SmartWakeDetector.UserActivity.PASSIVE,
                100_000L, 130_000L);

        SmartWakeDetector.Decision decision = detector.evaluate(2_500_000L);
        assertEquals(0, decision.systemNonAsleepObservations);
        assertFalse(decision.systemAwakePersistent);
        assertFalse(decision.shouldWake);
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
        assertTrue(summary.contains("timestamp=675000 → system_state=UNKNOWN"));
        assertTrue(summary.contains("WAKE_SCORE=0 → groups=0 → fresh_groups=0"));
        assertTrue(summary.contains("wakeability_state=STABLE_OR_LOW_WAKEABILITY"));
        assertTrue(summary.contains("decision=CONTINUE"));
        String telemetry = decision.telemetry();
        assertTrue(telemetry.contains("WAKE_SCORE="));
        assertTrue(telemetry.contains("EVIDENCE_GROUPS="));
        assertTrue(telemetry.contains("CANDIDATE_ACTIVE="));
        assertTrue(telemetry.contains("CANDIDATE_AGE="));
        assertTrue(telemetry.contains("CANDIDATE_ORIGIN="));
        assertTrue(telemetry.contains("CANDIDATE_CONFIRMATION_STATUS="));
        assertTrue(telemetry.contains("CANDIDATE_CONFIRMATION_SOURCE="));
        assertTrue(telemetry.contains("CLEARLY_AWAKE="));
        assertTrue(telemetry.contains("CLEARLY_AWAKE_REASON="));
        assertTrue(telemetry.contains("ACTIVE_MOVEMENT_BUCKETS="));
        assertTrue(telemetry.contains("MOVEMENT_SPAN="));
        assertTrue(telemetry.contains("STEPS_RECENT="));
        assertTrue(telemetry.contains("ASLEEP_TO_NON_ASLEEP="));
        assertTrue(telemetry.contains("SYSTEM_AWAKE_PERSISTENT="));
        assertTrue(telemetry.contains("SYSTEM_AWAKE_FRESH="));
        assertTrue(telemetry.contains("HR_RECENT_SLOPE="));
        assertTrue(telemetry.contains("HRV=NO_DATA"));
        assertTrue(telemetry.contains("ACCEL_SAMPLES="));
        assertTrue(telemetry.contains("GYRO_SAMPLES="));
        assertTrue(telemetry.contains("MICRO_MOVEMENT_COUNT="));
        assertTrue(telemetry.contains("MOVEMENT_CLUSTER_COUNT="));
        assertTrue(telemetry.contains("WAKEABILITY_STATE="));
        assertTrue(telemetry.contains("WAKEABILITY_TREND="));
        assertTrue(telemetry.contains("FRESH_EVIDENCE_GROUPS="));
        assertTrue(telemetry.contains("DECISION_REASON="));
    }

    @Test public void stableQuietSleepDoesNotWake() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000L);
        detector.addHeartRate(60, 620_000L);
        detector.addHeartRate(61, 650_000L);
        detector.addAccelerometerMotion(.08, 620_000L);
        detector.addGyroscopeMotion(.10, 620_000L);
        detector.addAccelerometerMotion(.08, 650_000L);
        detector.addGyroscopeMotion(.10, 650_000L);

        SmartWakeDetector.Decision decision = detector.evaluate(675_000L);

        assertEquals(SmartWakeDetector.WakeabilityState.STABLE_OR_LOW_WAKEABILITY,
                decision.wakeabilityState);
        assertEquals("CONTINUE_STABLE_SLEEP", decision.continueReason);
        assertFalse(decision.shouldWake);
    }

    @Test public void oneHeartRateSpikeIsIsolatedNoise() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000L);
        detector.addHeartRate(105, 650_000L);

        SmartWakeDetector.Decision decision = detector.evaluate(675_000L);

        assertFalse(decision.candidateActive);
        assertFalse(decision.shouldWake);
        assertEquals(0, decision.evidenceGroups);
    }

    @Test public void moderateMultiGroupEventThenSilenceDecaysWithoutWake() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000L);
        addGentleWakeChange(detector, 610_000L, true);
        assertTrue(detector.evaluate(675_000L).candidateActive);

        SmartWakeDetector.Decision fading = detector.evaluate(751_000L);
        SmartWakeDetector.Decision silent = detector.evaluate(781_000L);

        assertFalse(fading.shouldWake);
        assertFalse(silent.shouldWake);
        assertFalse(silent.candidateActive);
        assertEquals("CONTINUE_CANDIDATE_DECAYED", silent.continueReason);
    }

    @Test public void systemAsleepAllowsTemporalWakeOpportunityBeforeClearlyAwake() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000L);
        addGentleWakeChange(detector, 610_000L, true);
        SmartWakeDetector.Decision rising = detector.evaluate(675_000L);
        assertEquals(SmartWakeDetector.WakeabilityState.WAKEABILITY_RISING,
                rising.wakeabilityState);
        assertFalse(rising.shouldWake);

        addGentleWakeChange(detector, 680_000L, true);
        SmartWakeDetector.Decision opportunity = detector.evaluate(745_000L);

        assertEquals(SmartWakeDetector.UserActivity.ASLEEP, opportunity.userActivity);
        assertFalse(opportunity.clearlyAwake);
        assertEquals(SmartWakeDetector.WakeabilityState.WAKE_OPPORTUNITY,
                opportunity.wakeabilityState);
        assertEquals("WAKE_LIGHT_SLEEP_OPPORTUNITY", opportunity.wakeReason);
        assertTrue(opportunity.shouldWake);
    }

    @Test public void naturalTransitionTriggersBeforeLaterPassiveCallback() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000L);
        addGentleWakeChange(detector, 610_000L, true);
        detector.evaluate(675_000L);
        addGentleWakeChange(detector, 680_000L, true);

        SmartWakeDetector.Decision beforePassive = detector.evaluate(745_000L);
        detector.setUserActivity(SmartWakeDetector.UserActivity.PASSIVE, 760_000L);

        assertTrue(beforePassive.shouldWake);
        assertEquals(SmartWakeDetector.UserActivity.ASLEEP, beforePassive.userActivity);
        assertEquals("WAKE_LIGHT_SLEEP_OPPORTUNITY", beforePassive.wakeReason);
    }

    @Test public void awakeLyingInBedUsesLateFallbackWithoutSteps() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 590_000L);
        addGentleWakeChange(detector, 610_000L, false);
        addStrongAccelBucket(detector, 600_000L);
        addStrongAccelBucket(detector, 615_000L);
        addStrongAccelBucket(detector, 630_000L);
        addStrongAccelBucket(detector, 645_000L);

        SmartWakeDetector.Decision decision = detector.evaluate(675_000L);

        assertEquals(0, decision.steps);
        assertTrue(decision.clearlyAwake);
        assertEquals("WAKE_CLEARLY_AWAKE", decision.wakeReason);
        assertTrue(decision.shouldWake);
    }

    @Test public void bathroomTripThenFreshAsleepAndQuietDoesNotReuseStaleAwakeEvidence() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.PASSIVE, 610_000L);
        detector.setUserActivity(SmartWakeDetector.UserActivity.PASSIVE, 620_000L);
        for (int i = 0; i < 5; i++) detector.addStep(625_000L + i * 5_000L);
        addActiveAccelBucket(detector, 630_000L);
        addActiveAccelBucket(detector, 650_000L);
        assertTrue(detector.evaluate(675_000L).shouldWake);

        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 680_000L);
        SmartWakeDetector.Decision returnedToSleep = detector.evaluate(800_000L);

        assertFalse(returnedToSleep.systemAwakePersistent);
        assertFalse(returnedToSleep.clearlyAwake);
        assertFalse(returnedToSleep.shouldWake);
    }

    @Test public void missingMovementSensorDataIsNotMeasuredQuiet() {
        SmartWakeDetector detector = new SmartWakeDetector(0L);
        for (int minute = 0; minute < 12; minute++) {
            detector.addHeartRate(60, minute * 60_000L + 5_000L);
        }

        SmartWakeDetector.Decision decision = detector.evaluate(800_000L);

        assertEquals("NO_DATA", decision.movementDataStatus);
        assertFalse(decision.baselineReady);
        assertFalse(decision.shouldWake);
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

    @Test public void rollingBaselineFreezesDuringGradualHeartRateRise() {
        SmartWakeDetector detector = lowHeartRateBaselineDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000L);

        // The first two elevated samples create interesting cardiovascular evidence and freeze
        // the pre-transition sleep baseline at 48.0 BPM.
        detector.addHeartRate(51, 610_000L);
        detector.addHeartRate(53, 645_000L);
        SmartWakeDetector.Decision firstRise = detector.evaluate(675_000L);
        assertEquals(48.0, firstRise.heartRateBaseline, .001);

        // Continue the 3–5 minute-like rise. Without freezing, 51/53/56/59 would enter the
        // rolling baseline after the 75-second exclusion and reduce the final delta.
        detector.addHeartRate(56, 680_000L);
        detector.addHeartRate(59, 715_000L);
        detector.evaluate(745_000L);
        detector.addHeartRate(61, 750_000L);
        detector.addHeartRate(63, 785_000L);
        SmartWakeDetector.Decision continuingRise = detector.evaluate(815_000L);

        assertEquals("READY_FROZEN", continuingRise.baselineStatus);
        assertEquals(48.0, continuingRise.heartRateBaseline, .001);
        assertEquals(14.0, continuingRise.hrAboveBaseline, .001);
    }

    @Test public void movementThenCurrentHeartRateCanFormTemporalMultiGroupEvidence() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000L);
        addActiveAccelBucket(detector, 610_000L); // T+00s micro-movement cluster.
        detector.addHeartRate(64, 645_000L); // T+35s cardiovascular evidence begins.
        detector.addHeartRate(65, 665_000L);

        SmartWakeDetector.Decision decision = detector.evaluate(675_000L);

        assertEquals(2, decision.evidenceGroups);
        assertTrue(decision.candidateActive);
        assertFalse(decision.shouldWake); // One composite evaluation remains candidate-only.
    }

    @Test public void staleMovementCannotCombineWithLaterHeartRate() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000L);
        addActiveAccelBucket(detector, 610_000L);
        detector.addHeartRate(64, 690_000L);
        detector.addHeartRate(65, 710_000L);

        SmartWakeDetector.Decision decision = detector.evaluate(735_000L);

        assertEquals(1, decision.evidenceGroups);
        assertFalse(decision.candidateActive);
        assertFalse(decision.shouldWake);
    }

    @Test public void heartRateThenCurrentMovementCanFormTemporalMultiGroupEvidence() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000L);
        detector.addHeartRate(64, 610_000L); // T+00s cardiovascular evidence begins.
        detector.addHeartRate(65, 630_000L);
        addActiveAccelBucket(detector, 650_000L); // T+40s micro-movement cluster.

        SmartWakeDetector.Decision decision = detector.evaluate(675_000L);

        assertEquals(2, decision.evidenceGroups);
        assertTrue(decision.candidateActive);
        assertFalse(decision.shouldWake);
    }

    @Test public void temporallyConfirmedTrendDoesNotNeedPostCandidateThirdEvent() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000L);

        // Three interesting frames over 130 seconds: first movement, then HR, then fresh HR
        // plus movement. The final frame creates candidate context, but the preceding fresh
        // history already supplies the independent temporal confirmation.
        addActiveAccelBucket(detector, 610_000L);
        assertFalse(detector.evaluate(675_000L).shouldWake);
        detector.addHeartRate(64, 690_000L);
        detector.addHeartRate(65, 710_000L);
        assertFalse(detector.evaluate(735_000L).shouldWake);
        addActiveAccelBucket(detector, 750_000L);
        detector.addHeartRate(64, 760_000L);
        detector.addHeartRate(65, 780_000L);

        SmartWakeDetector.Decision opportunity = detector.evaluate(805_000L);

        assertTrue(opportunity.candidateActive);
        assertFalse(opportunity.candidateConfirmed);
        assertEquals(SmartWakeDetector.UserActivity.ASLEEP, opportunity.userActivity);
        assertFalse(opportunity.clearlyAwake);
        assertEquals("WAKE_LIGHT_SLEEP_OPPORTUNITY", opportunity.wakeReason);
        assertTrue(opportunity.shouldWake);
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

    private static SmartWakeDetector lowHeartRateBaselineDetector() {
        SmartWakeDetector detector = new SmartWakeDetector(0L);
        for (int minute = 0; minute < 10; minute++) {
            long at = minute * 60_000L + 5_000L;
            detector.addHeartRate(48, at);
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
