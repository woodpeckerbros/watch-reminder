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
        InformationalAlertReceiver.show(context, "tekufa", title, text);
    }

    static void cancelNotification(Context context) {
        InformationalAlertReceiver.complete(context, "tekufa");
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

}
