package com.woodpeckerbros.watchreminder.reminder;

import com.woodpeckerbros.watchreminder.*;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Locale;

public final class WaterReminderStore {
    static final String PREFS_NAME = "water_reminder_state";
    private static final String KEY_DAY = "day";
    private static final String KEY_CONSUMED_ML = "consumed_ml";
    private static final String KEY_LAST_HANDLED_TRIGGER = "last_handled_trigger";
    private static final String KEY_PENDING_AUTO_TRIGGER = "pending_auto_trigger";
    private static final Object AUTO_TRIGGER_LOCK = new Object();

    private final SharedPreferences prefs;

    public WaterReminderStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int consumedTodayMl() {
        ensureToday();
        return Math.max(0, prefs.getInt(KEY_CONSUMED_ML, 0));
    }

    public boolean isHandled(long triggerAt) {
        ensureToday();
        return triggerAt > 0L && triggerAt <= prefs.getLong(KEY_LAST_HANDLED_TRIGGER, 0L);
    }

    public void markHandled(long triggerAt, int consumedMl) {
        ensureToday();
        int consumed = Math.max(0, prefs.getInt(KEY_CONSUMED_ML, 0));
        consumed += Math.max(0, consumedMl);
        prefs.edit()
                .putInt(KEY_CONSUMED_ML, consumed)
                .putLong(KEY_LAST_HANDLED_TRIGGER, triggerAt)
                .apply();
        clearPendingAutoTrigger(triggerAt);
    }

    public void setPendingAutoTrigger(long triggerAt) {
        ensureToday();
        synchronized (AUTO_TRIGGER_LOCK) {
            prefs.edit().putLong(KEY_PENDING_AUTO_TRIGGER, triggerAt).commit();
        }
    }

    public boolean consumePendingAutoTrigger(long triggerAt) {
        ensureToday();
        synchronized (AUTO_TRIGGER_LOCK) {
            if (triggerAt <= 0L || prefs.getLong(KEY_PENDING_AUTO_TRIGGER, 0L) != triggerAt
                    || isHandled(triggerAt)) return false;
            return prefs.edit().remove(KEY_PENDING_AUTO_TRIGGER).commit();
        }
    }

    public void clearPendingAutoTrigger(long triggerAt) {
        synchronized (AUTO_TRIGGER_LOCK) {
            if (prefs.getLong(KEY_PENDING_AUTO_TRIGGER, 0L) == triggerAt) {
                prefs.edit().remove(KEY_PENDING_AUTO_TRIGGER).commit();
            }
        }
    }

    public void clearPendingAutoTrigger() {
        synchronized (AUTO_TRIGGER_LOCK) {
            prefs.edit().remove(KEY_PENDING_AUTO_TRIGGER).commit();
        }
    }

    /** Records water entered from the daily progress screen, independent of an alert. */
    public void addConsumedMl(int amountMl) {
        if (amountMl <= 0) {
            return;
        }
        ensureToday();
        int consumed = Math.max(0, prefs.getInt(KEY_CONSUMED_ML, 0));
        prefs.edit().putInt(KEY_CONSUMED_ML, consumed + amountMl).apply();
    }

    public void resetToday() {
        prefs.edit()
                .putString(KEY_DAY, todayKey())
                .putInt(KEY_CONSUMED_ML, 0)
                .remove(KEY_LAST_HANDLED_TRIGGER)
                .remove(KEY_PENDING_AUTO_TRIGGER)
                .apply();
    }

    private void ensureToday() {
        String today = todayKey();
        if (!today.equals(prefs.getString(KEY_DAY, ""))) {
            prefs.edit()
                    .putString(KEY_DAY, today)
                    .putInt(KEY_CONSUMED_ML, 0)
                    .remove(KEY_LAST_HANDLED_TRIGGER)
                    .apply();
        }
    }

    private static String todayKey() {
        Calendar now = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%03d",
                now.get(Calendar.YEAR), now.get(Calendar.DAY_OF_YEAR));
    }
}
