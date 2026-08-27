package com.woodpeckerbros.watchreminder.smartwake;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.R;

public final class SmartAlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "smart_alarm_alert_v1";

    @Override public void onReceive(Context context, Intent intent) {
        long targetAt = intent.getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
        int alarmId = intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        fire(context, alarmId, targetAt, intent.getStringExtra("reason"));
    }

    public static void fire(Context context, int alarmId, long targetAt, String reason) {
        SmartAlarmStateStore state = new SmartAlarmStateStore(context, alarmId);
        if (!state.claimFire(targetAt)) {
            AppLog.w(context, "SmartAlarm duplicate/stale fire target=" + targetAt + " reason=" + reason);
            return;
        }
        SmartWakeMonitoringService.stop(context, alarmId);
        if (!"deadline".equals(reason)) SmartAlarmScheduler.cancelDeadline(context, alarmId);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Smart Alarm", NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null); channel.enableVibration(false); manager.createNotificationChannel(channel);
        Intent activity = new Intent(context, SmartAlarmAlertActivity.class)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt).putExtra("reason", reason)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(context, 0x534d5703 + alarmId, activity,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle("Smart Alarm")
                .setContentText("זמן להתעורר").setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX).setContentIntent(pending)
                .setFullScreenIntent(pending, true).setSound(null).setVibrate(new long[]{0}).setAutoCancel(true).build();
        manager.notify(0x534d5704 + alarmId, notification);
        SmartAlarmRingingService.start(context, alarmId);
        try {
            context.startActivity(activity);
        } catch (RuntimeException error) {
            AppLog.w(context, "SmartAlarm direct activity launch blocked; full-screen notification remains available: " + error.getClass().getSimpleName());
        }
        AppLog.d(context, "SmartAlarm fired target=" + targetAt + " reason=" + reason);
    }
}
