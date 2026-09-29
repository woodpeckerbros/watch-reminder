package com.woodpeckerbros.watchreminder.reminder;

import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class DeferredWearStateServiceTest {
    @Test
    public void waitingForWearDoesNotReplaceReminderMonitoringNotification() {
        assertNotEquals(ReminderMonitoringService.NOTIFICATION_ID,
                DeferredWearStateService.NOTIFICATION_ID);
    }
}
