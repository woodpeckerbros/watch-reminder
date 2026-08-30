package com.woodpeckerbros.watchreminder.smartwake;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.ReminderScheduler;

import java.util.Calendar;

public final class SmartAlarmScheduler {
    public static final String EXTRA_TARGET_AT = "smart_alarm_target_at";
    public static final String EXTRA_ALARM_ID = "smart_alarm_id";
    private SmartAlarmScheduler() {}

    public static void reschedule(Context context) {
        cancel(context);
        for (int alarmId : SmartAlarmStore.ids(context)) schedule(context, alarmId);
    }

    public static void reschedule(Context context, int alarmId) {
        cancel(context, alarmId);
        schedule(context, alarmId);
    }

    private static void schedule(Context context, int alarmId) {
        SmartAlarmStore store = new SmartAlarmStore(context, alarmId);
        if (!store.enabled()) return;
        long targetAt = nextTarget(store.hour(), store.minute(), store.daysMask(), System.currentTimeMillis());
        if (targetAt == Long.MAX_VALUE) return;
        long windowAt = targetAt - store.windowMinutes() * 60_000L;
        SmartAlarmStateStore state = new SmartAlarmStateStore(context, alarmId);
        state.begin(targetAt);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        setWindowAlarm(context, manager, windowAt, windowIntent(context, alarmId, targetAt));
        setDeadlineAlarm(context, manager, alarmId, targetAt, deadlineIntent(context, alarmId, targetAt));
        AppLog.d(context, "SmartAlarm scheduled id=" + alarmId + " window=" + windowAt + " target=" + targetAt);
    }

