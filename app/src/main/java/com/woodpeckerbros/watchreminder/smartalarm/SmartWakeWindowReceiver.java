package com.woodpeckerbros.watchreminder.smartalarm;

import com.woodpeckerbros.watchreminder.reminder.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class SmartWakeWindowReceiver extends BroadcastReceiver {
    static final String EXTRA_WINDOW_START_CHECK = "smart_wake_window_start_check";
    @Override public void onReceive(Context context, Intent intent) {
        long targetAt = intent.getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
        long wakeWindowStartAt = intent.getLongExtra(SmartAlarmScheduler.EXTRA_WAKE_WINDOW_START_AT, targetAt);
        int alarmId = intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        if (intent.getBooleanExtra(EXTRA_WINDOW_START_CHECK, false)) {
            SmartWakeMonitoringService.windowStarted(context, alarmId, targetAt, wakeWindowStartAt);
        } else {
            SmartWakeMonitoringService.start(context, alarmId, targetAt, wakeWindowStartAt);
        }
    }
}
