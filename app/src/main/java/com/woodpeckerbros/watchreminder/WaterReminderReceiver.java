package com.woodpeckerbros.watchreminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class WaterReminderReceiver extends BroadcastReceiver {
    static final String EXTRA_AMOUNT_ML = "water_amount_ml";
    static final String EXTRA_CONSUMED_ML = "water_consumed_ml";
    static final String EXTRA_TARGET_ML = "water_target_ml";
    private static final String CHANNEL_ID = "water_reminders_no_system_vibration_v1";
    private static final int NOTIFICATION_ID = "water_reminder".hashCode();

    @Override
    public void onReceive(Context context, Intent intent) {
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.waterRemindersEnabled()) {
            return;
        }
        long triggerAt = intent == null ? 0L : intent.getLongExtra(WaterReminderScheduler.EXTRA_TRIGGER_AT, 0L);
        if (triggerAt <= 0L) {
            triggerAt = ReminderScheduler.floorToMinute(System.currentTimeMillis());
        }
        WaterReminderStore store = new WaterReminderStore(context);
        if (store.isHandled(triggerAt)) {
            WaterReminderScheduler.schedule(context);
            return;
        }
        long now = System.currentTimeMillis();
        long quietAdjusted = QuietTimeHelper.adjust(context, Math.max(triggerAt, now));
        if (quietAdjusted > now) {
            WaterReminderScheduler.scheduleAt(context, quietAdjusted);
            AppLog.d(context, "water deferred by quiet time until="
                    + NextReminderCalculator.formatDateTime(quietAdjusted));
            return;
        }
        int amountMl = WaterReminderScheduler.plannedAmountMl(context, triggerAt);
        if (amountMl <= 0) {
            WaterReminderScheduler.schedule(context);
            return;
        }
        int consumedMl = store.consumedTodayMl();
        ComplicationRefresh.requestWater(context);
        showNotification(context, triggerAt, amountMl, consumedMl, settings.waterDailyTargetMl());
        WaterReminderScheduler.schedule(context);
    }

    static void cancelNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

    private static void showNotification(Context context, long triggerAt, int amountMl, int consumedMl, int targetMl) {
        Context localized = AppLanguage.wrap(context);
        createChannel(context, localized);
        String title = localized.getString(R.string.water_alert_title);
        String message = localized.getString(R.string.water_alert_amount, amountMl);
        Intent open = new Intent(context, WaterReminderAlertActivity.class)
                .putExtra(WaterReminderScheduler.EXTRA_TRIGGER_AT, triggerAt)
                .putExtra(EXTRA_AMOUNT_ML, amountMl)
                .putExtra(EXTRA_CONSUMED_ML, consumedMl)
                .putExtra(EXTRA_TARGET_ML, targetMl)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(context, (int) (triggerAt ^ (triggerAt >>> 32)), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_water_drop_notification)
                .setColor(0xFF4EC9E8)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(pending)
                .setFullScreenIntent(pending, true)
                .setVibrate(new long[]{0})
                .setSound(null)
                .setDefaults(0)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private static void createChannel(Context context, Context localized) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                localized.getString(R.string.water_reminders_title), NotificationManager.IMPORTANCE_HIGH);
        channel.enableVibration(false);
        channel.setVibrationPattern(new long[]{0});
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }
}
