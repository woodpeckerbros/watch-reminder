package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

public class IntermittentFastingStore {
    private static final String PREFS_NAME = "intermittent_fasting";
    private static final String KEY_CURRENT_START_AT = "current_start_at";
    private static final String KEY_FINISHED_AT = "finished_at";
    private static final String KEY_ACKED_ALERT_ID = "acked_alert_id";

    private final Context context;
    private final SharedPreferences prefs;

    public IntermittentFastingStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public Window window() {
        return window(System.currentTimeMillis());
    }

    public Window window(long now) {
        ReminderSettings settings = new ReminderSettings(context);
        long startAt = prefs.getLong(KEY_CURRENT_START_AT, 0L);
        if (startAt <= 0L) {
            startAt = initialStartAt(settings, now);
        }
        int eatingHours = settings.fastingEatingHours();
        long endAt = startAt + eatingHours * 60L * 60_000L;
        long finishedAt = prefs.getLong(KEY_FINISHED_AT, 0L);
        if (finishedAt >= startAt && finishedAt < endAt) {
            long nextStartAt = finishedAt + settings.fastingHours() * 60L * 60_000L;
            if (now < nextStartAt) {
                return new Window(startAt, endAt, finishedAt, nextStartAt, true);
            }
            startAt = nextStartAt;
            endAt = startAt + eatingHours * 60L * 60_000L;
            prefs.edit()
                    .putLong(KEY_CURRENT_START_AT, startAt)
                    .remove(KEY_FINISHED_AT)
                    .apply();
        }
        while (now >= endAt) {
            startAt = endAt + settings.fastingHours() * 60L * 60_000L;
            endAt = startAt + eatingHours * 60L * 60_000L;
        }
        prefs.edit()
                .putLong(KEY_CURRENT_START_AT, startAt)
                .remove(KEY_FINISHED_AT)
                .apply();
        return new Window(startAt, endAt, 0L, startAt, false);
    }

    public void resetToInitialStart() {
        long startAt = initialStartAt(new ReminderSettings(context), System.currentTimeMillis());
        prefs.edit()
                .putLong(KEY_CURRENT_START_AT, startAt)
                .remove(KEY_FINISHED_AT)
                .apply();
    }

    public void startEatingNow() {
        startEatingAt(ReminderScheduler.floorToMinute(System.currentTimeMillis()));
    }

    public void startEatingAt(long startAt) {
        prefs.edit()
                .putLong(KEY_CURRENT_START_AT, ReminderScheduler.floorToMinute(startAt))
                .remove(KEY_FINISHED_AT)
                .apply();
    }

    public void finishEatingNow() {
        finishEatingAt(ReminderScheduler.floorToMinute(System.currentTimeMillis()));
    }

    public void finishEatingAt(long finishedAt) {
        prefs.edit()
                .putLong(KEY_FINISHED_AT, ReminderScheduler.floorToMinute(finishedAt))
                .apply();
    }

    public String alertId(String eventType, long triggerAt) {
        return eventType + ":" + ReminderScheduler.floorToMinute(triggerAt);
    }

    public boolean isAlertAcknowledged(String eventType, long triggerAt) {
        String alertId = alertId(eventType, triggerAt);
        return alertId.equals(prefs.getString(KEY_ACKED_ALERT_ID, ""));
    }

    public void acknowledgeAlert(String eventType, long triggerAt) {
        prefs.edit()
                .putString(KEY_ACKED_ALERT_ID, alertId(eventType, triggerAt))
                .apply();
    }

    private long initialStartAt(ReminderSettings settings, long now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.HOUR_OF_DAY, settings.fastingStartHour());
        calendar.set(Calendar.MINUTE, settings.fastingStartMinute());
        long startAt = calendar.getTimeInMillis();
        long endAt = startAt + settings.fastingEatingHours() * 60L * 60_000L;
        if (now >= endAt) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            startAt = calendar.getTimeInMillis();
        }
        return ReminderScheduler.floorToMinute(startAt);
    }

    public static class Window {
        public final long startAt;
        public final long endAt;
        public final long finishedAt;
        public final long nextStartAt;
        public final boolean finished;

        Window(long startAt, long endAt, long finishedAt, long nextStartAt, boolean finished) {
            this.startAt = startAt;
            this.endAt = endAt;
            this.finishedAt = finishedAt;
            this.nextStartAt = nextStartAt;
            this.finished = finished;
        }

        public boolean eatingOpen(long now) {
            return !finished && now >= startAt && now < endAt;
        }
    }
}
