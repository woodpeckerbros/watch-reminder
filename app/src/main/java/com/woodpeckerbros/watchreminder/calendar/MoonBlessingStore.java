package com.woodpeckerbros.watchreminder.calendar;

import com.woodpeckerbros.watchreminder.reminder.*;

import com.woodpeckerbros.watchreminder.*;

import android.content.Context;
import android.content.SharedPreferences;

public class MoonBlessingStore {
    private static final String PREFS_NAME = "moon_blessing_state";
    private static final String KEY_HANDLED_MONTH = "handled_month";
    private static final String KEY_LAST_ALERT = "last_alert";

    private final SharedPreferences prefs;

    public MoonBlessingStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isHandled(String monthKey) {
        return monthKey != null && monthKey.equals(prefs.getString(KEY_HANDLED_MONTH, ""));
    }

    public void markHandled(String monthKey) {
        prefs.edit().putString(KEY_HANDLED_MONTH, monthKey == null ? "" : monthKey).apply();
    }

    public boolean wasAlertShown(String monthKey, String kind, long triggerAt) {
        return alertKey(monthKey, kind, triggerAt).equals(prefs.getString(KEY_LAST_ALERT, ""));
    }

    public void markAlertShown(String monthKey, String kind, long triggerAt) {
        prefs.edit().putString(KEY_LAST_ALERT, alertKey(monthKey, kind, triggerAt)).apply();
    }

    private static String alertKey(String monthKey, String kind, long triggerAt) {
        return (monthKey == null ? "" : monthKey) + ":" + (kind == null ? "" : kind)
                + ":" + ReminderScheduler.floorToMinute(triggerAt);
    }
}
