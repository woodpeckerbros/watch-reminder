package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;

public class ReminderDueChecker {
    public static final long CHECK_INTERVAL_MS = ReminderSettings.DEFAULT_CHECK_INTERVAL_SECONDS * 1000L;
    public static final long LOOKBACK_MS = CHECK_INTERVAL_MS + 45_000L;
    public static final long CATCH_UP_LOOKBACK_MS = 24 * 60 * 60_000L;
    private static final String PREFS_NAME = "reminder_due_checker";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final Object DISPATCH_LOCK = new Object();

    public static void markCheckedNow(Context context) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .apply();
    }

    public static void dispatchDue(Context context, long from, long to) {
        synchronized (DISPATCH_LOCK) {
            dispatchDueLocked(context, from, to);
        }
    }

    private static void dispatchDueLocked(Context context, long from, long to) {
        AppLog.d(context, "dispatchDue from=" + NextReminderCalculator.formatDateTime(from) + " to=" + NextReminderCalculator.formatDateTime(to));
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long effectiveFrom = prefs.getLong(KEY_LAST_CHECK, from);
        ReminderEventStore eventStore = new ReminderEventStore(context);
        ReminderSnoozeStore snoozeStore = new ReminderSnoozeStore(context);
        java.util.HashSet<String> futureSnoozes = new java.util.HashSet<>();
        ArrayList<DueDispatch> due = new ArrayList<>();
        for (ReminderSnoozeStore.Snooze snooze : snoozeStore.getAll()) {
            if (snooze.scheduledAt > to) {
                futureSnoozes.add(snooze.reminderId);
                continue;
            }
            ReminderEventStore.Event existing = eventStore.findReminderOccurrence(snooze.reminderId, snooze.scheduledAt);
            boolean pendingSnooze = existing != null
                    && (ReminderEventStore.STATUS_SNOOZED.equals(existing.status)
                    || ReminderEventStore.STATUS_AUTO_SNOOZED.equals(existing.status));
            if (existing != null && !pendingSnooze) {
                snoozeStore.delete(snooze.reminderId);
            } else {
                AppLog.d(context, "dispatchDue firing snooze id=" + snooze.reminderId + " at=" + NextReminderCalculator.formatDateTime(snooze.scheduledAt));
                due.add(new DueDispatch(snooze.reminderId, snooze.reminderName, snooze.scheduledAt, snooze.originalScheduledAt, -1, true));
            }
        }
        for (Reminder reminder : new ReminderStore(context).getAll()) {
            if (!reminder.enabled) {
                continue;
            }
            if (futureSnoozes.contains(reminder.id)) {
                continue;
            }
            if (reminder.isOneTime()) {
                long originalAt = ReminderScheduler.floorToMinute(reminder.useZmanim ? ZmanimHelper.timeFor(context, reminder, reminder.oneTimeAt) : reminder.oneTimeAt);
                long scheduledAt = ReminderScheduler.floorToMinute(QuietTimeHelper.adjust(context, originalAt, reminder));
                if (scheduledAt <= to
                        && !eventStore.hasReminderOccurrence(reminder.id, scheduledAt)
                        && !eventStore.hasDoneOnDay(reminder.id, scheduledAt)) {
                    AppLog.d(context, "dispatchDue firing oneTime id=" + reminder.id + " at=" + NextReminderCalculator.formatDateTime(scheduledAt));
                    due.add(new DueDispatch(reminder.id, reminder.name, scheduledAt, originalAt, -1, false));
                }
                continue;
            }
            if (reminder.isPeriodic()) {
                DueOccurrence occurrence = periodicOccurrenceBetween(context, effectiveFrom, to, reminder);
                if (occurrence != null
                        && !eventStore.hasReminderOccurrence(reminder.id, occurrence.scheduledAt)) {
                    AppLog.d(context, "dispatchDue firing periodic id=" + reminder.id + " at=" + NextReminderCalculator.formatDateTime(occurrence.scheduledAt) + " original=" + NextReminderCalculator.formatDateTime(occurrence.originalAt));
                    due.add(new DueDispatch(reminder.id, reminder.name, occurrence.scheduledAt, occurrence.originalAt, -1, false));
                }
                continue;
            }
            if (reminder.isAnnualEvent()) {
                long cursor = effectiveFrom;
                for (int i = 0; i < 3; i++) {
                    DueOccurrence occurrence = annualOccurrenceBetween(context, cursor, to, reminder);
                    if (occurrence == null) {
                        break;
                    }
                    if (!eventStore.hasReminderOccurrence(reminder.id, occurrence.scheduledAt)) {
                        String name = AnnualReminderHelper.displayName(reminder, occurrence.originalAt);
                        AppLog.d(context, "dispatchDue firing annual id=" + reminder.id + " at=" + NextReminderCalculator.formatDateTime(occurrence.scheduledAt) + " original=" + NextReminderCalculator.formatDateTime(occurrence.originalAt));
                        due.add(new DueDispatch(reminder.id, name, occurrence.scheduledAt, occurrence.originalAt, -1, false));
                    }
                    cursor = occurrence.scheduledAt;
                }
                continue;
            }
            for (Integer day : reminder.days) {
                DueOccurrence occurrence = occurrenceBetween(context, effectiveFrom, to, reminder, day);
                if (occurrence == null
                        || eventStore.hasReminderOccurrence(reminder.id, occurrence.scheduledAt)
                        || eventStore.hasDoneOnDay(reminder.id, occurrence.scheduledAt)) {
                    continue;
                }
                AppLog.d(context, "dispatchDue firing regular id=" + reminder.id + " day=" + day + " at=" + NextReminderCalculator.formatDateTime(occurrence.scheduledAt) + " original=" + NextReminderCalculator.formatDateTime(occurrence.originalAt));
                due.add(new DueDispatch(reminder.id, reminder.name, occurrence.scheduledAt, occurrence.originalAt, day, false));
            }
        }
        due.sort(Comparator.comparingLong(item -> item.scheduledAt));
        AppLog.d(context, "dispatchDue dueCount=" + due.size() + " effectiveFrom=" + NextReminderCalculator.formatDateTime(effectiveFrom));
        for (DueDispatch item : due) {
            ReminderReceiver.fire(context, item.reminderId, item.reminderName, item.scheduledAt, item.originalAt, item.day, item.snooze);
        }
        prefs.edit().putLong(KEY_LAST_CHECK, to).apply();
        AppLog.d(context, "dispatchDue saved lastCheck=" + NextReminderCalculator.formatDateTime(to));
    }

    private static DueOccurrence occurrenceBetween(Context context, long from, long to, Reminder reminder, int day) {
        Calendar cursor = Calendar.getInstance();
        cursor.setTimeInMillis(from);
        cursor.set(Calendar.SECOND, 0);
        cursor.set(Calendar.MILLISECOND, 0);

        for (int i = 0; i < 8; i++) {
            Calendar candidate = (Calendar) cursor.clone();
            candidate.add(Calendar.DAY_OF_YEAR, i);
            candidate.set(Calendar.DAY_OF_WEEK, day);
            if (!reminder.useZmanim) {
                candidate.set(Calendar.HOUR_OF_DAY, reminder.hour);
                candidate.set(Calendar.MINUTE, reminder.minute);
            }
            candidate.set(Calendar.SECOND, 0);
            candidate.set(Calendar.MILLISECOND, 0);
            long originalAt = ReminderScheduler.floorToMinute(reminder.useZmanim ? ZmanimHelper.timeFor(context, reminder, candidate.getTimeInMillis()) : candidate.getTimeInMillis());
            long scheduledAt = ReminderScheduler.floorToMinute(QuietTimeHelper.adjust(context, originalAt, reminder));
            if (scheduledAt > from && scheduledAt <= to) {
                return new DueOccurrence(scheduledAt, originalAt);
            }
        }
        return null;
    }

    private static DueOccurrence periodicOccurrenceBetween(Context context, long from, long to, Reminder reminder) {
        PeriodicReminderHelper.Occurrence occurrence = PeriodicReminderHelper.between(context, from, to, reminder);
        return occurrence == null ? null : new DueOccurrence(occurrence.scheduledAt, occurrence.originalAt);
    }

    private static DueOccurrence annualOccurrenceBetween(Context context, long from, long to, Reminder reminder) {
        AnnualReminderHelper.Occurrence occurrence = AnnualReminderHelper.between(context, from, to, reminder);
        return occurrence == null ? null : new DueOccurrence(occurrence.scheduledAt, occurrence.originalAt);
    }

    private static class DueOccurrence {
        final long scheduledAt;
        final long originalAt;

        DueOccurrence(long scheduledAt, long originalAt) {
            this.scheduledAt = scheduledAt;
            this.originalAt = originalAt;
        }
    }

    private static class DueDispatch {
        final String reminderId;
        final String reminderName;
        final long scheduledAt;
        final long originalAt;
        final int day;
        final boolean snooze;

        DueDispatch(String reminderId, String reminderName, long scheduledAt, long originalAt, int day, boolean snooze) {
            this.reminderId = reminderId;
            this.reminderName = reminderName;
            this.scheduledAt = scheduledAt;
            this.originalAt = originalAt;
            this.day = day;
            this.snooze = snooze;
        }
    }
}
