package com.woodpeckerbros.watchreminder.calendar;

import android.content.Context;

/** Catch-up entry points for calendar-driven alerts which have no normal Reminder record. */
public final class CalendarReminderCatchUp {
    private CalendarReminderCatchUp() {
    }

    public static void dispatchAfterRecovery(Context context) {
        DafYomiScheduler.dispatchIfDueNow(context);
        OmerScheduler.dispatchIfDueNow(context);
        dispatchWhenAwake(context);
    }

    public static void dispatchWhenAwake(Context context) {
        JewishDayScheduler.dispatchMissedIfDueNow(context);
        MoonBlessingScheduler.dispatchMissedIfDueNow(context);
        TekufaScheduler.dispatchMissedIfDueNow(context);
    }
}
