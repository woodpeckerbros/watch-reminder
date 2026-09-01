package com.woodpeckerbros.watchreminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class OmerScheduler {
    static final String EXTRA_RETRY = "omer_retry";
    static final String EXTRA_TRIGGER_AT = "omer_trigger_at";
    private static final String REQUEST_KEY = "omer";

    private OmerScheduler() {
    }

    public static void schedule(Context context) {
        ReminderSettings settings = new ReminderSettings(context);
        cancelDaily(context);
        if (!settings.jewishMode() || !settings.omerEnabled()) {
            AppLog.d(context, "omer schedule skipped disabled");
            return;
        }
        OmerHelper.Item item = OmerHelper.next(context, settings.omerOffsetMinutes());
        if (item == null) {
            AppLog.d(context, "omer schedule skipped outside season");
            return;
        }
        AppLog.d(context, "omer schedule day=" + item.day + " at=" + NextReminderCalculator.formatDateTime(item.triggerAt));
        setBest(context, item.triggerAt, pendingIntent(context, false, item.triggerAt));
    }

    public static boolean dispatchIfDueNow(Context context) {
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.jewishMode() || !settings.omerEnabled()) {
            return false;
        }
        OmerStore store = new OmerStore(context);
        long now = System.currentTimeMillis();
        long retryUntil = store.retryUntil();
        if (retryUntil > now) {
            AppLog.d(context, "omer catch-up skipped retry until=" + NextReminderCalculator.formatDateTime(retryUntil));
            schedule(context);
            return false;
        }
        if (retryUntil > 0) {
            store.clearRetryUntil();
        }
        OmerHelper.Item item = OmerHelper.dueNow(context, settings.omerOffsetMinutes());
        if (item == null || store.isHandled(item.key)) {
            schedule(context);
            return false;
        }
        if (new ReminderAlertQueueStore(context).hasActiveAlert()) {
            AppLog.d(context, "omer catch-up deferred behind regular alert");
            scheduleRetry(context, 1);
            return false;
        }
        AppLog.d(context, "omer catch-up open alert day=" + item.day + " at=" + NextReminderCalculator.formatDateTime(item.triggerAt));
        openAlert(context, item.triggerAt);
        OmerReceiver.cancelNotification(context);
        schedule(context);
        return true;
    }

    public static void scheduleRetry(Context context, int minutes) {
        long triggerAt = ReminderScheduler.ceilToMinute(System.currentTimeMillis() + minutes * 60_000L);
        new OmerStore(context).setRetryUntil(triggerAt);
        AppLog.d(context, "omer retry at=" + NextReminderCalculator.formatDateTime(triggerAt));
        setBest(context, triggerAt, pendingIntent(context, true, triggerAt));
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent(context, false, 0));
        alarmManager.cancel(pendingIntent(context, true, 0));
        new OmerStore(context).clearRetryUntil();
    }

    static void openAlert(Context context, long triggerAt) {
        Intent alert = new Intent(context, OmerAlertActivity.class)
                .putExtra(EXTRA_TRIGGER_AT, triggerAt)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(alert);
    }

    private static void cancelDaily(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent(context, false, 0));
    }

    private static PendingIntent pendingIntent(Context context, boolean retry, long triggerAt) {
        Intent intent = new Intent(context, OmerReceiver.class)
                .putExtra(EXTRA_RETRY, retry)
                .putExtra(EXTRA_TRIGGER_AT, triggerAt);
        return PendingIntent.getBroadcast(
                context,
                (REQUEST_KEY + (retry ? ":retry" : "")).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static void setBest(Context context, long triggerAt, PendingIntent pendingIntent) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } catch (SecurityException exception) {
            AppLog.e(context, "omer exact alarm failed", exception);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }
}
