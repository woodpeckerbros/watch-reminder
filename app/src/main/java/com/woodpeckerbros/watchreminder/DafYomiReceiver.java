package com.woodpeckerbros.watchreminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.List;

public class DafYomiReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "daf_yomi_alerts_no_system_vibration_v1";
    private static final int NOTIFICATION_ID = "daf_yomi_alert".hashCode();

    @Override
    public void onReceive(Context context, Intent intent) {
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.dafYomiEnabled()) {
            AppLog.d(context, "daf yomi receiver skipped disabled");
            return;
        }
        boolean retry = intent != null && intent.getBooleanExtra(DafYomiScheduler.EXTRA_RETRY, false);
        DafYomiStore store = new DafYomiStore(context);
        if (retry) {
            store.clearRetryUntil();
        }
        List<DafYomiHelper.Item> due = store.dueItems(context);
        AppLog.d(context, "daf yomi receiver due=" + due.size() + " retry=" + retry);
        if (due.isEmpty()) {
            DafYomiScheduler.schedule(context);
            return;
        }
        showNotification(context);
        DafYomiScheduler.schedule(context);
    }

    static void showNotification(Context context) {
        createChannel(context);
        AppLog.d(context, "daf yomi showNotification");
        Intent alertIntent = new Intent(context, DafYomiAlertActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                alertIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(UiText.t(context, "דף היומי"))
                .setContentText(UiText.t(context, "בדיקת לימוד דף היומי"))
                .setStyle(new Notification.BigTextStyle().bigText(UiText.t(context, "בדיקת לימוד דף היומי")))
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
                UiText.t(context, "דף היומי"),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.enableVibration(false);
        channel.setVibrationPattern(new long[]{0});
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }
}
