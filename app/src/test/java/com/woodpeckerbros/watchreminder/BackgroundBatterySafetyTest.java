package com.woodpeckerbros.watchreminder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackgroundBatterySafetyTest {
    @Test
    public void awakeWithoutDeferredAlertsDoesNotRunFullDispatch() {
        assertFalse(ReminderHealthPassiveService.shouldDispatchAfterUserActivity(false, false));
    }

    @Test
    public void awakeWithDeferredAlertsRunsFullDispatch() {
        assertTrue(ReminderHealthPassiveService.shouldDispatchAfterUserActivity(false, true));
    }

    @Test
    public void asleepNeverDispatchesDeferredAlerts() {
        assertFalse(ReminderHealthPassiveService.shouldDispatchAfterUserActivity(true, true));
    }
}
