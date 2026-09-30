package com.woodpeckerbros.watchreminder.smartalarm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SmartWakeEvaluationCadenceTest {
    private static final SmartWakeDetector.WakeabilityState STABLE =
            SmartWakeDetector.WakeabilityState.STABLE_OR_LOW_WAKEABILITY;

    @Test public void quietSleepKeepsThirtySecondEvaluation() {
        assertEquals(SmartWakeEvaluationCadence.Mode.NORMAL,
                SmartWakeEvaluationCadence.forSignals(false, 0, 0, STABLE));
        assertEquals(30_000L, SmartWakeEvaluationCadence.Mode.NORMAL.intervalMs);
    }

    @Test public void interestingEvidenceSwitchesToTenSeconds() {
        assertEquals(SmartWakeEvaluationCadence.Mode.WATCHING,
                SmartWakeEvaluationCadence.forSignals(false, 18, 2,
                        SmartWakeDetector.WakeabilityState.NORMAL_SLEEP));
        assertEquals(10_000L, SmartWakeEvaluationCadence.Mode.WATCHING.intervalMs);
    }

    @Test public void activeCandidateTakesFiveSecondPrecedenceEvenAtEighteenOverTwo() {
        assertEquals(SmartWakeEvaluationCadence.Mode.CANDIDATE,
                SmartWakeEvaluationCadence.forSignals(true, 18, 2,
                        SmartWakeDetector.WakeabilityState.WAKEABILITY_RISING));
        assertEquals(5_000L, SmartWakeEvaluationCadence.Mode.CANDIDATE.intervalMs);
    }

    @Test public void candidateDecayReturnsViaWatchingThenNormalAsEvidenceAgesOut() {
        assertEquals(SmartWakeEvaluationCadence.Mode.WATCHING,
                SmartWakeEvaluationCadence.forSignals(false, 15, 1,
                        SmartWakeDetector.WakeabilityState.NORMAL_SLEEP));
        assertEquals(SmartWakeEvaluationCadence.Mode.NORMAL,
                SmartWakeEvaluationCadence.forSignals(false, 0, 0, STABLE));
    }

    @Test public void refaelEighteenTwentyFourFortyTwoUsesFastCandidateChecksWithoutChangingWakeRules() {
        SmartWakeDetector.WakeabilityState rising =
                SmartWakeDetector.WakeabilityState.WAKEABILITY_RISING;
        assertEquals(5_000L, SmartWakeEvaluationCadence.forSignals(true, 18, 2, rising).intervalMs);
        assertEquals(5_000L, SmartWakeEvaluationCadence.forSignals(true, 24, 2, rising).intervalMs);
        assertEquals(5_000L, SmartWakeEvaluationCadence.forSignals(true, 42, 2, rising).intervalMs);
        // If no candidate had yet formed, the same early 18/2 signal would use WATCHING.
        assertEquals(10_000L, SmartWakeEvaluationCadence.forSignals(false, 18, 2, rising).intervalMs);
    }

    @Test public void stoppedSessionHasNoLoopAndDuplicateSessionHasOneIdentity() {
        assertFalse(SmartWakeEvaluationCadence.shouldSchedule(0));
        assertTrue(SmartWakeEvaluationCadence.shouldSchedule(1));
        assertTrue(SmartWakeRuntimePolicy.isSameSession(3, 123_000L, 3, 123_000L));
    }

    @Test public void overrunUsesBoundedNonZeroDelayWithoutChangingCandidateCadence() {
        assertEquals(5_000L, SmartWakeEvaluationCadence.Mode.CANDIDATE.intervalMs);
        assertEquals(SmartWakeEvaluationCadence.MIN_SAFE_DELAY_MS,
                SmartWakeEvaluationCadence.nextDelayMs(5_000L, 6_200L));
        assertEquals(1_000L, SmartWakeEvaluationCadence.MIN_SAFE_DELAY_MS);
        assertEquals(3_500L, SmartWakeEvaluationCadence.nextDelayMs(5_000L, 1_500L));
    }
}
