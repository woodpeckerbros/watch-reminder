package com.woodpeckerbros.watchreminder.reminder;

import com.woodpeckerbros.watchreminder.*;

import android.content.Context;

public class DeferredReminderDispatcher {
    private DeferredReminderDispatcher() {
    }

    public static void run(Context context) {
        if (!new ReminderAlertQueueStore(context).hasDeferredAlerts()) {
            AppLog.d(context, "deferred dispatch skipped empty queue");
            DeferredWearRetryReceiver.cancel(context);
            return;
        }
        long now = System.currentTimeMillis();
        ReminderDueChecker.dispatchDue(context, now - ReminderDueChecker.CATCH_UP_LOOKBACK_MS, now);
        // Claim and present the first recovered alert before maintenance work.  Rebuilding every
        // reminder schedule can be comparatively slow on a watch and must not hold up the burst.
        ReminderReceiver.dispatchNextQueued(context);
        new ReminderStore(context).rescheduleAll();
        ReminderScheduler.scheduleWatchdog(context);
        ComplicationRefresh.request(context);
    }
}
