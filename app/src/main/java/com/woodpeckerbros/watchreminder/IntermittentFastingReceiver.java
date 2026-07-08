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
    private static final String CHANNEL_ID = "intermittent_fasting_alerts_v1";
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
        IntermittentFastingStore store = new IntermittentFastingStore(context);
        IntermittentFastingStore.Window window = store.window(System.currentTimeMillis());
        AppLog.d(context, "fasting receiver type=" + eventType + " trigger=" + NextReminderCalculator.formatDateTime(triggerAt));
        if (IntermittentFastingScheduler.EVENT_START.equals(eventType)) {
            store.startEatingAt(triggerAt > 0L ? triggerAt : System.currentTimeMillis());
            showNotification(context, "אפשר להתחיל לאכול", "חלון האכילה שלך נפתח עכשיו. הוא יימשך " + settings.fastingEatingHours() + " שעות.");
        } else if (IntermittentFastingScheduler.EVENT_END_WARNING.equals(eventType)) {
            if (!window.eatingOpen(System.currentTimeMillis())) {
                IntermittentFastingScheduler.schedule(context);
                return;
            }
            showNotification(context, "עוד חצי שעה חלון האכילה נסגר", "כדאי לסיים את האכילה בזמן. אפשר לסמן באפליקציה שסיימת לאכול עכשיו.");
        } else if (IntermittentFastingScheduler.EVENT_END.equals(eventType)) {
            window = store.window(System.currentTimeMillis());
            if (window.finished) {
                IntermittentFastingScheduler.schedule(context);
                return;
            }
            showNotification(context, "חלון האכילה נסגר", "נגמר חלון זמן האכילה להיום. הצום הבא מתחיל עכשיו.");
        }
        IntermittentFastingScheduler.schedule(context);
    }

    static void cancelNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

    private static void showNotification(Context context, String title, String message) {
        createChannel(context);
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
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
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
                "צום לסירוגין",
                NotificationManager.IMPORTANCE_HIGH
        );
        manager.createNotificationChannel(channel);
    }
}
