package com.woodpeckerbros.watchreminder.smartwake;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.R;
import com.woodpeckerbros.watchreminder.ReminderScheduler;

public final class SmartAlarmWakeCheckReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "smart_alarm_wake_check_v1";
    private static final int NOTIFICATION_BASE = 0x534d5A00;

    static void schedule(Context context, int alarmId, long targetAt, int minutes) {
        cancel(context, alarmId);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        long at = System.currentTimeMillis() + Math.max(1, minutes) * 60_000L;
        PendingIntent pending = receiverIntent(context, alarmId, targetAt, false);
        try {
            if (ReminderScheduler.canScheduleExactAlarms(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
        } catch (SecurityException error) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending); }
        AppLog.d(context, "SmartAlarm wake check scheduled id=" + alarmId + " at=" + at);
    }

    static void cancel(Context context, int alarmId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) {
            manager.cancel(receiverIntent(context, alarmId, 0L, false));
            manager.cancel(receiverIntent(context, alarmId, 0L, true));
        }
        NotificationManager notifications = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notifications != null) notifications.cancel(NOTIFICATION_BASE + alarmId);
    }

    static void confirm(Context context, int alarmId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(receiverIntent(context, alarmId, 0L, true));
        NotificationManager notifications = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notifications != null) notifications.cancel(NOTIFICATION_BASE + alarmId);
    }

    @Override public void onReceive(Context context, Intent source) {
        int alarmId = source.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        long targetAt = source.getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
        if (source.getBooleanExtra("escalate", false)) {
            SmartAlarmReceiver.fireWakeCheckEscalation(context, alarmId, targetAt);
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Smart Alarm wake check", NotificationManager.IMPORTANCE_HIGH));
        Intent activity = new Intent(context, SmartAlarmWakeCheckActivity.class)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(context, NOTIFICATION_BASE + alarmId, activity,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Smart Alarm").setContentText("האם אתם ערים?")
                .setCategory(Notification.CATEGORY_ALARM).setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(open).setFullScreenIntent(open, true).setAutoCancel(false).setOngoing(true).build();
        manager.notify(NOTIFICATION_BASE + alarmId, notification);
        scheduleEscalation(context, alarmId, targetAt);
    }

    private static void scheduleEscalation(Context context, int alarmId, long targetAt) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        long at = System.currentTimeMillis() + 30_000L;
        PendingIntent pending = receiverIntent(context, alarmId, targetAt, true);
        if (ReminderScheduler.canScheduleExactAlarms(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
    }

    private static PendingIntent receiverIntent(Context context, int alarmId, long targetAt, boolean escalate) {
        Intent intent = new Intent(context, SmartAlarmWakeCheckReceiver.class)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt).putExtra("escalate", escalate);
        return PendingIntent.getBroadcast(context, NOTIFICATION_BASE + alarmId + (escalate ? 0x1000 : 0), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
