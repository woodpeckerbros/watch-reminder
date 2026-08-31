package com.woodpeckerbros.watchreminder.smartwake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Debug-build-only entry point for physical-watch sensor verification. */
public final class SmartWakeDebugReceiver extends BroadcastReceiver {
    private static final int DEBUG_ALARM_ID = 9_999;

    @Override public void onReceive(Context context, Intent intent) {
        long targetAt = System.currentTimeMillis() + Math.max(2, intent.getIntExtra("minutes", 10)) * 60_000L;
        int alarmId = intent.hasExtra(SmartAlarmScheduler.EXTRA_ALARM_ID)
                ? intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, DEBUG_ALARM_ID)
                : intent.getIntExtra("alarm_id", DEBUG_ALARM_ID);
        if (intent.getBooleanExtra("restore_schedule", false)) {
            SmartAlarmRingingService.stop(context);
            SmartAlarmActions.cancelNotification(context, alarmId);
            SmartAlarmScheduler.reschedule(context, alarmId);
            return;
        }
        new SmartAlarmStateStore(context, alarmId).begin(targetAt);
        if (intent.getBooleanExtra("fire", false)) SmartAlarmScheduler.scheduleDetectedFire(context, alarmId, targetAt);
        else SmartWakeMonitoringService.start(context, alarmId, targetAt);
    }
}
