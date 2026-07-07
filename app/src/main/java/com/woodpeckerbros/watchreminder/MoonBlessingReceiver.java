package com.woodpeckerbros.watchreminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.TimeZone;

public class MoonBlessingReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "moon_blessing_alerts_no_system_vibration_v1";
    private static final int NOTIFICATION_ID = "moon_blessing_alert".hashCode();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!new ReminderSettings(context).moonBlessingEnabled()) {
            AppLog.d(context, "moon blessing receiver skipped disabled");
            return;
        }
        String action = intent == null ? "" : intent.getAction();
        String monthKey = intent == null ? "" : intent.getStringExtra(MoonBlessingScheduler.EXTRA_MONTH_KEY);
        if (MoonBlessingScheduler.ACTION_YES.equals(action)) {
            new MoonBlessingStore(context).markHandled(monthKey);
            cancelNotification(context);
            AppLog.d(context, "moon blessing marked handled month=" + monthKey);
            MoonBlessingScheduler.schedule(context);
            return;
        }
        if (MoonBlessingScheduler.ACTION_NO.equals(action)) {
            cancelNotification(context);
            AppLog.d(context, "moon blessing marked not yet month=" + monthKey);
            MoonBlessingScheduler.schedule(context);
            return;
        }

        MoonBlessingScheduler.Event event = MoonBlessingScheduler.nextEvent(context, System.currentTimeMillis() - 60_000L);
        if (event == null || new MoonBlessingStore(context).isHandled(event.monthKey)) {
            MoonBlessingScheduler.schedule(context);
            return;
        }
        String kind = intent == null ? event.kind : intent.getStringExtra(MoonBlessingScheduler.EXTRA_KIND);
        if (kind == null || kind.trim().isEmpty()) {
            kind = event.kind;
        }
        showNotification(context, kind, event.monthKey, event.window);
        MoonBlessingScheduler.schedule(context);
    }

    private static void showNotification(Context context, String kind, String monthKey, MoonBlessingHelper.Window window) {
        createChannel(context);
        String title = UiText.t(context, "ברכת הלבנה");
        String text;
        if (MoonBlessingScheduler.KIND_PRE_START.equals(kind)) {
            text = UiText.t(context, "הלילה יהיה אפשר להתחיל לברך ברכת הלבנה משעה") + " " + NextReminderCalculator.formatTime(window.startAt);
        } else if (MoonBlessingScheduler.KIND_LAST_NIGHT.equals(kind)) {
            text = lastNightText(context, window);
        } else {
            text = UiText.t(context, "האם ברכת ברכת הלבנה?");
        }

        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setVibrate(new long[]{0})
                .setSound(null)
                .setDefaults(0)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true);

        if (!MoonBlessingScheduler.KIND_PRE_START.equals(kind)) {
            builder.addAction(
                    R.drawable.ic_notification,
                    UiText.t(context, "כן"),
                    MoonBlessingScheduler.actionIntent(context, MoonBlessingScheduler.ACTION_YES, monthKey)
            );
            builder.addAction(
                    R.drawable.ic_notification,
                    UiText.t(context, "לא"),
                    MoonBlessingScheduler.actionIntent(context, MoonBlessingScheduler.ACTION_NO, monthKey)
            );
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private static String lastNightText(Context context, MoonBlessingHelper.Window window) {
        ZmanimSettings settings = new ZmanimSettings(context);
        TimeZone timeZone = TimeZone.getTimeZone(settings.timeZoneId());
        Calendar end = Calendar.getInstance(timeZone);
        end.setTimeInMillis(window.endAt);
        Calendar night = Calendar.getInstance(timeZone);
        night.setTimeInMillis(window.endAt);
        if (end.get(Calendar.HOUR_OF_DAY) >= 10) {
            night.add(Calendar.DAY_OF_YEAR, -1);
        }
        long alos = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_ALOS, night.getTimeInMillis() + 24 * 60 * 60_000L);
        boolean allNight = alos != Long.MAX_VALUE && Math.abs(window.endAt - alos) < 60_000L;
        if (allNight) {
            return UiText.t(context, "הלילה זה הלילה האחרון לברך ברכת הלבנה, ואפשר לברך כל הלילה.");
        }
        return UiText.t(context, "הלילה זה הלילה האחרון לברך ברכת הלבנה. סוף הזמן בשעה") + " " + NextReminderCalculator.formatTime(window.endAt);
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
                UiText.t(context, "ברכת הלבנה"),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.enableVibration(false);
        channel.setVibrationPattern(new long[]{0});
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }
}
