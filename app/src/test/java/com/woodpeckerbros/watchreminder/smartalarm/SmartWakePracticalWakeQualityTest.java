package com.woodpeckerbros.watchreminder.smartalarm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SmartWakePracticalWakeQualityTest {
    @Test public void highNeedsFreshConvergenceStrongCardioAndRenewal() {
        SmartWakePracticalWakeQuality.Result result = quality(1_000L, 2_000L, 2,
                "5S", 7.0, 2.5, window(2, 2, 4, "RISING", 100));
        assertEquals(SmartWakePracticalWakeQuality.Level.HIGH, result.level);
        assertTrue(result.reasons.contains("FRESH_CROSS_MODAL_5S"));
        assertTrue(result.reasons.contains("MULTIPLE_CROSS_MODAL_RENEWALS"));
    }

    @Test public void singleMovementEpisodeWithStrongRisingCardioIsMediumNotLow() {
        // Refael-like: a one-episode movement pattern may still be a developing transition.
        SmartWakePracticalWakeQuality.Result result = quality(1_171L, 20_924L, 1,
                "30S", 6.9, 2.56, window(1, 1, 4, "RISING", 100));
        assertEquals(SmartWakePracticalWakeQuality.Level.MEDIUM, result.level);
        assertTrue(result.reasons.contains("SINGLE_MOVEMENT_EPISODE"));
        assertTrue(result.reasons.contains("STRONG_RISING_CARDIO"));
    }

    @Test public void staleMovementWithWeakCardioOnlyContinuationIsLow() {
        SmartWakePracticalWakeQuality.Result result = quality(900L, 45_000L, 1,
                "NONE", 1.0, .2, window(1, 0, 2, "FLAT", 50));
        assertEquals(SmartWakePracticalWakeQuality.Level.LOW, result.level);
        assertTrue(result.reasons.contains("STALE_MOVEMENT_AT_WAKE"));
        assertTrue(result.reasons.contains("WEAK_CARDIO_RESPONSE"));
    }

    @Test public void missingSourcesReturnsInsufficientData() {
        SmartWakePracticalWakeQuality.Result result = SmartWakePracticalWakeQuality.evaluate(
                -1L, -1L, 0, 0, false, 0, "NO_DATA", 0, 0, null);
        assertEquals(SmartWakePracticalWakeQuality.Level.INSUFFICIENT_DATA, result.level);
    }

    @Test public void qualityResultCannotAlterAProductionDecision() {
        SmartWakeDetector control = preparedDetector();
        SmartWakeDetector observed = preparedDetector();
        SmartWakeDetector.Decision expected = control.evaluate(675_000L);
        SmartWakeDetector.Decision actual = observed.evaluate(675_000L);
        SmartWakePracticalWakeQuality.evaluate(actual, null);
        assertEquals(expected.shouldWake, actual.shouldWake);
        assertEquals(expected.candidateActive, actual.candidateActive);
        assertEquals(expected.score, actual.score);
    }

    private static SmartWakePracticalWakeQuality.Result quality(long hrAge, long movementAge,
                                                                 int recent15, String convergence,
                                                                 double delta, double slope,
                                                                 SmartWakeShadowTelemetry.Window window) {
        return SmartWakePracticalWakeQuality.evaluate(hrAge, movementAge, 0, 0, false,
                recent15, convergence, delta, slope, window);
    }

    private static SmartWakeShadowTelemetry.Window window(int episodes, int crossRenewals,
                                                            int freshHr,
                                                            String trend, int consistency) {
        return new SmartWakeShadowTelemetry.Window(90_000L, episodes, episodes,
                freshHr, freshHr, crossRenewals, 30, 20.0, 3, 0, 3,
                3.0, 7.0, trend, consistency, 0L, 0L,
                SmartWakeShadowTelemetry.State.SHADOW_SINGLE_BURST);
    }

    private static SmartWakeDetector preparedDetector() {
        SmartWakeDetector detector = new SmartWakeDetector(0L);
        for (int minute = 0; minute < 10; minute++) {
            long at = minute * 60_000L + 5_000L;
            detector.addHeartRate(60 + (minute % 2), at);
            detector.addGyroscopeMotion(.10, at);
        }
        return detector;
    }
}
