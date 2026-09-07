package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Locale;

public final class WaterReminderStore {
    static final String PREFS_NAME = "water_reminder_state";
    private static final String KEY_DAY = "day";
    private static final String KEY_CONSUMED_ML = "consumed_ml";
    private static final String KEY_LAST_HANDLED_TRIGGER = "last_handled_trigger";

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

    public void markHandled(long triggerAt, int amountMl, boolean drank) {
        ensureToday();
        int consumed = Math.max(0, prefs.getInt(KEY_CONSUMED_ML, 0));
        if (drank) {
            consumed += Math.max(0, amountMl);
        }
        prefs.edit()
                .putInt(KEY_CONSUMED_ML, consumed)
                .putLong(KEY_LAST_HANDLED_TRIGGER, triggerAt)
                .apply();
    }

    public void resetToday() {
        prefs.edit()
                .putString(KEY_DAY, todayKey())
                .putInt(KEY_CONSUMED_ML, 0)
                .remove(KEY_LAST_HANDLED_TRIGGER)
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
