package com.woodpeckerbros.watchreminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;

import java.util.Calendar;
import java.util.TimeZone;

public class JewishDayScheduler {
    static final String EXTRA_KIND = "jewish_day_kind";
    static final String EXTRA_LABEL = "jewish_day_label";
    static final String EXTRA_TRIGGER_AT = "jewish_day_trigger_at";
    static final String KIND_TODAY_EREV = "today_erev";
    static final String KIND_TOMORROW = "tomorrow";
    private static final String REQUEST_KEY = "jewish_day";
    private static final long HOUR_MILLIS = 60 * 60_000L;

    private JewishDayScheduler() {
    }

    public static void schedule(Context context) {
        cancel(context);
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.jewishMode() || !settings.jewishDayRemindersEnabled()) {
            AppLog.d(context, "jewish day schedule skipped disabled");
            return;
        }
        Event event = nextEvent(context, System.currentTimeMillis());
        if (event == null) {
            AppLog.d(context, "jewish day schedule skipped no event");
            return;
        }
        AppLog.d(context, "jewish day schedule kind=" + event.kind + " label=" + event.label
                + " at=" + NextReminderCalculator.formatDateTime(event.triggerAt));
        setBest(context, event.triggerAt, pendingIntent(context, event));
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent(context, new Event(KIND_TODAY_EREV, "", 0)));
        }
    }

    static Event nextEvent(Context context, long now) {
        ZmanimSettings settings = new ZmanimSettings(context);
        TimeZone timeZone = TimeZone.getTimeZone(settings.timeZoneId());
        Calendar day = Calendar.getInstance(timeZone);
        day.setTimeInMillis(now);
        day.set(Calendar.HOUR_OF_DAY, 12);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);

        Event best = null;
        for (int i = 0; i < 370; i++) {
            Calendar targetDay = (Calendar) day.clone();
            targetDay.add(Calendar.DAY_OF_YEAR, i);
            JewishCalendar jewishCalendar = JewishCalendarHelper.calendar(context, targetDay);
            Event candidate = eventForDay(context, timeZone, targetDay, jewishCalendar);
            if (candidate != null && candidate.triggerAt > now && (best == null || candidate.triggerAt < best.triggerAt)) {
                best = candidate;
            }
        }
        return best;
    }

    private static Event eventForDay(Context context, TimeZone timeZone, Calendar targetDay, JewishCalendar jewishCalendar) {
        EventInfo info = eventInfo(context, jewishCalendar);
        if (info == null) {
            return null;
        }
        if (info.erev) {
            Calendar trigger = (Calendar) targetDay.clone();
            trigger.set(Calendar.HOUR_OF_DAY, 10);
            trigger.set(Calendar.MINUTE, 0);
            trigger.set(Calendar.SECOND, 0);
            trigger.set(Calendar.MILLISECOND, 0);
            return new Event(KIND_TODAY_EREV, info.label, ReminderScheduler.floorToMinute(trigger.getTimeInMillis()));
        }

        Calendar previousDay = (Calendar) targetDay.clone();
        previousDay.add(Calendar.DAY_OF_YEAR, -1);
        long sunset = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_SUNSET, previousDay.getTimeInMillis());
        if (sunset == Long.MAX_VALUE) {
            previousDay.set(Calendar.HOUR_OF_DAY, 17);
            previousDay.set(Calendar.MINUTE, 0);
            previousDay.set(Calendar.SECOND, 0);
            previousDay.set(Calendar.MILLISECOND, 0);
            sunset = previousDay.getTimeInMillis();
        }
        return new Event(KIND_TOMORROW, info.label, ReminderScheduler.floorToMinute(sunset - 2 * HOUR_MILLIS));
    }

    private static EventInfo eventInfo(Context context, JewishCalendar jewishCalendar) {
        if (jewishCalendar.isBeHaB()) {
            return null;
        }
        HebrewDateFormatter formatter = JewishCalendarHelper.formatter(context);
        int index = jewishCalendar.getYomTovIndex();
        if (index != -1 && index != JewishCalendar.BEHAB) {
            String label = formatter.formatYomTov(jewishCalendar);
            if (label == null || label.trim().isEmpty()) {
                return null;
            }
            return new EventInfo(label, isErevIndex(index));
        }
        if (jewishCalendar.isRoshChodesh()) {
            return new EventInfo(formatter.formatRoshChodesh(jewishCalendar), false);
        }
        if (jewishCalendar.isYomKippurKatan()) {
            return new EventInfo(UiText.t(context, "יום כיפור קטן"), false);
        }
        return null;
    }

    private static boolean isErevIndex(int index) {
        return index == JewishCalendar.EREV_PESACH
                || index == JewishCalendar.EREV_SHAVUOS
                || index == JewishCalendar.EREV_ROSH_HASHANA
                || index == JewishCalendar.EREV_YOM_KIPPUR
                || index == JewishCalendar.EREV_SUCCOS;
    }

    private static PendingIntent pendingIntent(Context context, Event event) {
        Intent intent = new Intent(context, JewishDayReceiver.class)
                .putExtra(EXTRA_KIND, event.kind)
                .putExtra(EXTRA_LABEL, event.label)
                .putExtra(EXTRA_TRIGGER_AT, event.triggerAt);
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
            AppLog.e(context, "jewish day exact alarm failed", exception);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    static class Event {
        final String kind;
        final String label;
        final long triggerAt;

        Event(String kind, String label, long triggerAt) {
            this.kind = kind;
            this.label = label == null ? "" : label;
            this.triggerAt = triggerAt;
        }
    }

    private static class EventInfo {
        final String label;
        final boolean erev;

        EventInfo(String label, boolean erev) {
            this.label = label;
            this.erev = erev;
        }
    }
}
