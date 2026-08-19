package com.woodpeckerbros.watchreminder;

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
        new ReminderStore(context).rescheduleAll();
        ReminderReceiver.dispatchNextQueued(context);
        ReminderScheduler.scheduleWatchdog(context);
        ComplicationRefresh.request(context);
    }
}
