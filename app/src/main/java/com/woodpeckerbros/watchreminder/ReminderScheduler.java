package com.woodpeckerbros.watchreminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

public class ReminderScheduler {
    public static final String EXTRA_REMINDER_ID = "reminder_id";
    public static final String EXTRA_REMINDER_NAME = "reminder_name";
    public static final String EXTRA_OCCURRENCE_ID = "occurrence_id";
    public static final String EXTRA_SCHEDULED_AT = "scheduled_at";
    public static final String EXTRA_ORIGINAL_SCHEDULED_AT = "original_scheduled_at";
    public static final String EXTRA_DAY = "day";
    public static final String EXTRA_IS_SNOOZE = "is_snooze";
    public static final String EXTRA_WATCHDOG_SLOT = "watchdog_slot";
    private static final long WATCHDOG_INTERVAL_MS = 12 * 60 * 60_000L;
    private static final long WATCHDOG_SAFETY_OFFSET_MS = 2 * 60_000L;

    public static void schedule(Context context, Reminder reminder) {
        scheduleNearest(context);
    }

    public static void scheduleNext(Context context, Reminder reminder, int day) {
        scheduleNearest(context);
    }

    public static synchronized void scheduleNearest(Context context) {
        java.util.List<Reminder> reminders = new ReminderStore(context).getAll();
        for (Reminder reminder : reminders) {
            cancel(context, reminder);
        }
        ScheduledCandidate nearest = null;
        ReminderEventStore eventStore = new ReminderEventStore(context);
        for (Reminder reminder : reminders) {
            ScheduledCandidate candidate = candidateFor(context, reminder, eventStore);
            if (candidate != null && (nearest == null || candidate.triggerAt < nearest.triggerAt)) {
                nearest = candidate;
            }
        }
        if (nearest == null) {
            AppLog.d(context, "scheduleNearest no enabled future reminder");
            return;
        }
        AppLog.d(context, "scheduleNearest id=" + nearest.reminder.id
                + " name=" + nearest.reminder.name
                + " at=" + NextReminderCalculator.formatDateTime(nearest.triggerAt));
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        setBestAvailableAlarm(context, alarmManager, nearest.triggerAt, nearest.pendingIntent, true);
    }

    private static ScheduledCandidate candidateFor(Context context, Reminder reminder, ReminderEventStore eventStore) {
        if (reminder == null || !reminder.enabled) {
            return null;
        }
        long triggerAt;
        PendingIntent pendingIntent;
        if (reminder.isOneTime()) {
            long originalAt = floorToMinute(reminder.useZmanim
                    ? ZmanimHelper.timeFor(context, reminder, reminder.oneTimeAt)
                    : reminder.oneTimeAt);
            triggerAt = floorToMinute(QuietTimeHelper.adjust(context, originalAt, reminder));
            if (triggerAt <= System.currentTimeMillis() || triggerAt == Long.MAX_VALUE || originalAt == Long.MAX_VALUE) {
                return null;
            }
            pendingIntent = oneTimeIntent(context, reminder, triggerAt, originalAt);
        } else if (reminder.isPeriodic()) {
            PeriodicReminderHelper.Occurrence occurrence = PeriodicReminderHelper.next(context, reminder, eventStore, true);
            if (occurrence == null || occurrence.scheduledAt == Long.MAX_VALUE) {
                return null;
            }
            triggerAt = occurrence.scheduledAt;
            pendingIntent = periodicIntent(context, reminder, occurrence.scheduledAt, occurrence.originalAt);
        } else if (reminder.isAnnualEvent()) {
            AnnualReminderHelper.Occurrence occurrence = AnnualReminderHelper.next(context, reminder, eventStore, true);
            if (occurrence == null || occurrence.scheduledAt == Long.MAX_VALUE) {
                return null;
            }
            triggerAt = occurrence.scheduledAt;
            pendingIntent = annualIntent(context, reminder, occurrence.scheduledAt, occurrence.originalAt);
        } else {
            RegularTrigger trigger = nextRegularTrigger(context, reminder);
            if (trigger == null || trigger.scheduledAt == Long.MAX_VALUE) {
                return null;
            }
            triggerAt = trigger.scheduledAt;
            pendingIntent = regularIntent(context, reminder, trigger.day, trigger.scheduledAt, trigger.originalAt);
        }
        return new ScheduledCandidate(reminder, triggerAt, pendingIntent);
    }

