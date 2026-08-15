package com.woodpeckerbros.watchreminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DeferredWearRetryReceiver extends BroadcastReceiver {
    private static final long RETRY_INTERVAL_MS = 15 * 60_000L;
    private static final int REQUEST_CODE = "deferred_wear_retry".hashCode();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (new ReminderAlertQueueStore(context).hasDeferredAlerts()) {
            DeferredWearStateService.start(context);
        } else {
            cancel(context);
        }
    }

    public static void schedule(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            return;
        }
        manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + RETRY_INTERVAL_MS,
                pendingIntent(context)
        );
    }

    public static void cancel(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) {
            manager.cancel(pendingIntent(context));
        }
    }

    private static PendingIntent pendingIntent(Context context) {
        return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                new Intent(context, DeferredWearRetryReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
