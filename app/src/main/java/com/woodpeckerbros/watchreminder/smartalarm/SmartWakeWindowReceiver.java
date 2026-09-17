package com.woodpeckerbros.watchreminder.smartalarm;

import com.woodpeckerbros.watchreminder.reminder.*;
import com.woodpeckerbros.watchreminder.entitlement.EntitlementAccess;
import com.woodpeckerbros.watchreminder.entitlement.EntitlementEnforcer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;

public final class SmartWakeWindowReceiver extends BroadcastReceiver {
    static final String EXTRA_WINDOW_START_CHECK = "smart_wake_window_start_check";
    @Override public void onReceive(Context context, Intent intent) {
        long targetAt = intent.getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
        long wakeWindowStartAt = intent.getLongExtra(SmartAlarmScheduler.EXTRA_WAKE_WINDOW_START_AT, targetAt);
        int alarmId = intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        UserManager userManager = context.getSystemService(UserManager.class);
        boolean locked = userManager != null && !userManager.isUserUnlocked();
        if (locked) {
            if (!SmartAlarmBootStore.supportsSmartWake(context, alarmId, targetAt)) return;
            if (intent.getBooleanExtra(EXTRA_WINDOW_START_CHECK, false)) {
                SmartWakeMonitoringService.windowStartedDirectBoot(context, alarmId, targetAt, wakeWindowStartAt);
            } else {
                SmartWakeMonitoringService.startDirectBoot(context, alarmId, targetAt, wakeWindowStartAt);
            }
            return;
        }
        if (!EntitlementAccess.isFeatureAccessGranted(context)) { EntitlementEnforcer.disableDeliveries(context); return; }
        if (intent.getBooleanExtra(EXTRA_WINDOW_START_CHECK, false)) {
            SmartWakeMonitoringService.windowStarted(context, alarmId, targetAt, wakeWindowStartAt);
        } else {
            SmartWakeMonitoringService.start(context, alarmId, targetAt, wakeWindowStartAt);
        }
    }
}