    public static long scheduleSnooze(Context context, String reminderId, String reminderName, int minutes) {
        return scheduleSnooze(context, reminderId, reminderName, minutes, floorToMinute(System.currentTimeMillis()));
    }

    public static long scheduleSnooze(Context context, String reminderId, String reminderName, int minutes, long originalScheduledAt) {
        long baseAt = floorToMinute(System.currentTimeMillis());
        return scheduleSnoozeAt(context, reminderId, reminderName, baseAt + minutes * 60_000L, originalScheduledAt);
    }

    public static long scheduleSnoozeAt(Context context, String reminderId, String reminderName, long triggerAt, long originalScheduledAt) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        originalScheduledAt = floorToMinute(originalScheduledAt);
        triggerAt = floorToMinute(Math.max(triggerAt, floorToMinute(System.currentTimeMillis()) + 60_000L));
        Reminder reminder = new ReminderStore(context).find(reminderId);
        long adjustedTriggerAt = floorToMinute(QuietTimeHelper.adjust(context, triggerAt, reminder));
        if (adjustedTriggerAt != triggerAt) {
            AppLog.d(context, "scheduleSnooze quiet adjusted id=" + reminderId
                    + " from=" + NextReminderCalculator.formatDateTime(triggerAt)
                    + " to=" + NextReminderCalculator.formatDateTime(adjustedTriggerAt));
            triggerAt = adjustedTriggerAt;
        }
        new ReminderSnoozeStore(context).upsert(reminderId, reminderName, triggerAt, originalScheduledAt);
        PendingIntent pendingIntent = snoozeIntent(context, reminderId, reminderName, triggerAt, originalScheduledAt);
        AppLog.d(context, "scheduleSnooze id=" + reminderId + " at=" + NextReminderCalculator.formatDateTime(triggerAt));
        cancelLegacyActivity(context, alarmManager, (reminderId + ":snooze").hashCode());
        setBestAvailableAlarm(context, alarmManager, triggerAt, pendingIntent, false);
        return triggerAt;
    }

    public static void scheduleDeferredRetry(Context context, String reminderId, String reminderName, long triggerAt) {
        triggerAt = floorToMinute(triggerAt);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class)
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_REMINDER_NAME, reminderName)
                .putExtra(EXTRA_SCHEDULED_AT, triggerAt)
                .putExtra(EXTRA_ORIGINAL_SCHEDULED_AT, triggerAt)
                .putExtra(EXTRA_DAY, -1)
                .putExtra(EXTRA_IS_SNOOZE, false);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (reminderId + ":wear-state-retry").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AppLog.d(context, "scheduleDeferredRetry id=" + reminderId + " at=" + NextReminderCalculator.formatDateTime(triggerAt));
        cancelLegacyActivity(context, alarmManager, (reminderId + ":wear-state-retry").hashCode());
        setBestAvailableAlarm(context, alarmManager, triggerAt, pendingIntent, false);
    }

    public static void cancelDeferredRetry(Context context, String reminderId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (reminderId + ":wear-state-retry").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(pendingIntent);
        cancelLegacyActivity(context, alarmManager, (reminderId + ":wear-state-retry").hashCode());
    }

    public static void scheduleAutoSnooze(Context context, String occurrenceId, String reminderId, String reminderName) {
        scheduleAutoSnooze(context, occurrenceId, reminderId, reminderName, floorToMinute(System.currentTimeMillis()));
    }

    public static void scheduleAutoSnooze(Context context, String occurrenceId, String reminderId, String reminderName, long originalScheduledAt) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        originalScheduledAt = floorToMinute(originalScheduledAt);
        long triggerAt = ceilToMinute(System.currentTimeMillis() + new ReminderSettings(context).autoSnoozeDelayMs());
        PendingIntent pendingIntent = autoSnoozeIntent(context, occurrenceId, reminderId, reminderName, originalScheduledAt);
        AppLog.d(context, "scheduleAutoSnooze occurrence=" + occurrenceId + " at=" + NextReminderCalculator.formatDateTime(triggerAt));
        setBestAvailableAlarm(context, alarmManager, triggerAt, pendingIntent, false);
    }

    public static void cancelAutoSnooze(Context context, String occurrenceId, String reminderId, String reminderName) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(autoSnoozeIntent(context, occurrenceId, reminderId, reminderName));
    }

    public static void cancelSnooze(Context context, String reminderId, String reminderName) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(snoozeIntent(context, reminderId, reminderName, 0));
        cancelLegacyBroadcast(context, alarmManager, (reminderId + ":snooze").hashCode(), ReminderReceiver.class);
        cancelLegacyActivity(context, alarmManager, (reminderId + ":snooze").hashCode());
        cancelDeferredRetry(context, reminderId);
        new ReminderSnoozeStore(context).delete(reminderId);
    }

    public static void scheduleWatchdog(Context context) {
        cancelWatchdog(context);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long now = System.currentTimeMillis();
        try {
            NextReminderCalculator.NextReminder next = NextReminderCalculator.next(
                    context,
                    new ReminderStore(context).getAll(),
                    new ReminderSnoozeStore(context),
                    new ReminderEventStore(context),
                    true
            );
            if (next != null && next.scheduledAt > now) {
                long triggerAt = ceilToMinute(next.scheduledAt + WATCHDOG_SAFETY_OFFSET_MS);
                if (triggerAt > now) {
                    AppLog.d(context, "scheduleWatchdog at=" + NextReminderCalculator.formatDateTime(triggerAt) + " alarmClock=false");
                    setBestAvailableAlarm(context, alarmManager, triggerAt, watchdogIntent(context), false);
                    return;
                }
            }
        } catch (Exception exception) {
            AppLog.e(context, "scheduleWatchdog next calculation failed", exception);
        }
        long triggerAt = ceilToMinute(now + WATCHDOG_INTERVAL_MS);
        AppLog.d(context, "scheduleWatchdog at=" + NextReminderCalculator.formatDateTime(triggerAt) + " alarmClock=false");
        setBestAvailableAlarm(context, alarmManager, triggerAt, watchdogIntent(context), false);
    }

    public static void cancelWatchdog(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            AppLog.w(context, "cancelWatchdog skipped no AlarmManager");
            return;
        }
        alarmManager.cancel(watchdogIntent(context));
        cancelLegacyBroadcast(context, alarmManager, "watchdog".hashCode(), WatchdogReceiver.class);
        cancelLegacyActivity(context, alarmManager, "watchdog".hashCode());
        for (int i = 0; i < 4; i++) {
            cancelLegacyBroadcast(context, alarmManager, ("watchdog:" + i).hashCode(), WatchdogReceiver.class);
            cancelLegacyActivity(context, alarmManager, ("watchdog:" + i).hashCode());
        }
    }

    private static PendingIntent watchdogIntent(Context context) {
        Intent intent = new Intent(context, WatchdogReceiver.class);
        return PendingIntent.getBroadcast(
                context,
                "watchdog".hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    public static void cancel(Context context, Reminder reminder) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        for (int day = Calendar.SUNDAY; day <= Calendar.SATURDAY; day++) {
            alarmManager.cancel(alertIntent(context, reminder, day));
            cancelLegacyBroadcast(context, alarmManager, (reminder.id + ":" + day).hashCode(), ReminderReceiver.class);
            cancelLegacyActivity(context, alarmManager, (reminder.id + ":" + day).hashCode());
        }
        alarmManager.cancel(regularIntent(context, reminder));
        alarmManager.cancel(oneTimeIntent(context, reminder));
        alarmManager.cancel(periodicIntent(context, reminder));
        alarmManager.cancel(annualIntent(context, reminder));
        cancelLegacyBroadcast(context, alarmManager, (reminder.id + ":regular").hashCode(), ReminderReceiver.class);
        cancelLegacyBroadcast(context, alarmManager, (reminder.id + ":once").hashCode(), ReminderReceiver.class);
        cancelLegacyBroadcast(context, alarmManager, (reminder.id + ":periodic").hashCode(), ReminderReceiver.class);
        cancelLegacyBroadcast(context, alarmManager, (reminder.id + ":annual").hashCode(), ReminderReceiver.class);
        cancelLegacyActivity(context, alarmManager, (reminder.id + ":regular").hashCode());
        cancelLegacyActivity(context, alarmManager, (reminder.id + ":once").hashCode());
        cancelLegacyActivity(context, alarmManager, (reminder.id + ":periodic").hashCode());
        cancelLegacyActivity(context, alarmManager, (reminder.id + ":annual").hashCode());
    }

    public static void skipOccurrence(Context context, Reminder reminder, long scheduledAt) {
        if (reminder.isOneTime()) {
            cancel(context, reminder);
            ComplicationRefresh.request(context);
            return;
        }
        if (reminder.isPeriodic()) {
            alarmManagerCancelPeriodic(context, reminder);
            scheduleNext(context, reminder, -1);
            ComplicationRefresh.request(context);
            return;
        }
        if (reminder.isAnnualEvent()) {
            alarmManagerCancelAnnual(context, reminder);
            scheduleNext(context, reminder, -1);
            ComplicationRefresh.request(context);
            return;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(scheduledAt);
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(alertIntent(context, reminder, day, scheduledAt));
        alarmManager.cancel(regularIntent(context, reminder));
        cancelLegacyBroadcast(context, alarmManager, (reminder.id + ":" + day).hashCode(), ReminderReceiver.class);
        cancelLegacyBroadcast(context, alarmManager, (reminder.id + ":regular").hashCode(), ReminderReceiver.class);
        cancelLegacyActivity(context, alarmManager, (reminder.id + ":" + day).hashCode());
        cancelLegacyActivity(context, alarmManager, (reminder.id + ":regular").hashCode());
        scheduleNext(context, reminder, day);
        ComplicationRefresh.request(context);
    }

    private static void alarmManagerCancelPeriodic(Context context, Reminder reminder) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(periodicIntent(context, reminder));
        cancelLegacyBroadcast(context, alarmManager, (reminder.id + ":periodic").hashCode(), ReminderReceiver.class);
        cancelLegacyActivity(context, alarmManager, (reminder.id + ":periodic").hashCode());
    }

    private static void alarmManagerCancelAnnual(Context context, Reminder reminder) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(annualIntent(context, reminder));
        cancelLegacyBroadcast(context, alarmManager, (reminder.id + ":annual").hashCode(), ReminderReceiver.class);
        cancelLegacyActivity(context, alarmManager, (reminder.id + ":annual").hashCode());
    }

    private static void cancelLegacyBroadcast(Context context, AlarmManager alarmManager, int requestCode, Class<?> receiverClass) {
        if (alarmManager == null) {
            return;
        }
        PendingIntent legacy = PendingIntent.getBroadcast(
                context,
                requestCode,
                new Intent(context, receiverClass),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(legacy);
    }

    private static void cancelLegacyActivity(Context context, AlarmManager alarmManager, int requestCode) {
        if (alarmManager == null) {
            return;
        }
        Intent intent = new Intent();
        intent.setClassName(context.getPackageName(), context.getPackageName() + ".AlarmDispatchActivity");
        PendingIntent legacy = PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(legacy);
    }

    private static void setBestAvailableAlarm(Context context, AlarmManager alarmManager, long triggerAt, PendingIntent pendingIntent, boolean alarmClock) {
        if (alarmManager == null) {
            AppLog.w(context, "alarm skipped no AlarmManager at=" + NextReminderCalculator.formatDateTime(triggerAt));
            return;
        }
        AppLog.d(context, "alarm request at=" + NextReminderCalculator.formatDateTime(triggerAt)
                + " now=" + NextReminderCalculator.formatDateTime(System.currentTimeMillis())
                + " inMs=" + (triggerAt - System.currentTimeMillis())
                + " alarmClock=" + alarmClock
                + " canExact=" + canScheduleExactAlarms(alarmManager)
                + " serviceEnabled=" + new ReminderSettings(context).serviceEnabled());
        try {
            if (alarmClock) {
                AppLog.d(context, "alarm setAlarmClock at=" + NextReminderCalculator.formatDateTime(triggerAt));
                alarmManager.setAlarmClock(
                        new AlarmManager.AlarmClockInfo(triggerAt, pendingIntent),
                        pendingIntent
                );
                AppLog.d(context, "alarm setAlarmClock success at=" + NextReminderCalculator.formatDateTime(triggerAt));
            } else if (canScheduleExactAlarms(alarmManager)) {
                AppLog.d(context, "alarm setExactAndAllowWhileIdle at=" + NextReminderCalculator.formatDateTime(triggerAt));
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                AppLog.d(context, "alarm setExactAndAllowWhileIdle success at=" + NextReminderCalculator.formatDateTime(triggerAt));
            } else {
                AppLog.w(context, "alarm setAndAllowWhileIdle fallback at=" + NextReminderCalculator.formatDateTime(triggerAt));
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                AppLog.w(context, "alarm setAndAllowWhileIdle fallback success at=" + NextReminderCalculator.formatDateTime(triggerAt));
            }
        } catch (SecurityException exception) {
            AppLog.e(context, "alarm SecurityException fallback set()", exception);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            AppLog.w(context, "alarm fallback set success after SecurityException at=" + NextReminderCalculator.formatDateTime(triggerAt));
        }
    }

    public static boolean canScheduleExactAlarms(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return canScheduleExactAlarms(alarmManager);
    }

    private static boolean canScheduleExactAlarms(AlarmManager alarmManager) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms();
    }

    private static class ScheduledCandidate {
        final Reminder reminder;
        final long triggerAt;
        final PendingIntent pendingIntent;

        ScheduledCandidate(Reminder reminder, long triggerAt, PendingIntent pendingIntent) {
            this.reminder = reminder;
            this.triggerAt = triggerAt;
            this.pendingIntent = pendingIntent;
        }
    }

    private static long nextTriggerAt(Context context, Reminder reminder, int day, boolean skipToday) {
        return nextTriggerTimes(context, reminder, day, skipToday).scheduledAt;
    }

    private static TriggerTimes nextTriggerTimes(Context context, Reminder reminder, int day, boolean skipToday) {
        Calendar now = Calendar.getInstance();
        Calendar trigger = Calendar.getInstance();
        trigger.set(Calendar.SECOND, 0);
        trigger.set(Calendar.MILLISECOND, 0);
        if (!reminder.useZmanim) {
            trigger.set(Calendar.HOUR_OF_DAY, reminder.hour);
            trigger.set(Calendar.MINUTE, reminder.minute);
        }
        trigger.set(Calendar.DAY_OF_WEEK, day);
        long originalAt = floorToMinute(reminder.useZmanim ? ZmanimHelper.timeFor(context, reminder, trigger.getTimeInMillis()) : trigger.getTimeInMillis());
        long triggerAt = floorToMinute(QuietTimeHelper.adjust(context, originalAt, reminder));
        if (skipToday || triggerAt <= now.getTimeInMillis()) {
            trigger.add(Calendar.WEEK_OF_YEAR, 1);
            originalAt = floorToMinute(reminder.useZmanim ? ZmanimHelper.timeFor(context, reminder, trigger.getTimeInMillis()) : trigger.getTimeInMillis());
            triggerAt = floorToMinute(QuietTimeHelper.adjust(context, originalAt, reminder));
        }
        return new TriggerTimes(triggerAt, originalAt);
    }

    private static RegularTrigger nextRegularTrigger(Context context, Reminder reminder) {
        RegularTrigger best = null;
        java.util.Set<Integer> days = reminder.days == null || reminder.days.isEmpty()
                ? java.util.Collections.singleton(Calendar.SUNDAY)
                : reminder.days;
        for (Integer day : days) {
            TriggerTimes trigger = nextTriggerTimes(context, reminder, day, false);
            if (trigger.scheduledAt == Long.MAX_VALUE) {
                continue;
            }
            if (best == null || trigger.scheduledAt < best.scheduledAt) {
                best = new RegularTrigger(day, trigger.scheduledAt, trigger.originalAt);
            }
        }
        return best;
    }

    private static PendingIntent alertIntent(Context context, Reminder reminder, int day) {
        TriggerTimes trigger = nextTriggerTimes(context, reminder, day, false);
        return alertIntent(context, reminder, day, trigger.scheduledAt, trigger.originalAt);
    }

    private static PendingIntent alertIntent(Context context, Reminder reminder, int day, long scheduledAt) {
        return alertIntent(context, reminder, day, scheduledAt, scheduledAt);
    }

    private static PendingIntent alertIntent(Context context, Reminder reminder, int day, long scheduledAt, long originalAt) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .putExtra(EXTRA_REMINDER_ID, reminder.id)
                .putExtra(EXTRA_REMINDER_NAME, reminder.name)
                .putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
                .putExtra(EXTRA_ORIGINAL_SCHEDULED_AT, originalAt)
                .putExtra(EXTRA_DAY, day)
                .putExtra(EXTRA_IS_SNOOZE, false);
        return PendingIntent.getBroadcast(
                context,
                (reminder.id + ":" + day).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent regularIntent(Context context, Reminder reminder) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        return PendingIntent.getBroadcast(
                context,
                (reminder.id + ":regular").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent regularIntent(Context context, Reminder reminder, int day, long scheduledAt, long originalAt) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .putExtra(EXTRA_REMINDER_ID, reminder.id)
                .putExtra(EXTRA_REMINDER_NAME, reminder.name)
                .putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
                .putExtra(EXTRA_ORIGINAL_SCHEDULED_AT, originalAt)
                .putExtra(EXTRA_DAY, day)
                .putExtra(EXTRA_IS_SNOOZE, false);
        return PendingIntent.getBroadcast(
                context,
                (reminder.id + ":regular").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent oneTimeIntent(Context context, Reminder reminder) {
        long originalAt = floorToMinute(reminder.useZmanim ? ZmanimHelper.timeFor(context, reminder, reminder.oneTimeAt) : reminder.oneTimeAt);
        long scheduledAt = floorToMinute(QuietTimeHelper.adjust(context, originalAt, reminder));
        return oneTimeIntent(context, reminder, scheduledAt, originalAt);
    }

    private static PendingIntent periodicIntent(Context context, Reminder reminder) {
        PeriodicReminderHelper.Occurrence occurrence = PeriodicReminderHelper.next(context, reminder, null, true);
        long scheduledAt = occurrence == null ? 0 : occurrence.scheduledAt;
        long originalAt = occurrence == null ? scheduledAt : occurrence.originalAt;
        return periodicIntent(context, reminder, scheduledAt, originalAt);
    }

    private static PendingIntent annualIntent(Context context, Reminder reminder) {
        AnnualReminderHelper.Occurrence occurrence = AnnualReminderHelper.next(context, reminder, null, true);
        long scheduledAt = occurrence == null ? 0 : occurrence.scheduledAt;
        long originalAt = occurrence == null ? scheduledAt : occurrence.originalAt;
        return annualIntent(context, reminder, scheduledAt, originalAt);
    }

    public static long floorToMinute(long time) {
        return time == Long.MAX_VALUE ? time : time - (time % 60_000L);
    }

    public static long ceilToMinute(long time) {
        if (time == Long.MAX_VALUE) {
            return time;
        }
        long floored = floorToMinute(time);
        return floored == time ? time : floored + 60_000L;
    }

    private static PendingIntent oneTimeIntent(Context context, Reminder reminder, long scheduledAt) {
        return oneTimeIntent(context, reminder, scheduledAt, scheduledAt);
    }

    private static PendingIntent oneTimeIntent(Context context, Reminder reminder, long scheduledAt, long originalAt) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .putExtra(EXTRA_REMINDER_ID, reminder.id)
                .putExtra(EXTRA_REMINDER_NAME, reminder.name)
                .putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
                .putExtra(EXTRA_ORIGINAL_SCHEDULED_AT, originalAt)
                .putExtra(EXTRA_DAY, -1)
                .putExtra(EXTRA_IS_SNOOZE, false);
        return PendingIntent.getBroadcast(
                context,
                (reminder.id + ":once").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent periodicIntent(Context context, Reminder reminder, long scheduledAt, long originalAt) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .putExtra(EXTRA_REMINDER_ID, reminder.id)
                .putExtra(EXTRA_REMINDER_NAME, reminder.name)
                .putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
                .putExtra(EXTRA_ORIGINAL_SCHEDULED_AT, originalAt)
                .putExtra(EXTRA_DAY, -1)
                .putExtra(EXTRA_IS_SNOOZE, false);
        return PendingIntent.getBroadcast(
                context,
                (reminder.id + ":periodic").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent annualIntent(Context context, Reminder reminder, long scheduledAt, long originalAt) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .putExtra(EXTRA_REMINDER_ID, reminder.id)
                .putExtra(EXTRA_REMINDER_NAME, AnnualReminderHelper.displayName(reminder, originalAt))
                .putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
                .putExtra(EXTRA_ORIGINAL_SCHEDULED_AT, originalAt)
                .putExtra(EXTRA_DAY, -1)
                .putExtra(EXTRA_IS_SNOOZE, false);
        return PendingIntent.getBroadcast(
                context,
                (reminder.id + ":annual").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent snoozeIntent(Context context, String reminderId, String reminderName, long scheduledAt) {
        return snoozeIntent(context, reminderId, reminderName, scheduledAt, scheduledAt);
    }

    private static PendingIntent snoozeIntent(Context context, String reminderId, String reminderName, long scheduledAt, long originalScheduledAt) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_REMINDER_NAME, reminderName)
                .putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
                .putExtra(EXTRA_ORIGINAL_SCHEDULED_AT, originalScheduledAt)
                .putExtra(EXTRA_DAY, -1)
                .putExtra(EXTRA_IS_SNOOZE, true);
        return PendingIntent.getBroadcast(
                context,
                (reminderId + ":snooze").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent autoSnoozeIntent(Context context, String occurrenceId, String reminderId, String reminderName) {
        return autoSnoozeIntent(context, occurrenceId, reminderId, reminderName, 0);
    }

    private static PendingIntent autoSnoozeIntent(Context context, String occurrenceId, String reminderId, String reminderName, long originalScheduledAt) {
        Intent intent = new Intent(context, AutoSnoozeReceiver.class)
                .putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_REMINDER_NAME, reminderName)
                .putExtra(EXTRA_ORIGINAL_SCHEDULED_AT, originalScheduledAt);
        return PendingIntent.getBroadcast(
                context,
                (occurrenceId + ":auto").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static class TriggerTimes {
        final long scheduledAt;
        final long originalAt;

        TriggerTimes(long scheduledAt, long originalAt) {
            this.scheduledAt = scheduledAt;
            this.originalAt = originalAt;
        }
    }

    private static class RegularTrigger {
        final int day;
        final long scheduledAt;
        final long originalAt;

        RegularTrigger(int day, long scheduledAt, long originalAt) {
            this.day = day;
            this.scheduledAt = scheduledAt;
            this.originalAt = originalAt;
        }
    }
}
