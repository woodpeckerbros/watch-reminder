package com.woodpeckerbros.watchreminder.smartalarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SmartAlarmAlertActivityTest {
    @Test public void onlyAnInteractionNearTimeoutExtendsTheTaskScreen() {
        long timeout = 30_000L;
        assertEquals(0L, SmartAlarmAlertActivity.interactionGraceDelayMs(timeout, 10_000L));
        assertEquals(4_000L, SmartAlarmAlertActivity.interactionGraceDelayMs(timeout, timeout - 1_000L));
    }

    @Test public void latestInteractionResetsInsteadOfAccumulatingGrace() {
        long timeout = 30_000L;
        assertEquals(2_000L, SmartAlarmAlertActivity.interactionGraceDelayMs(timeout, timeout - 3_000L));
        assertEquals(4_000L, SmartAlarmAlertActivity.interactionGraceDelayMs(timeout, timeout - 1_000L));
    }

    @Test public void finalDeadlineSupersedesActiveEarlySnoozeBeforePostingItsOwnSurface() {
        assertTrue(SmartAlarmAlertActivity.shouldSupersedeForFinalDeadline(
                1, 7_09_45_000L, 1, 7_10_00_000L, true));
        assertTrue(SmartAlarmAlertActivity.shouldSupersedeForFinalDeadline(
                1, 7_10_00_000L, 1, 7_10_00_000L, true));
        assertFalse(SmartAlarmAlertActivity.shouldSupersedeForFinalDeadline(
                1, 7_09_45_000L, 2, 7_10_00_000L, true));
    }
}
