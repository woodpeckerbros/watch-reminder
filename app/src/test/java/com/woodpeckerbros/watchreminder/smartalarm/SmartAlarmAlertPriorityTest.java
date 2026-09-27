package com.woodpeckerbros.watchreminder.smartalarm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SmartAlarmAlertPriorityTest {
    @Test public void exactDeadlineWinsOverNormalReminder() {
        assertTrue(SmartAlarmAlertPriority.normalReminderMustDefer(
                false, true));
    }

    @Test public void normalReminderCannotCoverActiveSmartAlarm() {
        assertTrue(SmartAlarmAlertPriority.normalReminderMustDefer(
                true, false));
    }

    @Test public void normalReminderCannotStealFocusDuringWakeCheck() {
        assertTrue(SmartAlarmAlertPriority.normalReminderMustDefer(
                true, false));
    }

    @Test public void ringingReminderIsDeferredAndPreservedByTheOwnerRule() {
        assertTrue(SmartAlarmAlertPriority.normalReminderMustDefer(
                true, false));
    }

    @Test public void autoSnoozeChainRetainsPriorityForSecondRing() {
        assertTrue(SmartAlarmAlertPriority.normalReminderMustDefer(
                true, false));
    }

    @Test public void thirdRingStillHasPriority() {
        assertTrue(SmartAlarmAlertPriority.normalReminderMustDefer(
                true, false));
    }

    @Test public void terminalDismissReleasesPriority() {
        assertFalse(SmartAlarmAlertPriority.normalReminderMustDefer(
                false, false));
    }

    @Test public void imAwakeReleasesPriority() {
        assertFalse(SmartAlarmAlertPriority.normalReminderMustDefer(
                false, false));
    }

    @Test public void normalReminderWithoutSmartAlarmIsUnchanged() {
        assertFalse(SmartAlarmAlertPriority.normalReminderMustDefer(
                false, false));
    }
}
