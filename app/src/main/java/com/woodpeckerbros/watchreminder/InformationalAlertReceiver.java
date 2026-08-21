package com.woodpeckerbros.watchreminder;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class InformationalAlertReceiver extends BroadcastReceiver {
    static final String EXTRA_KEY = "info_alert_key";
    static final String EXTRA_TITLE = "info_alert_title";
    static final String EXTRA_MESSAGE = "info_alert_message";
    private static final String CHANNEL_ID = "informational_alerts_full_screen_v1";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        show(context,
                intent.getStringExtra(EXTRA_KEY),
                intent.getStringExtra(EXTRA_TITLE),
                intent.getStringExtra(EXTRA_MESSAGE));
    }

    static void show(Context context, String key, String title, String message) {
        if (key == null || title == null || message == null) return;
        createChannel(context);
        Intent alertIntent = new Intent(context, InformationalAlertActivity.class)
                .putExtra(EXTRA_KEY, key)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MESSAGE, message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(
                context, notificationId(key), alertIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .setVibrate(new long[]{0})
                .setSound(null)
                .setDefaults(0)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(notificationId(key), notification);
        scheduleRetry(context, key, title, message, new ReminderSettings(context).autoSnoozeMinutes());
        AppLog.d(context, "informational alert shown key=" + key);
    }

    static void scheduleRetry(Context context, String key, String title, String message, int minutes) {
        long at = ReminderScheduler.ceilToMinute(System.currentTimeMillis() + Math.max(1, minutes) * 60_000L);
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) return;
        PendingIntent pending = retryIntent(context, key, title, message);
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
        } catch (SecurityException exception) {
            manager.set(AlarmManager.RTC_WAKEUP, at, pending);
        }
    }

    static void complete(Context context, String key) {
        AlarmManager alarm = context.getSystemService(AlarmManager.class);
        if (alarm != null) alarm.cancel(retryIntent(context, key, "", ""));
        dismissNotification(context, key);
    }

    static void dismissNotification(Context context, String key) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications != null) notifications.cancel(notificationId(key));
    }

    private static PendingIntent retryIntent(Context context, String key, String title, String message) {
        Intent intent = new Intent(context, InformationalAlertReceiver.class)
                .putExtra(EXTRA_KEY, key).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_MESSAGE, message);
        return PendingIntent.getBroadcast(context, ("info-retry:" + key).hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int notificationId(String key) {
        return ("info-notification:" + key).hashCode();
    }

    private static void createChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, UiText.t(context, "התראות מידע"), NotificationManager.IMPORTANCE_HIGH);
        channel.enableVibration(false);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }
}