    public static void scheduleSnooze(Context context, int alarmId, long originalTargetAt, int minutes) {
        cancelAutoSnooze(context, alarmId);
        cancel(context, alarmId);
        long targetAt = System.currentTimeMillis() + minutes * 60_000L;
        SmartAlarmStateStore state = new SmartAlarmStateStore(context, alarmId);
        state.beginSnooze(targetAt, state.snoozeUsed() + 1);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) setDeadlineAlarm(context, manager, alarmId, targetAt, deadlineIntent(context, alarmId, targetAt));
        AppLog.d(context, "SmartAlarm snoozed id=" + alarmId + " original=" + originalTargetAt + " target=" + targetAt);
    }

    public static void scheduleNextAfterHandled(Context context, int alarmId) { reschedule(context, alarmId); }

    public static void scheduleDetectedFire(Context context, int alarmId, long targetAt) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        long fireAt = System.currentTimeMillis() + 500L;
        PendingIntent operation = detectedFireIntent(context, alarmId, targetAt);
        try {
            if (ReminderScheduler.canScheduleExactAlarms(context)) {
                manager.setAlarmClock(new AlarmManager.AlarmClockInfo(
                        fireAt, alertIntent(context, alarmId, targetAt)), operation);
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, operation);
            }
        } catch (SecurityException error) {
            AppLog.e(context, "SmartAlarm detected fire exact permission missing", error);
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, operation);
        }
        AppLog.d(context, "SmartAlarm detected fire handed to AlarmManager id=" + alarmId
                + " target=" + targetAt + " fireAt=" + fireAt);
    }

    public static void scheduleAutoSnooze(Context context, int alarmId, long targetAt, int delaySeconds) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        long at = System.currentTimeMillis() + Math.max(5, delaySeconds) * 1000L;
        PendingIntent pending = autoSnoozeIntent(context, alarmId, targetAt);
        try {
            if (ReminderScheduler.canScheduleExactAlarms(context))
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
        } catch (SecurityException error) {
            AppLog.e(context, "SmartAlarm auto-snooze exact permission missing", error);
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
        }
        AppLog.d(context, "SmartAlarm auto-snooze scheduled id=" + alarmId + " at=" + at);
    }

    public static void cancelAutoSnooze(Context context, int alarmId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(autoSnoozeIntent(context, alarmId, 0L));
    }

    public static void cancel(Context context) {
        for (int alarmId : SmartAlarmStore.ids(context)) cancel(context, alarmId);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) {
            manager.cancel(legacyWindowIntent(context)); manager.cancel(legacyDeadlineIntent(context));
        }
    }

    public static void cancel(Context context, int alarmId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        manager.cancel(windowIntent(context, alarmId, 0));
        manager.cancel(deadlineIntent(context, alarmId, 0));
        manager.cancel(detectedFireIntent(context, alarmId, 0));
        manager.cancel(autoSnoozeIntent(context, alarmId, 0));
    }

    public static void cancelDeadline(Context context, int alarmId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(deadlineIntent(context, alarmId, 0));
    }

    static long nextTarget(int hour, int minute, int daysMask, long now) {
        if (daysMask == 0) return Long.MAX_VALUE;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now); calendar.set(Calendar.HOUR_OF_DAY, hour); calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0);
        for (int offset = 0; offset <= 7; offset++) {
            if (offset > 0) calendar.add(Calendar.DAY_OF_YEAR, 1);
            boolean selected = (daysMask & (1 << calendar.get(Calendar.DAY_OF_WEEK))) != 0;
            if (selected && calendar.getTimeInMillis() > now) return calendar.getTimeInMillis();
        }
        return Long.MAX_VALUE;
    }

    private static void setWindowAlarm(Context context, AlarmManager manager, long at, PendingIntent intent) {
        try {
            if (ReminderScheduler.canScheduleExactAlarms(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
        } catch (SecurityException error) {
            AppLog.e(context, "SmartAlarm exact window permission missing", error);
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
        }
    }

    private static void setDeadlineAlarm(Context context, AlarmManager manager, int alarmId, long at, PendingIntent intent) {
        try {
            if (ReminderScheduler.canScheduleExactAlarms(context))
                manager.setAlarmClock(new AlarmManager.AlarmClockInfo(at, alertIntent(context, alarmId, at)), intent);
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
        } catch (SecurityException error) {
            AppLog.e(context, "SmartAlarm exact deadline permission missing", error);
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
        }
    }

    private static PendingIntent windowIntent(Context context, int alarmId, long targetAt) {
        Intent intent = alarmIntent(context, SmartWakeWindowReceiver.class, alarmId, targetAt);
        return PendingIntent.getBroadcast(context, requestCode(alarmId, 1), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent deadlineIntent(Context context, int alarmId, long targetAt) {
        Intent intent = alarmIntent(context, SmartAlarmReceiver.class, alarmId, targetAt).putExtra("reason", "deadline");
        return PendingIntent.getBroadcast(context, requestCode(alarmId, 2), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent detectedFireIntent(Context context, int alarmId, long targetAt) {
        Intent intent = alarmIntent(context, SmartAlarmReceiver.class, alarmId, targetAt)
                .putExtra("reason", "estimated_wake_window");
        return PendingIntent.getBroadcast(context, requestCode(alarmId, 5), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent alertIntent(Context context, int alarmId, long targetAt) {
        Intent source = alarmIntent(context, SmartAlarmAlertActivity.class, alarmId, targetAt);
        return PendingIntent.getActivity(context, requestCode(alarmId, 3), source, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent autoSnoozeIntent(Context context, int alarmId, long targetAt) {
        Intent intent = alarmIntent(context, SmartAlarmAutoSnoozeReceiver.class, alarmId, targetAt);
        return PendingIntent.getBroadcast(context, requestCode(alarmId, 4), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Intent alarmIntent(Context context, Class<?> type, int alarmId, long targetAt) {
        return new Intent(context, type).putExtra(EXTRA_ALARM_ID, alarmId).putExtra(EXTRA_TARGET_AT, targetAt);
    }
    private static int requestCode(int alarmId, int kind) { return 0x53000000 | ((alarmId & 0xfffff) << 3) | kind; }
    private static PendingIntent legacyWindowIntent(Context context) { return PendingIntent.getBroadcast(context, 0x534d5701, new Intent(context, SmartWakeWindowReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); }
    private static PendingIntent legacyDeadlineIntent(Context context) { return PendingIntent.getBroadcast(context, 0x534d5702, new Intent(context, SmartAlarmReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); }
}
