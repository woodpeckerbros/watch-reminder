package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ReminderSnoozeStore {
    private static final String PREFS_NAME = "reminder_snoozes";
    private static final String KEY_ITEMS = "items";

    private final Context context;
    private final SharedPreferences prefs;
    private ArrayList<Snooze> cache;

    public ReminderSnoozeStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void upsert(String reminderId, String reminderName, long scheduledAt) {
        upsert(reminderId, reminderName, scheduledAt, scheduledAt);
    }

    public void upsert(String reminderId, String reminderName, long scheduledAt, long originalScheduledAt) {
        ArrayList<Snooze> snoozes = new ArrayList<>();
        for (Snooze snooze : getAll()) {
            if (!snooze.reminderId.equals(reminderId)) {
                snoozes.add(snooze);
            }
        }
        snoozes.add(new Snooze(reminderId, reminderName, scheduledAt, originalScheduledAt));
        save(snoozes);
        ComplicationRefresh.request(context);
    }

    public void delete(String reminderId) {
        ArrayList<Snooze> snoozes = new ArrayList<>();
        for (Snooze snooze : getAll()) {
            if (!snooze.reminderId.equals(reminderId)) {
                snoozes.add(snooze);
            }
        }
        save(snoozes);
        ComplicationRefresh.request(context);
    }

    public List<Snooze> getAll() {
        if (cache != null) {
            return new ArrayList<>(cache);
        }
        String raw = prefs.getString(KEY_ITEMS, null);
        ArrayList<Snooze> snoozes = new ArrayList<>();
        if (raw == null) {
            cache = snoozes;
            return new ArrayList<>(cache);
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                snoozes.add(Snooze.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        snoozes.sort(Comparator.comparingLong((Snooze snooze) -> snooze.scheduledAt));
        cache = snoozes;
        return new ArrayList<>(cache);
    }

    private void save(ArrayList<Snooze> snoozes) {
        snoozes.sort(Comparator.comparingLong((Snooze snooze) -> snooze.scheduledAt));
        JSONArray array = new JSONArray();
        try {
            for (Snooze snooze : snoozes) {
                array.put(snooze.toJson());
            }
            prefs.edit().putString(KEY_ITEMS, array.toString()).commit();
            cache = null;
        } catch (JSONException ignored) {
        }
    }

    public static class Snooze {
        public final String reminderId;
        public final String reminderName;
        public final long scheduledAt;
        public final long originalScheduledAt;

        Snooze(String reminderId, String reminderName, long scheduledAt) {
            this(reminderId, reminderName, scheduledAt, scheduledAt);
        }

        Snooze(String reminderId, String reminderName, long scheduledAt, long originalScheduledAt) {
            this.reminderId = reminderId;
            this.reminderName = reminderName;
            this.scheduledAt = scheduledAt;
            this.originalScheduledAt = originalScheduledAt;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("reminderId", reminderId)
                    .put("reminderName", reminderName)
                    .put("scheduledAt", scheduledAt)
                    .put("originalScheduledAt", originalScheduledAt);
        }

        static Snooze fromJson(JSONObject json) throws JSONException {
            long scheduledAt = ReminderScheduler.floorToMinute(json.getLong("scheduledAt"));
            return new Snooze(
                    json.getString("reminderId"),
                    json.getString("reminderName"),
                    scheduledAt,
                    ReminderScheduler.floorToMinute(json.optLong("originalScheduledAt", scheduledAt))
            );
        }
    }
}
