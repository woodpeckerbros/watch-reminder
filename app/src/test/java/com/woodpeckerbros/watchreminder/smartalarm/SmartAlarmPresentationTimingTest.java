package com.woodpeckerbros.watchreminder.smartalarm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SmartAlarmPresentationTimingTest {
    @Test public void lateTouchKeepsTaskVisibleButFeedbackExpiresAtThirtySeconds() {
        assertEquals(4_000L, SmartAlarmPresentationTiming.graceRemaining(30_000L, 29_000L));
        assertEquals(0, SmartAlarmPresentationTiming.feedbackRemaining(30_000L, 30_000L, 30_000));
    }

    @Test public void wristOrStepProgressAfterTimeoutRenewsOnlySilentDisplay() {
        assertEquals(2_000L, SmartAlarmPresentationTiming.graceRemaining(32_000L, 29_000L));
        assertEquals(4_000L, SmartAlarmPresentationTiming.graceRemaining(34_000L, 33_000L));
        assertEquals(4_000L, SmartAlarmPresentationTiming.graceRemaining(38_000L, 37_000L));
        assertEquals(0L, SmartAlarmPresentationTiming.graceRemaining(42_000L, 37_000L));
        assertEquals(0, SmartAlarmPresentationTiming.feedbackRemaining(38_000L, 30_000L, 30_000));
    }

    @Test public void earlyInteractionDoesNotLeaveAnIdleTaskOnScreen() {
        assertEquals(0L, SmartAlarmPresentationTiming.graceRemaining(30_000L, 10_000L));
        assertEquals(0L, SmartAlarmPresentationTiming.graceRemaining(30_000L, 0L));
    }

    @Test public void delayedActivityOrRecreationCannotRestartThirtySecondsOfSound() {
        assertEquals(27_000, SmartAlarmPresentationTiming.feedbackRemaining(3_000L, 30_000L, 30_000));
        assertEquals(15_000, SmartAlarmPresentationTiming.feedbackRemaining(15_000L, 30_000L, 30_000));
        assertEquals(0, SmartAlarmPresentationTiming.feedbackRemaining(35_000L, 30_000L, 30_000));
    }
}
