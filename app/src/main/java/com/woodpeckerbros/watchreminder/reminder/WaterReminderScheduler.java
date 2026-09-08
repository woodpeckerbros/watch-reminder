package com.woodpeckerbros.watchreminder.reminder;

import com.woodpeckerbros.watchreminder.*;

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
        cancel(context);
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.waterRemindersEnabled()) {
            AppLog.d(context, "water schedule skipped disabled");
            return;
        }
        long now = System.currentTimeMillis();
        boolean targetReached = ReminderSettings.WATER_MODE_DAILY_TARGET.equals(settings.waterMode())
                && new WaterReminderStore(context).consumedTodayMl() >= settings.waterDailyTargetMl();
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
        long triggerAt = ReminderScheduler.ceilToMinute(System.currentTimeMillis() + Math.max(1, minutes) * 60_000L);
        triggerAt = QuietTimeHelper.adjust(context, triggerAt);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        scheduleAt(context, triggerAt);
        AppLog.d(context, "water snooze scheduled at=" + NextReminderCalculator.formatDateTime(triggerAt));
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
        if (ReminderSettings.WATER_MODE_FIXED_AMOUNT.equals(settings.waterMode())) {
            return settings.waterAmountMl();
        }
        int consumed = new WaterReminderStore(context).consumedTodayMl();
        int remaining = Math.max(0, settings.waterDailyTargetMl() - consumed);
        if (remaining == 0) {
            return 0;
        }
        Calendar trigger = Calendar.getInstance();
        trigger.setTimeInMillis(triggerAt);
        int triggerMinute = trigger.get(Calendar.HOUR_OF_DAY) * 60 + trigger.get(Calendar.MINUTE);
        int endMinute = settings.waterEndHour() * 60 + settings.waterEndMinute();
        int remainingSlots = Math.max(1, ((endMinute - triggerMinute) / settings.waterIntervalMinutes()) + 1);
        return amountForRemaining(remaining, remainingSlots);
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
