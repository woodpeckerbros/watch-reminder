package com.woodpeckerbros.watchreminder.smartwake;

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
}
