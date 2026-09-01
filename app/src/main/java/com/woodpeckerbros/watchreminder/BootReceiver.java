package com.woodpeckerbros.watchreminder;

import com.woodpeckerbros.watchreminder.smartwake.SmartAlarmScheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        UserManager userManager = context.getSystemService(UserManager.class);
        if (userManager != null && !userManager.isUserUnlocked()) {
            return;
        }
        AppLog.d(context, "BootReceiver action=" + (intent == null ? "" : intent.getAction()));
        ReminderRecoveryJobService.schedule(context);
        recover(context);
    }

    static void recover(Context context) {
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
