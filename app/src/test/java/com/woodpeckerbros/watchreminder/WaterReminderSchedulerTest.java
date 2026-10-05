package com.woodpeckerbros.watchreminder;

import com.woodpeckerbros.watchreminder.reminder.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WaterReminderSchedulerTest {
    @Test
    public void pendingSnoozeSurvivesReschedulingAndShortRecoveryDelay() {
        long now = 1_000_000_000L;
        assertEquals(now + 15 * 60_000L, WaterReminderScheduler.requestedSnoozeAt(now, 15));
        assertTrue(WaterReminderScheduler.shouldRecoverSnooze(now + 15 * 60_000L, now));
        assertTrue(WaterReminderScheduler.shouldRecoverSnooze(now - 5 * 60_000L, now));
        assertFalse(WaterReminderScheduler.shouldRecoverSnooze(now - 31 * 60_000L, now));
        assertFalse(WaterReminderScheduler.shouldRecoverSnooze(0L, now));
    }

    @Test
    public void defaultWaterIntervalIsOneHour() {
        assertEquals(60, ReminderSettings.DEFAULT_WATER_INTERVAL_MINUTES);
    }

    @Test
    public void remindersPerDayIncludesStartAndLastAlignedSlot() {
        assertEquals(8, WaterReminderScheduler.remindersPerDay(8 * 60, 22 * 60, 120));
    }

    @Test
    public void remindersPerDayDoesNotSchedulePastEnd() {
        assertEquals(5, WaterReminderScheduler.remindersPerDay(9 * 60, 17 * 60 + 30, 120));
    }

    @Test
    public void remindersPerDayRejectsInvalidWindow() {
        assertEquals(0, WaterReminderScheduler.remindersPerDay(22 * 60, 8 * 60, 60));
        assertEquals(0, WaterReminderScheduler.remindersPerDay(8 * 60, 8 * 60, 60));
    }

    @Test
    public void dailyTargetSplitsEvenlyAndRoundsUpToTenMl() {
        assertEquals(250, WaterReminderScheduler.amountForRemaining(2000, 8));
        assertEquals(265, WaterReminderScheduler.amountForRemaining(1850, 7));
    }

    @Test
    public void remainingSlotsIncludeThisAlertAndOnlyFutureRegularAlerts() {
        // 08:00–22:00 every two hours; at 10:00, 10/12/14/16/18/20/22 remain.
        assertEquals(7, WaterReminderScheduler.remainingReminderSlots(
                8 * 60, 22 * 60, 120, 10 * 60));
        // A 10:15 snooze replaces the 10:00 slot; the next regular one is still 12:00.
        assertEquals(7, WaterReminderScheduler.remainingReminderSlots(
                8 * 60, 22 * 60, 120, 10 * 60 + 15));
    }

    @Test
    public void missedWaterIsRebalancedAcrossEveryRemainingAlert() {
        // After three missed 200 ml opportunities, 2,000 ml is spread over the four
        // remaining alerts, rather than leaving the whole gap to the final one.
        assertEquals(500, WaterReminderScheduler.amountForRemaining(2000, 4));
        // Recording water immediately lowers the next equal-share request.
        assertEquals(200, WaterReminderScheduler.amountForRemaining(1400, 7));
    }

    @Test
    public void glassSizePlanKeepsThirtyMinutesBetweenRegularReminders() {
        assertEquals(30, WaterReminderScheduler.automaticIntervalMinutes(
                8 * 60, 22 * 60, 2000, 200));
        assertEquals(30, WaterReminderScheduler.automaticIntervalMinutes(
                10 * 60, 22 * 60, 2500, 200));
        assertEquals(0, WaterReminderScheduler.automaticIntervalMinutes(
                10 * 60, 10 * 60 + 20, 2500, 200));
        assertEquals(24, WaterReminderScheduler.fixedAmountSlotsAvailable(10 * 60, 22 * 60));
        long start = 10 * 60 * 60_000L;
        long end = 22 * 60 * 60_000L;
        assertEquals(start + 30 * 60_000L,
                WaterReminderScheduler.fixedAmountNextAt(start, end, start, 2500, 200));
        long first = WaterReminderScheduler.fixedAmountNextAt(start, end, start, 2500, 200);
        assertEquals(first + 30 * 60_000L,
                WaterReminderScheduler.fixedAmountNextAt(start, end, first, 2300, 200));
        assertEquals(0L, WaterReminderScheduler.fixedAmountNextAt(
                start, end, end - 20 * 60_000L, 2500, 200));
    }

    @Test
    public void glassSizePlanRaisesAmountOnlyWhenRemainingSlotsRequireIt() {
        long end = 22 * 60 * 60_000L;
        assertEquals(200, WaterReminderScheduler.fixedAmountForReminder(
                2500, 200, 10 * 60 * 60_000L + 30 * 60_000L, end));
        assertEquals(209, WaterReminderScheduler.fixedAmountForReminder(
                5000, 50, 10 * 60 * 60_000L + 30 * 60_000L, end));
        assertEquals(834, WaterReminderScheduler.fixedAmountForReminder(
                2500, 200, 21 * 60 * 60_000L, end));
        assertEquals(100, WaterReminderScheduler.fixedAmountForReminder(
                100, 200, 21 * 60 * 60_000L, end));
    }

    @Test
    public void skipAndFifteenMinuteSnoozeKeepTheRegularThirtyMinuteCadence() {
        long start = 10 * 60 * 60_000L;
        long end = 22 * 60 * 60_000L;
        long first = WaterReminderScheduler.fixedAmountNextAt(start, end, start, 2500, 200);
        long afterSkip = WaterReminderScheduler.fixedAmountNextAt(start, end, first, 2500, 200);
        assertEquals(first + 30 * 60_000L, afterSkip);
        long snoozed = WaterReminderScheduler.requestedSnoozeAt(first, 15);
        assertEquals(first + 15 * 60_000L, snoozed);
        long afterDrinkingAtSnooze = WaterReminderScheduler.fixedAmountNextAt(
                start, end, snoozed, 2300, 200);
        assertEquals(snoozed + 30 * 60_000L, afterDrinkingAtSnooze);
        assertEquals(end - 10 * 60_000L, WaterReminderScheduler.fixedAmountNextAt(
                start, end, end - 40 * 60_000L, 100, 200));
    }

    @Test
    public void noRemainingWaterProducesNoAmount() {
        assertEquals(0, WaterReminderScheduler.amountForRemaining(0, 4));
        assertEquals(0, WaterReminderScheduler.amountForRemaining(1000, 0));
    }
}
