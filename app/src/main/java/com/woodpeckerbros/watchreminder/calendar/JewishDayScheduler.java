package com.woodpeckerbros.watchreminder.calendar;

import com.woodpeckerbros.watchreminder.reminder.*;

import com.woodpeckerbros.watchreminder.zmanim.*;

import com.woodpeckerbros.watchreminder.*;
import com.woodpeckerbros.watchreminder.entitlement.EntitlementAccess;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;

import java.util.Calendar;
import java.util.TimeZone;

public class JewishDayScheduler {
    static final String EXTRA_KIND = "jewish_day_kind";
    static final String EXTRA_LABEL = "jewish_day_label";
    static final String EXTRA_TRIGGER_AT = "jewish_day_trigger_at";
    static final String EXTRA_EVENT_DAY = "jewish_day_event_day";
    public static final String KIND_TODAY_EREV = "today_erev";
    static final String KIND_TOMORROW = "tomorrow";
    static final String KIND_TODAY = "today";
    private static final String REQUEST_KEY = "jewish_day";
    private static final String DELIVERY_PREFS = "jewish_day_delivery";
    private static final String KEY_LAST_DELIVERED = "last_delivered";
    private static final long HOUR_MILLIS = 60 * 60_000L;

    private JewishDayScheduler() {
    }

    public static void schedule(Context context) {
        if (!EntitlementAccess.isFeatureAccessGranted(context)) { cancel(context); return; }
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

    public static Event nextEvent(Context context, long now) {
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
            return new Event(KIND_TODAY_EREV, info.label,
                    ReminderScheduler.floorToMinute(trigger.getTimeInMillis()), targetDay.getTimeInMillis());
        }

        Calendar previousDay = (Calendar) targetDay.clone();
        previousDay.add(Calendar.DAY_OF_YEAR, -1);
        long tzeis = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_TZAIS, previousDay.getTimeInMillis());
        if (tzeis == Long.MAX_VALUE) {
            previousDay.set(Calendar.HOUR_OF_DAY, 17);
            previousDay.set(Calendar.MINUTE, 0);
            previousDay.set(Calendar.SECOND, 0);
            previousDay.set(Calendar.MILLISECOND, 0);
            tzeis = previousDay.getTimeInMillis() + 3 * HOUR_MILLIS;
        }
        return new Event(KIND_TOMORROW, info.label,
                ReminderScheduler.floorToMinute(dayBeforeReminderAt(tzeis)), targetDay.getTimeInMillis());
    }

    static long dayBeforeReminderAt(long tzeisAt) {
        return tzeisAt - 3 * HOUR_MILLIS;
    }

    /**
     * Delivers a still-relevant Jewish-day event once when its advance alarm was lost while the
     * watch was powered down.  The receiver records delivery, so foreground/Health callbacks
     * cannot replay the same event.
     */
    public static boolean dispatchMissedIfDueNow(Context context) {
        if (!EntitlementAccess.isFeatureAccessGranted(context)) {
            return false;
        }
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.jewishMode() || !settings.jewishDayRemindersEnabled()) {
            return false;
        }
        ZmanimSettings zmanimSettings = new ZmanimSettings(context);
        TimeZone timeZone = TimeZone.getTimeZone(zmanimSettings.timeZoneId());
        Calendar today = Calendar.getInstance(timeZone);
        today.setTimeInMillis(System.currentTimeMillis());
        today.set(Calendar.HOUR_OF_DAY, 12);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        Event event = eventForDay(context, timeZone, today,
                JewishCalendarHelper.calendar(context, today));
        long now = System.currentTimeMillis();
        if (event == null || event.triggerAt > now || !isStillRelevant(context, today, now)) {
            return false;
        }
        String deliveryKey = deliveryKey(today.getTimeInMillis(), event.kind, event.label);
        if (wasDelivered(context, deliveryKey)) {
            return false;
        }
        JewishDayReceiver.showNotification(context, KIND_TODAY, event.label);
        markDelivered(context, deliveryKey);
        AppLog.d(context, "jewish day catch-up delivered label=" + event.label);
        return true;
    }

    private static boolean isStillRelevant(Context context, Calendar day, long now) {
        long tzeis = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_TZAIS, day.getTimeInMillis());
        if (tzeis != Long.MAX_VALUE) {
            return now <= tzeis;
        }
        Calendar end = (Calendar) day.clone();
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        return now <= end.getTimeInMillis();
    }

    static void markDelivered(Context context, long eventDay, String kind, String label) {
        markDelivered(context, deliveryKey(eventDay, kind, label));
    }

    private static void markDelivered(Context context, String key) {
        deliveryPrefs(context).edit().putString(KEY_LAST_DELIVERED, key).apply();
    }

    private static boolean wasDelivered(Context context, String key) {
        return key.equals(deliveryPrefs(context).getString(KEY_LAST_DELIVERED, ""));
    }

    private static SharedPreferences deliveryPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(DELIVERY_PREFS, Context.MODE_PRIVATE);
    }

    private static String deliveryKey(long eventDay, String kind, String label) {
        return ReminderScheduler.floorToMinute(eventDay) + ":" + kind + ":" + label;
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
                .putExtra(EXTRA_TRIGGER_AT, event.triggerAt)
                .putExtra(EXTRA_EVENT_DAY, event.eventDay);
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
            AppLog.e(context, "jewish day exact alarm failed", exception);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    public static class Event {
        public final String kind;
        public final String label;
        public final long triggerAt;
        final long eventDay;

        Event(String kind, String label, long triggerAt) {
            this(kind, label, triggerAt, triggerAt);
        }

        Event(String kind, String label, long triggerAt, long eventDay) {
            this.kind = kind;
            this.label = label == null ? "" : label;
            this.triggerAt = triggerAt;
            this.eventDay = eventDay;
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
