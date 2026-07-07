package com.woodpeckerbros.watchreminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class JewishDayReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "jewish_day_alerts_no_system_vibration_v1";
    private static final int NOTIFICATION_ID = "jewish_day_alert".hashCode();

    @Override
    public void onReceive(Context context, Intent intent) {
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.jewishMode() || !settings.jewishDayRemindersEnabled()) {
            AppLog.d(context, "jewish day receiver skipped disabled");
            return;
        }
        String kind = intent == null ? "" : intent.getStringExtra(JewishDayScheduler.EXTRA_KIND);
        String label = intent == null ? "" : intent.getStringExtra(JewishDayScheduler.EXTRA_LABEL);
        if (label == null || label.trim().isEmpty()) {
            JewishDayScheduler.Event event = JewishDayScheduler.nextEvent(context, System.currentTimeMillis() - 60_000L);
            if (event != null) {
                kind = event.kind;
                label = event.label;
            }
        }
        if (label != null && !label.trim().isEmpty()) {
            showNotification(context, kind, label);
        }
        JewishDayScheduler.schedule(context);
    }

    private static void showNotification(Context context, String kind, String label) {
        createChannel(context);
        String title = UiText.t(context, "ימים יהודיים");
        String text = JewishDayScheduler.KIND_TODAY_EREV.equals(kind)
                ? UiText.t(context, "היום") + " " + label
                : UiText.t(context, "מחר") + " " + label;
        Intent openIntent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0})
                .setSound(null)
                .setDefaults(0)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    static void cancelNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

    private static void createChannel(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                UiText.t(context, "ימים יהודיים"),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.enableVibration(false);
        channel.setVibrationPattern(new long[]{0});
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }
}
