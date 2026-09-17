package com.woodpeckerbros.watchreminder.smartalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.woodpeckerbros.watchreminder.AppLog;

/** Independent AlarmManager backstop that prevents a sensor session from outliving its deadline. */
public final class SmartWakeHardStopReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        int alarmId = intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        long targetAt = intent.getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
        AppLog.w(context, "SMART_WAKE_HARD_STOP_RECEIVED id=" + alarmId
                + " occurrence_id=" + alarmId + ":" + targetAt + " deadline=" + targetAt);
        SmartWakeMonitoringService.hardStop(context, alarmId, targetAt);
    }
}
