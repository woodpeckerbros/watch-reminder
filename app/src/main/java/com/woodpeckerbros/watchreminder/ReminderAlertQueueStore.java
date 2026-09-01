package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ReminderAlertQueueStore {
    private static final String PREFS_NAME = "reminder_alert_queue";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_ACTIVE_ALERT = "active_alert";
    private static final String KEY_ACTIVE_AT = "active_at";
    private static final String KEY_QUEUE = "queue";
    private static final long MIN_ACTIVE_TIMEOUT_MS = 2 * 60_000L;

    private final Context context;
    private final SharedPreferences prefs;

    public ReminderAlertQueueStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean claimOrEnqueue(QueuedAlert alert) {
        clearStaleActive();
        String active = prefs.getString(KEY_ACTIVE, null);
        if (active == null || active.equals(alert.occurrenceId)) {
            prefs.edit()
                    .putString(KEY_ACTIVE, alert.occurrenceId)
                    .putString(KEY_ACTIVE_ALERT, alert.toJsonString())
                    .putLong(KEY_ACTIVE_AT, System.currentTimeMillis())
                    .apply();
            return true;
        }
        QueuedAlert activeAlert = getActiveAlert();
        if (activeAlert != null && activeAlert.canMerge(alert)) {
            prefs.edit()
                    .putString(KEY_ACTIVE_ALERT, activeAlert.merge(alert).toJsonString())
                    .putLong(KEY_ACTIVE_AT, System.currentTimeMillis())
                    .apply();
            return false;
        }
        enqueue(alert);
        return false;
    }

    public void enqueueDeferred(QueuedAlert alert) {
        clearStaleActive();
        enqueue(alert);
    }

    public boolean moveActiveToDeferred(String occurrenceId) {
        QueuedAlert activeAlert = getActiveAlert(occurrenceId);
        if (activeAlert == null) {
            return false;
        }
        enqueue(activeAlert);
        complete(occurrenceId);
        return true;
    }

    public boolean hasDeferredAlerts() {
        return !getQueue().isEmpty();
    }

    public boolean hasActiveAlert() {
        clearStaleActive();
        return prefs.getString(KEY_ACTIVE, null) != null;
    }

    public void complete(String occurrenceId) {
        String active = prefs.getString(KEY_ACTIVE, null);
        if (occurrenceId == null || occurrenceId.equals(active)) {
            prefs.edit().remove(KEY_ACTIVE).remove(KEY_ACTIVE_ALERT).remove(KEY_ACTIVE_AT).apply();
        }
    }

    public QueuedAlert getActiveAlert(String occurrenceId) {
        QueuedAlert alert = getActiveAlert();
        if (alert == null || occurrenceId == null || !occurrenceId.equals(alert.occurrenceId)) {
            return null;
        }
        return alert;
    }

    public QueuedAlert popNext() {
        clearStaleActive();
        if (prefs.getString(KEY_ACTIVE, null) != null) {
            return null;
        }
        ArrayList<QueuedAlert> queue = new ArrayList<>(getQueue());
        if (queue.isEmpty()) {
            return null;
        }
        QueuedAlert next = queue.remove(0);
        saveQueue(queue);
        prefs.edit()
                .putString(KEY_ACTIVE, next.occurrenceId)
                .putString(KEY_ACTIVE_ALERT, next.toJsonString())
                .putLong(KEY_ACTIVE_AT, System.currentTimeMillis())
                .apply();
        return next;
    }

    private void enqueue(QueuedAlert alert) {
        ArrayList<QueuedAlert> queue = new ArrayList<>(getQueue());
        for (int i = 0; i < queue.size(); i++) {
            QueuedAlert existing = queue.get(i);
            if (existing.contains(alert.occurrenceId)) {
                return;
            }
            if (existing.canMerge(alert)) {
                queue.set(i, existing.merge(alert));
                saveQueue(queue);
                return;
            }
        }
        queue.add(alert);
        saveQueue(queue);
    }

    private QueuedAlert getActiveAlert() {
        String raw = prefs.getString(KEY_ACTIVE_ALERT, null);
        if (raw == null) {
            return null;
        }
        try {
            return QueuedAlert.fromJson(new JSONObject(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<QueuedAlert> getQueue() {
        String raw = prefs.getString(KEY_QUEUE, null);
        ArrayList<QueuedAlert> queue = new ArrayList<>();
        if (raw == null) {
            return queue;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                queue.add(QueuedAlert.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return queue;
    }

    private void saveQueue(List<QueuedAlert> queue) {
        JSONArray array = new JSONArray();
        try {
            for (QueuedAlert alert : queue) {
                array.put(alert.toJson());
            }
            prefs.edit().putString(KEY_QUEUE, array.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    private void clearStaleActive() {
        long activeAt = prefs.getLong(KEY_ACTIVE_AT, 0);
        long timeout = Math.max(MIN_ACTIVE_TIMEOUT_MS, new ReminderSettings(context).autoSnoozeDelayMs() + 60_000L);
        if (activeAt > 0 && System.currentTimeMillis() - activeAt > timeout) {
            prefs.edit().remove(KEY_ACTIVE).remove(KEY_ACTIVE_ALERT).remove(KEY_ACTIVE_AT).apply();
        }
    }

    public static class QueuedAlert {
        public final String occurrenceId;
        public final String reminderId;
        public final String reminderName;
        public final long scheduledAt;
        public final long originalScheduledAt;
        public final int day;
        public final boolean snooze;
        public final ArrayList<String> occurrenceIds;
        public final ArrayList<Long> scheduledAts;
        public final ArrayList<Long> originalScheduledAts;

        public QueuedAlert(String occurrenceId, String reminderId, String reminderName, long scheduledAt, long originalScheduledAt, int day, boolean snooze) {
            this(occurrenceId, reminderId, reminderName, scheduledAt, originalScheduledAt, day, snooze, null, null, null);
        }

        private QueuedAlert(String occurrenceId, String reminderId, String reminderName, long scheduledAt, long originalScheduledAt,
                            int day, boolean snooze, ArrayList<String> occurrenceIds, ArrayList<Long> scheduledAts,
                            ArrayList<Long> originalScheduledAts) {
            this.occurrenceId = occurrenceId;
            this.reminderId = reminderId;
            this.reminderName = reminderName;
            this.scheduledAt = ReminderScheduler.floorToMinute(scheduledAt);
            this.originalScheduledAt = ReminderScheduler.floorToMinute(originalScheduledAt);
            this.day = day;
            this.snooze = snooze;
            this.occurrenceIds = occurrenceIds == null ? new ArrayList<>() : occurrenceIds;
            this.scheduledAts = scheduledAts == null ? new ArrayList<>() : scheduledAts;
            this.originalScheduledAts = originalScheduledAts == null ? new ArrayList<>() : originalScheduledAts;
            if (this.occurrenceIds.isEmpty()) {
                this.occurrenceIds.add(occurrenceId);
                this.scheduledAts.add(this.scheduledAt);
                this.originalScheduledAts.add(this.originalScheduledAt);
            }
        }

        public int count() {
            return occurrenceIds.size();
        }

        public long latestOriginalScheduledAt() {
            long latest = originalScheduledAt;
            for (Long value : originalScheduledAts) {
                if (value != null && value > latest) {
                    latest = value;
                }
            }
            return latest;
        }

        boolean contains(String candidateOccurrenceId) {
            return candidateOccurrenceId != null && occurrenceIds.contains(candidateOccurrenceId);
        }

        boolean containsScheduledAt(long candidateScheduledAt) {
            long flooredCandidate = ReminderScheduler.floorToMinute(candidateScheduledAt);
            for (Long scheduled : scheduledAts) {
                if (scheduled != null && ReminderScheduler.floorToMinute(scheduled) == flooredCandidate) {
                    return true;
                }
            }
            return false;
        }

        boolean canMerge(QueuedAlert other) {
            return other != null
                    && reminderId.equals(other.reminderId)
                    && snooze == other.snooze;
        }

        QueuedAlert merge(QueuedAlert other) {
            ArrayList<String> mergedIds = new ArrayList<>(occurrenceIds);
            ArrayList<Long> mergedScheduled = new ArrayList<>(scheduledAts);
            ArrayList<Long> mergedOriginal = new ArrayList<>(originalScheduledAts);
            for (int i = 0; i < other.occurrenceIds.size(); i++) {
                String id = other.occurrenceIds.get(i);
                Long scheduledAt = other.scheduledAts.get(i);
                if (mergedIds.contains(id) || containsScheduledAtIn(mergedScheduled, scheduledAt)) {
                    continue;
                }
                mergedIds.add(id);
                mergedScheduled.add(scheduledAt);
                mergedOriginal.add(other.originalScheduledAts.get(i));
            }
            long earliestScheduledAt = scheduledAt;
            long earliestOriginalAt = originalScheduledAt;
            for (int i = 1; i < mergedScheduled.size(); i++) {
                if (mergedScheduled.get(i) < earliestScheduledAt) {
                    earliestScheduledAt = mergedScheduled.get(i);
                    earliestOriginalAt = mergedOriginal.get(i);
                }
            }
            return new QueuedAlert(
                    occurrenceId,
                    reminderId,
                    reminderName,
                    earliestScheduledAt,
                    earliestOriginalAt,
                    day,
                    snooze,
                    mergedIds,
                    mergedScheduled,
                    mergedOriginal
            );
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("occurrenceId", occurrenceId)
                    .put("reminderId", reminderId)
                    .put("reminderName", reminderName)
                    .put("scheduledAt", scheduledAt)
                    .put("originalScheduledAt", originalScheduledAt)
                    .put("day", day)
                    .put("snooze", snooze)
                    .put("occurrenceIds", stringArray(occurrenceIds))
                    .put("scheduledAts", longArray(scheduledAts))
                    .put("originalScheduledAts", longArray(originalScheduledAts));
        }

        String toJsonString() {
            try {
                return toJson().toString();
            } catch (JSONException ignored) {
                return "{}";
            }
        }

        static QueuedAlert fromJson(JSONObject json) throws JSONException {
            long scheduledAt = ReminderScheduler.floorToMinute(json.getLong("scheduledAt"));
            ArrayList<String> occurrenceIds = readStringArray(json.optJSONArray("occurrenceIds"));
            ArrayList<Long> scheduledAts = readLongArray(json.optJSONArray("scheduledAts"));
            ArrayList<Long> originalScheduledAts = readLongArray(json.optJSONArray("originalScheduledAts"));
            if (occurrenceIds.size() != scheduledAts.size() || occurrenceIds.size() != originalScheduledAts.size()) {
                occurrenceIds.clear();
                scheduledAts.clear();
                originalScheduledAts.clear();
            }
            return new QueuedAlert(
                    json.getString("occurrenceId"),
                    json.getString("reminderId"),
                    json.optString("reminderName", "תזכורת"),
                    scheduledAt,
                    ReminderScheduler.floorToMinute(json.optLong("originalScheduledAt", scheduledAt)),
                    json.optInt("day", -1),
                    json.optBoolean("snooze", false),
                    occurrenceIds,
                    scheduledAts,
                    originalScheduledAts
            );
        }

        private static boolean containsScheduledAtIn(List<Long> values, Long candidate) {
            if (candidate == null) {
                return false;
            }
            long flooredCandidate = ReminderScheduler.floorToMinute(candidate);
            for (Long value : values) {
                if (value != null && ReminderScheduler.floorToMinute(value) == flooredCandidate) {
                    return true;
                }
            }
            return false;
        }

        private static JSONArray stringArray(List<String> values) {
            JSONArray array = new JSONArray();
            for (String value : values) {
                array.put(value);
            }
            return array;
        }

        private static JSONArray longArray(List<Long> values) {
            JSONArray array = new JSONArray();
            for (Long value : values) {
                array.put(value);
            }
            return array;
        }

        private static ArrayList<String> readStringArray(JSONArray array) throws JSONException {
            ArrayList<String> values = new ArrayList<>();
            if (array == null) {
                return values;
            }
            for (int i = 0; i < array.length(); i++) {
                values.add(array.getString(i));
            }
            return values;
        }

        private static ArrayList<Long> readLongArray(JSONArray array) throws JSONException {
            ArrayList<Long> values = new ArrayList<>();
            if (array == null) {
                return values;
            }
            for (int i = 0; i < array.length(); i++) {
                values.add(ReminderScheduler.floorToMinute(array.getLong(i)));
            }
            return values;
        }
    }
}
