package com.woodpeckerbros.watchreminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class JewishDayReceiver extends BroadcastReceiver {
    private static final int NOTIFICATION_ID = "jewish_day_alert".hashCode();

    @Override
    public void onReceive(Context context, Intent intent) {
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.jewishMode() || !settings.jewishDayRemindersEnabled()) {
            AppLog.d(context, "jewish day receiver skipped disabled");
            return;
        }
        String kind = intent == null ? "" : intent.getStringExtra(JewishDayScheduler.EXTRA_KIND);
        String label = intent == null ? "" : intent.getStringExtra(JewishDayScheduler.EXTRA_LABEL);
        if (label == null || label.trim().isEmpty()) {
            JewishDayScheduler.Event event = JewishDayScheduler.nextEvent(context, System.currentTimeMillis() - 60_000L);
            if (event != null) {
                kind = event.kind;
                label = event.label;
            }
        }
        if (label != null && !label.trim().isEmpty()) {
            showNotification(context, kind, label);
        }
        JewishDayScheduler.schedule(context);
    }

    private static void showNotification(Context context, String kind, String label) {
        String title = UiText.t(context, "ימים יהודיים");
        String text = JewishDayScheduler.KIND_TODAY_EREV.equals(kind)
                ? UiText.t(context, "היום") + " " + label
                : UiText.t(context, "מחר") + " " + label;
        InformationalAlertReceiver.show(context, "jewish-day", title, text);
    }

    static void cancelNotification(Context context) {
        InformationalAlertReceiver.complete(context, "jewish-day");
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

}
