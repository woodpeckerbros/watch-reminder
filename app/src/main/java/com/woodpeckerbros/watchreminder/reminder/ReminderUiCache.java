package com.woodpeckerbros.watchreminder.reminder;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide generation for UI data derived from reminders.  Stores advance it whenever a
 * reminder, snooze, or reminder event changes, allowing the activity to reuse its prepared
 * presentation data without polling SharedPreferences on every resume.
 */
public final class ReminderUiCache {
    private static final AtomicLong GENERATION = new AtomicLong(1L);

    private ReminderUiCache() {
    }

    public static long generation() {
        return GENERATION.get();
    }

    public static void invalidate() {
        GENERATION.incrementAndGet();
    }
}
