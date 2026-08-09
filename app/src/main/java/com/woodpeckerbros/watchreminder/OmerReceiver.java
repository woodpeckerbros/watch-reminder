package com.woodpeckerbros.watchreminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class OmerReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "omer_alerts_no_system_vibration_v1";
    private static final int NOTIFICATION_ID = "omer_alert".hashCode();

    @Override
    public void onReceive(Context context, Intent intent) {
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.omerEnabled()) {
            AppLog.d(context, "omer receiver skipped disabled");
            return;
        }
        boolean retry = intent != null && intent.getBooleanExtra(OmerScheduler.EXTRA_RETRY, false);
        long triggerAt = intent == null ? 0 : intent.getLongExtra(OmerScheduler.EXTRA_TRIGGER_AT, 0);
        OmerStore store = new OmerStore(context);
        if (retry) {
            store.clearRetryUntil();
        }
        OmerHelper.Item item = triggerAt > 0
                ? OmerHelper.itemForTrigger(context, triggerAt)
                : OmerHelper.dueNow(context, settings.omerOffsetMinutes());
        AppLog.d(context, "omer receiver retry=" + retry + " item=" + (item == null ? "none" : item.day));
        if (item == null || store.isHandled(item.key)) {
            OmerScheduler.schedule(context);
            return;
        }
        showNotification(context, triggerAt > 0 ? triggerAt : item.triggerAt, item);
        OmerScheduler.schedule(context);
    }

    static void showNotification(Context context, long triggerAt, OmerHelper.Item item) {
        createChannel(context);
        AppLog.d(context, "omer showNotification day=" + (item == null ? "none" : item.day));
        Intent alertIntent = new Intent(context, OmerAlertActivity.class)
                .putExtra(OmerScheduler.EXTRA_TRIGGER_AT, triggerAt)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                alertIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String text = item == null ? UiText.t(context, "תזכורת לספירת העומר") : item.label;
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(UiText.t(context, "ספירת העומר"))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
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
                UiText.t(context, "ספירת העומר"),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.enableVibration(false);
        channel.setVibrationPattern(new long[]{0});
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }
}
