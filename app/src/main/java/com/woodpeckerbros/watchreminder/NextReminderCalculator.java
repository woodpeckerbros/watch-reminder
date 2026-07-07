package com.woodpeckerbros.watchreminder;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NextReminderCalculator {
    private NextReminderCalculator() {
    }

    public static NextReminder next(Context context) {
        return next(context, true);
    }

    public static NextReminder next(Context context, boolean applyQuietTime) {
        ReminderStore reminderStore = new ReminderStore(context);
        return next(context, reminderStore.getAll(), new ReminderSnoozeStore(context), new ReminderEventStore(context), applyQuietTime);
    }

    public static NextReminder next(Context context, List<Reminder> reminders, ReminderSnoozeStore snoozeStore, ReminderEventStore eventStore, boolean applyQuietTime) {
        NextReminder best = null;
        for (Reminder reminder : reminders) {
            if (!reminder.enabled) {
                continue;
            }
            NextReminder candidate = nextForReminder(context, reminder, snoozeStore, eventStore, applyQuietTime);
            if (candidate != null && (best == null || candidate.scheduledAt < best.scheduledAt)) {
                best = candidate;
            }
        }
        return best;
    }

    public static NextReminder nextForReminder(Reminder reminder, ReminderSnoozeStore snoozeStore) {
        return nextForReminder(reminder, snoozeStore, null);
    }

    public static NextReminder nextForReminder(Reminder reminder, ReminderSnoozeStore snoozeStore, ReminderEventStore eventStore) {
        return nextForReminder(null, reminder, snoozeStore, eventStore);
    }

    public static NextReminder nextForReminder(Context context, Reminder reminder, ReminderSnoozeStore snoozeStore, ReminderEventStore eventStore) {
        return nextForReminder(context, reminder, snoozeStore, eventStore, true);
    }

    public static NextReminder nextForReminder(Context context, Reminder reminder, ReminderSnoozeStore snoozeStore, ReminderEventStore eventStore, boolean applyQuietTime) {
        if (!reminder.enabled) {
            return null;
        }
        long snoozeAt = pendingSnoozeAt(reminder.id, snoozeStore);
        long regularAt = nextRegularAt(context, reminder, eventStore, applyQuietTime);
        if (snoozeAt <= regularAt) {
            return new NextReminder(reminder.id, reminder.name, snoozeAt, true);
        }
        if (regularAt == Long.MAX_VALUE) {
            return null;
        }
        String name = reminder.isAnnualEvent() ? annualName(context, reminder, eventStore, applyQuietTime) : reminder.name;
        return new NextReminder(reminder.id, name, regularAt, false);
    }

    private static String annualName(Context context, Reminder reminder, ReminderEventStore eventStore, boolean applyQuietTime) {
        AnnualReminderHelper.Occurrence occurrence = AnnualReminderHelper.next(context, reminder, eventStore, applyQuietTime);
        return occurrence == null ? reminder.name : AnnualReminderHelper.displayName(reminder, occurrence.originalAt);
    }

    public static long pendingSnoozeAt(String reminderId, ReminderSnoozeStore snoozeStore) {
        for (ReminderSnoozeStore.Snooze snooze : snoozeStore.getAll()) {
            if (snooze.reminderId.equals(reminderId)) {
                return snooze.scheduledAt;
            }
        }
        return Long.MAX_VALUE;
    }

    public static long nextRegularAt(Reminder reminder) {
        return nextRegularAt(reminder, null);
    }

    public static long nextRegularAt(Reminder reminder, ReminderEventStore eventStore) {
        return nextRegularAt(null, reminder, eventStore);
    }

    public static long nextRegularAt(Context context, Reminder reminder, ReminderEventStore eventStore) {
        return nextRegularAt(context, reminder, eventStore, true);
    }

    public static long nextRegularAt(Context context, Reminder reminder, ReminderEventStore eventStore, boolean applyQuietTime) {
        if (!reminder.enabled) {
            return Long.MAX_VALUE;
        }
        if (reminder.isOneTime()) {
            long oneTimeAt = reminder.useZmanim && context != null ? ZmanimHelper.timeFor(context, reminder, reminder.oneTimeAt) : reminder.oneTimeAt;
            oneTimeAt = ReminderScheduler.floorToMinute(applyQuietTime ? QuietTimeHelper.adjust(context, oneTimeAt, reminder) : oneTimeAt);
            if (oneTimeAt <= System.currentTimeMillis()) {
                return Long.MAX_VALUE;
            }
            return shouldSkip(reminder.id, oneTimeAt, eventStore) ? Long.MAX_VALUE : oneTimeAt;
        }
        if (reminder.isPeriodic()) {
            PeriodicReminderHelper.Occurrence occurrence = PeriodicReminderHelper.next(context, reminder, eventStore, applyQuietTime);
            return occurrence == null ? Long.MAX_VALUE : occurrence.scheduledAt;
        }
        if (reminder.isAnnualEvent()) {
            AnnualReminderHelper.Occurrence occurrence = AnnualReminderHelper.next(context, reminder, eventStore, applyQuietTime);
            return occurrence == null ? Long.MAX_VALUE : occurrence.scheduledAt;
        }
        long bestTime = Long.MAX_VALUE;
        for (Integer day : reminder.days) {
            Calendar trigger = Calendar.getInstance();
            trigger.set(Calendar.SECOND, 0);
            trigger.set(Calendar.MILLISECOND, 0);
            if (!reminder.useZmanim) {
                trigger.set(Calendar.HOUR_OF_DAY, reminder.hour);
                trigger.set(Calendar.MINUTE, reminder.minute);
            }
            trigger.set(Calendar.DAY_OF_WEEK, day);
            long scheduledAt = reminder.useZmanim && context != null ? ZmanimHelper.timeFor(context, reminder, trigger.getTimeInMillis()) : trigger.getTimeInMillis();
            scheduledAt = ReminderScheduler.floorToMinute(applyQuietTime ? QuietTimeHelper.adjust(context, scheduledAt, reminder) : scheduledAt);
            if (scheduledAt <= System.currentTimeMillis()) {
                trigger.add(Calendar.WEEK_OF_YEAR, 1);
                scheduledAt = reminder.useZmanim && context != null ? ZmanimHelper.timeFor(context, reminder, trigger.getTimeInMillis()) : trigger.getTimeInMillis();
                scheduledAt = ReminderScheduler.floorToMinute(applyQuietTime ? QuietTimeHelper.adjust(context, scheduledAt, reminder) : scheduledAt);
            }
            for (int i = 0; i < 53 && shouldSkip(reminder.id, scheduledAt, eventStore); i++) {
                trigger.add(Calendar.WEEK_OF_YEAR, 1);
                scheduledAt = reminder.useZmanim && context != null ? ZmanimHelper.timeFor(context, reminder, trigger.getTimeInMillis()) : trigger.getTimeInMillis();
                scheduledAt = ReminderScheduler.floorToMinute(applyQuietTime ? QuietTimeHelper.adjust(context, scheduledAt, reminder) : scheduledAt);
            }
            bestTime = Math.min(bestTime, scheduledAt);
        }
        return bestTime;
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

    public static String formatTime(long time) {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(time));
    }

    public static String formatDateTime(long time) {
        return new SimpleDateFormat("dd/MM HH:mm", Locale.US).format(new Date(time));
    }

    public static class NextReminder {
        public final String reminderId;
        public final String reminderName;
        public final long scheduledAt;
        public final boolean snoozed;

        NextReminder(String reminderId, String reminderName, long scheduledAt, boolean snoozed) {
            this.reminderId = reminderId;
            this.reminderName = reminderName == null || reminderName.trim().isEmpty() ? "תזכורת" : reminderName;
            this.scheduledAt = scheduledAt;
            this.snoozed = snoozed;
        }
    }
}
