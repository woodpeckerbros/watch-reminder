package com.woodpeckerbros.watchreminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;

import java.util.Calendar;

public class DafYomiScheduler {
    static final String EXTRA_RETRY = "daf_yomi_retry";
    private static final String REQUEST_KEY = "daf_yomi_daily";

    private DafYomiScheduler() {
    }

    public static void schedule(Context context) {
        ReminderSettings settings = new ReminderSettings(context);
        cancelDaily(context);
        if (!settings.jewishMode() || !settings.dafYomiEnabled()) {
            AppLog.d(context, "daf yomi schedule skipped disabled");
            return;
        }
        new DafYomiStore(context).ensureStartToday();
        long triggerAt = nextTriggerAt(context, settings.dafYomiHour(), settings.dafYomiMinute());
        AppLog.d(context, "daf yomi schedule at=" + NextReminderCalculator.formatDateTime(triggerAt));
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        setBest(context, alarmManager, triggerAt, pendingIntent(context, false));
    }

    public static boolean dispatchIfDueNow(Context context) {
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.jewishMode() || !settings.dafYomiEnabled()) {
            return false;
        }
        DafYomiStore store = new DafYomiStore(context);
        long retryUntil = store.retryUntil();
        long now = System.currentTimeMillis();
        if (retryUntil > now) {
            AppLog.d(context, "daf yomi catch-up skipped retry until=" + NextReminderCalculator.formatDateTime(retryUntil));
            schedule(context);
            return false;
        }
        if (retryUntil > 0) {
            store.clearRetryUntil();
        }
        if (store.dueItems(context).isEmpty()) {
            schedule(context);
            return false;
        }
        long todayTriggerAt = todayTriggerAt(context, settings.dafYomiHour(), settings.dafYomiMinute());
        if (todayTriggerAt > now) {
            schedule(context);
            return false;
        }
        AppLog.d(context, "daf yomi catch-up open alert trigger=" + NextReminderCalculator.formatDateTime(todayTriggerAt));
        Intent alert = new Intent(context, DafYomiAlertActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(alert);
        DafYomiReceiver.cancelNotification(context);
        schedule(context);
        return true;
    }

    public static void scheduleRetry(Context context, int minutes) {
        long triggerAt = ReminderScheduler.ceilToMinute(System.currentTimeMillis() + minutes * 60_000L);
        triggerAt = adjustForShabbosOrYomTov(context, triggerAt);
        new DafYomiStore(context).setRetryUntil(triggerAt);
        AppLog.d(context, "daf yomi retry at=" + NextReminderCalculator.formatDateTime(triggerAt));
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        setBest(context, alarmManager, triggerAt, pendingIntent(context, true));
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent(context, false));
        alarmManager.cancel(pendingIntent(context, true));
        new DafYomiStore(context).clearRetryUntil();
    }

    private static void cancelDaily(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent(context, false));
    }

    private static long nextTriggerAt(Context context, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        return adjustForShabbosOrYomTov(context, calendar.getTimeInMillis());
    }

    private static long todayTriggerAt(Context context, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        return adjustForShabbosOrYomTov(context, calendar.getTimeInMillis());
    }

    private static long adjustForShabbosOrYomTov(Context context, long triggerAt) {
        long adjusted = ReminderScheduler.floorToMinute(triggerAt);
        for (int i = 0; i < 4; i++) {
            long exit = blockedUntil(context, adjusted);
            if (exit <= adjusted) {
                return adjusted;
            }
            adjusted = ReminderScheduler.ceilToMinute(exit + 30 * 60_000L);
        }
        return adjusted;
    }

    private static long blockedUntil(Context context, long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        JewishCalendar today = jewishCalendar(context, calendar);
        long tzeisToday = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_TZAIS, time);
        if (today.isAssurBemelacha() && tzeisToday != Long.MAX_VALUE && time < tzeisToday) {
            return tzeisToday;
        }

        Calendar tomorrowCal = (Calendar) calendar.clone();
        tomorrowCal.add(Calendar.DAY_OF_YEAR, 1);
        JewishCalendar tomorrow = jewishCalendar(context, tomorrowCal);
        long candleLikeStart = ZmanimHelper.shabbatTimeForKey(context, ZmanimHelper.KEY_CANDLE_LIGHTING, time);
        if (tomorrow.isAssurBemelacha() && candleLikeStart > 0 && time >= candleLikeStart) {
            long tzeisTomorrow = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_TZAIS, tomorrowCal.getTimeInMillis());
            return tzeisTomorrow == Long.MAX_VALUE ? time : tzeisTomorrow;
        }
        return time;
    }

    private static JewishCalendar jewishCalendar(Context context, Calendar calendar) {
        return JewishCalendarHelper.calendar(context, calendar);
    }

    private static PendingIntent pendingIntent(Context context, boolean retry) {
        Intent intent = new Intent(context, DafYomiReceiver.class)
                .putExtra(EXTRA_RETRY, retry);
        return PendingIntent.getBroadcast(
                context,
                (REQUEST_KEY + (retry ? ":retry" : "")).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static void setBest(Context context, AlarmManager alarmManager, long triggerAt, PendingIntent pendingIntent) {
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } catch (SecurityException exception) {
            AppLog.e(context, "daf yomi exact alarm failed", exception);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }
}
