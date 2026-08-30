package com.woodpeckerbros.watchreminder.smartwake;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;

import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.R;

public final class SmartAlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "smart_alarm_alert_v3";

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            for (NotificationChannel existing : manager.getNotificationChannels()) {
                if (existing.getId().startsWith("smart_alarm_alert") && !CHANNEL.equals(existing.getId()))
                    manager.deleteNotificationChannel(existing.getId());
            }
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Smart Alarm", NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null); channel.enableVibration(false); channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
        Intent activity = new Intent(context, SmartAlarmAlertActivity.class)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt).putExtra("reason", reason)
                .putExtra("wake_check_escalation", "wake_check_escalation".equals(reason))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Bundle creatorOptions = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            creatorOptions = options.toBundle();
        }
        PendingIntent pending = PendingIntent.getActivity(context, 0x534d5803 + alarmId, activity,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE, creatorOptions);
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle("Smart Alarm")
                .setContentText("זמן להתעורר").setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX).setContentIntent(pending)
                .setFullScreenIntent(pending, true).setSound(null).setVibrate(new long[]{0})
                .setDefaults(0).setOnlyAlertOnce(true).setAutoCancel(false).setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(SmartAlarmActions.snoozeAction(context, alarmId, targetAt))
                .addAction(SmartAlarmActions.dismissAction(context, alarmId, targetAt)).build();
        manager.notify(0x534d5704 + alarmId, notification);
        AppLog.d(context, "SmartAlarm notified id=" + alarmId
                + " fullScreen=" + AppLog.fullScreenIntentAllowed(context)
                + " notifications=" + AppLog.notificationPermissionAllowed(context));
        wakeAndOpenAlert(context, pending, alarmId);
        // Stop sensor collection only after both the full-screen notification and its explicit
        // launch fallback have been requested. This preserves the foreground execution window
        // that exists when an early wake decision comes from the monitoring service.
        SmartWakeMonitoringService.stop(context, alarmId);
        SmartAlarmScheduler.scheduleAutoSnooze(context, alarmId, targetAt,
                new SmartAlarmStore(context, alarmId).alertDurationSeconds());
        SmartAlarmRingingService.start(context, alarmId, targetAt);
        AppLog.d(context, "SmartAlarm fired target=" + targetAt + " reason=" + reason);
    }

    private static void wakeAndOpenAlert(Context context, PendingIntent pending, int alarmId) {
        PowerManager.WakeLock wakeLock = null;
        try {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (power != null) {
                wakeLock = power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP, "Zmanio:SmartAlarmScreen");
                wakeLock.acquire(10_000L);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                pending.send(context, 0, null, null, null, null, options.toBundle());
            } else {
                pending.send();
            }
            AppLog.d(context, "SmartAlarm explicit alert launch sent id=" + alarmId);
        } catch (Exception error) {
            AppLog.e(context, "SmartAlarm explicit alert launch failed id=" + alarmId, error);
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }
}
