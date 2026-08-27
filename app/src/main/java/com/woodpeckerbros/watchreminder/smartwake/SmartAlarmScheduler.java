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
    private SmartAlarmScheduler() {}

    public static void reschedule(Context context) {
        cancel(context);
        SmartAlarmStore store = new SmartAlarmStore(context);
        if (!store.enabled()) return;
        long targetAt = nextTarget(store.hour(), store.minute(), store.daysMask(), System.currentTimeMillis());
        if (targetAt == Long.MAX_VALUE) return;
        long windowAt = targetAt - store.windowMinutes() * 60_000L;
        SmartAlarmStateStore state = new SmartAlarmStateStore(context);
        state.beginSnooze(targetAt, state.snoozeUsed() + 1);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        setWindowAlarm(context, manager, windowAt, windowIntent(context, targetAt));
        setDeadlineAlarm(context, manager, targetAt, deadlineIntent(context, targetAt));
        AppLog.d(context, "SmartAlarm scheduled window=" + windowAt + " target=" + targetAt);
    }

    public static void scheduleSnooze(Context context, long originalTargetAt, int minutes) {
        cancel(context);
        long targetAt = System.currentTimeMillis() + minutes * 60_000L;
        new SmartAlarmStateStore(context).begin(targetAt);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) setDeadlineAlarm(context, manager, targetAt, deadlineIntent(context, targetAt));
        AppLog.d(context, "SmartAlarm snoozed original=" + originalTargetAt + " target=" + targetAt);
    }

    public static void scheduleNextAfterHandled(Context context) { reschedule(context); }

    public static void cancel(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        manager.cancel(windowIntent(context, 0));
        manager.cancel(deadlineIntent(context, 0));
    }

    public static void cancelDeadline(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(deadlineIntent(context, 0));
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

    private static void setDeadlineAlarm(Context context, AlarmManager manager, long at, PendingIntent intent) {
        try {
            if (ReminderScheduler.canScheduleExactAlarms(context))
                manager.setAlarmClock(new AlarmManager.AlarmClockInfo(at, alertIntent(context, at)), intent);
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
        } catch (SecurityException error) {
            AppLog.e(context, "SmartAlarm exact deadline permission missing", error);
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
        }
    }

    private static PendingIntent windowIntent(Context context, long targetAt) {
        Intent intent = new Intent(context, SmartWakeWindowReceiver.class).putExtra(EXTRA_TARGET_AT, targetAt);
        return PendingIntent.getBroadcast(context, 0x534d5701, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent deadlineIntent(Context context, long targetAt) {
        Intent intent = new Intent(context, SmartAlarmReceiver.class).putExtra(EXTRA_TARGET_AT, targetAt).putExtra("reason", "deadline");
        return PendingIntent.getBroadcast(context, 0x534d5702, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent alertIntent(Context context, long targetAt) {
        Intent intent = new Intent(context, SmartAlarmAlertActivity.class).putExtra(EXTRA_TARGET_AT, targetAt);
        return PendingIntent.getActivity(context, 0x534d5703, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
