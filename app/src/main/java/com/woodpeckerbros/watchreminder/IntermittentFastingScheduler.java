package com.woodpeckerbros.watchreminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class IntermittentFastingScheduler {
    static final String EXTRA_EVENT_TYPE = "fasting_event_type";
    static final String EXTRA_TRIGGER_AT = "fasting_trigger_at";
    static final String EXTRA_RETRY = "fasting_retry";
    static final String EVENT_START = "start";
    static final String EVENT_END_WARNING = "end_warning";
    static final String EVENT_END = "end";

    private static final String REQUEST_KEY = "intermittent_fasting";
    private static final long WARNING_BEFORE_END_MS = 30 * 60_000L;

    private IntermittentFastingScheduler() {
    }

    public static void schedule(Context context) {
        cancelScheduledEvents(context);
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.intermittentFastingEnabled()) {
            AppLog.d(context, "fasting schedule skipped disabled");
            cancelRetry(context);
            return;
        }
        long now = System.currentTimeMillis();
        IntermittentFastingStore.Window window = new IntermittentFastingStore(context).window(now);
        long triggerAt;
        String eventType;
        if (now < window.startAt) {
            triggerAt = window.startAt;
            eventType = EVENT_START;
        } else if (window.eatingOpen(now)) {
            long warningAt = window.endAt - WARNING_BEFORE_END_MS;
            if (warningAt > now) {
                triggerAt = warningAt;
                eventType = EVENT_END_WARNING;
            } else {
                triggerAt = window.endAt;
                eventType = EVENT_END;
            }
        } else {
            triggerAt = window.nextStartAt;
            eventType = EVENT_START;
        }
        triggerAt = ReminderScheduler.floorToMinute(Math.max(triggerAt, now + 10_000L));
        AppLog.d(context, "fasting schedule type=" + eventType + " at=" + NextReminderCalculator.formatDateTime(triggerAt));
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        setBest(context, alarmManager, triggerAt, pendingIntent(context, eventType, triggerAt));
    }

    public static void scheduleRetry(Context context, String eventType, long triggerAt) {
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.intermittentFastingEnabled() || eventType == null || triggerAt <= 0L) {
            return;
        }
        long retryAt = ReminderScheduler.ceilToMinute(System.currentTimeMillis() + settings.autoSnoozeMinutes() * 60_000L);
        AppLog.d(context, "fasting retry schedule type=" + eventType
                + " original=" + NextReminderCalculator.formatDateTime(triggerAt)
                + " at=" + NextReminderCalculator.formatDateTime(retryAt));
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        setBest(context, alarmManager, retryAt, retryPendingIntent(context, eventType, triggerAt));
    }

    public static void cancelRetry(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        alarmManager.cancel(retryPendingIntent(context, EVENT_START, 0L));
        alarmManager.cancel(retryPendingIntent(context, EVENT_END_WARNING, 0L));
        alarmManager.cancel(retryPendingIntent(context, EVENT_END, 0L));
    }

    public static void cancel(Context context) {
        cancelScheduledEvents(context);
        cancelRetry(context);
    }

    private static void cancelScheduledEvents(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        alarmManager.cancel(pendingIntent(context, EVENT_START, 0L));
        alarmManager.cancel(pendingIntent(context, EVENT_END_WARNING, 0L));
        alarmManager.cancel(pendingIntent(context, EVENT_END, 0L));
    }

    private static PendingIntent pendingIntent(Context context, String eventType, long triggerAt) {
        Intent intent = new Intent(context, IntermittentFastingReceiver.class)
                .putExtra(EXTRA_EVENT_TYPE, eventType)
                .putExtra(EXTRA_TRIGGER_AT, triggerAt);
        return PendingIntent.getBroadcast(
                context,
                (REQUEST_KEY + ":" + eventType).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent retryPendingIntent(Context context, String eventType, long triggerAt) {
        Intent intent = new Intent(context, IntermittentFastingReceiver.class)
                .putExtra(EXTRA_EVENT_TYPE, eventType)
                .putExtra(EXTRA_TRIGGER_AT, triggerAt)
                .putExtra(EXTRA_RETRY, true);
        return PendingIntent.getBroadcast(
                context,
                (REQUEST_KEY + ":retry:" + eventType).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static void setBest(Context context, AlarmManager alarmManager, long triggerAt, PendingIntent pendingIntent) {
        if (alarmManager == null) {
            return;
        }
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } catch (SecurityException exception) {
            AppLog.e(context, "fasting exact alarm failed", exception);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }
}
