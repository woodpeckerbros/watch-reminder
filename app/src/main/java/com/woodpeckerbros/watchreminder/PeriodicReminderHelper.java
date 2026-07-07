package com.woodpeckerbros.watchreminder;

import android.content.Context;

import com.kosherjava.zmanim.hebrewcalendar.JewishDate;

import java.util.Calendar;

public class PeriodicReminderHelper {
    private PeriodicReminderHelper() {
    }

    public static Occurrence next(Context context, Reminder reminder, ReminderEventStore eventStore, boolean applyQuietTime) {
        long now = System.currentTimeMillis();
        Occurrence occurrence = firstAfter(context, reminder, now, applyQuietTime);
        for (int i = 0; i < 400 && occurrence != null && shouldSkip(reminder.id, occurrence.scheduledAt, eventStore); i++) {
            occurrence = firstAfter(context, reminder, occurrence.originalAt + 60_000L, applyQuietTime);
        }
        return occurrence;
    }

    public static Occurrence between(Context context, long from, long to, Reminder reminder) {
        Occurrence occurrence = firstAfter(context, reminder, from, true);
        if (occurrence != null && occurrence.scheduledAt <= to) {
            return occurrence;
        }
        return null;
    }

    public static long startMillis(Reminder reminder) {
        Calendar calendar = reminder.periodicHebrew
                ? hebrewToGregorian(reminder.periodicStartYear, reminder.periodicStartMonth, reminder.periodicStartDay)
                : Calendar.getInstance();
        if (!reminder.periodicHebrew) {
            calendar.set(Calendar.YEAR, reminder.periodicStartYear);
            calendar.set(Calendar.MONTH, Math.max(1, reminder.periodicStartMonth) - 1);
            calendar.set(Calendar.DAY_OF_MONTH, safeGregorianDay(reminder.periodicStartYear, reminder.periodicStartMonth, reminder.periodicStartDay));
        }
        calendar.set(Calendar.HOUR_OF_DAY, reminder.hour);
        calendar.set(Calendar.MINUTE, reminder.minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static Occurrence firstAfter(Context context, Reminder reminder, long after, boolean applyQuietTime) {
        if (!reminder.isPeriodic()) {
            return null;
        }
        String unit = reminder.periodicUnit;
        if (Reminder.PERIOD_UNIT_MONTHS.equals(unit) && reminder.periodicHebrew) {
            return firstHebrewMonthAfter(context, reminder, after, applyQuietTime);
        }
        if (Reminder.PERIOD_UNIT_YEARS.equals(unit) && reminder.periodicHebrew) {
            return firstHebrewYearAfter(context, reminder, after, applyQuietTime);
        }
        if (Reminder.PERIOD_UNIT_HOURS.equals(unit)) {
            return firstDailyHourlyAfter(context, reminder, after, applyQuietTime);
        }
        Calendar candidate = Calendar.getInstance();
        candidate.setTimeInMillis(startMillis(reminder));
        candidate.set(Calendar.SECOND, 0);
        candidate.set(Calendar.MILLISECOND, 0);
        while (true) {
            long originalAt = ReminderScheduler.floorToMinute(candidate.getTimeInMillis());
            long scheduledAt = ReminderScheduler.floorToMinute(applyQuietTime ? QuietTimeHelper.adjust(context, originalAt, reminder) : originalAt);
            if (scheduledAt > after) {
                return new Occurrence(scheduledAt, originalAt);
            }
            add(candidate, reminder);
            if (candidate.getTimeInMillis() - after > 10L * 365 * 24 * 60 * 60_000L) {
                return null;
            }
        }
    }

    private static Occurrence firstDailyHourlyAfter(Context context, Reminder reminder, long after, boolean applyQuietTime) {
        Calendar dayStart = Calendar.getInstance();
        dayStart.setTimeInMillis(startMillis(reminder));
        dayStart.set(Calendar.SECOND, 0);
        dayStart.set(Calendar.MILLISECOND, 0);

        Calendar afterDay = Calendar.getInstance();
        afterDay.setTimeInMillis(after);
        afterDay.set(Calendar.HOUR_OF_DAY, reminder.hour);
        afterDay.set(Calendar.MINUTE, reminder.minute);
        afterDay.set(Calendar.SECOND, 0);
        afterDay.set(Calendar.MILLISECOND, 0);
        if (afterDay.getTimeInMillis() < dayStart.getTimeInMillis()) {
            afterDay.setTimeInMillis(dayStart.getTimeInMillis());
        }

        long intervalMs = Math.max(1, reminder.periodicInterval) * 60L * 60_000L;
        for (int checked = 0; checked < 3660; checked++) {
            Calendar windowStart = (Calendar) afterDay.clone();
            windowStart.set(Calendar.HOUR_OF_DAY, reminder.hour);
            windowStart.set(Calendar.MINUTE, reminder.minute);
            windowStart.set(Calendar.SECOND, 0);
            windowStart.set(Calendar.MILLISECOND, 0);

            Calendar windowEnd = (Calendar) windowStart.clone();
            windowEnd.set(Calendar.HOUR_OF_DAY, reminder.periodicEndHour);
            windowEnd.set(Calendar.MINUTE, reminder.periodicEndMinute);
            if (windowEnd.before(windowStart)) {
                windowEnd.setTimeInMillis(windowStart.getTimeInMillis());
            }

            long candidateAt = windowStart.getTimeInMillis();
            if (candidateAt <= after) {
                long diff = after - candidateAt;
                long steps = diff / intervalMs + 1;
                candidateAt += steps * intervalMs;
            }

            while (candidateAt <= windowEnd.getTimeInMillis()) {
                long originalAt = ReminderScheduler.floorToMinute(candidateAt);
                long scheduledAt = ReminderScheduler.floorToMinute(applyQuietTime ? QuietTimeHelper.adjust(context, originalAt, reminder) : originalAt);
                if (scheduledAt > after) {
                    return new Occurrence(scheduledAt, originalAt);
                }
                candidateAt += intervalMs;
            }

            afterDay.add(Calendar.DAY_OF_YEAR, 1);
            if (afterDay.getTimeInMillis() - after > 10L * 365 * 24 * 60 * 60_000L) {
                return null;
            }
        }
        return null;
    }

    private static Occurrence firstHebrewYearAfter(Context context, Reminder reminder, long after, boolean applyQuietTime) {
        int startYear = Math.max(1, reminder.periodicStartYear);
        JewishDate today = new JewishDate(Calendar.getInstance());
        int year = Math.max(startYear, today.getJewishYear() - 1);
        for (int i = 0; i < 30; i++) {
            int candidateYear = startYear + Math.max(0, ((year - startYear + reminder.periodicInterval - 1) / reminder.periodicInterval)) * reminder.periodicInterval;
            Occurrence occurrence = hebrewOccurrence(context, reminder, candidateYear, reminder.periodicStartMonth, after, applyQuietTime);
            if (occurrence != null) {
                return occurrence;
            }
            year = candidateYear + reminder.periodicInterval;
        }
        return null;
    }

    private static Occurrence firstHebrewMonthAfter(Context context, Reminder reminder, long after, boolean applyQuietTime) {
        int year = Math.max(1, reminder.periodicStartYear);
        int month = Math.max(1, reminder.periodicStartMonth);
        int checked = 0;
        while (checked < 240) {
            Occurrence occurrence = hebrewOccurrence(context, reminder, year, month, after, applyQuietTime);
            if (occurrence != null) {
                return occurrence;
            }
            for (int i = 0; i < reminder.periodicInterval; i++) {
                month++;
                if (month > lastHebrewMonth(year)) {
                    year++;
                    month = JewishDate.NISSAN;
                }
            }
            checked++;
        }
        return null;
    }

    private static Occurrence hebrewOccurrence(Context context, Reminder reminder, int year, int month, long after, boolean applyQuietTime) {
        int safeMonth = safeHebrewMonth(year, month);
        JewishDate date = new JewishDate(year, safeMonth, 1);
        int day = Math.min(Math.max(1, reminder.periodicStartDay), date.getDaysInJewishMonth());
        date.setJewishDate(year, safeMonth, day);
        Calendar calendar = date.getGregorianCalendar();
        calendar.set(Calendar.HOUR_OF_DAY, reminder.hour);
        calendar.set(Calendar.MINUTE, reminder.minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long originalAt = ReminderScheduler.floorToMinute(calendar.getTimeInMillis());
        long scheduledAt = ReminderScheduler.floorToMinute(applyQuietTime ? QuietTimeHelper.adjust(context, originalAt, reminder) : originalAt);
        return scheduledAt > after ? new Occurrence(scheduledAt, originalAt) : null;
    }

    private static Calendar hebrewToGregorian(int year, int month, int day) {
        int safeYear = Math.max(1, year);
        int safeMonth = safeHebrewMonth(safeYear, month);
        JewishDate date = new JewishDate(safeYear, safeMonth, 1);
        date.setJewishDate(safeYear, safeMonth, Math.min(Math.max(1, day), date.getDaysInJewishMonth()));
        return date.getGregorianCalendar();
    }

    private static void add(Calendar calendar, Reminder reminder) {
        switch (reminder.periodicUnit) {
            case Reminder.PERIOD_UNIT_HOURS:
                calendar.add(Calendar.HOUR_OF_DAY, reminder.periodicInterval);
                break;
            case Reminder.PERIOD_UNIT_WEEKS:
                calendar.add(Calendar.WEEK_OF_YEAR, reminder.periodicInterval);
                break;
            case Reminder.PERIOD_UNIT_MONTHS:
                calendar.add(Calendar.MONTH, reminder.periodicInterval);
                break;
            case Reminder.PERIOD_UNIT_YEARS:
                calendar.add(Calendar.YEAR, reminder.periodicInterval);
                break;
            case Reminder.PERIOD_UNIT_DAYS:
            default:
                calendar.add(Calendar.DAY_OF_YEAR, reminder.periodicInterval);
                break;
        }
    }

    private static int safeGregorianDay(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, Math.max(1, month) - 1);
        return Math.min(Math.max(1, day), calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
    }

    private static int safeHebrewMonth(int year, int month) {
        if (month == JewishDate.ADAR_II && !isJewishLeapYear(year)) {
            return JewishDate.ADAR;
        }
        return Math.min(Math.max(JewishDate.NISSAN, month), lastHebrewMonth(year));
    }

    private static int lastHebrewMonth(int year) {
        return isJewishLeapYear(year) ? JewishDate.ADAR_II : JewishDate.ADAR;
    }

    private static boolean isJewishLeapYear(int year) {
        return new JewishDate(year, JewishDate.NISSAN, 1).isJewishLeapYear();
    }

    private static boolean shouldSkip(String reminderId, long scheduledAt, ReminderEventStore eventStore) {
        if (eventStore == null) {
            return false;
        }
        ReminderEventStore.Event event = eventStore.findReminderOccurrence(reminderId, scheduledAt);
        return event != null
                && ReminderEventStore.STATUS_DONE.equals(event.status)
                && ReminderEventStore.NOTE_EARLY_DONE.equals(event.note)
                && event.scheduledAt > System.currentTimeMillis();
    }

    public static class Occurrence {
        public final long scheduledAt;
        public final long originalAt;

        Occurrence(long scheduledAt, long originalAt) {
            this.scheduledAt = scheduledAt;
            this.originalAt = originalAt;
        }
    }
}
