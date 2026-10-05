package com.woodpeckerbros.watchreminder.calendar;

import com.woodpeckerbros.watchreminder.AppLanguage;
import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.UiText;
import com.woodpeckerbros.watchreminder.entitlement.EntitlementAccess;
import com.woodpeckerbros.watchreminder.reminder.ReminderSettings;
import com.woodpeckerbros.watchreminder.zmanim.ZmanimSettings;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** One independent Jewish-times alarm for the next prayer wording transition. */
public final class PrayerSeasonScheduler {
    static final String EXTRA_TRIGGER_AT = "prayer_season_trigger_at";
    static final String EXTRA_EXPIRES_AT = "prayer_season_expires_at";
    static final String EXTRA_KINDS = "prayer_season_kinds";
    static final String EXTRA_START_DAYS = "prayer_season_start_days";
    private static final int REQUEST_CODE = "prayer_season".hashCode();
    private static final int REMINDER_HOUR = 10;

    private PrayerSeasonScheduler() { }

    static void schedule(Context context) {
        cancel(context);
        ReminderSettings settings = new ReminderSettings(context);
        if (!EntitlementAccess.isFeatureAccessGranted(context)
                || !settings.jewishMode() || !settings.jewishDayRemindersEnabled()) return;
        long now = System.currentTimeMillis();
        TimeZone zone = TimeZone.getTimeZone(new ZmanimSettings(context).timeZoneId());
        Calendar today = Calendar.getInstance(zone);
        today.setTimeInMillis(now);
        today.set(Calendar.HOUR_OF_DAY, 12);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        Event dueToday = eventForReminderDay(context, today);
        if (dueToday != null && now >= dueToday.triggerAt && now < dueToday.expiresAt) {
            PrayerSeasonReceiver.deliver(context, dueToday);
        }
        Event next = nextEvent(context, now);
        if (next == null) return;
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms == null) return;
        PendingIntent pending = pendingIntent(context, next);
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerAt, pending);
        } catch (SecurityException error) {
            AppLog.e(context, "prayer season exact alarm unavailable", error);
            alarms.set(AlarmManager.RTC_WAKEUP, next.triggerAt, pending);
        }
        AppLog.d(context, "prayer season scheduled at=" + next.triggerAt
                + " changes=" + next.items.size());
    }

    static void cancel(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms != null) alarms.cancel(pendingIntent(context, null));
    }

    static Event nextEvent(Context context, long now) {
        TimeZone zone = TimeZone.getTimeZone(new ZmanimSettings(context).timeZoneId());
        Calendar day = Calendar.getInstance(zone);
        day.setTimeInMillis(now);
        day.set(Calendar.HOUR_OF_DAY, 12);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        Event best = null;
        for (int offset = 0; offset < 370; offset++) {
            Calendar start = (Calendar) day.clone();
            start.add(Calendar.DAY_OF_YEAR, offset);
            JewishCalendar jewishDay = JewishCalendarHelper.calendar(context, start);
            for (PrayerSeasonChanges.Change change : PrayerSeasonChanges.startingOn(jewishDay)) {
                Calendar reminder = civilDate(zone, PrayerSeasonChanges.reminderDay(jewishDay));
                reminder.set(Calendar.HOUR_OF_DAY, REMINDER_HOUR);
                long triggerAt = reminder.getTimeInMillis();
                if (triggerAt <= now) continue;
                if (best == null || triggerAt < best.triggerAt) {
                    best = new Event(triggerAt, endOfDay(reminder), new ArrayList<>());
                }
                if (triggerAt == best.triggerAt) {
                    best.items.add(new Item(change.kind, start.getTimeInMillis()));
                }
            }
        }
        return best;
    }

    static boolean dispatchMissedIfDueNow(Context context) {
        ReminderSettings settings = new ReminderSettings(context);
        if (!EntitlementAccess.isFeatureAccessGranted(context)
                || !settings.jewishMode() || !settings.jewishDayRemindersEnabled()) return false;
        long now = System.currentTimeMillis();
        TimeZone zone = TimeZone.getTimeZone(new ZmanimSettings(context).timeZoneId());
        Calendar today = Calendar.getInstance(zone);
        today.setTimeInMillis(now);
        today.set(Calendar.HOUR_OF_DAY, 12);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        Event event = eventForReminderDay(context, today);
        if (event == null || now < event.triggerAt || now >= event.expiresAt) return false;
        boolean delivered = PrayerSeasonReceiver.deliver(context, event);
        if (delivered) JewishDayScheduler.schedule(context);
        return delivered;
    }

    private static Event eventForReminderDay(Context context, Calendar reminderDay) {
        TimeZone zone = reminderDay.getTimeZone();
        Calendar reminder = (Calendar) reminderDay.clone();
        reminder.set(Calendar.HOUR_OF_DAY, REMINDER_HOUR);
        Event event = new Event(reminder.getTimeInMillis(), endOfDay(reminder), new ArrayList<>());
        for (int offset = 0; offset <= 8; offset++) {
            Calendar start = (Calendar) reminderDay.clone();
            start.add(Calendar.DAY_OF_YEAR, offset);
            JewishCalendar jewishDay = JewishCalendarHelper.calendar(context, start);
            for (PrayerSeasonChanges.Change change : PrayerSeasonChanges.startingOn(jewishDay)) {
                Calendar changeReminder = civilDate(zone, PrayerSeasonChanges.reminderDay(jewishDay));
                if (sameCivilDay(reminderDay, changeReminder)) {
                    event.items.add(new Item(change.kind, start.getTimeInMillis()));
                }
            }
        }
        return event.items.isEmpty() ? null : event;
    }

    private static boolean sameCivilDay(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    static String message(Context context, Item item) {
        boolean english = AppLanguage.isEnglish(context);
        String day = startDayLabel(context, item.startDay);
        String wording;
        switch (item.kind) {
            case MASHIV_HARUACH: wording = "משיב הרוח ומוריד הגשם"; break;
            case MORID_HATAL: wording = "מוריד הטל"; break;
            case BARECH_ALEINU: wording = "ברך עלינו"; break;
            default: wording = "ברכנו";
        }
        if (english) {
            String englishWording;
            switch (item.kind) {
                case MASHIV_HARUACH: englishWording = "Mashiv Haruach Umorid Hageshem"; break;
                case MORID_HATAL: englishWording = "Morid Hatal"; break;
                case BARECH_ALEINU: englishWording = "Barech Aleinu"; break;
                default: englishWording = "Barchenu";
            }
            return "From " + (prayer(item.kind) == PrayerSeasonChanges.Prayer.MUSAF
                    ? "Musaf on " : "Maariv on the evening of ") + day + ": " + englishWording;
        }
        return "מ" + (prayer(item.kind) == PrayerSeasonChanges.Prayer.MUSAF
                ? "תפילת מוסף ב" : "תפילת ערבית בערב ") + day
                + " מתחילים לומר ״" + wording + "״";
    }

    /** The prayer change is easier to verify when its civil and Hebrew dates travel together. */
    private static String startDayLabel(Context context, long startDay) {
        TimeZone zone = TimeZone.getTimeZone(new ZmanimSettings(context).timeZoneId());
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(startDay);
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        SimpleDateFormat gregorian = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        gregorian.setTimeZone(zone);
        String hebrewDate = JewishCalendarHelper.formatter(context)
                .format(JewishCalendarHelper.calendar(context, calendar));
        if (AppLanguage.isEnglish(context)) {
            String weekday = new DateFormatSymbols(Locale.US).getWeekdays()
                    [calendar.get(Calendar.DAY_OF_WEEK)];
            return weekday + ", " + gregorian.format(calendar.getTime()) + " (" + hebrewDate + ")";
        }
        String weekday = new String[]{"", "א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳"}
                [calendar.get(Calendar.DAY_OF_WEEK)];
        return "יום " + weekday + ", " + hebrewDate + " (" + gregorian.format(calendar.getTime()) + ")";
    }

    /** Shows both the real first-prayer day and an earlier non-rest reminder day. */
    public static List<String> displayForDay(Context context, long selectedDayMillis) {
        TimeZone zone = TimeZone.getTimeZone(new ZmanimSettings(context).timeZoneId());
        Calendar selected = Calendar.getInstance(zone);
        selected.setTimeInMillis(selectedDayMillis);
        selected.set(Calendar.HOUR_OF_DAY, 12);
        selected.set(Calendar.MINUTE, 0);
        selected.set(Calendar.SECOND, 0);
        selected.set(Calendar.MILLISECOND, 0);
        List<String> rows = new ArrayList<>();
        for (int offset = -1; offset <= 8; offset++) {
            Calendar start = (Calendar) selected.clone();
            start.add(Calendar.DAY_OF_YEAR, offset);
            JewishCalendar jewishDay = JewishCalendarHelper.calendar(context, start);
            for (PrayerSeasonChanges.Change change : PrayerSeasonChanges.startingOn(jewishDay)) {
                Calendar reminder = civilDate(zone, PrayerSeasonChanges.reminderDay(jewishDay));
                boolean startsToday = sameCivilDay(selected, start);
                boolean startedLastNight = offset == -1
                        && change.prayer == PrayerSeasonChanges.Prayer.MAARIV;
                if (!startsToday && !startedLastNight && !sameCivilDay(selected, reminder)) continue;
                String prefix = AppLanguage.isEnglish(context)
                        ? startsToday ? "Today · " : startedLastNight ? "Since last night · "
                            : "Advance notice · "
                        : startsToday ? "היום · " : startedLastNight ? "מאמש · "
                            : "תזכורת מראש · ";
                String detail = message(context, new Item(change.kind, start.getTimeInMillis()));
                if (startedLastNight && !AppLanguage.isEnglish(context)) {
                    detail = detail.replace("מתחילים לומר", "התחילו לומר");
                }
                rows.add(prefix + detail);
            }
        }
        return rows;
    }

    static String title(Context context) {
        return UiText.t(context, "ימים יהודיים");
    }

    static PrayerSeasonChanges.Prayer prayer(PrayerSeasonChanges.Kind kind) {
        return kind == PrayerSeasonChanges.Kind.MASHIV_HARUACH
                || kind == PrayerSeasonChanges.Kind.MORID_HATAL
                ? PrayerSeasonChanges.Prayer.MUSAF : PrayerSeasonChanges.Prayer.MAARIV;
    }

    private static Calendar civilDate(TimeZone zone, JewishCalendar day) {
        Calendar result = Calendar.getInstance(zone);
        result.set(day.getGregorianYear(), day.getGregorianMonth(), day.getGregorianDayOfMonth(), 12, 0, 0);
        result.set(Calendar.MILLISECOND, 0);
        return result;
    }

    private static long endOfDay(Calendar reminder) {
        Calendar end = (Calendar) reminder.clone();
        end.add(Calendar.DAY_OF_YEAR, 1);
        end.set(Calendar.HOUR_OF_DAY, 0);
        end.set(Calendar.MINUTE, 0);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);
        return end.getTimeInMillis();
    }

    private static PendingIntent pendingIntent(Context context, Event event) {
        Intent intent = new Intent(context, PrayerSeasonReceiver.class);
        if (event != null) {
            String[] kinds = new String[event.items.size()];
            long[] days = new long[event.items.size()];
            for (int i = 0; i < event.items.size(); i++) {
                kinds[i] = event.items.get(i).kind.name();
                days[i] = event.items.get(i).startDay;
            }
            intent.putExtra(EXTRA_KINDS, kinds)
                    .putExtra(EXTRA_START_DAYS, days)
                    .putExtra(EXTRA_TRIGGER_AT, event.triggerAt)
                    .putExtra(EXTRA_EXPIRES_AT, event.expiresAt);
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static final class Item {
        final PrayerSeasonChanges.Kind kind;
        final long startDay;
        Item(PrayerSeasonChanges.Kind kind, long startDay) {
            this.kind = kind;
            this.startDay = startDay;
        }
    }

    static final class Event {
        final long triggerAt, expiresAt;
        final List<Item> items;
        Event(long triggerAt, long expiresAt, List<Item> items) {
            this.triggerAt = triggerAt;
            this.expiresAt = expiresAt;
            this.items = items;
        }
    }
}
