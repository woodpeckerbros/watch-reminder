package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;

public class ReminderOccurrenceStateStore {
    private static final String PREFS_NAME = "reminder_occurrence_state";
    private static final String KEY_ITEMS = "items";
    private static final String STATE_HANDLED = "handled";
    private static final String STATE_DONE = "done";
    private static final long SAME_OCCURRENCE_WINDOW_MS = 60_000L;
    private static final long RETENTION_MS = 30L * 24L * 60L * 60L * 1000L;
    private static final int MAX_ITEMS = 600;

    private final SharedPreferences prefs;
    private ArrayList<Item> cache;

    public ReminderOccurrenceStateStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void markHandled(String reminderId, long scheduledAt) {
        upsert(reminderId, scheduledAt, STATE_HANDLED);
    }

    public void markDone(String reminderId, long scheduledAt) {
        upsert(reminderId, scheduledAt, STATE_DONE);
    }

    public boolean hasOccurrence(String reminderId, long scheduledAt) {
        return find(reminderId, scheduledAt) != null;
    }

    public boolean hasDoneOnDay(String reminderId, long scheduledAt) {
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(scheduledAt);
        int year = target.get(Calendar.YEAR);
        int day = target.get(Calendar.DAY_OF_YEAR);
        for (Item item : loadAll()) {
            if (!item.reminderId.equals(reminderId) || !STATE_DONE.equals(item.state)) {
                continue;
            }
            Calendar itemDay = Calendar.getInstance();
            itemDay.setTimeInMillis(item.scheduledAt);
            if (itemDay.get(Calendar.YEAR) == year && itemDay.get(Calendar.DAY_OF_YEAR) == day) {
                return true;
            }
        }
        return false;
    }

    public void deleteOccurrence(String reminderId, long scheduledAt) {
        ArrayList<Item> items = new ArrayList<>();
        for (Item item : loadAll()) {
            if (!item.reminderId.equals(reminderId) || Math.abs(item.scheduledAt - scheduledAt) >= SAME_OCCURRENCE_WINDOW_MS) {
                items.add(item);
            }
        }
        save(items);
    }

    public void deleteReminder(String reminderId) {
        ArrayList<Item> items = new ArrayList<>();
        for (Item item : loadAll()) {
            if (!item.reminderId.equals(reminderId)) {
                items.add(item);
            }
        }
        save(items);
    }

    public void clear() {
        prefs.edit().remove(KEY_ITEMS).apply();
        cache = null;
    }

    private Item find(String reminderId, long scheduledAt) {
        for (Item item : loadAll()) {
            if (item.reminderId.equals(reminderId) && Math.abs(item.scheduledAt - scheduledAt) < SAME_OCCURRENCE_WINDOW_MS) {
                return item;
            }
        }
        return null;
    }

    private void upsert(String reminderId, long scheduledAt, String state) {
        if (reminderId == null || reminderId.trim().isEmpty() || scheduledAt <= 0) {
            return;
        }
        ArrayList<Item> items = new ArrayList<>();
        for (Item item : loadAll()) {
            if (!item.reminderId.equals(reminderId) || Math.abs(item.scheduledAt - scheduledAt) >= SAME_OCCURRENCE_WINDOW_MS) {
                items.add(item);
            } else if (STATE_DONE.equals(item.state) && STATE_HANDLED.equals(state)) {
                state = STATE_DONE;
            }
        }
        items.add(new Item(reminderId, scheduledAt, state, System.currentTimeMillis()));
        save(items);
    }

    private ArrayList<Item> loadAll() {
        if (cache != null) {
            return cache;
        }
        ArrayList<Item> items = new ArrayList<>();
        String raw = prefs.getString(KEY_ITEMS, null);
        if (raw == null) {
            cache = items;
            return cache;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                items.add(Item.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
            cache = new ArrayList<>();
            return cache;
        }
        cache = items;
        return cache;
    }

    private void save(List<Item> source) {
        long cutoff = System.currentTimeMillis() - RETENTION_MS;
        ArrayList<Item> items = new ArrayList<>();
        for (Item item : source) {
            if (item.scheduledAt >= cutoff) {
                items.add(item);
            }
        }
        items.sort(Comparator.comparingLong((Item item) -> item.scheduledAt).reversed());
        while (items.size() > MAX_ITEMS) {
            items.remove(items.size() - 1);
        }
        JSONArray array = new JSONArray();
        try {
            for (Item item : items) {
                array.put(item.toJson());
            }
            prefs.edit().putString(KEY_ITEMS, array.toString()).apply();
            cache = null;
        } catch (JSONException ignored) {
        }
    }

    private static class Item {
        final String reminderId;
        final long scheduledAt;
        final String state;
        final long updatedAt;

        Item(String reminderId, long scheduledAt, String state, long updatedAt) {
            this.reminderId = reminderId;
            this.scheduledAt = scheduledAt;
            this.state = state;
            this.updatedAt = updatedAt;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("reminderId", reminderId)
                    .put("scheduledAt", scheduledAt)
                    .put("state", state)
                    .put("updatedAt", updatedAt);
        }

        static Item fromJson(JSONObject json) throws JSONException {
            return new Item(
                    json.getString("reminderId"),
                    json.getLong("scheduledAt"),
                    json.optString("state", STATE_HANDLED),
                    json.optLong("updatedAt", 0)
            );
        }
    }
}
