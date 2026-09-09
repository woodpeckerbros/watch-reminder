package com.woodpeckerbros.watchreminder.reminder;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReminderMonitoringServiceTest {
    @Test
    public void monitoringNotificationUsesExplicitHebrewAppLanguage() {
        assertEquals("ניטור תזכורות פעיל",
                ReminderMonitoringService.monitoringTextForAppLanguage(ReminderSettings.LANGUAGE_HEBREW));
    }

    @Test
    public void monitoringNotificationUsesExplicitEnglishAppLanguage() {
        assertEquals("Active reminder monitoring",
                ReminderMonitoringService.monitoringTextForAppLanguage(ReminderSettings.LANGUAGE_ENGLISH));
    }

    @Test
    public void monitoringNotificationAutoDoesNotReadWearOsLanguage() {
        assertEquals("Active reminder monitoring",
                ReminderMonitoringService.monitoringTextForAppLanguage(ReminderSettings.LANGUAGE_AUTO));
    }
}
