package com.woodpeckerbros.watchreminder.smartalarm;

import static org.junit.Assert.assertEquals;

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
}
