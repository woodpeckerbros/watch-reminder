package com.woodpeckerbros.watchreminder.reminder;

import com.woodpeckerbros.watchreminder.*;

import com.woodpeckerbros.watchreminder.calendar.*;

import com.woodpeckerbros.watchreminder.smartalarm.SmartAlarmScheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;

import java.util.concurrent.atomic.AtomicBoolean;

public class BootReceiver extends BroadcastReceiver {
    private static final AtomicBoolean RECOVERY_RUNNING = new AtomicBoolean(false);

    public static boolean isRecoveryRunning() {
        return RECOVERY_RUNNING.get();
    }
    @Override
    public void onReceive(Context context, Intent intent) {
        UserManager userManager = context.getSystemService(UserManager.class);
        if (userManager != null && !userManager.isUserUnlocked()) {
            return;
        }
        String action = intent == null ? "" : intent.getAction();
        AppLog.d(context, "BootReceiver action=" + action);
        // An APK replacement starts this receiver at the same time the launcher may start the
        // activity. Full recovery recalculates every reminder and can starve the initial frame
        // on a two-core watch. Keep boot/time recovery immediate, but defer post-update repair
        // to a one-off job; MainActivity performs the same repair after its first frame.
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            ReminderRecoveryJobService.schedulePostUpdateRecovery(context);
            return;
        }
        PendingResult pendingResult = goAsync();
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            RECOVERY_RUNNING.set(true);
            try {
                recover(appContext, true);
            } finally {
                ReminderRecoveryJobService.schedule(appContext);
                RECOVERY_RUNNING.set(false);
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
        ReminderRecoveryJobService.markRecoveryCompleted(context);
    }
}
