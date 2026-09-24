package com.woodpeckerbros.watchreminder.reminder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReminderAlertQueueStoreTest {
    private static final long FIRST = 1_800_000L;
    private static final long SECOND = 3_600_000L;

    @Test public void sameReminderMergesEvenWhenOneOccurrenceWasSnoozed() {
        ReminderAlertQueueStore.QueuedAlert regular = alert("water", "water:first", FIRST, false);
        ReminderAlertQueueStore.QueuedAlert snoozed = alert("water", "water:second", SECOND, true);

        assertTrue(regular.canMerge(snoozed));
        ReminderAlertQueueStore.QueuedAlert merged = regular.merge(snoozed);

        assertEquals(2, merged.count());
        assertEquals(FIRST, merged.scheduledAt);
        assertEquals(SECOND, merged.latestOriginalScheduledAt());
    }

    @Test public void mergeKeepsEveryDistinctMissedOccurrenceOfTheReminder() {
        ReminderAlertQueueStore.QueuedAlert first = alert("hourly", "hourly:first", FIRST, false);
        ReminderAlertQueueStore.QueuedAlert second = alert("hourly", "hourly:second", SECOND, false);

        ReminderAlertQueueStore.QueuedAlert merged = first.merge(second);

        assertEquals(2, merged.count());
        assertEquals("hourly:first", merged.occurrenceIds.get(0));
        assertEquals("hourly:second", merged.occurrenceIds.get(1));
    }

    private ReminderAlertQueueStore.QueuedAlert alert(String reminderId, String occurrenceId, long at, boolean snooze) {
        return new ReminderAlertQueueStore.QueuedAlert(
                occurrenceId,
                reminderId,
                "תזכורת",
                at,
                at,
                -1,
                snooze
        );
    }
}
