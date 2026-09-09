package com.woodpeckerbros.watchreminder.smartalarm;

import com.woodpeckerbros.watchreminder.reminder.*;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.R;

public final class SmartAlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_PREFIX = "smart_alarm_alert_v9";
    private static final String CHANNEL_ATTENTION = CHANNEL_PREFIX + "_attention";
    private static final long[] ATTENTION_VIBRATION = {0L, 1L};

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
        // OnePlus Wear OS does not present a full-screen notification that it considers entirely
        // silent.  Keep sound under the ringing service/activity, but give this transport channel
        // a one-millisecond attention vibration so SystemUI classifies it as alerting and actually
        // dispatches its full-screen PendingIntent.  It overlaps the configured alarm vibration
        // and is not perceptible as a second alert.
        String channelId = CHANNEL_ATTENTION;
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
        channel.setVibrationPattern(ATTENTION_VIBRATION);
        channel.enableVibration(true);
        channel.setBypassDnd(manager.isNotificationPolicyAccessGranted());
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
        Intent activity = new Intent(context, SmartAlarmAlertActivity.class)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt).putExtra("reason", reason)
                .putExtra("wake_check_escalation", "wake_check_escalation".equals(reason))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle creatorOptions = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            creatorOptions = options.toBundle();
        }
        PendingIntent pending = PendingIntent.getActivity(context, 0x534d5803 + alarmId, activity,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE, creatorOptions);
        Notification.Builder builder = new Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle("Smart Alarm")
                .setContentText("זמן להתעורר").setCategory(Notification.CATEGORY_ALARM)
                .setStyle(new Notification.BigTextStyle().bigText("זמן להתעורר"))
                .setPriority(Notification.PRIORITY_MAX).setContentIntent(pending)
                .setFullScreenIntent(pending, true).setSound(null)
                .setVibrate(ATTENTION_VIBRATION)
                .setDefaults(0).setOnlyAlertOnce(true).setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(SmartAlarmActions.openAction(context, alarmId, targetAt))
                .addAction(SmartAlarmActions.snoozeAction(context, alarmId, targetAt))
                .addAction(SmartAlarmActions.dismissAction(context, alarmId, targetAt));
        Notification notification = builder.build();
        manager.notify(0x534d5704 + alarmId, notification);
        AppLog.d(context, "SmartAlarm notified id=" + alarmId
                + " fullScreen=" + AppLog.fullScreenIntentAllowed(context)
                + " notifications=" + AppLog.notificationPermissionAllowed(context));
        // Request the ringing foreground service while this exact-alarm receiver still owns
        // Android's temporary background-start exemption. Starting it later from a Handler can
        // be rejected after onReceive returns, precisely when Wear OS suppresses the full-screen
        // intent in Bedtime/DND mode.
        boolean ringingServiceRequested = SmartAlarmRingingService.start(context, alarmId, targetAt);
        AppLog.d(context, "SmartAlarm immediate ringing service requested id=" + alarmId
                + " accepted=" + ringingServiceRequested);
        // Use the same proven full-screen notification path as regular reminders. Explicitly
        // sending the same PendingIntent here can create/reuse a background task before Wear OS
        // processes the full-screen intent, leaving sound active without presenting the screen.
        SmartWakeMonitoringService.stop(context, alarmId);
        SmartAlarmScheduler.scheduleAutoSnooze(context, alarmId, targetAt,
                settings.alertDurationSeconds());
        Context appContext = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (SmartAlarmAlertActivity.isShowing(alarmId, targetAt)) {
                AppLog.d(appContext, "SmartAlarm full-screen activity confirmed id=" + alarmId);
            } else {
                AppLog.w(appContext, "SmartAlarm full-screen activity not visible id=" + alarmId
                        + " ringingServiceRequested=" + ringingServiceRequested);
            }
        }, 2_000L);
        AppLog.d(context, "SmartAlarm fired target=" + targetAt + " reason=" + reason
                + ("deadline".equals(reason) ? " WAKE_REASON=FINAL_DEADLINE" : ""));
    }

}
