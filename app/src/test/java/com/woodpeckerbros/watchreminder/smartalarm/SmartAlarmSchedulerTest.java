package com.woodpeckerbros.watchreminder.smartalarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

public class SmartAlarmSchedulerTest {
    @Test public void handledEarlyOccurrenceIsSkippedInsteadOfRecreatedAtDeadline() {
        Calendar deadline = Calendar.getInstance();
        deadline.clear();
        deadline.set(2030, Calendar.JANUARY, 7, 7, 10, 0);
        int selectedDay = deadline.get(Calendar.DAY_OF_WEEK);
        int daysMask = 1 << selectedDay;

        Calendar earlyWake = (Calendar) deadline.clone();
        earlyWake.add(Calendar.MINUTE, -11);
        assertEquals(deadline.getTimeInMillis(), SmartAlarmScheduler.nextTarget(
                7, 10, daysMask, earlyWake.getTimeInMillis()));

        Calendar expectedNext = (Calendar) deadline.clone();
        expectedNext.add(Calendar.DAY_OF_YEAR, 7);
        assertEquals(expectedNext.getTimeInMillis(), SmartAlarmScheduler.nextTarget(
                7, 10, daysMask, deadline.getTimeInMillis()));
    }

    @Test public void earlyChainExhaustionKeepsOriginalMorningDeadlineAsSafetyNet() {
        Calendar originalDeadline = Calendar.getInstance();
        originalDeadline.clear();
        originalDeadline.set(2030, Calendar.JANUARY, 7, 7, 10, 0);
        Calendar snoozeDeadline = (Calendar) originalDeadline.clone();
        snoozeDeadline.add(Calendar.MINUTE, -44);
        Calendar handledAt = (Calendar) snoozeDeadline.clone();
        handledAt.add(Calendar.SECOND, 31);

        assertEquals(originalDeadline.getTimeInMillis(), SmartAlarmScheduler.handledOccurrenceBoundary(
                handledAt.getTimeInMillis(), snoozeDeadline.getTimeInMillis(),
                originalDeadline.getTimeInMillis()));
        assertTrue(SmartAlarmScheduler.canScheduleEarlySnooze(
                originalDeadline.getTimeInMillis() - 1L, originalDeadline.getTimeInMillis()));
    }

    @Test public void finalAlarmPendingIntentIdentityIsStableAcrossRecovery() {
        assertEquals(SmartAlarmScheduler.requestCode(7, 2),
                SmartAlarmScheduler.requestCode(7, 2));
        assertNotEquals(SmartAlarmScheduler.requestCode(7, 2),
                SmartAlarmScheduler.requestCode(7, 7));
    }

    @Test public void earlySnoozeHasItsOwnPendingIntentIdentity() {
        assertNotEquals(SmartAlarmScheduler.requestCode(7, 0),
                SmartAlarmScheduler.requestCode(7, 2));
    }

    @Test public void earlySnoozeNeverPassesOriginalFinalDeadline() {
        long finalDeadline = 1_000_000L;
        assertTrue(SmartAlarmScheduler.canScheduleEarlySnooze(finalDeadline - 1L, finalDeadline));
        assertFalse(SmartAlarmScheduler.canScheduleEarlySnooze(finalDeadline, finalDeadline));
        assertFalse(SmartAlarmScheduler.canScheduleEarlySnooze(finalDeadline + 1L, finalDeadline));
    }

    @Test public void deadlineReasonIsDistinctFromEarlySnooze() {
        assertTrue(SmartAlarmScheduler.isFinalDeadlineReason("deadline"));
        assertFalse(SmartAlarmScheduler.isFinalDeadlineReason("early_snooze"));
        assertFalse(SmartAlarmScheduler.isFinalDeadlineReason("WAKE_TEMPORAL_MULTI_SENSOR_CONFIRMATION"));
    }
}
