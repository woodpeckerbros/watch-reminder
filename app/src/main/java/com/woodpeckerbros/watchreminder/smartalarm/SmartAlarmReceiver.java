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
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;

import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.R;
import com.woodpeckerbros.watchreminder.entitlement.EntitlementAccess;
import com.woodpeckerbros.watchreminder.entitlement.EntitlementEnforcer;

public final class SmartAlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_PREFIX = "smart_alarm_alert_v9";
    private static final String CHANNEL_ATTENTION = CHANNEL_PREFIX + "_attention";
    private static final String CHANNEL_DIRECT_BOOT = "smart_alarm_direct_boot_v1";
    private static final long[] ATTENTION_VIBRATION = {0L, 1L};
    private static final int NOTIFICATION_BASE = 0x534d5704;
    private static final Object DELIVERY_LOCK = new Object();

    @Override public void onReceive(Context context, Intent intent) {
        long targetAt = intent.getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
        int alarmId = intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        String reason = intent.getStringExtra("reason");
        UserManager userManager = context.getSystemService(UserManager.class);
        boolean userUnlocked = userManager == null || userManager.isUserUnlocked();
        AppLog.w(context, "FINAL_ALARM_RECEIVED id=" + alarmId + " occurrence_id="
                + alarmId + ":" + targetAt + " target=" + targetAt + " reason=" + reason
                + " userUnlocked=" + userUnlocked);
        if (!userUnlocked) {
            fireDirectBoot(context, alarmId, targetAt);
            return;
        }
        fire(context, alarmId, targetAt, reason);
    }

    public static void fire(Context context, int alarmId, long targetAt, String reason) {
        if (!EntitlementAccess.isFeatureAccessGranted(context)) {
            AppLog.d(context, "SmartAlarm delivery blocked: entitlement expired");
            EntitlementEnforcer.disableDeliveries(context);
            return;
        }
        synchronized (DELIVERY_LOCK) {
            SmartAlarmStateStore state = new SmartAlarmStateStore(context, alarmId);
            if (!state.canFire(targetAt)) {
                AppLog.w(context, "SmartAlarm duplicate/stale fire target=" + targetAt
                        + " reason=" + reason);
                return;
            }
            try {
                if (!deliver(context, alarmId, targetAt, reason)) {
                    AppLog.e(context, "SmartAlarm delivery failed before durable notification",
                            new IllegalStateException("notification manager unavailable"));
                }
            } catch (RuntimeException error) {
                AppLog.e(context, "SmartAlarm delivery failed before durable notification", error);
            }
        }
    }

    public static void fireWakeCheckEscalation(Context context, int alarmId, long targetAt) {
        deliver(context, alarmId, targetAt, "wake_check_escalation");
    }

    private static boolean deliver(Context context, int alarmId, long targetAt, String reason) {
        boolean wakeCheckEscalation = "wake_check_escalation".equals(reason);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return false;
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
                .putExtra("wake_check_escalation", wakeCheckEscalation)
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
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        if (wakeCheckEscalation) {
            builder.addAction(SmartAlarmActions.dismissAction(
                    context, alarmId, targetAt, true));
        } else {
            builder.addAction(SmartAlarmActions.openAction(context, alarmId, targetAt))
                    .addAction(SmartAlarmActions.snoozeAction(context, alarmId, targetAt))
                    .addAction(SmartAlarmActions.dismissAction(context, alarmId, targetAt));
        }
        Notification notification = builder.build();
        manager.notify(NOTIFICATION_BASE + alarmId, notification);
        // manager.notify() is the first durable alert-delivery point.  An internal Smart Wake
        // decision must never terminalize state or remove the independent final deadline first.
        boolean occurrenceStateDurable = wakeCheckEscalation
                || new SmartAlarmStateStore(context, alarmId).markFireDelivered(targetAt);
        if (!occurrenceStateDurable) {
            AppLog.e(context, "SmartAlarm notification posted but fired state was not durable; "
                    + "retaining final deadline", new IllegalStateException("state commit failed"));
        }
        if (shouldCancelFinalDeadline(reason, occurrenceStateDurable)) {
            SmartAlarmScheduler.cancelDeadline(context, alarmId, targetAt,
                    "EARLY_ALERT_NOTIFICATION_POSTED");
        } else if ("deadline".equals(reason) && occurrenceStateDurable) {
            SmartAlarmBootStore.disarm(context, alarmId, targetAt);
        }
        AppLog.d(context, "SmartAlarm notified id=" + alarmId
                + " fullScreen=" + AppLog.fullScreenIntentAllowed(context)
                + " notifications=" + AppLog.notificationPermissionAllowed(context));
        // Request the ringing foreground service while this exact-alarm receiver still owns
        // Android's temporary background-start exemption. Starting it later from a Handler can
        // be rejected after onReceive returns, precisely when Wear OS suppresses the full-screen
        // intent in Bedtime/DND mode.
        boolean ringingServiceRequested = SmartAlarmRingingService.start(
                context, alarmId, targetAt, wakeCheckEscalation);
        AppLog.d(context, "SmartAlarm immediate ringing service requested id=" + alarmId
                + " accepted=" + ringingServiceRequested);
        // Use the same proven full-screen notification path as regular reminders. Explicitly
        // sending the same PendingIntent here can create/reuse a background task before Wear OS
        // processes the full-screen intent, leaving sound active without presenting the screen.
        SmartWakeMonitoringService.stop(context, alarmId);
        SmartAlarmScheduler.scheduleAutoSnooze(context, alarmId, targetAt,
                settings.alertDurationSeconds(), wakeCheckEscalation);
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
                + " WAKE_REASON=" + diagnosticWakeReason(reason));
        if ("deadline".equals(reason)) {
            AppLog.w(context, "SMART_WAKE_DEADLINE_FALLBACK id=" + alarmId
                    + " target=" + targetAt
                    + " reason=NO_CONFIRMED_EARLY_WAKE_OPPORTUNITY");
        }
        return true;
    }

    static boolean shouldCancelFinalDeadline(String deliveryReason, boolean deliveryDurable) {
        return deliveryDurable && !"deadline".equals(deliveryReason)
                && !"wake_check_escalation".equals(deliveryReason);
    }

    private static void fireDirectBoot(Context context, int alarmId, long targetAt) {
        synchronized (DELIVERY_LOCK) {
            if (!SmartAlarmBootStore.matches(context, alarmId, targetAt)
                    || SmartAlarmBootStore.delivered(context, alarmId, targetAt)) {
                AppLog.w(context, "SmartAlarm direct-boot duplicate/stale id=" + alarmId
                        + " target=" + targetAt);
                return;
            }
            try {
                NotificationManager manager = context.getSystemService(NotificationManager.class);
                if (manager == null) throw new IllegalStateException("notification manager unavailable");
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_DIRECT_BOOT, "Smart Alarm after restart", NotificationManager.IMPORTANCE_HIGH);
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attributes);
                channel.setVibrationPattern(new long[]{0L, 500L, 300L, 500L});
                channel.enableVibration(true);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                manager.createNotificationChannel(channel);
                Notification notification = new Notification.Builder(context, CHANNEL_DIRECT_BOOT)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle("Smart Alarm")
                        .setContentText("זמן להתעורר · יש לפתוח את השעון לניהול ההתראה")
                        .setCategory(Notification.CATEGORY_ALARM)
                        .setPriority(Notification.PRIORITY_MAX)
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setTimeoutAfter(SmartAlarmRingingService.DIRECT_BOOT_ALERT_DURATION_MS)
                        .build();
                manager.notify(NOTIFICATION_BASE + alarmId, notification);
                boolean markedDelivered = SmartAlarmBootStore.markDirectBootDelivered(
                        context, alarmId, targetAt);
                boolean serviceStarted = SmartAlarmRingingService.startDirectBoot(
                        context, alarmId, targetAt);
                AppLog.w(context, "SmartAlarm direct-boot final alert posted id=" + alarmId
                        + " target=" + targetAt + " stateDurable=" + markedDelivered
                        + " ringingServiceRequested=" + serviceStarted);
            } catch (RuntimeException error) {
                AppLog.e(context, "SmartAlarm direct-boot delivery failed before durable notification", error);
            }
        }
    }

    static String diagnosticWakeReason(String deliveryReason) {
        return "deadline".equals(deliveryReason) ? "WAKE_FINAL_DEADLINE"
                : deliveryReason == null ? "WAKE_TEMPORAL_MULTI_SENSOR_CONFIRMATION" : deliveryReason;
    }

    /**
     * A simultaneous system full-screen card can win the first delivery race.  A fresh alarm
     * notification after that card is dismissed is more reliable than another direct background
     * Activity launch, which Android 15 may reject.
     */
    static void repostFullScreen(Context context, int alarmId, long targetAt) {
        SmartAlarmStateStore state = new SmartAlarmStateStore(context, alarmId);
        if (!state.fired(targetAt) || state.dismissed(targetAt)) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        ensureAttentionChannel(manager);
        Intent activity = new Intent(context, SmartAlarmAlertActivity.class)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt)
                .putExtra("reason", "screen_guard_reannounce")
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
        Notification notification = new Notification.Builder(context, CHANNEL_ATTENTION)
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
                .addAction(SmartAlarmActions.dismissAction(context, alarmId, targetAt))
                .build();
        manager.cancel(NOTIFICATION_BASE + alarmId);
        manager.notify(NOTIFICATION_BASE + alarmId, notification);
        AppLog.w(context, "SmartAlarm full-screen notification reposted id=" + alarmId);
    }

    private static void ensureAttentionChannel(NotificationManager manager) {
        if (manager.getNotificationChannel(CHANNEL_ATTENTION) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ATTENTION, "Smart Alarm", NotificationManager.IMPORTANCE_HIGH);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(null, attributes);
        channel.setVibrationPattern(ATTENTION_VIBRATION);
        channel.enableVibration(true);
        channel.setBypassDnd(manager.isNotificationPolicyAccessGranted());
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
    }

}
