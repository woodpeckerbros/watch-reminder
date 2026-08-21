package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReminderEventStore {
    public static final String STATUS_FIRED = "לא בוצע";
    public static final String STATUS_DONE = "בוצע";
    public static final String STATUS_SNOOZED = "נדחה";
    public static final String STATUS_AUTO_SNOOZED = "נדחה אוטומטית";
    public static final String NOTE_EARLY_DONE = "סומן מראש";

    private static final String PREFS_NAME = "reminder_events";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_CLEAR_GENERATION = "clear_generation";
    private static final int MAX_EVENTS = 80;
    private static final int MAX_EVENTS_TO_READ = MAX_EVENTS * 3;
    private static final long SNOOZE_CHAIN_WINDOW_MS = 8 * 60 * 60 * 1000L;

    private final Context context;
    private final SharedPreferences prefs;
    private int clearGeneration;
    private ArrayList<Event> rawCache;
    private ArrayList<Event> collapsedCache;

    public ReminderEventStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        clearGeneration = prefs.getInt(KEY_CLEAR_GENERATION, 0);
    }

    public void markFired(String occurrenceId, String reminderId, String reminderName, long scheduledAt, boolean snooze) {
        markFired(occurrenceId, reminderId, reminderName, "", scheduledAt, snooze);
    }

    public void markFired(String occurrenceId, String reminderId, String reminderName, String reminderDescription, long scheduledAt, boolean snooze) {
        new ReminderOccurrenceStateStore(context).markHandled(reminderId, scheduledAt);
        if (snooze && updateExistingSnoozeChain(occurrenceId, reminderId, reminderName, reminderDescription, scheduledAt)) {
            return;
        }
        upsert(new Event(
                occurrenceId,
                reminderId,
                reminderName,
                reminderDescription,
                scheduledAt,
                System.currentTimeMillis(),
                STATUS_FIRED,
                0,
                snooze ? "תזכורת שנדחתה" : ""
        ));
    }

    public void markMissed(String occurrenceId, String reminderId, String reminderName, long scheduledAt) {
        markMissed(occurrenceId, reminderId, reminderName, "", scheduledAt);
    }

    public void markMissed(String occurrenceId, String reminderId, String reminderName, String reminderDescription, long scheduledAt) {
        new ReminderOccurrenceStateStore(context).markHandled(reminderId, scheduledAt);
        upsert(new Event(
                occurrenceId,
                reminderId,
                reminderName,
                reminderDescription,
                scheduledAt,
                System.currentTimeMillis(),
                STATUS_FIRED,
                0,
                "עבר הזמן"
        ));
    }

    public void markDone(String occurrenceId) {
        ArrayList<Event> events = new ArrayList<>(getAll());
        Event targetEvent = null;
        for (Event event : events) {
            if (event.occurrenceId.equals(occurrenceId)) {
                targetEvent = event;
                break;
            }
        }
        if (targetEvent == null) {
            return;
        }
        new ReminderOccurrenceStateStore(context).markDone(targetEvent.reminderId, targetEvent.scheduledAt);
        ArrayList<Event> updated = new ArrayList<>();
        boolean changed = false;
        for (Event event : events) {
            boolean target = event.occurrenceId.equals(occurrenceId);
            boolean sameSnoozeChain = !target
                    && shouldMergeChainEvents(event, targetEvent)
                    && (STATUS_FIRED.equals(event.status)
                    || STATUS_SNOOZED.equals(event.status)
                    || STATUS_AUTO_SNOOZED.equals(event.status));
            if (target) {
                updated.add(new Event(
                        event.occurrenceId,
                        event.reminderId,
                        event.reminderName,
                        event.description,
                        event.scheduledAt,
                        event.firedAt,
                        STATUS_DONE,
                        System.currentTimeMillis(),
                        ""
                ));
                changed = true;
            } else if (sameSnoozeChain) {
                changed = true;
            } else {
                updated.add(event);
            }
        }
        if (changed) {
            save(updated);
        }
    }

    public void markSnoozed(String occurrenceId, int minutes) {
        updateStatus(occurrenceId, STATUS_SNOOZED, "לעוד " + formatMinutes(minutes));
    }

    public void markSnoozed(String occurrenceId, int minutes, long nextScheduledAt) {
        updateStatus(occurrenceId, STATUS_SNOOZED, "לעוד " + formatMinutes(minutes), nextScheduledAt);
    }

    public void markSnoozedUntil(String occurrenceId, long nextScheduledAt, String note) {
        updateStatus(occurrenceId, STATUS_SNOOZED, note, nextScheduledAt);
    }

    public void markAutoSnoozed(String occurrenceId, int minutes) {
        updateStatus(occurrenceId, STATUS_AUTO_SNOOZED, "לעוד " + formatMinutes(minutes));
    }

    public void markAutoSnoozed(String occurrenceId, int minutes, long nextScheduledAt) {
        updateStatus(occurrenceId, STATUS_AUTO_SNOOZED, "לעוד " + formatMinutes(minutes), nextScheduledAt);
    }

    public void markLatestPendingDone(String reminderId) {
        ArrayList<Event> events = new ArrayList<>(getAll());
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            if (event.reminderId.equals(reminderId)
                    && (STATUS_FIRED.equals(event.status)
                    || STATUS_SNOOZED.equals(event.status)
                    || STATUS_AUTO_SNOOZED.equals(event.status))) {
                events.set(i, new Event(
                        event.occurrenceId,
                        event.reminderId,
                        event.reminderName,
                        event.description,
                        event.scheduledAt,
                        event.firedAt,
                        STATUS_DONE,
                        System.currentTimeMillis(),
                        event.note
                ));
                new ReminderOccurrenceStateStore(context).markDone(event.reminderId, event.scheduledAt);
                save(events);
                return;
            }
        }
    }

    public void markUpcomingDone(String reminderId, String reminderName, long scheduledAt) {
        markUpcomingDone(reminderId, reminderName, "", scheduledAt);
    }

    public void markUpcomingDone(String reminderId, String reminderName, String reminderDescription, long scheduledAt) {
        new ReminderOccurrenceStateStore(context).markDone(reminderId, scheduledAt);
        upsert(new Event(
                reminderId + ":" + scheduledAt + ":done",
                reminderId,
                reminderName,
                reminderDescription,
                scheduledAt,
                System.currentTimeMillis(),
                STATUS_DONE,
                System.currentTimeMillis(),
                NOTE_EARLY_DONE
        ));
    }

    public void markUpcomingSnoozed(String reminderId, String reminderName, String reminderDescription, long originalScheduledAt, int minutes, long nextScheduledAt) {
        new ReminderOccurrenceStateStore(context).markHandled(reminderId, originalScheduledAt);
        upsert(new Event(
                reminderId + ":" + originalScheduledAt + ":snooze",
                reminderId,
                reminderName,
                reminderDescription,
                nextScheduledAt,
                System.currentTimeMillis(),
                STATUS_SNOOZED,
                System.currentTimeMillis(),
                "לעוד " + formatMinutes(minutes)
        ));
    }

    public Event find(String occurrenceId) {
        for (Event event : loadAll()) {
            if (event.occurrenceId.equals(occurrenceId)) {
                return event;
            }
        }
        return null;
    }

    public Event findReminderOccurrence(String reminderId, long scheduledAt) {
        for (Event event : loadAll()) {
            if (event.reminderId.equals(reminderId) && Math.abs(event.scheduledAt - scheduledAt) < 60_000L) {
                return event;
            }
        }
        return null;
    }

    public boolean hasReminderOccurrence(String reminderId, long scheduledAt) {
        return findReminderOccurrence(reminderId, scheduledAt) != null
                || new ReminderOccurrenceStateStore(context).hasOccurrence(reminderId, scheduledAt);
    }

    public boolean hasDoneOnDay(String reminderId, long scheduledAt) {
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(scheduledAt);
        int year = target.get(Calendar.YEAR);
        int day = target.get(Calendar.DAY_OF_YEAR);
        for (Event event : loadAll()) {
            if (!event.reminderId.equals(reminderId) || !STATUS_DONE.equals(event.status)) {
                continue;
            }
            Calendar eventDay = Calendar.getInstance();
            eventDay.setTimeInMillis(event.scheduledAt);
            if (eventDay.get(Calendar.YEAR) == year && eventDay.get(Calendar.DAY_OF_YEAR) == day) {
                return true;
            }
        }
        return new ReminderOccurrenceStateStore(context).hasDoneOnDay(reminderId, scheduledAt);
    }

    public boolean hasPendingOrDoneOnDay(String reminderId, long scheduledAt) {
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(scheduledAt);
        for (Event event : loadAll()) {
            if (!event.reminderId.equals(reminderId)) {
                continue;
            }
            Calendar eventDay = Calendar.getInstance();
            eventDay.setTimeInMillis(event.scheduledAt);
            if (eventDay.get(Calendar.YEAR) == target.get(Calendar.YEAR)
                    && eventDay.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
                    && (STATUS_DONE.equals(event.status) || STATUS_FIRED.equals(event.status)
                    || STATUS_SNOOZED.equals(event.status) || STATUS_AUTO_SNOOZED.equals(event.status))) {
                return true;
            }
        }
        return new ReminderOccurrenceStateStore(context).hasDoneOnDay(reminderId, scheduledAt);
    }

    public List<Event> getAll() {
        if (collapsedCache == null) {
            collapsedCache = collapseSnoozeChains(loadAll());
        }
        return new ArrayList<>(collapsedCache);
    }

    public JSONArray recentForBackup(long since) {
        JSONArray result = new JSONArray();
        for (Event event : getAll()) {
            long latestActivity = Math.max(event.scheduledAt, Math.max(event.firedAt, event.actionAt));
            if (latestActivity < since) {
                continue;
            }
            try {
                result.put(event.toJson());
            } catch (JSONException ignored) {
            }
        }
        return result;
    }

    public void restoreRecent(JSONArray source) throws JSONException {
        ArrayList<Event> restored = new ArrayList<>();
        if (source != null) {
            for (int i = 0; i < source.length(); i++) {
                restored.add(Event.fromJson(source.getJSONObject(i)));
            }
        }
        save(restored);
    }

    private ArrayList<Event> loadAll() {
        if (rawCache != null) {
            return rawCache;
        }
        String raw = prefs.getString(KEY_ITEMS, null);
        ArrayList<Event> events = new ArrayList<>();
        if (raw == null) {
            rawCache = events;
            return rawCache;
        }
        try {
            JSONArray array = new JSONArray(raw);
            int limit = Math.min(array.length(), MAX_EVENTS_TO_READ);
            for (int i = 0; i < limit; i++) {
                events.add(Event.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
            rawCache = new ArrayList<>();
            return rawCache;
        }
        events.sort((left, right) -> Long.compare(right.scheduledAt, left.scheduledAt));
        rawCache = events;
        return rawCache;
    }

    public void delete(String occurrenceId) {
        ArrayList<Event> events = new ArrayList<>();
        for (Event event : getAll()) {
            if (!event.occurrenceId.equals(occurrenceId)) {
                events.add(event);
            }
        }
        save(events);
    }

    public void clear() {
        preserveOperationalState(getAll());
        int nextGeneration = prefs.getInt(KEY_CLEAR_GENERATION, 0) + 1;
        prefs.edit()
                .putInt(KEY_CLEAR_GENERATION, nextGeneration)
                .remove(KEY_ITEMS)
                .commit();
        clearGeneration = nextGeneration;
        rawCache = null;
        collapsedCache = null;
        ReminderDueChecker.markCheckedNow(context);
        ReminderAudit.markAuditedNow(context);
    }

    private void updateStatus(String occurrenceId, String status, String note) {
        updateStatus(occurrenceId, status, note, Long.MIN_VALUE);
    }

    private void updateStatus(String occurrenceId, String status, String note, long scheduledAtOverride) {
        ArrayList<Event> events = new ArrayList<>(getAll());
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            if (event.occurrenceId.equals(occurrenceId)) {
                events.set(i, new Event(
                        event.occurrenceId,
                        event.reminderId,
                        event.reminderName,
                        event.description,
                        scheduledAtOverride == Long.MIN_VALUE ? event.scheduledAt : scheduledAtOverride,
                        event.firedAt,
                        status,
                        System.currentTimeMillis(),
                        note
                ));
                save(events);
                return;
            }
        }
    }

    private boolean updateExistingSnoozeChain(String occurrenceId, String reminderId, String reminderName, String reminderDescription, long scheduledAt) {
        ArrayList<Event> events = new ArrayList<>(getAll());
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            if (event.reminderId.equals(reminderId)
                    && (STATUS_SNOOZED.equals(event.status) || STATUS_AUTO_SNOOZED.equals(event.status))) {
                events.set(i, new Event(
                        occurrenceId,
                        reminderId,
                        reminderName,
                        reminderDescription == null || reminderDescription.isEmpty() ? event.description : reminderDescription,
                        scheduledAt,
                        System.currentTimeMillis(),
                        STATUS_FIRED,
                        0,
                        "תזכורת שנדחתה"
                ));
                save(events);
                return true;
            }
        }
        return false;
    }

    private void upsert(Event event) {
        ArrayList<Event> events = new ArrayList<>();
        for (Event existing : getAll()) {
            if (!existing.occurrenceId.equals(event.occurrenceId)) {
                events.add(existing);
            }
        }
        events.add(event);
        save(events);
    }

    private void preserveOperationalState(List<Event> events) {
        ReminderOccurrenceStateStore stateStore = new ReminderOccurrenceStateStore(context);
        for (Event event : events) {
            if (STATUS_DONE.equals(event.status)) {
                stateStore.markDone(event.reminderId, event.scheduledAt);
            } else {
                stateStore.markHandled(event.reminderId, event.scheduledAt);
            }
        }
    }

    private void save(ArrayList<Event> events) {
        if (clearGeneration != prefs.getInt(KEY_CLEAR_GENERATION, 0)) {
            rawCache = null;
            collapsedCache = null;
            return;
        }
        events = collapseSnoozeChains(events);
        events.sort(Comparator.comparingLong((Event event) -> event.scheduledAt).reversed());
        while (events.size() > MAX_EVENTS) {
            events.remove(events.size() - 1);
        }
        JSONArray array = new JSONArray();
        try {
            for (Event event : events) {
                array.put(event.toJson());
            }
            prefs.edit().putString(KEY_ITEMS, array.toString()).apply();
            rawCache = null;
            collapsedCache = null;
        } catch (JSONException ignored) {
        }
    }

    private ArrayList<Event> collapseSnoozeChains(List<Event> source) {
        ArrayList<Event> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparingLong((Event event) -> event.scheduledAt));
        ArrayList<Event> collapsed = new ArrayList<>();
        for (Event event : sorted) {
            int mergeIndex = findMergeIndex(collapsed, event);
            if (mergeIndex >= 0) {
                collapsed.set(mergeIndex, mergedEvent(collapsed.get(mergeIndex), event));
            } else {
                collapsed.add(event);
            }
        }
        collapsed.sort(Comparator.comparingLong((Event event) -> event.scheduledAt).reversed());
        return collapsed;
    }

    private int findMergeIndex(List<Event> events, Event event) {
        for (int i = events.size() - 1; i >= 0; i--) {
            Event existing = events.get(i);
            if (shouldMergeChainEvents(existing, event)) {
                return i;
            }
        }
        return -1;
    }

    private boolean shouldMergeChainEvents(Event left, Event right) {
        if (!left.reminderId.equals(right.reminderId)) {
            return false;
        }
        if (NOTE_EARLY_DONE.equals(left.note) || NOTE_EARLY_DONE.equals(right.note)) {
            return false;
        }
        if (Math.abs(left.scheduledAt - right.scheduledAt) > SNOOZE_CHAIN_WINDOW_MS) {
            return false;
        }
        return isChainStatus(left.status) || isChainStatus(right.status);
    }

    private boolean isChainStatus(String status) {
        return STATUS_SNOOZED.equals(status)
                || STATUS_AUTO_SNOOZED.equals(status)
                || STATUS_FIRED.equals(status);
    }

    private Event mergedEvent(Event older, Event newer) {
        Event latest = newer.scheduledAt >= older.scheduledAt ? newer : older;
        Event earliest = newer.scheduledAt >= older.scheduledAt ? older : newer;
        return new Event(
                latest.occurrenceId,
                latest.reminderId,
                latest.reminderName,
                latest.description,
                latest.scheduledAt,
                earliest.firedAt,
                latest.status,
                Math.max(older.actionAt, newer.actionAt),
                latest.note
        );
    }

    private String formatMinutes(int minutes) {
        if (minutes == 60) return "שעה";
        if (minutes == 120) return "שעתיים";
        return minutes + " דקות";
    }

    public static class Event {
        public final String occurrenceId;
        public final String reminderId;
        public final String reminderName;
        public final String description;
        public final long scheduledAt;
        public final long firedAt;
        public final String status;
        public final long actionAt;
        public final String note;

        public Event(String occurrenceId, String reminderId, String reminderName, long scheduledAt, long firedAt, String status, long actionAt, String note) {
            this(occurrenceId, reminderId, reminderName, "", scheduledAt, firedAt, status, actionAt, note);
        }

        public Event(String occurrenceId, String reminderId, String reminderName, String description, long scheduledAt, long firedAt, String status, long actionAt, String note) {
            this.occurrenceId = occurrenceId;
            this.reminderId = reminderId;
            this.reminderName = reminderName;
            this.description = description == null ? "" : description;
            this.scheduledAt = scheduledAt;
            this.firedAt = firedAt;
            this.status = status;
            this.actionAt = actionAt;
            this.note = note == null ? "" : note;
        }

        public String displayTime() {
            return new SimpleDateFormat("dd/MM HH:mm", Locale.US).format(new Date(scheduledAt));
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("occurrenceId", occurrenceId)
                    .put("reminderId", reminderId)
                    .put("reminderName", reminderName)
                    .put("description", description)
                    .put("scheduledAt", scheduledAt)
                    .put("firedAt", firedAt)
                    .put("status", status)
                    .put("actionAt", actionAt)
                    .put("note", note);
        }

        static Event fromJson(JSONObject json) throws JSONException {
            return new Event(
                    json.getString("occurrenceId"),
                    json.getString("reminderId"),
                    json.getString("reminderName"),
                    json.optString("description", ""),
                    json.getLong("scheduledAt"),
                    json.getLong("firedAt"),
                    json.getString("status"),
                    json.optLong("actionAt", 0),
                    json.optString("note", "")
            );
        }
    }
}
