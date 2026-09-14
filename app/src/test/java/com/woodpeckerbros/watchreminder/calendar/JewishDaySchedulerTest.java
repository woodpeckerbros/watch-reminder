package com.woodpeckerbros.watchreminder.calendar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class JewishDaySchedulerTest {
    @Test public void dayBeforeNoticeIsThreeHoursBeforeTzeis() {
        long tzeis = 1_000_000_000L;
        assertEquals(tzeis - 3L * 60L * 60_000L,
                JewishDayScheduler.dayBeforeReminderAt(tzeis));
    }
}
