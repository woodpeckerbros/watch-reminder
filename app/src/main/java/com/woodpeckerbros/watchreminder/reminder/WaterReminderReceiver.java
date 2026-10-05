package com.woodpeckerbros.watchreminder.reminder;

import com.woodpeckerbros.watchreminder.*;
import com.woodpeckerbros.watchreminder.entitlement.EntitlementAccess;
import com.woodpeckerbros.watchreminder.entitlement.EntitlementEnforcer;

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
    private static final String CHANNEL_ID = "water_reminders_attention_v2";
    private static final int NOTIFICATION_ID = "water_reminder".hashCode();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!EntitlementAccess.isFeatureAccessGranted(context)) {
            EntitlementEnforcer.disableDeliveries(context);
            return;
        }
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.waterRemindersEnabled()) {
            WaterReminderScheduler.cancel(context);
            return;
        }
        if (intent != null && WaterReminderScheduler.ACTION_AUTO_SNOOZE.equals(intent.getAction())) {
            autoSnoozeIfPending(context, intent.getLongExtra(WaterReminderScheduler.EXTRA_TRIGGER_AT, 0L));
            return;
        }
        long triggerAt = intent == null ? 0L : intent.getLongExtra(WaterReminderScheduler.EXTRA_TRIGGER_AT, 0L);
        if (triggerAt <= 0L) {
            triggerAt = ReminderScheduler.floorToMinute(System.currentTimeMillis());
        }
        WaterReminderStore store = new WaterReminderStore(context);
        if (store.isHandled(triggerAt)) {
            store.clearPendingSnoozeAt(triggerAt);
            WaterReminderScheduler.schedule(context);
            return;
        }
        long pendingSnoozeAt = store.pendingSnoozeAt();
        if (pendingSnoozeAt > 0L && pendingSnoozeAt != triggerAt
                && WaterReminderScheduler.shouldRecoverSnooze(pendingSnoozeAt, System.currentTimeMillis())) {
            WaterReminderScheduler.schedule(context);
            return;
        }
        long now = System.currentTimeMillis();
        // A snooze was already placed at its chosen time. Reapplying quiet-time here
        // would silently move a manual 15-minute snooze after the user selected it.
        long quietAdjusted = pendingSnoozeAt == triggerAt
                ? Math.max(triggerAt, now)
                : QuietTimeHelper.adjust(context, Math.max(triggerAt, now));
        if (quietAdjusted > now) {
            if (pendingSnoozeAt == triggerAt) store.setPendingSnoozeAt(quietAdjusted);
            WaterReminderScheduler.scheduleAt(context, quietAdjusted);
            AppLog.d(context, "water deferred by quiet time until="
                    + NextReminderCalculator.formatDateTime(quietAdjusted));
            return;
        }
        store.clearPendingSnoozeAt(triggerAt);
        store.clearNextFixedAt(triggerAt);
        int amountMl = WaterReminderScheduler.plannedAmountMl(context, triggerAt);
        if (amountMl <= 0) {
            WaterReminderScheduler.schedule(context);
            return;
        }
        int consumedMl = store.consumedTodayMl();
        WaterReminderScheduler.schedule(context);
        ComplicationRefresh.requestWater(context);
        WaterReminderScheduler.scheduleAutoSnooze(context, triggerAt);
        showNotification(context, triggerAt, amountMl, consumedMl, settings.waterDailyTargetMl());
    }

    static void autoSnoozeIfPending(Context context, long triggerAt) {
        WaterReminderStore store = new WaterReminderStore(context);
        if (!store.consumePendingAutoTrigger(triggerAt)) return;
        WaterReminderScheduler.cancelAutoSnooze(context);
        cancelNotification(context);
        boolean fixedAmount = ReminderSettings.WATER_MODE_FIXED_AMOUNT.equals(
                new ReminderSettings(context).waterMode());
        if (fixedAmount) {
            // The next regular half-hour alert is already planned; do not replace it
            // with a retry measured from the later auto-close time.
            WaterReminderScheduler.schedule(context);
        } else {
            WaterReminderScheduler.scheduleSnooze(context,
                    WaterReminderScheduler.AUTO_RETRY_MINUTES, true);
        }
        ComplicationRefresh.requestWater(context);
        AppLog.d(context, "water auto-snooze original=" + triggerAt
                + (fixedAmount ? " next_regular_half_hour" :
                " retry_minutes=" + WaterReminderScheduler.AUTO_RETRY_MINUTES));
    }

    public static void cancelNotification(Context context) {
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
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(pending)
                .setFullScreenIntent(pending, true)
                .setVibrate(AlertAttention.VIBRATION)
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
        AlertAttention.configure(channel);
        manager.createNotificationChannel(channel);
    }
}
