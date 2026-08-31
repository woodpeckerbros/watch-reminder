package com.woodpeckerbros.watchreminder.smartwake;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.R;
import com.woodpeckerbros.watchreminder.ReminderSettings;

public final class SmartAlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_PREFIX = "smart_alarm_alert_v7";
    private static final String CHANNEL_VIBRATION = CHANNEL_PREFIX + "_vibration";
    private static final String CHANNEL_SILENT = CHANNEL_PREFIX + "_silent";
    private static final long[] SYSTEM_ALARM_VIBRATION =
            new long[]{0L, 350L, 180L, 350L, 180L, 350L, 180L, 350L};

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
        deliver(context, alarmId, targetAt, reason);
    }

    public static void fireWakeCheckEscalation(Context context, int alarmId, long targetAt) {
        deliver(context, alarmId, targetAt, "wake_check_escalation");
    }

    private static void deliver(Context context, int alarmId, long targetAt, String reason) {
        if (!"deadline".equals(reason)) SmartAlarmScheduler.cancelDeadline(context, alarmId);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        SmartAlarmStore settings = new SmartAlarmStore(context, alarmId);
        boolean systemVibrationEnabled = settings.vibrationEnabled()
                && !ReminderSettings.VIBRATION_OFF.equals(settings.vibrationStyle());
        String channelId = systemVibrationEnabled ? CHANNEL_VIBRATION : CHANNEL_SILENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            for (NotificationChannel existing : manager.getNotificationChannels()) {
                if (existing.getId().startsWith("smart_alarm_alert")
                        && !existing.getId().startsWith(CHANNEL_PREFIX))
                    manager.deleteNotificationChannel(existing.getId());
            }
        }
        NotificationChannel channel = new NotificationChannel(channelId, "Smart Alarm", NotificationManager.IMPORTANCE_HIGH);
        AudioAttributes alarmAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(null, alarmAttributes);
        channel.enableVibration(systemVibrationEnabled);
        if (systemVibrationEnabled) channel.setVibrationPattern(SYSTEM_ALARM_VIBRATION);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
        Intent activity = new Intent(context, SmartAlarmAlertActivity.class)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt).putExtra("reason", reason)
                .putExtra("wake_check_escalation", "wake_check_escalation".equals(reason))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(context, 0x534d5803 + alarmId, activity,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle("Smart Alarm")
                .setContentText("זמן להתעורר").setCategory(Notification.CATEGORY_ALARM)
                .setStyle(new Notification.BigTextStyle().bigText("זמן להתעורר"))
                .setPriority(Notification.PRIORITY_MAX).setContentIntent(pending)
                .setFullScreenIntent(pending, true).setSound(null)
                .setDefaults(0).setOnlyAlertOnce(true).setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(SmartAlarmActions.snoozeAction(context, alarmId, targetAt))
                .addAction(SmartAlarmActions.dismissAction(context, alarmId, targetAt));
        if (systemVibrationEnabled) builder.setVibrate(SYSTEM_ALARM_VIBRATION);
        Notification notification = builder.build();
        manager.notify(0x534d5704 + alarmId, notification);
        AppLog.d(context, "SmartAlarm notified id=" + alarmId
                + " fullScreen=" + AppLog.fullScreenIntentAllowed(context)
                + " notifications=" + AppLog.notificationPermissionAllowed(context));
        // Use the same proven full-screen notification path as regular reminders. Explicitly
        // sending the same PendingIntent here can create/reuse a background task before Wear OS
        // processes the full-screen intent, leaving sound active without presenting the screen.
        SmartWakeMonitoringService.stop(context, alarmId);
        SmartAlarmScheduler.scheduleAutoSnooze(context, alarmId, targetAt,
                settings.alertDurationSeconds());
        Context appContext = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (SmartAlarmAlertActivity.isShowing(alarmId, targetAt)) {
                AppLog.d(appContext, "SmartAlarm ringing fallback skipped; activity visible id=" + alarmId);
            } else {
                SmartAlarmRingingService.start(appContext, alarmId, targetAt);
                AppLog.w(appContext, "SmartAlarm ringing fallback started id=" + alarmId);
            }
        }, 900L);
        AppLog.d(context, "SmartAlarm fired target=" + targetAt + " reason=" + reason);
    }

}
