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
    public void glassSizePlanDerivesAnIntervalThatFitsTheDailyGoal() {
        // 2,000 ml as ten 200 ml glasses between 08:00 and 22:00 fits every 90 minutes.
        assertEquals(90, WaterReminderScheduler.automaticIntervalMinutes(
                8 * 60, 22 * 60, 2000, 200));
        assertEquals(10, WaterReminderScheduler.remindersPerDay(8 * 60, 22 * 60, 90));
    }

    @Test
    public void glassSizePlanUsesQuarterHourMinimumForDensePlans() {
        assertEquals(15, WaterReminderScheduler.automaticIntervalMinutes(
                8 * 60, 22 * 60, 5000, 100));
    }

    @Test
    public void noRemainingWaterProducesNoAmount() {
        assertEquals(0, WaterReminderScheduler.amountForRemaining(0, 4));
        assertEquals(0, WaterReminderScheduler.amountForRemaining(1000, 0));
    }
}
