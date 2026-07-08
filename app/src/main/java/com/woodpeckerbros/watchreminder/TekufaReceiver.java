package com.woodpeckerbros.watchreminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TekufaReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "tekufa_alerts_no_system_vibration_v1";
    private static final int NOTIFICATION_ID = "tekufa_alert".hashCode();

    @Override
    public void onReceive(Context context, Intent intent) {
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.jewishMode() || !settings.tekufaRemindersEnabled()) {
            AppLog.d(context, "tekufa receiver skipped disabled");
            return;
        }
        TekufaScheduler.ScheduledEvent event = eventFromIntent(intent);
        if (event == null) {
            event = TekufaScheduler.nextEvent(System.currentTimeMillis() - 60_000L);
        }
        if (event != null) {
            showNotification(context, event);
        }
        TekufaScheduler.schedule(context);
    }

    private static TekufaScheduler.ScheduledEvent eventFromIntent(Intent intent) {
        if (intent == null || !intent.hasExtra(TekufaScheduler.EXTRA_WINDOW_START_AT)) {
            return null;
        }
        TekufaHelper.Event tekufa = new TekufaHelper.Event(
                intent.getIntExtra(TekufaScheduler.EXTRA_SEASON_INDEX, 0),
                intent.getLongExtra(TekufaScheduler.EXTRA_LOCAL_MEAN_AT, 0),
                intent.getLongExtra(TekufaScheduler.EXTRA_OFFICIAL_AT, 0),
                intent.getLongExtra(TekufaScheduler.EXTRA_WINDOW_START_AT, 0),
                intent.getLongExtra(TekufaScheduler.EXTRA_WINDOW_END_AT, 0)
        );
        return new TekufaScheduler.ScheduledEvent(
                intent.getStringExtra(TekufaScheduler.EXTRA_KIND),
                0,
                tekufa
        );
    }

    private static void showNotification(Context context, TekufaScheduler.ScheduledEvent event) {
        createChannel(context);
        String title = UiText.t(context, "תזכורת תקופה");
        TekufaHelper.Event tekufa = event.tekufa;
        String prefix = TekufaScheduler.KIND_START.equals(event.kind)
                ? UiText.t(context, "זמן התקופה מתחיל עכשיו")
                : UiText.t(context, "תזכורת מקדימה לזמן התקופה");
        String text = prefix
                + ". "
                + TekufaHelper.name(context, tekufa.seasonIndex)
                + " | "
                + UiText.t(context, "תאריך")
                + ": "
                + new SimpleDateFormat("dd/MM", Locale.US).format(new Date(tekufa.windowStartAt))
                + " | "
                + UiText.t(context, "זמן התקופה")
                + ": "
                + NextReminderCalculator.formatTime(tekufa.localMeanAt)
                + " / "
                + NextReminderCalculator.formatTime(tekufa.officialAt)
                + " | "
                + UiText.t(context, "חלון")
                + ": "
                + UiText.t(context, "יש להימנע משתיית מים גלויים מ־")
                + NextReminderCalculator.formatTime(tekufa.windowStartAt)
                + " "
                + UiText.t(context, "עד")
                + " "
                + NextReminderCalculator.formatTime(tekufa.windowEndAt);
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
                UiText.t(context, "תזכורת תקופה"),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.enableVibration(false);
        channel.setVibrationPattern(new long[]{0});
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }
}
