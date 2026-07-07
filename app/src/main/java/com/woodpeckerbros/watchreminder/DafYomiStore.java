package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class DafYomiStore {
    private static final String PREFS_NAME = "daf_yomi_state";
    private static final String KEY_START_DAY = "start_day";
    private static final String KEY_ANSWERED_DAYS = "answered_days";
    private static final String KEY_MISSED = "missed";
    private static final String KEY_RETRY_UNTIL = "retry_until";

    private final SharedPreferences prefs;

    public DafYomiStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public long startDay() {
        long stored = prefs.getLong(KEY_START_DAY, 0);
        if (stored > 0) {
            return stored;
        }
        long today = epochDay(System.currentTimeMillis());
        prefs.edit().putLong(KEY_START_DAY, today).apply();
        return today;
    }

    public void ensureStartToday() {
        if (prefs.getLong(KEY_START_DAY, 0) == 0) {
            prefs.edit().putLong(KEY_START_DAY, epochDay(System.currentTimeMillis())).apply();
        }
    }

    public List<DafYomiHelper.Item> dueItems(Context context) {
        long today = epochDay(System.currentTimeMillis());
        long start = startDay();
        ArrayList<DafYomiHelper.Item> items = new ArrayList<>();
        for (long day = start; day <= today; day++) {
            if (!answeredDays().contains(day) && !missedDays().contains(day)) {
                items.add(DafYomiHelper.itemForEpochDay(context, day));
            }
        }
        return items;
    }

    public List<DafYomiHelper.Item> missedItems(Context context) {
        ArrayList<DafYomiHelper.Item> items = new ArrayList<>();
        for (long day : missedDays()) {
            items.add(DafYomiHelper.itemForEpochDay(context, day));
        }
        return items;
    }

    public List<DafYomiHelper.Item> recentlyLearnedItems(Context context, int daysBack) {
        long today = epochDay(System.currentTimeMillis());
        List<Long> answered = answeredDays();
        List<Long> missed = missedDays();
        ArrayList<DafYomiHelper.Item> items = new ArrayList<>();
        for (long day = today; day >= today - Math.max(0, daysBack); day--) {
            if (answered.contains(day) && !missed.contains(day)) {
                items.add(DafYomiHelper.itemForEpochDay(context, day));
            }
        }
        return items;
    }

    public long retryUntil() {
        return prefs.getLong(KEY_RETRY_UNTIL, 0);
    }

    public void setRetryUntil(long triggerAt) {
        prefs.edit().putLong(KEY_RETRY_UNTIL, triggerAt).apply();
    }

    public void clearRetryUntil() {
        prefs.edit().remove(KEY_RETRY_UNTIL).apply();
    }

    public void markLearned(List<DafYomiHelper.Item> items) {
        JSONArray answered = answeredDaysJson();
        JSONArray missed = missedJson();
        for (DafYomiHelper.Item item : items) {
            putUnique(answered, item.epochDay);
            removeMissed(missed, item.epochDay);
        }
        prefs.edit()
                .putString(KEY_ANSWERED_DAYS, answered.toString())
                .putString(KEY_MISSED, missed.toString())
                .remove(KEY_RETRY_UNTIL)
                .apply();
    }

    public void markLearned(DafYomiHelper.Item item) {
        ArrayList<DafYomiHelper.Item> items = new ArrayList<>();
        items.add(item);
        markLearned(items);
    }

    public void markMissed(List<DafYomiHelper.Item> items) {
        JSONArray missed = missedJson();
        JSONArray answered = answeredDaysJson();
        for (DafYomiHelper.Item item : items) {
            putUnique(answered, item.epochDay);
            if (!hasMissed(missed, item.epochDay)) {
                JSONObject object = new JSONObject();
                try {
                    object.put("day", item.epochDay)
                            .put("masechta", item.masechta)
                            .put("daf", item.daf)
                            .put("label", item.label);
                    missed.put(object);
                } catch (Exception ignored) {
                }
            }
        }
        prefs.edit()
                .putString(KEY_ANSWERED_DAYS, answered.toString())
                .putString(KEY_MISSED, missed.toString())
                .remove(KEY_RETRY_UNTIL)
                .apply();
    }

    public void markMissed(DafYomiHelper.Item item) {
        ArrayList<DafYomiHelper.Item> items = new ArrayList<>();
        items.add(item);
        markMissed(items);
    }

    public void markMissedLearned(long epochDay) {
        JSONArray answered = answeredDaysJson();
        JSONArray missed = missedJson();
        putUnique(answered, epochDay);
        removeMissed(missed, epochDay);
        prefs.edit()
                .putString(KEY_ANSWERED_DAYS, answered.toString())
                .putString(KEY_MISSED, missed.toString())
                .remove(KEY_RETRY_UNTIL)
                .apply();
    }

    private List<Long> answeredDays() {
        ArrayList<Long> days = new ArrayList<>();
        JSONArray array = answeredDaysJson();
        for (int i = 0; i < array.length(); i++) {
            days.add(array.optLong(i));
        }
        return days;
    }

    private List<Long> missedDays() {
        ArrayList<Long> days = new ArrayList<>();
        JSONArray array = missedJson();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object != null) {
                days.add(object.optLong("day"));
            }
        }
        java.util.Collections.sort(days);
        return days;
    }

    private JSONArray answeredDaysJson() {
        try {
            return new JSONArray(prefs.getString(KEY_ANSWERED_DAYS, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private JSONArray missedJson() {
        try {
            return new JSONArray(prefs.getString(KEY_MISSED, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private boolean hasMissed(JSONArray array, long epochDay) {
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object != null && object.optLong("day") == epochDay) {
                return true;
            }
        }
        return false;
    }

    private void removeMissed(JSONArray array, long epochDay) {
        for (int i = array.length() - 1; i >= 0; i--) {
            JSONObject object = array.optJSONObject(i);
            if (object != null && object.optLong("day") == epochDay) {
                array.remove(i);
            }
        }
    }

    private void putUnique(JSONArray array, long epochDay) {
        for (int i = 0; i < array.length(); i++) {
            if (array.optLong(i) == epochDay) {
                return;
            }
        }
        array.put(epochDay);
    }

    public static long epochDay(long millis) {
        TimeZone timeZone = TimeZone.getDefault();
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return (calendar.getTimeInMillis() + timeZone.getOffset(calendar.getTimeInMillis())) / 86_400_000L;
    }

    public static long millisForEpochDay(long epochDay) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.clear();
        calendar.set(1970, Calendar.JANUARY, 1, 0, 0, 0);
        calendar.add(Calendar.DAY_OF_YEAR, (int) epochDay);
        return calendar.getTimeInMillis();
    }
}
