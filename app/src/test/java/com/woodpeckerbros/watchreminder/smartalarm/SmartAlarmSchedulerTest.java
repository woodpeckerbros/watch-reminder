package com.woodpeckerbros.watchreminder.smartalarm;

import static org.junit.Assert.assertEquals;

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

    @Test public void exhaustedSnoozeStillSkipsOriginalMorningDeadline() {
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

        int daysMask = 1 << originalDeadline.get(Calendar.DAY_OF_WEEK);
        Calendar expectedNext = (Calendar) originalDeadline.clone();
        expectedNext.add(Calendar.DAY_OF_YEAR, 7);
        assertEquals(expectedNext.getTimeInMillis(), SmartAlarmScheduler.nextTarget(
                7, 10, daysMask, originalDeadline.getTimeInMillis()));
    }
}
