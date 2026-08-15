package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ReminderStore {
    private static final String PREFS_NAME = "reminders";
    private static final String KEY_ITEMS = "items";

    private final Context context;
    private final SharedPreferences prefs;
    private ArrayList<Reminder> cache;

    public ReminderStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<Reminder> getAll() {
        if (cache != null) {
            return new ArrayList<>(cache);
        }
        String raw = prefs.getString(KEY_ITEMS, null);
        ArrayList<Reminder> reminders = new ArrayList<>();
        if (raw == null) {
            cache = reminders;
            return reminders;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                reminders.add(Reminder.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        reminders.sort(reminderComparator());
        cache = reminders;
        return new ArrayList<>(cache);
    }

    public Reminder find(String id) {
        for (Reminder reminder : getAll()) {
            if (reminder.id.equals(id)) {
                return reminder;
            }
        }
        return null;
    }

    public void upsert(Reminder reminder) {
        AppLog.d(context, "store upsert id=" + reminder.id + " name=" + reminder.name + " enabled=" + reminder.enabled + " hour=" + reminder.hour + ":" + reminder.minute + " days=" + reminder.days + " oneTime=" + reminder.oneTimeAt);
        ArrayList<Reminder> reminders = new ArrayList<>();
        for (Reminder existing : getAll()) {
            if (!existing.id.equals(reminder.id)) {
                reminders.add(existing);
            }
        }
        reminders.add(reminder);
        reminders.sort(reminderComparator());
        save(reminders);
        if (!reminder.enabled) {
            new ReminderSnoozeStore(context).delete(reminder.id);
        }
        ReminderScheduler.scheduleNearest(context);
        if (new ReminderSettings(context).serviceEnabled()) {
            ReminderForegroundService.start(context);
        } else {
            ReminderForegroundService.stop(context);
        }
        ComplicationRefresh.request(context);
    }

    public void delete(Reminder reminder) {
        ReminderScheduler.cancel(context, reminder);
        new ReminderSnoozeStore(context).delete(reminder.id);
        new ReminderOccurrenceStateStore(context).deleteReminder(reminder.id);
        ArrayList<Reminder> reminders = new ArrayList<>();
        for (Reminder existing : getAll()) {
            if (!existing.id.equals(reminder.id)) {
                reminders.add(existing);
            }
        }
        save(reminders);
        ReminderScheduler.scheduleNearest(context);
        if (new ReminderSettings(context).serviceEnabled()) {
            ReminderForegroundService.start(context);
        } else {
            ReminderForegroundService.stop(context);
        }
        ComplicationRefresh.request(context);
    }

    public void replaceAll(List<Reminder> reminders) {
        for (Reminder reminder : getAll()) {
            ReminderScheduler.cancel(context, reminder);
        }
        ArrayList<Reminder> next = new ArrayList<>(reminders);
        next.sort(reminderComparator());
        save(next);
        context.getSharedPreferences("reminder_snoozes", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("reminder_alert_queue", Context.MODE_PRIVATE).edit().clear().commit();
        new ReminderOccurrenceStateStore(context).clear();
        rescheduleAll();
        ComplicationRefresh.request(context);
    }

    public void rescheduleAll() {
        ReminderScheduler.scheduleNearest(context);
    }

    private void save(List<Reminder> reminders) {
        JSONArray array = new JSONArray();
        try {
            for (Reminder reminder : reminders) {
                array.put(reminder.toJson());
            }
            prefs.edit().putString(KEY_ITEMS, array.toString()).apply();
            cache = null;
        } catch (Exception ignored) {
        }
    }

    private Comparator<Reminder> reminderComparator() {
        return (left, right) -> {
            int byGroup = Integer.compare(sortGroup(left), sortGroup(right));
            if (byGroup != 0) return byGroup;
            if (left.isAnnualEvent() && right.isAnnualEvent()) {
                int byMonth = Integer.compare(left.annualMonth, right.annualMonth);
                if (byMonth != 0) return byMonth;
                int byDay = Integer.compare(left.annualDay, right.annualDay);
                if (byDay != 0) return byDay;
            }
            int byHour = Integer.compare(sortHour(left), sortHour(right));
            if (byHour != 0) return byHour;
            int byMinute = Integer.compare(sortMinute(left), sortMinute(right));
            if (byMinute != 0) return byMinute;
            return left.name.compareToIgnoreCase(right.name);
        };
    }

    private int sortGroup(Reminder reminder) {
        return reminder.isAnnualEvent() ? 1 : 0;
    }

    private int sortHour(Reminder reminder) {
        return reminder.useZmanim ? 99 : reminder.hour;
    }

    private int sortMinute(Reminder reminder) {
        return reminder.useZmanim ? 99 : reminder.minute;
    }
}
