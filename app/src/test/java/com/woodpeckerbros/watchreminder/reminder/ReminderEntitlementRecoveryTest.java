package com.woodpeckerbros.watchreminder.reminder;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReminderEntitlementRecoveryTest {
    @Test public void onlyFutureSnoozesAreRearmedAfterAccessReturns() {
        long now = 1_700_000_000_000L;
        assertTrue(ReminderScheduler.shouldRestoreSnooze(now + 60_000L, now));
        assertFalse(ReminderScheduler.shouldRestoreSnooze(now, now));
        assertFalse(ReminderScheduler.shouldRestoreSnooze(now - 60_000L, now));
    }
}
