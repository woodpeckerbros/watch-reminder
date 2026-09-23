package com.woodpeckerbros.watchreminder;

import com.woodpeckerbros.watchreminder.reminder.*;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WaterReminderSchedulerTest {
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
        assertEquals(270, WaterReminderScheduler.amountForRemaining(1850, 7));
    }

    @Test
    public void dailyGoalUsesAStablePortionRatherThanCatchUpAmounts() {
        int portion = WaterReminderScheduler.dailyTargetAmountForReminderMl(2000, 0, 8);
        assertEquals(250, portion);
        // After recording the requested 250 ml, the next request remains 250 ml,
        // instead of being increased because an earlier time slot has passed.
        assertEquals(portion, WaterReminderScheduler.dailyTargetAmountForReminderMl(2000, 250, 8));
    }

    @Test
    public void lastDailyPortionCanOnlyShrinkToTheRemainingGoal() {
        assertEquals(250, WaterReminderScheduler.dailyTargetAmountForReminderMl(2000, 1_750, 8));
        assertEquals(120, WaterReminderScheduler.dailyTargetAmountForReminderMl(2000, 1_880, 8));
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
