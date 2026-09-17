package com.woodpeckerbros.watchreminder.smartalarm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SmartWakeRuntimePolicyTest {
    @Test public void monitoringUsesConfiguredFortyFiveMinuteLeadIn() {
        long earliestWake = 8_000_000L;
        assertEquals(earliestWake - 45 * 60_000L,
                earliestWake - SmartWakeSamplingProfile.monitorLeadTime(0L));
    }

    @Test public void serviceRestartRecognizesSameSessionInsteadOfCreatingSecondOne() {
        assertTrue(SmartWakeRuntimePolicy.isSameSession(3, 20_000L, 3, 20_000L));
        assertFalse(SmartWakeRuntimePolicy.isSameSession(3, 20_000L, 3, 20_001L));
    }

    @Test public void duplicateStartDoesNotDuplicateMotionListeners() {
        assertFalse(SmartWakeRuntimePolicy.shouldRegisterMotion(true, false, false));
        assertFalse(SmartWakeRuntimePolicy.shouldRegisterMotion(true, true, true));
    }

    @Test public void phaseChangeReplacesRatherThanDuplicatesMotionListeners() {
        assertTrue(SmartWakeRuntimePolicy.shouldRegisterMotion(true, false, true));
    }

    @Test public void initialStartRegistersMotionListenersOnce() {
        assertTrue(SmartWakeRuntimePolicy.shouldRegisterMotion(false, false, false));
    }

    @Test public void monitoringCannotContinuePastDeadlinePlusGrace() {
        long deadline = 5_000_000L;
        assertFalse(SmartWakeRuntimePolicy.isPastHardStop(
                SmartWakeRuntimePolicy.hardStopAt(deadline) - 1L, deadline));
        assertTrue(SmartWakeRuntimePolicy.isPastHardStop(
                SmartWakeRuntimePolicy.hardStopAt(deadline), deadline));
    }

    @Test public void hardStopGraceIsSmallAndBounded() {
        assertEquals(60_000L, SmartWakeRuntimePolicy.HARD_STOP_GRACE_MS);
    }

    @Test public void resourcesReleaseImmediatelyWhenLastSessionEnds() {
        assertTrue(SmartWakeRuntimePolicy.shouldReleaseResources(0));
        assertFalse(SmartWakeRuntimePolicy.shouldReleaseResources(1));
    }
}
