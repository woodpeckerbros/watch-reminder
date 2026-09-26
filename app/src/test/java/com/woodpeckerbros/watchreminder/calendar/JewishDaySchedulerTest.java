package com.woodpeckerbros.watchreminder.calendar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class JewishDaySchedulerTest {
    @Test public void dayBeforeNoticeIsThreeHoursBeforeTzeis() {
        long tzeis = 1_000_000_000L;
        assertEquals(tzeis - 3L * 60L * 60_000L,
                JewishDayScheduler.dayBeforeReminderAt(tzeis));
    }

    @Test public void erevHolidayNoticeIsAtPreviousNightsTzeis() {
        long tzeis = 1_000_059_999L;
        assertEquals(1_000_020_000L, JewishDayScheduler.erevHolidayReminderAt(tzeis));
    }

    @Test public void legacyMorningErevAlarmIsNotRejectedBeforeItsMidnightExpiryIsProvided() {
        long tenInTheMorning = 1_000_000_000L;
        assertEquals(false, JewishDayScheduler.isExpiredDelivery(
                JewishDayScheduler.KIND_TODAY_EREV,
                tenInTheMorning, 0L, tenInTheMorning + 61L * 60_000L));
    }

    @Test public void erevHolidayAlarmIsRejectedOnlyAfterMidnightExpiry() {
        long tzeis = 1_000_000_000L;
        long expiresAt = tzeis + 5L * 60L * 60_000L;
        assertEquals(false, JewishDayScheduler.isExpiredDelivery(
                JewishDayScheduler.KIND_TOMORROW, tzeis, expiresAt, expiresAt));
        assertEquals(true, JewishDayScheduler.isExpiredDelivery(
                JewishDayScheduler.KIND_TOMORROW, tzeis, expiresAt, expiresAt + 1L));
    }
}
