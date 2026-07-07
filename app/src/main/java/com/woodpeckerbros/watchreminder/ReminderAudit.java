package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

public class ReminderAudit {
    private static final String PREFS_NAME = "reminder_audit";
    private static final String KEY_LAST_AUDIT = "last_audit";
    private static final long MAX_LOOKBACK_MS = 24 * 60 * 60_000L;

    public static void markAuditedNow(Context context) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_AUDIT, System.currentTimeMillis())
                .apply();
    }

    public static void run(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!prefs.contains(KEY_LAST_AUDIT)) {
            prefs.edit().putLong(KEY_LAST_AUDIT, now).apply();
            return;
        }
        long lastAudit = prefs.getLong(KEY_LAST_AUDIT, now - MAX_LOOKBACK_MS);
        long from = Math.max(lastAudit, now - MAX_LOOKBACK_MS);

        ReminderEventStore eventStore = new ReminderEventStore(context);
        java.util.HashSet<String> futureSnoozes = new java.util.HashSet<>();
        for (ReminderSnoozeStore.Snooze snooze : new ReminderSnoozeStore(context).getAll()) {
            if (snooze.scheduledAt > now) {
                futureSnoozes.add(snooze.reminderId);
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
                long scheduledAt = reminder.useZmanim ? ZmanimHelper.timeFor(context, reminder, reminder.oneTimeAt) : reminder.oneTimeAt;
                scheduledAt = ReminderScheduler.floorToMinute(QuietTimeHelper.adjust(context, scheduledAt, reminder));
                if (scheduledAt > from
                        && scheduledAt <= now
                        && !eventStore.hasReminderOccurrence(reminder.id, scheduledAt)
                        && !eventStore.hasDoneOnDay(reminder.id, scheduledAt)) {
                    String occurrenceId = "missed:" + reminder.id + ":" + scheduledAt;
                    eventStore.markMissed(occurrenceId, reminder.id, reminder.name, reminder.description, scheduledAt);
                }
                continue;
            }
            if (reminder.isPeriodic()) {
                PeriodicReminderHelper.Occurrence occurrence = PeriodicReminderHelper.between(context, from, now, reminder);
                if (occurrence != null) {
                    String occurrenceId = "missed:" + reminder.id + ":" + occurrence.scheduledAt;
                    if (eventStore.find(occurrenceId) == null
                            && !eventStore.hasReminderOccurrence(reminder.id, occurrence.scheduledAt)) {
                        eventStore.markMissed(occurrenceId, reminder.id, reminder.name, reminder.description, occurrence.scheduledAt);
                    }
                }
                continue;
            }
            if (reminder.isAnnualEvent()) {
                AnnualReminderHelper.Occurrence occurrence = AnnualReminderHelper.between(context, from, now, reminder);
                if (occurrence != null) {
                    String occurrenceId = "missed:" + reminder.id + ":" + occurrence.scheduledAt;
                    if (eventStore.find(occurrenceId) == null
                            && !eventStore.hasReminderOccurrence(reminder.id, occurrence.scheduledAt)) {
                        eventStore.markMissed(occurrenceId, reminder.id, AnnualReminderHelper.displayName(reminder, occurrence.originalAt), reminder.description, occurrence.scheduledAt);
                    }
                }
                continue;
            }
            for (Integer day : reminder.days) {
                long scheduledAt = occurrenceBetween(context, from, now, reminder, day);
                if (scheduledAt == 0) {
                    continue;
                }
                String occurrenceId = "missed:" + reminder.id + ":" + scheduledAt;
                if (eventStore.find(occurrenceId) != null
                        || eventStore.hasReminderOccurrence(reminder.id, scheduledAt)
                        || eventStore.hasDoneOnDay(reminder.id, scheduledAt)) {
                    continue;
                }
                eventStore.markMissed(occurrenceId, reminder.id, reminder.name, reminder.description, scheduledAt);
            }
        }

        prefs.edit().putLong(KEY_LAST_AUDIT, now).apply();
    }

    private static long occurrenceBetween(Context context, long from, long to, Reminder reminder, int day) {
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
            long scheduledAt = reminder.useZmanim ? ZmanimHelper.timeFor(context, reminder, candidate.getTimeInMillis()) : candidate.getTimeInMillis();
            scheduledAt = ReminderScheduler.floorToMinute(QuietTimeHelper.adjust(context, scheduledAt, reminder));
            if (scheduledAt > from && scheduledAt <= to) {
                return scheduledAt;
            }
        }
        return 0;
    }
}
