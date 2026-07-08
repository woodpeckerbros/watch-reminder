package com.woodpeckerbros.watchreminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class TekufaScheduler {
    static final String EXTRA_SEASON_INDEX = "tekufa_season_index";
    static final String EXTRA_LOCAL_MEAN_AT = "tekufa_local_mean_at";
    static final String EXTRA_OFFICIAL_AT = "tekufa_official_at";
    static final String EXTRA_WINDOW_START_AT = "tekufa_window_start_at";
    static final String EXTRA_WINDOW_END_AT = "tekufa_window_end_at";
    private static final String REQUEST_KEY = "tekufa";

    private TekufaScheduler() {
    }

    public static void schedule(Context context) {
        cancel(context);
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.jewishMode() || !settings.tekufaRemindersEnabled()) {
            AppLog.d(context, "tekufa schedule skipped disabled");
            return;
        }
        TekufaHelper.Event event = nextEvent(System.currentTimeMillis());
        if (event == null) {
            AppLog.d(context, "tekufa schedule skipped no event");
            return;
        }
        AppLog.d(context, "tekufa schedule season=" + event.seasonIndex
                + " window=" + NextReminderCalculator.formatDateTime(event.windowStartAt)
                + "-" + NextReminderCalculator.formatTime(event.windowEndAt)
                + " times=" + NextReminderCalculator.formatTime(event.localMeanAt)
                + "/" + NextReminderCalculator.formatTime(event.officialAt));
        setBest(context, event.windowStartAt, pendingIntent(context, event));
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent(context, new TekufaHelper.Event(0, 0, 0, 0, 0)));
        }
    }

    static TekufaHelper.Event nextEvent(long now) {
        return TekufaHelper.next(now);
    }

    private static PendingIntent pendingIntent(Context context, TekufaHelper.Event event) {
        Intent intent = new Intent(context, TekufaReceiver.class)
                .putExtra(EXTRA_SEASON_INDEX, event.seasonIndex)
                .putExtra(EXTRA_LOCAL_MEAN_AT, event.localMeanAt)
                .putExtra(EXTRA_OFFICIAL_AT, event.officialAt)
                .putExtra(EXTRA_WINDOW_START_AT, event.windowStartAt)
                .putExtra(EXTRA_WINDOW_END_AT, event.windowEndAt);
        return PendingIntent.getBroadcast(
                context,
                REQUEST_KEY.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static void setBest(Context context, long triggerAt, PendingIntent pendingIntent) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        try {
            alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerAt, pendingIntent), pendingIntent);
        } catch (SecurityException exception) {
            AppLog.e(context, "tekufa exact alarm failed", exception);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }
}
