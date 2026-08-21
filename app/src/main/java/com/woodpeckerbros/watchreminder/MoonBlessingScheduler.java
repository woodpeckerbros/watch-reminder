package com.woodpeckerbros.watchreminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;
import java.util.TimeZone;

public class MoonBlessingScheduler {
    static final String EXTRA_KIND = "moon_kind";
    static final String EXTRA_MONTH_KEY = "moon_month_key";
    static final String EXTRA_TRIGGER_AT = "moon_trigger_at";
    static final String EXTRA_RETRY = "moon_retry";
    static final String KIND_PRE_START = "pre_start";
    static final String KIND_QUESTION = "question";
    static final String KIND_LAST_NIGHT = "last_night";
    static final String ACTION_YES = "com.woodpeckerbros.watchreminder.MOON_BLESSING_YES";
    static final String ACTION_NO = "com.woodpeckerbros.watchreminder.MOON_BLESSING_NO";
    private static final String REQUEST_KEY = "moon_blessing";
    private static final long HOUR_MILLIS = 60 * 60_000L;

    private MoonBlessingScheduler() {
    }

    public static void schedule(Context context) {
        cancel(context);
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.jewishMode() || !settings.moonBlessingEnabled()) {
            AppLog.d(context, "moon blessing schedule skipped disabled");
            return;
        }
        Event event = nextEvent(context, System.currentTimeMillis());
        if (event == null) {
            AppLog.d(context, "moon blessing schedule skipped no event");
            return;
        }
        AppLog.d(context, "moon blessing schedule kind=" + event.kind + " at=" + NextReminderCalculator.formatDateTime(event.triggerAt));
        setBest(context, event.triggerAt, pendingIntent(context, event.kind, event.monthKey, event.triggerAt));
    }

    public static void cancel(Context context) {
        cancelScheduledEvent(context);
        cancelRetry(context);
    }

    private static void cancelScheduledEvent(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent(context, KIND_PRE_START, "", 0));
            alarmManager.cancel(pendingIntent(context, KIND_QUESTION, "", 0));
            alarmManager.cancel(pendingIntent(context, KIND_LAST_NIGHT, "", 0));
        }
    }

    static void scheduleRetry(Context context, String kind, String monthKey, long originalTriggerAt, int minutes) {
        long retryAt = ReminderScheduler.ceilToMinute(System.currentTimeMillis() + Math.max(1, minutes) * 60_000L);
        AppLog.d(context, "moon blessing pre-start retry original="
                + NextReminderCalculator.formatDateTime(originalTriggerAt)
                + " at=" + NextReminderCalculator.formatDateTime(retryAt));
        setBest(context, retryAt, retryPendingIntent(context, kind, monthKey, originalTriggerAt));
    }

    static void cancelRetry(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(retryPendingIntent(context, KIND_PRE_START, "", 0L));
            alarmManager.cancel(retryPendingIntent(context, KIND_QUESTION, "", 0L));
            alarmManager.cancel(retryPendingIntent(context, KIND_LAST_NIGHT, "", 0L));
        }
    }

    static Event nextEvent(Context context, long now) {
        MoonBlessingStore store = new MoonBlessingStore(context);
        ZmanimSettings settings = new ZmanimSettings(context);
        TimeZone timeZone = TimeZone.getTimeZone(settings.timeZoneId());
        Calendar cursor = Calendar.getInstance(timeZone);
        cursor.setTimeInMillis(now - 2 * 24 * HOUR_MILLIS);
        cursor.set(Calendar.HOUR_OF_DAY, 12);
        cursor.set(Calendar.MINUTE, 0);
        cursor.set(Calendar.SECOND, 0);
        cursor.set(Calendar.MILLISECOND, 0);

        Event best = null;
        for (int i = 0; i < 90; i += 20) {
            Calendar probe = (Calendar) cursor.clone();
            probe.add(Calendar.DAY_OF_YEAR, i);
            MoonBlessingHelper.Window window = MoonBlessingHelper.windowFor(context, probe.getTimeInMillis());
            String key = MoonBlessingHelper.monthKey(window);
            if (store.isHandled(key)) {
                continue;
            }
            Event candidate = nextEventForWindow(context, timeZone, window, key, now);
            if (candidate != null && (best == null || candidate.triggerAt < best.triggerAt)) {
                best = candidate;
            }
        }
        return best;
    }

    private static Event nextEventForWindow(Context context, TimeZone timeZone, MoonBlessingHelper.Window window, String key, long now) {
        long preStart = ReminderScheduler.floorToMinute(window.startAt - HOUR_MILLIS);
        if (preStart > now) {
            return new Event(KIND_PRE_START, key, preStart, window);
        }

        Calendar day = Calendar.getInstance(timeZone);
        day.setTimeInMillis(Math.max(now, window.startAt));
        day.set(Calendar.HOUR_OF_DAY, 12);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);

        for (int i = 0; i < 40; i++) {
            if (!MoonBlessingHelper.isBlockedNightForTimeZone(context, timeZone, day.getTimeInMillis())) {
                long questionAt = MoonBlessingHelper.questionTimeForNight(context, window, day.getTimeInMillis());
                if (questionAt != Long.MAX_VALUE && questionAt <= window.endAt && questionAt > now) {
                    String kind = MoonBlessingHelper.isLastNight(context, window, day.getTimeInMillis())
                            ? KIND_LAST_NIGHT
                            : KIND_QUESTION;
                    return new Event(kind, key, questionAt, window);
                }
            }
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
        return null;
    }

    private static PendingIntent pendingIntent(Context context, String kind, String monthKey, long triggerAt) {
        Intent intent = new Intent(context, MoonBlessingReceiver.class)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_MONTH_KEY, monthKey)
                .putExtra(EXTRA_TRIGGER_AT, triggerAt);
        return PendingIntent.getBroadcast(
                context,
                (REQUEST_KEY + ":" + kind).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    static PendingIntent actionIntent(Context context, String action, String monthKey) {
        Intent intent = new Intent(context, MoonBlessingReceiver.class)
                .setAction(action)
                .putExtra(EXTRA_MONTH_KEY, monthKey);
        return PendingIntent.getBroadcast(
                context,
                (REQUEST_KEY + ":" + action).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent retryPendingIntent(Context context, String kind, String monthKey, long originalTriggerAt) {
        Intent intent = new Intent(context, MoonBlessingReceiver.class)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_MONTH_KEY, monthKey)
                .putExtra(EXTRA_TRIGGER_AT, originalTriggerAt)
                .putExtra(EXTRA_RETRY, true);
        return PendingIntent.getBroadcast(
                context,
                (REQUEST_KEY + ":retry:" + kind).hashCode(),
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
            AppLog.e(context, "moon blessing exact alarm failed", exception);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    static class Event {
        final String kind;
        final String monthKey;
        final long triggerAt;
        final MoonBlessingHelper.Window window;

        Event(String kind, String monthKey, long triggerAt, MoonBlessingHelper.Window window) {
            this.kind = kind;
            this.monthKey = monthKey;
            this.triggerAt = triggerAt;
            this.window = window;
        }
    }
}
