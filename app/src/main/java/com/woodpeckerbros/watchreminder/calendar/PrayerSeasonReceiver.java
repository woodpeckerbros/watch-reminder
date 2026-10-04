package com.woodpeckerbros.watchreminder.calendar;

import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.entitlement.EntitlementAccess;
import com.woodpeckerbros.watchreminder.reminder.InformationalAlertReceiver;
import com.woodpeckerbros.watchreminder.reminder.ReminderSettings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public final class PrayerSeasonReceiver extends BroadcastReceiver {
    private static final String ALERT_KEY = "jewish-day-prayer-season";
    private static final String DELIVERY_PREFS = "prayer_season_delivery";
    private static final String KEY_LAST_DELIVERED = "last_delivered";

    @Override public void onReceive(Context context, Intent intent) {
        if (!EntitlementAccess.isFeatureAccessGranted(context)) {
            JewishDayScheduler.cancel(context);
            cancelNotification(context);
            return;
        }
        ReminderSettings settings = new ReminderSettings(context);
        if (!settings.jewishMode() || !settings.jewishDayRemindersEnabled()) {
            JewishDayScheduler.cancel(context);
            cancelNotification(context);
            return;
        }
        long now = System.currentTimeMillis();
        long triggerAt = intent == null ? 0L : intent.getLongExtra(PrayerSeasonScheduler.EXTRA_TRIGGER_AT, 0L);
        long expiresAt = intent == null ? 0L : intent.getLongExtra(PrayerSeasonScheduler.EXTRA_EXPIRES_AT, 0L);
        String[] kinds = intent == null ? null : intent.getStringArrayExtra(PrayerSeasonScheduler.EXTRA_KINDS);
        long[] days = intent == null ? null : intent.getLongArrayExtra(PrayerSeasonScheduler.EXTRA_START_DAYS);
        if (triggerAt > 0L && now >= triggerAt && now < expiresAt && kinds != null
                && days != null && kinds.length == days.length) {
            List<PrayerSeasonScheduler.Item> items = new ArrayList<>();
            for (int i = 0; i < kinds.length; i++) {
                try {
                    PrayerSeasonChanges.Kind kind = PrayerSeasonChanges.Kind.valueOf(kinds[i]);
                    items.add(new PrayerSeasonScheduler.Item(kind, days[i]));
                } catch (IllegalArgumentException ignored) {
                    AppLog.w(context, "prayer season receiver skipped unknown kind=" + kinds[i]);
                }
            }
            deliver(context, new PrayerSeasonScheduler.Event(triggerAt, expiresAt, items));
        } else {
            AppLog.d(context, "prayer season stale or malformed trigger=" + triggerAt);
        }
        JewishDayScheduler.schedule(context);
    }

    public static void cancelNotification(Context context) {
        InformationalAlertReceiver.complete(context, ALERT_KEY);
    }

    static boolean deliver(Context context, PrayerSeasonScheduler.Event event) {
        if (event.items.isEmpty()) return false;
        StringBuilder identity = new StringBuilder(Long.toString(event.triggerAt));
        List<String> lines = new ArrayList<>();
        for (PrayerSeasonScheduler.Item item : event.items) {
            identity.append(':').append(item.kind).append(':').append(item.startDay);
            lines.add(PrayerSeasonScheduler.message(context, item));
        }
        SharedPreferences prefs = context.getSharedPreferences(DELIVERY_PREFS, Context.MODE_PRIVATE);
        if (identity.toString().equals(prefs.getString(KEY_LAST_DELIVERED, ""))) return false;
        InformationalAlertReceiver.show(context, ALERT_KEY,
                PrayerSeasonScheduler.title(context), String.join("\n", lines));
        prefs.edit().putString(KEY_LAST_DELIVERED, identity.toString()).apply();
        AppLog.d(context, "prayer season delivered trigger=" + event.triggerAt
                + " changes=" + lines.size());
        return true;
    }
}
