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
        PendingResult pendingResult = goAsync();
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                recover(appContext, true);
            } finally {
                pendingResult.finish();
            }
        }, "wr-boot-recovery").start();
    }

    static void recover(Context context, boolean mayStartMonitoringService) {
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
        WaterReminderScheduler.schedule(context);
        SmartAlarmScheduler.recover(context);
        ReminderScheduler.scheduleWatchdog(context);
        ComplicationRefresh.requestAll(context);
        ReminderReceiver.dispatchNextQueued(context);
        if (mayStartMonitoringService && new ReminderSettings(context).serviceEnabled()) {
            AppLog.d(context, "ReminderMonitoring boot recovery start");
            ReminderMonitoringService.start(context);
        } else if (!new ReminderSettings(context).serviceEnabled()) {
            ReminderMonitoringService.stop(context);
        }
    }
}
