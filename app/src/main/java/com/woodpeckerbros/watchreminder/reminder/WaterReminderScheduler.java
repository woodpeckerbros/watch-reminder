package com.woodpeckerbros.watchreminder.reminder;

import com.woodpeckerbros.watchreminder.*;
import com.woodpeckerbros.watchreminder.entitlement.EntitlementAccess;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

public final class WaterReminderScheduler {
    static final String EXTRA_TRIGGER_AT = "water_trigger_at";
    private static final int REQUEST_CODE = "water_reminder".hashCode();

    private WaterReminderScheduler() {
    }

    public static void schedule(Context context) {
        if (!EntitlementAccess.isFeatureAccessGranted(context)) { cancel(context); return; }
        cancel(context);
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.waterRemindersEnabled()) {
            AppLog.d(context, "water schedule skipped disabled");
            return;
        }
        long now = System.currentTimeMillis();
        boolean targetReached = new WaterReminderStore(context).consumedTodayMl()
                >= settings.waterDailyTargetMl();
        long triggerAt = nextTriggerAt(settings, now, targetReached);
        triggerAt = QuietTimeHelper.adjust(context, triggerAt);
        if (triggerAt <= now) {
            return;
        }
        AppLog.d(context, "water schedule at=" + NextReminderCalculator.formatDateTime(triggerAt));
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        setBest(context, manager, triggerAt, pendingIntent(context, triggerAt));
    }

    public static void cancel(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) {
            manager.cancel(pendingIntent(context, 0L));
        }
    }

    public static void scheduleSnooze(Context context, int minutes) {
        if (!EntitlementAccess.isFeatureAccessGranted(context)) {
            cancel(context);
            return;
        }
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.waterRemindersEnabled()) {
            cancel(context);
            return;
        }
        long requestedAt = ReminderScheduler.ceilToMinute(
                System.currentTimeMillis() + Math.max(1, minutes) * 60_000L);
        long triggerAt = QuietTimeHelper.adjust(context, requestedAt);
        // A snooze replaces the normal periodic alarm, never competes with it.
        cancel(context);
        scheduleAt(context, triggerAt);
        AppLog.d(context, "water snooze minutes=" + Math.max(1, minutes)
                + " requested_at=" + NextReminderCalculator.formatDateTime(requestedAt)
                + " scheduled_at=" + NextReminderCalculator.formatDateTime(triggerAt)
                + " quiet_adjusted=" + (triggerAt != requestedAt));
    }

    static void scheduleAt(Context context, long triggerAt) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        setBest(context, manager, triggerAt, pendingIntent(context, triggerAt));
    }

    static int remindersPerDay(ReminderSettings settings) {
        return remindersPerDay(
                settings.waterStartHour() * 60 + settings.waterStartMinute(),
                settings.waterEndHour() * 60 + settings.waterEndMinute(),
                settings.waterIntervalMinutes());
    }

    public static int remindersPerDay(int startMinute, int endMinute, int intervalMinutes) {
        if (endMinute <= startMinute || intervalMinutes <= 0) {
            return 0;
        }
        return ((endMinute - startMinute) / intervalMinutes) + 1;
    }

    static int plannedAmountMl(Context context, long triggerAt) {
        ReminderSettings settings = new ReminderSettings(context);
        int consumed = new WaterReminderStore(context).consumedTodayMl();
        int target = settings.waterDailyTargetMl();
        int remaining = Math.max(0, target - consumed);
        if (remaining == 0) {
            return 0;
        }
        if (ReminderSettings.WATER_MODE_FIXED_AMOUNT.equals(settings.waterMode())) {
            return Math.min(remaining, settings.waterAmountMl());
        }
        // A daily goal is a steady plan, not a catch-up mechanism. Recalculating based
        // on the remaining time made a later reminder ask for more even after a user
        // had followed the preceding suggestion.
        return dailyTargetAmountForReminderMl(target, consumed, remindersPerDay(settings));
    }

    public static int dailyTargetPortionMl(int targetMl, int remindersPerDay) {
        return amountForRemaining(targetMl, remindersPerDay);
    }

    /** The last request is reduced to the remaining goal; requests never grow to catch up. */
    public static int dailyTargetAmountForReminderMl(int targetMl, int consumedMl, int remindersPerDay) {
        int remaining = Math.max(0, targetMl - Math.max(0, consumedMl));
        return Math.min(remaining, dailyTargetPortionMl(targetMl, remindersPerDay));
    }

    /**
     * Derives the widest 15-minute-aligned interval that still fits the selected cups
     * in the daily window. The final cup may be smaller so the goal is never exceeded.
     */
    public static int automaticIntervalMinutes(int startMinute, int endMinute,
                                               int dailyTargetMl, int glassSizeMl) {
        int window = endMinute - startMinute;
        if (window <= 0 || dailyTargetMl <= 0 || glassSizeMl <= 0) {
            return 0;
        }
        int cups = (int) Math.ceil(dailyTargetMl / (double) glassSizeMl);
        if (cups <= 1) {
            return Math.max(15, Math.min(240, window));
        }
        int maximumInterval = window / (cups - 1);
        int aligned = (maximumInterval / 15) * 15;
        return Math.max(15, Math.min(240, aligned));
    }

    public static int amountForRemaining(int remainingMl, int remainingSlots) {
        if (remainingMl <= 0 || remainingSlots <= 0) {
            return 0;
        }
        return roundUpToTen((int) Math.ceil(remainingMl / (double) remainingSlots));
    }

    static long nextTriggerAt(ReminderSettings settings, long now, boolean tomorrowOnly) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(now);
        start.set(Calendar.HOUR_OF_DAY, settings.waterStartHour());
        start.set(Calendar.MINUTE, settings.waterStartMinute());
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        int startMinute = settings.waterStartHour() * 60 + settings.waterStartMinute();
        int endMinute = settings.waterEndHour() * 60 + settings.waterEndMinute();
        if (endMinute <= startMinute) {
            start.add(Calendar.DAY_OF_YEAR, 1);
            return start.getTimeInMillis();
        }
        if (tomorrowOnly) {
            start.add(Calendar.DAY_OF_YEAR, 1);
            return start.getTimeInMillis();
        }
        if (now < start.getTimeInMillis()) {
            return start.getTimeInMillis();
        }
        long intervalMs = settings.waterIntervalMinutes() * 60_000L;
        long elapsed = now - start.getTimeInMillis();
        long nextIndex = elapsed / intervalMs + 1L;
        long candidate = start.getTimeInMillis() + nextIndex * intervalMs;
        Calendar end = (Calendar) start.clone();
        end.set(Calendar.HOUR_OF_DAY, settings.waterEndHour());
        end.set(Calendar.MINUTE, settings.waterEndMinute());
        if (candidate <= end.getTimeInMillis()) {
            return candidate;
        }
        start.add(Calendar.DAY_OF_YEAR, 1);
        return start.getTimeInMillis();
    }

    private static int roundUpToTen(int value) {
        return Math.max(10, ((value + 9) / 10) * 10);
    }

    private static PendingIntent pendingIntent(Context context, long triggerAt) {
        Intent intent = new Intent(context, WaterReminderReceiver.class)
                .putExtra(EXTRA_TRIGGER_AT, triggerAt);
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void setBest(Context context, AlarmManager manager, long triggerAt, PendingIntent intent) {
        if (manager == null) {
            return;
        }
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent);
        } catch (SecurityException exception) {
            AppLog.e(context, "water exact alarm failed", exception);
            manager.set(AlarmManager.RTC_WAKEUP, triggerAt, intent);
        }
    }
}
