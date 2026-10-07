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
    private static final int AUTO_SNOOZE_REQUEST_CODE = "water_auto_snooze".hashCode();
    static final String ACTION_AUTO_SNOOZE = "com.woodpeckerbros.watchreminder.WATER_AUTO_SNOOZE";
    static final int AUTO_RETRY_MINUTES = 5;
    static final int MIN_GLASS_INTERVAL_MINUTES = 30;
    private static final long MIN_GLASS_INTERVAL_MS = MIN_GLASS_INTERVAL_MINUTES * 60_000L;
    private static final long SNOOZE_RECOVERY_WINDOW_MS = 30 * 60_000L;

    private WaterReminderScheduler() {
    }

    public static void schedule(Context context) {
        if (!EntitlementAccess.isFeatureAccessGranted(context)) {
            cancel(context);
            return;
        }
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.waterRemindersEnabled()) {
            cancel(context);
            AppLog.d(context, "water schedule skipped disabled");
            return;
        }
        cancelPeriodic(context);
        long now = System.currentTimeMillis();
        WaterReminderStore store = new WaterReminderStore(context);
        boolean targetReached = store.consumedTodayMl()
                >= settings.waterDailyTargetMl();
        long snoozeAt = store.pendingSnoozeAt();
        if (snoozeAt > 0L) {
            if (!targetReached && shouldRecoverSnooze(snoozeAt, now)) {
                scheduleAt(context, snoozeAt);
                AppLog.d(context, "water schedule preserved snooze_at="
                        + NextReminderCalculator.formatDateTime(snoozeAt));
                return;
            }
            store.clearPendingSnoozeAt(snoozeAt);
            AppLog.d(context, "water schedule cleared stale_or_completed snooze_at=" + snoozeAt);
        }
        boolean fixedAmount = ReminderSettings.WATER_MODE_FIXED_AMOUNT.equals(settings.waterMode());
        long triggerAt;
        if (fixedAmount) {
            long saved = store.nextFixedAt();
            if (!targetReached && shouldRecoverSnooze(saved, now)) {
                triggerAt = saved;
            } else {
                triggerAt = nextFixedAmountTriggerAt(settings, now, targetReached, store.consumedTodayMl());
            }
        } else {
            store.clearNextFixedAt();
            triggerAt = nextTriggerAt(settings, now, targetReached);
        }
        if (triggerAt <= 0L) return;
        triggerAt = QuietTimeHelper.adjust(context, triggerAt);
        if (fixedAmount) store.setNextFixedAt(triggerAt);
        if (!fixedAmount && triggerAt <= now) {
            return;
        }
        AppLog.d(context, "water schedule at=" + NextReminderCalculator.formatDateTime(triggerAt));
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        setBest(context, manager, triggerAt, pendingIntent(context, triggerAt));
    }

    public static void cancel(Context context) {
        cancelPeriodic(context);
        cancelAutoSnooze(context);
        WaterReminderStore store = new WaterReminderStore(context);
        store.clearPendingAutoTrigger();
        store.clearPendingSnoozeAt();
        store.clearNextFixedAt();
    }

    private static void cancelPeriodic(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) {
            manager.cancel(pendingIntent(context, 0L));
        }
    }

    static void scheduleAutoSnooze(Context context, long triggerAt) {
        new WaterReminderStore(context).setPendingAutoTrigger(triggerAt);
        long timeoutAt = System.currentTimeMillis() + new ReminderSettings(context).autoSnoozeDelayMs();
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        setBest(context, manager, timeoutAt, autoSnoozeIntent(context, triggerAt));
        AppLog.d(context, "water auto-snooze timeout=" + NextReminderCalculator.formatDateTime(timeoutAt)
                + " original=" + triggerAt);
    }

    static void cancelAutoSnooze(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(autoSnoozeIntent(context, 0L));
    }

    public static void scheduleSnooze(Context context, int minutes, boolean respectQuietTime) {
        if (!EntitlementAccess.isFeatureAccessGranted(context)) {
            cancel(context);
            return;
        }
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.waterRemindersEnabled()) {
            cancel(context);
            return;
        }
        long requestedAt = requestedSnoozeAt(System.currentTimeMillis(), minutes);
        long triggerAt = respectQuietTime ? QuietTimeHelper.adjust(context, requestedAt) : requestedAt;
        // A snooze replaces the normal periodic alarm, never competes with it.
        WaterReminderStore store = new WaterReminderStore(context);
        store.setPendingSnoozeAt(triggerAt);
        store.clearNextFixedAt();
        cancelPeriodic(context);
        scheduleAt(context, triggerAt);
        AppLog.d(context, "water snooze minutes=" + Math.max(1, minutes)
                + " requested_at=" + NextReminderCalculator.formatDateTime(requestedAt)
                + " scheduled_at=" + NextReminderCalculator.formatDateTime(triggerAt)
                + " quiet_adjusted=" + (triggerAt != requestedAt));
    }

    public static long requestedSnoozeAt(long now, int minutes) {
        return now + Math.max(1, minutes) * 60_000L;
    }

    static void scheduleAt(Context context, long triggerAt) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        setBest(context, manager, triggerAt, pendingIntent(context, triggerAt));
    }

    public static boolean shouldRecoverSnooze(long snoozeAt, long now) {
        return snoozeAt > 0L && snoozeAt >= now - SNOOZE_RECOVERY_WINDOW_MS;
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
            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(triggerAt);
            end.set(Calendar.HOUR_OF_DAY, settings.waterEndHour());
            end.set(Calendar.MINUTE, settings.waterEndMinute());
            end.set(Calendar.SECOND, 0);
            end.set(Calendar.MILLISECOND, 0);
            return fixedAmountForReminder(remaining, settings.waterAmountMl(),
                    triggerAt, end.getTimeInMillis());
        }
        // Rebalance the remaining goal across the alerts that can still be delivered today.
        // A missed glass therefore changes each later request gradually, instead of leaving
        // an unrealistic amount for the final alert.
        return amountForRemaining(remaining, remainingReminderSlots(settings, triggerAt));
    }

    public static int dailyTargetPortionMl(int targetMl, int remindersPerDay) {
        return amountForRemaining(targetMl, remindersPerDay);
    }

    /** The last request is reduced to the remaining goal; requests never grow to catch up. */
    public static int dailyTargetAmountForReminderMl(int targetMl, int consumedMl, int remindersPerDay) {
        int remaining = Math.max(0, targetMl - Math.max(0, consumedMl));
        return Math.min(remaining, dailyTargetPortionMl(targetMl, remindersPerDay));
    }

    /** Evenly spaces the chosen glass over the drinking window, at least 30 minutes apart. */
    public static int automaticIntervalMinutes(int startMinute, int endMinute,
                                               int dailyTargetMl, int glassSizeMl) {
        if (endMinute - startMinute < MIN_GLASS_INTERVAL_MINUTES
                || dailyTargetMl <= 0 || glassSizeMl <= 0) {
            return 0;
        }
        int windowMinutes = endMinute - startMinute;
        return Math.max(MIN_GLASS_INTERVAL_MINUTES, Math.min(windowMinutes,
                (int) Math.round(windowMinutes * (double) glassSizeMl / dailyTargetMl)));
    }

    public static int fixedAmountSlotsAvailable(int startMinute, int endMinute) {
        return Math.max(0, (endMinute - startMinute) / MIN_GLASS_INTERVAL_MINUTES);
    }

    /** Preview using the same spacing and amount calculations as regular reminders. */
    public static int fixedAmountPlannedReminders(int startMinute, int endMinute,
                                                   int dailyTargetMl, int glassSizeMl) {
        long startAt = startMinute * 60_000L;
        long endAt = endMinute * 60_000L;
        long previous = startAt;
        int remaining = dailyTargetMl;
        int count = 0;
        while (remaining > 0 && count < 48) {
            long next = fixedAmountNextAt(startAt, endAt, previous, remaining, glassSizeMl);
            if (next <= 0L) break;
            remaining -= fixedAmountForReminder(remaining, glassSizeMl, next, endAt);
            previous = next;
            count++;
        }
        return count;
    }

    /** Keep the chosen glass size unless the remaining half-hour slots need a larger amount. */
    public static int fixedAmountForReminder(int remainingMl, int glassSizeMl,
                                             long triggerAt, long endAt) {
        if (remainingMl <= 0 || glassSizeMl <= 0) return 0;
        long slots = triggerAt > endAt ? 1L : (endAt - triggerAt) / MIN_GLASS_INTERVAL_MS + 1L;
        int neededMl = (int) ((remainingMl + slots - 1L) / slots);
        return Math.min(remainingMl, Math.max(glassSizeMl, neededMl));
    }

    /** Rebalances the spacing after drinking or a missed alert, never below 30 minutes. */
    public static long fixedAmountNextAt(long startAt, long endAt, long now,
                                         int remainingMl, int glassSizeMl) {
        if (endAt - startAt < MIN_GLASS_INTERVAL_MS
                || remainingMl <= 0 || glassSizeMl <= 0) return 0L;
        long anchor = Math.max(startAt, now);
        long remainingTime = endAt - anchor;
        if (remainingTime < MIN_GLASS_INTERVAL_MS) return 0L;
        long interval = Math.max(MIN_GLASS_INTERVAL_MS, Math.min(remainingTime,
                Math.round(remainingTime * (double) glassSizeMl / remainingMl)));
        long nextAt = anchor + interval;
        return nextAt <= endAt ? nextAt : 0L;
    }

    static long nextFixedAmountTriggerAt(ReminderSettings settings, long now,
                                         boolean tomorrowOnly, int consumedMl) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(now);
        start.set(Calendar.HOUR_OF_DAY, settings.waterStartHour());
        start.set(Calendar.MINUTE, settings.waterStartMinute());
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = (Calendar) start.clone();
        end.set(Calendar.HOUR_OF_DAY, settings.waterEndHour());
        end.set(Calendar.MINUTE, settings.waterEndMinute());
        if (end.getTimeInMillis() <= start.getTimeInMillis()) return 0L;
        if (tomorrowOnly || now >= end.getTimeInMillis()) {
            start.add(Calendar.DAY_OF_YEAR, 1);
            end.add(Calendar.DAY_OF_YEAR, 1);
            consumedMl = 0;
        }
        long nextAt = fixedAmountNextAt(start.getTimeInMillis(), end.getTimeInMillis(), now,
                Math.max(0, settings.waterDailyTargetMl() - consumedMl), settings.waterAmountMl());
        if (nextAt > 0L) return nextAt;
        start.add(Calendar.DAY_OF_YEAR, 1);
        end.add(Calendar.DAY_OF_YEAR, 1);
        return fixedAmountNextAt(start.getTimeInMillis(), end.getTimeInMillis(), now,
                settings.waterDailyTargetMl(), settings.waterAmountMl());
    }

    public static long nextTriggerAt(Context context, long now, boolean targetReached) {
        ReminderSettings settings = new ReminderSettings(context);
        WaterReminderStore store = new WaterReminderStore(context);
        long snoozeAt = store.pendingSnoozeAt();
        if (snoozeAt > 0L && shouldRecoverSnooze(snoozeAt, now)) return snoozeAt;
        if (ReminderSettings.WATER_MODE_FIXED_AMOUNT.equals(settings.waterMode())) {
            long saved = store.nextFixedAt();
            if (!targetReached && shouldRecoverSnooze(saved, now)) return saved;
            return nextFixedAmountTriggerAt(settings, now, targetReached, store.consumedTodayMl());
        }
        return nextTriggerAt(settings, now, targetReached);
    }

    /** Returns the actual next water delivery time, including a preserved snooze and quiet time. */
    public static long nextScheduledAt(Context context) {
        if (!EntitlementAccess.isFeatureAccessGranted(context)) return 0L;
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.waterRemindersEnabled()) return 0L;
        WaterReminderStore store = new WaterReminderStore(context);
        long now = System.currentTimeMillis();
        long snoozeAt = store.pendingSnoozeAt();
        if (snoozeAt > 0L && shouldRecoverSnooze(snoozeAt, now)) return snoozeAt;
        boolean targetReached = store.consumedTodayMl() >= settings.waterDailyTargetMl();
        if (ReminderSettings.WATER_MODE_FIXED_AMOUNT.equals(settings.waterMode())) {
            long saved = store.nextFixedAt();
            if (!targetReached && shouldRecoverSnooze(saved, now)) return saved;
            long next = nextFixedAmountTriggerAt(settings, now, targetReached, store.consumedTodayMl());
            return next <= 0L ? 0L : QuietTimeHelper.adjust(context, next);
        }
        long triggerAt = nextTriggerAt(settings, now, targetReached);
        return triggerAt <= 0L ? 0L : QuietTimeHelper.adjust(context, triggerAt);
    }

    public static int amountForRemaining(int remainingMl, int remainingSlots) {
        if (remainingMl <= 0 || remainingSlots <= 0) {
            return 0;
        }
        // Keep the distribution even to the nearest millilitre. Re-evaluating after every
        // drink keeps any unavoidable rounding difference to at most one millilitre.
        return (int) Math.ceil(remainingMl / (double) remainingSlots);
    }

    static int remainingReminderSlots(ReminderSettings settings, long triggerAt) {
        Calendar trigger = Calendar.getInstance();
        trigger.setTimeInMillis(triggerAt);
        int triggerMinute = trigger.get(Calendar.HOUR_OF_DAY) * 60 + trigger.get(Calendar.MINUTE);
        return remainingReminderSlots(
                settings.waterStartHour() * 60 + settings.waterStartMinute(),
                settings.waterEndHour() * 60 + settings.waterEndMinute(),
                settings.waterIntervalMinutes(),
                triggerMinute);
    }

    /** Counts this alert plus the regular alert slots still available before the window ends. */
    public static int remainingReminderSlots(int startMinute, int endMinute, int intervalMinutes,
                                             int triggerMinute) {
        if (endMinute <= startMinute || intervalMinutes <= 0) {
            return 0;
        }
        if (triggerMinute < startMinute) {
            return remindersPerDay(startMinute, endMinute, intervalMinutes);
        }
        if (triggerMinute > endMinute) {
            return 1;
        }
        int nextRegularSlot = startMinute
                + ((triggerMinute - startMinute) / intervalMinutes + 1) * intervalMinutes;
        int laterSlots = nextRegularSlot > endMinute ? 0
                : ((endMinute - nextRegularSlot) / intervalMinutes) + 1;
        return 1 + laterSlots;
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

    private static PendingIntent pendingIntent(Context context, long triggerAt) {
        Intent intent = new Intent(context, WaterReminderReceiver.class)
                .putExtra(EXTRA_TRIGGER_AT, triggerAt);
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent autoSnoozeIntent(Context context, long triggerAt) {
        Intent intent = new Intent(context, WaterReminderReceiver.class)
                .setAction(ACTION_AUTO_SNOOZE)
                .putExtra(EXTRA_TRIGGER_AT, triggerAt);
        return PendingIntent.getBroadcast(context, AUTO_SNOOZE_REQUEST_CODE, intent,
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
