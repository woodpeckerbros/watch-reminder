package com.woodpeckerbros.watchreminder.smartalarm;

import com.woodpeckerbros.watchreminder.reminder.*;
import com.woodpeckerbros.watchreminder.entitlement.EntitlementAccess;
import com.woodpeckerbros.watchreminder.entitlement.EntitlementEnforcer;
import com.woodpeckerbros.watchreminder.AppLog;

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
        boolean windowStartCheckpoint = intent.getBooleanExtra(EXTRA_WINDOW_START_CHECK, false);
        long receivedAt = System.currentTimeMillis();
        long expectedMonitoringStartAt = intent.getLongExtra(
                SmartAlarmScheduler.EXTRA_EXPECTED_MONITORING_START_AT,
                SmartAlarmBootStore.monitoringStartAt(context, alarmId, targetAt));
        AppLog.d(context, (windowStartCheckpoint ? "SMART_WAKE_WINDOW_CHECKPOINT_RECEIVED"
                : "SMART_WAKE_MONITOR_ALARM_RECEIVED") + " occurrence_id=" + alarmId + ":" + targetAt
                + " receiver_at=" + receivedAt
                + " EXPECTED_MONITORING_START_AT=" + expectedMonitoringStartAt
                + " expected_receiver_at=" + (windowStartCheckpoint
                ? wakeWindowStartAt : expectedMonitoringStartAt)
                + " receiver_delay_ms=" + ((windowStartCheckpoint
                ? wakeWindowStartAt : expectedMonitoringStartAt) > 0L
                ? receivedAt - (windowStartCheckpoint
                ? wakeWindowStartAt : expectedMonitoringStartAt) : -1L));
        UserManager userManager = context.getSystemService(UserManager.class);
        boolean locked = userManager != null && !userManager.isUserUnlocked();
        if (locked) {
            if (!SmartAlarmBootStore.supportsSmartWake(context, alarmId, targetAt)) return;
            if (windowStartCheckpoint) {
                SmartWakeMonitoringService.windowStartedDirectBoot(context, alarmId, targetAt, wakeWindowStartAt);
            } else {
                SmartWakeMonitoringService.startDirectBoot(context, alarmId, targetAt, wakeWindowStartAt,
                        "SCHEDULED_ALARM", expectedMonitoringStartAt);
            }
            return;
        }
        if (!EntitlementAccess.isFeatureAccessGranted(context)) { EntitlementEnforcer.disableDeliveries(context); return; }
        if (windowStartCheckpoint) {
            SmartWakeMonitoringService.windowStarted(context, alarmId, targetAt, wakeWindowStartAt);
        } else {
            SmartWakeMonitoringService.start(context, alarmId, targetAt, wakeWindowStartAt,
                    "SCHEDULED_ALARM", expectedMonitoringStartAt);
        }
    }
}
