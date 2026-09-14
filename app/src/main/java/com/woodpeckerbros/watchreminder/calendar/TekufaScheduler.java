package com.woodpeckerbros.watchreminder.calendar;

import com.woodpeckerbros.watchreminder.reminder.*;

import com.woodpeckerbros.watchreminder.*;
import com.woodpeckerbros.watchreminder.entitlement.EntitlementAccess;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.TimeZone;

public class TekufaScheduler {
    static final String EXTRA_KIND = "tekufa_kind";
    static final String EXTRA_SEASON_INDEX = "tekufa_season_index";
    static final String EXTRA_LOCAL_MEAN_AT = "tekufa_local_mean_at";
    static final String EXTRA_OFFICIAL_AT = "tekufa_official_at";
    static final String EXTRA_WINDOW_START_AT = "tekufa_window_start_at";
    static final String EXTRA_WINDOW_END_AT = "tekufa_window_end_at";
    static final String KIND_ADVANCE = "advance";
    static final String KIND_START = "start";
    private static final String REQUEST_KEY = "tekufa";
    private static final String DELIVERY_PREFS = "tekufa_delivery";
    private static final String KEY_LAST_START = "last_start";
    private static final long HOUR_MILLIS = 60 * 60_000L;

    private TekufaScheduler() {
    }

    public static void schedule(Context context) {
        if (!EntitlementAccess.isFeatureAccessGranted(context)) { cancel(context); return; }
        cancel(context);
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.jewishMode() || !settings.tekufaRemindersEnabled()) {
            AppLog.d(context, "tekufa schedule skipped disabled");
            return;
        }
        ScheduledEvent event = nextEvent(System.currentTimeMillis());
        if (event == null) {
            AppLog.d(context, "tekufa schedule skipped no event");
            return;
        }
        AppLog.d(context, "tekufa schedule kind=" + event.kind
                + " trigger=" + NextReminderCalculator.formatDateTime(event.triggerAt)
                + " season=" + event.tekufa.seasonIndex
                + " window=" + NextReminderCalculator.formatDateTime(event.tekufa.windowStartAt)
                + "-" + NextReminderCalculator.formatTime(event.tekufa.windowEndAt)
                + " times=" + NextReminderCalculator.formatTime(event.tekufa.localMeanAt)
                + "/" + NextReminderCalculator.formatTime(event.tekufa.officialAt));
        setBest(context, event.triggerAt, pendingIntent(context, event));
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent(context, new ScheduledEvent(KIND_START, 0, new TekufaHelper.Event(0, 0, 0, 0, 0))));
        }
    }

    static ScheduledEvent nextEvent(long now) {
        ScheduledEvent best = null;
        for (TekufaHelper.Event tekufa : TekufaHelper.upcoming(now)) {
            ScheduledEvent advance = new ScheduledEvent(KIND_ADVANCE, advanceTriggerAt(tekufa.windowStartAt), tekufa);
            if (advance.triggerAt > now && (best == null || advance.triggerAt < best.triggerAt)) {
                best = advance;
            }
            ScheduledEvent start = new ScheduledEvent(KIND_START, tekufa.windowStartAt, tekufa);
            if (start.triggerAt > now && (best == null || start.triggerAt < best.triggerAt)) {
                best = start;
            }
        }
        return best;
    }

    /** Replays the start notice only while the no-drinking window itself is still open. */
    public static boolean dispatchMissedIfDueNow(Context context) {
        if (!EntitlementAccess.isFeatureAccessGranted(context)) {
            return false;
        }
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.jewishMode() || !settings.tekufaRemindersEnabled()) {
            return false;
        }
        long now = System.currentTimeMillis();
        TekufaHelper.Event tekufa = TekufaHelper.next(now);
        if (tekufa == null || now < tekufa.windowStartAt || now > tekufa.windowEndAt
                || wasStartDelivered(context, tekufa.windowStartAt)) {
            return false;
        }
        TekufaReceiver.showNotification(context,
                new ScheduledEvent(KIND_START, tekufa.windowStartAt, tekufa));
        markStartDelivered(context, tekufa.windowStartAt);
        AppLog.d(context, "tekufa catch-up delivered start="
                + NextReminderCalculator.formatDateTime(tekufa.windowStartAt));
        return true;
    }

    static void markStartDelivered(Context context, long windowStartAt) {
        context.getApplicationContext().getSharedPreferences(DELIVERY_PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_START, windowStartAt).apply();
    }

    private static boolean wasStartDelivered(Context context, long windowStartAt) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(DELIVERY_PREFS, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_START, 0L) == windowStartAt;
    }

    private static long advanceTriggerAt(long windowStartAt) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jerusalem"));
        calendar.setTimeInMillis(windowStartAt);
        int minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        if (minuteOfDay < 8 * 60 || minuteOfDay >= 22 * 60) {
            calendar.add(Calendar.DAY_OF_YEAR, -1);
            calendar.set(Calendar.HOUR_OF_DAY, 21);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            return ReminderScheduler.floorToMinute(calendar.getTimeInMillis());
        }
        return ReminderScheduler.floorToMinute(windowStartAt - HOUR_MILLIS);
    }

    private static PendingIntent pendingIntent(Context context, ScheduledEvent event) {
        Intent intent = new Intent(context, TekufaReceiver.class)
                .putExtra(EXTRA_KIND, event.kind)
                .putExtra(EXTRA_SEASON_INDEX, event.tekufa.seasonIndex)
                .putExtra(EXTRA_LOCAL_MEAN_AT, event.tekufa.localMeanAt)
                .putExtra(EXTRA_OFFICIAL_AT, event.tekufa.officialAt)
                .putExtra(EXTRA_WINDOW_START_AT, event.tekufa.windowStartAt)
                .putExtra(EXTRA_WINDOW_END_AT, event.tekufa.windowEndAt);
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
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } catch (SecurityException exception) {
            AppLog.e(context, "tekufa exact alarm failed", exception);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    static class ScheduledEvent {
        final String kind;
        final long triggerAt;
        final TekufaHelper.Event tekufa;

        ScheduledEvent(String kind, long triggerAt, TekufaHelper.Event tekufa) {
            this.kind = kind == null ? KIND_START : kind;
            this.triggerAt = triggerAt;
            this.tekufa = tekufa;
        }
    }
}
