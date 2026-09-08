package com.woodpeckerbros.watchreminder;

import com.woodpeckerbros.watchreminder.reminder.*;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WaterReminderSchedulerTest {
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
    public void noRemainingWaterProducesNoAmount() {
        assertEquals(0, WaterReminderScheduler.amountForRemaining(0, 4));
        assertEquals(0, WaterReminderScheduler.amountForRemaining(1000, 0));
    }
}
