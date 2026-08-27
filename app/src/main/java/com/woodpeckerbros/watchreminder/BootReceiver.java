package com.woodpeckerbros.watchreminder;

import com.woodpeckerbros.watchreminder.smartwake.SmartAlarmScheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AppLog.d(context, "BootReceiver action=" + (intent == null ? "" : intent.getAction()));
        AlarmScheduleMigration.clearLegacyAlarmsOnce(context);
        new ReminderSettings(context).applyPowerSaveDefaultOnce();
        long now = System.currentTimeMillis();
        ReminderDueChecker.dispatchDue(context, now - ReminderDueChecker.CATCH_UP_LOOKBACK_MS, now);
        HealthStateRegistrar.register(context);
        ReminderAudit.run(context);
        new ReminderStore(context).rescheduleAll();
        DafYomiScheduler.schedule(context);
        DafYomiScheduler.dispatchIfDueNow(context);
        MoonBlessingScheduler.schedule(context);
        OmerScheduler.schedule(context);
        OmerScheduler.dispatchIfDueNow(context);
        JewishDayScheduler.schedule(context);
        TekufaScheduler.schedule(context);
        IntermittentFastingScheduler.schedule(context);
        SmartAlarmScheduler.reschedule(context);
        ReminderScheduler.scheduleWatchdog(context);
        ComplicationRefresh.requestAll(context);
        ReminderReceiver.dispatchNextQueued(context);
        if (new ReminderSettings(context).serviceEnabled()) {
            ReminderForegroundService.start(context);
        } else {
            ReminderForegroundService.stop(context);
        }
    }
}
