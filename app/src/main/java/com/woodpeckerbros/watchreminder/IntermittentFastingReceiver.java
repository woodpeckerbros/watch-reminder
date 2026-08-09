package com.woodpeckerbros.watchreminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class IntermittentFastingReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "intermittent_fasting_alerts_no_system_vibration_v2";
    private static final int NOTIFICATION_ID = "intermittent_fasting".hashCode();

    @Override
    public void onReceive(Context context, Intent intent) {
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.intermittentFastingEnabled()) {
            AppLog.d(context, "fasting receiver skipped disabled");
            return;
        }
        String eventType = intent == null ? "" : intent.getStringExtra(IntermittentFastingScheduler.EXTRA_EVENT_TYPE);
        long triggerAt = intent == null ? 0L : intent.getLongExtra(IntermittentFastingScheduler.EXTRA_TRIGGER_AT, 0L);
        boolean retry = intent != null && intent.getBooleanExtra(IntermittentFastingScheduler.EXTRA_RETRY, false);
        if (triggerAt <= 0L) {
            triggerAt = ReminderScheduler.floorToMinute(System.currentTimeMillis());
        }
        IntermittentFastingStore store = new IntermittentFastingStore(context);
        if (store.isAlertAcknowledged(eventType, triggerAt)) {
            AppLog.d(context, "fasting receiver skipped acknowledged type=" + eventType + " trigger=" + NextReminderCalculator.formatDateTime(triggerAt));
            return;
        }
        IntermittentFastingStore.Window window = store.window(System.currentTimeMillis());
        AppLog.d(context, "fasting receiver type=" + eventType + " retry=" + retry + " trigger=" + NextReminderCalculator.formatDateTime(triggerAt));
        if (IntermittentFastingScheduler.EVENT_START.equals(eventType)) {
            if (!retry) {
                store.startEatingAt(triggerAt);
            }
            String message = AppLanguage.isEnglish(context)
                    ? "Your eating window is open now. It will last " + settings.fastingEatingHours() + " hours."
                    : "חלון האכילה שלך נפתח עכשיו. הוא יימשך " + settings.fastingEatingHours() + " שעות.";
            showNotification(context, eventType, triggerAt, UiText.t(context, "אפשר להתחיל לאכול"), message);
        } else if (IntermittentFastingScheduler.EVENT_END_WARNING.equals(eventType)) {
            if (!retry && !window.eatingOpen(System.currentTimeMillis())) {
                IntermittentFastingScheduler.schedule(context);
                return;
            }
            showNotification(context, eventType, triggerAt, UiText.t(context, "עוד חצי שעה לסיום"), UiText.t(context, "חלון האכילה ייסגר בעוד חצי שעה."));
        } else if (IntermittentFastingScheduler.EVENT_END.equals(eventType)) {
            window = store.window(System.currentTimeMillis());
            if (!retry && window.finished) {
                IntermittentFastingScheduler.schedule(context);
                return;
            }
            showNotification(context, eventType, triggerAt, UiText.t(context, "חלון האכילה נסגר"), UiText.t(context, "נגמר חלון זמן האכילה להיום."));
        } else {
            AppLog.w(context, "fasting receiver skipped unknown type=" + eventType);
            IntermittentFastingScheduler.schedule(context);
            return;
        }
        ComplicationRefresh.request(context);
        IntermittentFastingScheduler.scheduleRetry(context, eventType, triggerAt);
        IntermittentFastingScheduler.schedule(context);
    }

    static void cancelNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

    private static void showNotification(Context context, String eventType, long triggerAt, String title, String message) {
        createChannel(context);
        Intent openIntent = new Intent(context, IntermittentFastingAlertActivity.class)
                .putExtra(IntermittentFastingScheduler.EXTRA_EVENT_TYPE, eventType)
                .putExtra(IntermittentFastingScheduler.EXTRA_TRIGGER_AT, triggerAt)
                .putExtra("fasting_alert_title", title)
                .putExtra("fasting_alert_message", message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (eventType + ":" + triggerAt).hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
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

    private static void createChannel(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                UiText.t(context, "צום לסירוגין"),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.enableVibration(false);
        channel.setVibrationPattern(new long[]{0});
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }
}
