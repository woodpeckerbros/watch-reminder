package com.woodpeckerbros.watchreminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WatchdogReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int slot = intent == null ? -1 : intent.getIntExtra(ReminderScheduler.EXTRA_WATCHDOG_SLOT, -1);
        dispatch(context, slot, "receiver");
    }

    public static void dispatch(Context context, int slot, String source) {
        AppLog.d(context, "WatchdogReceiver dispatch source=" + source + " slot=" + slot);
        long now = System.currentTimeMillis();
        ReminderDueChecker.dispatchDue(context, now - ReminderDueChecker.CATCH_UP_LOOKBACK_MS, now);
        ReminderAudit.run(context);
        new ReminderStore(context).rescheduleAll();
        ReminderReceiver.dispatchNextQueued(context);
        ReminderScheduler.scheduleWatchdog(context);
        if (new ReminderSettings(context).serviceEnabled()) {
            ReminderForegroundService.start(context);
        }
    }
}
