package com.woodpeckerbros.watchreminder;

import android.content.Context;

import com.kosherjava.zmanim.hebrewcalendar.JewishDate;

import java.util.Calendar;

public class AnnualReminderHelper {
    private AnnualReminderHelper() {
    }

    public static Occurrence next(Context context, Reminder reminder, ReminderEventStore eventStore, boolean applyQuietTime) {
        long now = System.currentTimeMillis();
        Occurrence occurrence = firstAfter(context, reminder, now, applyQuietTime);
        for (int i = 0; i < 20 && occurrence != null && shouldSkip(reminder.id, occurrence.scheduledAt, eventStore); i++) {
            occurrence = firstAfter(context, reminder, occurrence.scheduledAt + 60_000L, applyQuietTime);
        }
        return occurrence;
    }

    public static Occurrence between(Context context, long from, long to, Reminder reminder) {
        Occurrence occurrence = firstAfter(context, reminder, from, true);
        return occurrence != null && occurrence.scheduledAt <= to ? occurrence : null;
    }

    public static int baseCounterYear(Reminder reminder) {
        Occurrence occurrence = firstAfter(null, reminder, System.currentTimeMillis(), false);
        if (occurrence == null) {
            Calendar calendar = Calendar.getInstance();
            return reminder.annualHebrew ? new JewishDate(calendar).getJewishYear() : calendar.get(Calendar.YEAR);
        }
        return occurrence.eventYear;
    }

    public static String displayName(Reminder reminder, long originalScheduledAt) {
        String name = reminder.name == null || reminder.name.trim().isEmpty() ? "אירוע שנתי" : reminder.name.trim();
        if (reminder.annualCounter <= 0) {
            return name;
        }
        int eventYear = eventYear(reminder, originalScheduledAt);
        int baseYear = reminder.annualCounterYear == 0 ? eventYear : reminder.annualCounterYear;
        int number = Math.max(1, reminder.annualCounter + Math.max(0, eventYear - baseYear));
        return name + " (" + number + ")";
    }

    private static Occurrence firstAfter(Context context, Reminder reminder, long after, boolean applyQuietTime) {
        if (!reminder.isAnnualEvent()) {
            return null;
        }
        Calendar now = Calendar.getInstance();
        int currentYear = reminder.annualHebrew ? new JewishDate(now).getJewishYear() : now.get(Calendar.YEAR);
        for (int i = -1; i <= 10; i++) {
            int year = currentYear + i;
            Calendar main = annualMain(reminder, year);
            Occurrence early = occurrenceFor(context, reminder, main, year, true, applyQuietTime);
            if (early.scheduledAt > after) {
                return early;
            }
            Occurrence exact = occurrenceFor(context, reminder, main, year, false, applyQuietTime);
            if (exact.scheduledAt > after) {
                return exact;
            }
        }
        return null;
    }

    private static Occurrence occurrenceFor(Context context, Reminder reminder, Calendar main, int eventYear, boolean early, boolean applyQuietTime) {
        long originalAt = originalTime(context, reminder, main);
        long scheduledAt = originalAt;
        if (early && reminder.annualAdvanceHours > 0) {
            scheduledAt -= reminder.annualAdvanceHours * 60L * 60_000L;
        }
        scheduledAt = ReminderScheduler.floorToMinute(applyQuietTime ? QuietTimeHelper.adjust(context, scheduledAt, reminder) : scheduledAt);
        return new Occurrence(scheduledAt, originalAt, eventYear, early && reminder.annualAdvanceHours > 0);
    }

    private static long originalTime(Context context, Reminder reminder, Calendar main) {
        if (context != null && reminder.useZmanim) {
            long zmanAt = ZmanimHelper.timeFor(context, reminder, main.getTimeInMillis());
            if (zmanAt != Long.MAX_VALUE) {
                return ReminderScheduler.floorToMinute(zmanAt);
            }
        }
        return ReminderScheduler.floorToMinute(main.getTimeInMillis());
    }

    private static Calendar annualMain(Reminder reminder, int year) {
        Calendar calendar;
        if (reminder.annualHebrew) {
            int month = safeHebrewMonth(year, reminder.annualMonth);
            JewishDate date = new JewishDate(year, month, 1);
            int day = Math.min(Math.max(1, reminder.annualDay), date.getDaysInJewishMonth());
            date.setJewishDate(year, month, day);
            calendar = date.getGregorianCalendar();
        } else {
            calendar = Calendar.getInstance();
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, Math.max(1, reminder.annualMonth) - 1);
            calendar.set(Calendar.DAY_OF_MONTH, safeGregorianDay(year, reminder.annualMonth, reminder.annualDay));
        }
        calendar.set(Calendar.HOUR_OF_DAY, reminder.hour);
        calendar.set(Calendar.MINUTE, reminder.minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private static int eventYear(Reminder reminder, long originalScheduledAt) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(originalScheduledAt);
        return reminder.annualHebrew ? new JewishDate(calendar).getJewishYear() : calendar.get(Calendar.YEAR);
    }

    private static int safeGregorianDay(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, Math.max(1, month) - 1);
        return Math.min(Math.max(1, day), calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
    }

    private static int safeHebrewMonth(int year, int month) {
        if (month == JewishDate.ADAR_II && !new JewishDate(year, JewishDate.NISSAN, 1).isJewishLeapYear()) {
            return JewishDate.ADAR;
        }
        int lastMonth = new JewishDate(year, JewishDate.NISSAN, 1).isJewishLeapYear() ? JewishDate.ADAR_II : JewishDate.ADAR;
        return Math.min(Math.max(JewishDate.NISSAN, month), lastMonth);
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
        public final int eventYear;
        public final boolean early;

        Occurrence(long scheduledAt, long originalAt, int eventYear, boolean early) {
            this.scheduledAt = scheduledAt;
            this.originalAt = originalAt;
            this.eventYear = eventYear;
            this.early = early;
        }
    }
}
