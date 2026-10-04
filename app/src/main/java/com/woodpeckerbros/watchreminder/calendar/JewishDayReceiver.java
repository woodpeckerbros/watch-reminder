package com.woodpeckerbros.watchreminder.calendar;

import com.woodpeckerbros.watchreminder.reminder.*;

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

public class JewishDayReceiver extends BroadcastReceiver {
    private static final int NOTIFICATION_ID = "jewish_day_alert".hashCode();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!EntitlementAccess.isFeatureAccessGranted(context)) { EntitlementEnforcer.disableDeliveries(context); return; }
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.jewishMode() || !settings.jewishDayRemindersEnabled()) {
            AppLog.d(context, "jewish day receiver skipped disabled");
            return;
        }
        String kind = intent == null ? "" : intent.getStringExtra(JewishDayScheduler.EXTRA_KIND);
        String label = intent == null ? "" : intent.getStringExtra(JewishDayScheduler.EXTRA_LABEL);
        long triggerAt = intent == null ? 0L
                : intent.getLongExtra(JewishDayScheduler.EXTRA_TRIGGER_AT, 0L);
        long expiresAt = intent == null ? 0L
                : intent.getLongExtra(JewishDayScheduler.EXTRA_EXPIRES_AT, 0L);
        long eventDay = intent == null ? 0L
                : intent.getLongExtra(JewishDayScheduler.EXTRA_EVENT_DAY, 0L);
        // Builds before the midnight expiry extra used `today_erev`. Keep those pending
        // alarms useful through their civil day, but never let one cross into the next day.
        if (expiresAt <= 0L && JewishDayScheduler.KIND_TODAY_EREV.equals(kind) && eventDay > 0L) {
            java.util.Calendar midnight = java.util.Calendar.getInstance(
                    java.util.TimeZone.getTimeZone(new com.woodpeckerbros.watchreminder.zmanim.ZmanimSettings(context).timeZoneId()));
            midnight.setTimeInMillis(eventDay);
            midnight.set(java.util.Calendar.HOUR_OF_DAY, 0);
            midnight.set(java.util.Calendar.MINUTE, 0);
            midnight.set(java.util.Calendar.SECOND, 0);
            midnight.set(java.util.Calendar.MILLISECOND, 0);
            midnight.add(java.util.Calendar.DAY_OF_YEAR, 1);
            expiresAt = midnight.getTimeInMillis();
        }
        if (JewishDayScheduler.isExpiredDelivery(kind, triggerAt, expiresAt, System.currentTimeMillis())) {
            AppLog.d(context, "jewish day receiver skipped stale kind=" + kind
                    + " trigger=" + NextReminderCalculator.formatDateTime(triggerAt));
            JewishDayScheduler.schedule(context);
            return;
        }
        if (eventDay > 0L) {
            String localized = JewishDayScheduler.localizedLabelForDay(context, eventDay);
            if (!localized.isEmpty()) label = localized;
        }
        if (label == null || label.trim().isEmpty()) {
            JewishDayScheduler.Event event = JewishDayScheduler.nextEvent(context, System.currentTimeMillis() - 60_000L);
            if (event != null) {
                kind = event.kind;
                label = event.label;
            }
        }
        if (label != null && !label.trim().isEmpty()) {
            showNotification(context, kind, label);
            if (eventDay > 0L) {
                JewishDayScheduler.markDelivered(context, eventDay, kind, label);
            }
        }
        JewishDayScheduler.schedule(context);
    }

    static void showNotification(Context context, String kind, String label) {
        String title = UiText.t(context, "ימים יהודיים");
        String text = (JewishDayScheduler.KIND_TODAY_EREV.equals(kind)
                || JewishDayScheduler.KIND_TODAY.equals(kind))
                ? UiText.t(context, "היום") + " " + label
                : UiText.t(context, "מחר") + " " + label;
        InformationalAlertReceiver.show(context, "jewish-day", title, text);
    }

    public static void cancelNotification(Context context) {
        InformationalAlertReceiver.complete(context, "jewish-day");
        PrayerSeasonReceiver.cancelNotification(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

}
