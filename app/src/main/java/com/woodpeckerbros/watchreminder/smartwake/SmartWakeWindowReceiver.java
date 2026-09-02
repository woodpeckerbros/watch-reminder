package com.woodpeckerbros.watchreminder.smartwake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class SmartWakeWindowReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        long targetAt = intent.getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
        int alarmId = intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        SystemAlarmHapticBridge.schedule(context, alarmId, targetAt);
        SmartWakeMonitoringService.start(context, alarmId, targetAt);
    }
}
