package com.woodpeckerbros.watchreminder.smartwake;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SmartWakeDetectorTest {
    @Test public void isolatedMovementDoesNotWake() {
        SmartWakeDetector detector = new SmartWakeDetector(0);
        detector.setAsleep(true); detector.addMotion(1.3);
        assertFalse(detector.evaluate(180_000).shouldWake);
    }

    @Test public void sustainedMovementAndHeartRateChangeWakeAfterTwoWindows() {
        SmartWakeDetector detector = new SmartWakeDetector(0); detector.setAsleep(true);
        addCandidateWindow(detector); assertFalse(detector.evaluate(180_000).shouldWake);
        addCandidateWindow(detector); assertTrue(detector.evaluate(210_000).shouldWake);
    }

    @Test public void asleepToAwakeTransitionWakesImmediatelyAfterWarmup() {
        SmartWakeDetector detector = new SmartWakeDetector(0); detector.setAsleep(true); detector.setAsleep(false);
        assertTrue(detector.evaluate(180_000).shouldWake);
    }

    private static void addCandidateWindow(SmartWakeDetector detector) {
        detector.addHeartRate(55); detector.addHeartRate(56); detector.addHeartRate(57);
        detector.addHeartRate(59); detector.addHeartRate(62);
        detector.addMotion(1.3); detector.addMotion(1.4); detector.addMotion(0.9); detector.addMotion(0.8);
    }
}
