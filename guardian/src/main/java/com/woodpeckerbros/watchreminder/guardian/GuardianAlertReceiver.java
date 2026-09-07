package com.woodpeckerbros.watchreminder.guardian;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class GuardianAlertReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "zmanio_guardian_alerts_v1";

    @Override public void onReceive(Context context, Intent source) {
        String key = source.getStringExtra(GuardianContract.EXTRA_KEY);
        String name = source.getStringExtra(GuardianContract.EXTRA_REMINDER_NAME);
        if (key == null || key.isEmpty()) return;
        if (name == null || name.trim().isEmpty()) name = "תזכורת";
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.guardian_alert_title), NotificationManager.IMPORTANCE_HIGH);
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);

        Intent activity = new Intent(context, GuardianAlertActivity.class)
                .putExtra(GuardianContract.EXTRA_KEY, key)
                .putExtra(GuardianContract.EXTRA_REMINDER_NAME, name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreen = PendingIntent.getActivity(context, key.hashCode(), activity,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_guardian)
                .setContentTitle(context.getString(R.string.guardian_alert_title))
                .setContentText(name)
                .setStyle(new Notification.BigTextStyle().bigText(name + "\n"
                        + context.getString(R.string.guardian_no_confirmation)))
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(fullScreen)
                .setFullScreenIntent(fullScreen, true)
                .build();
        manager.notify(key.hashCode(), notification);
        android.util.Log.e("ZmanioGuardian", "guardian popup shown key=" + key);
    }
}
