package com.woodpeckerbros.watchreminder.smartwake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Debug-build-only entry point for physical-watch sensor verification. */
public final class SmartWakeDebugReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        long targetAt = System.currentTimeMillis() + Math.max(2, intent.getIntExtra("minutes", 10)) * 60_000L;
        new SmartAlarmStateStore(context).begin(targetAt);
        if (intent.getBooleanExtra("fire", false)) SmartAlarmReceiver.fire(context, targetAt, "debug_test");
        else SmartWakeMonitoringService.start(context, targetAt);
    }
}
