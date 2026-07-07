package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QuietTimeRuleStore {
    private static final String PREFS_NAME = "quiet_time_rules";
    private static final String KEY_RULES = "rules";
    private static final String KEY_LEGACY_MIGRATED = "legacy_migrated";

    private final Context context;
    private final SharedPreferences prefs;

    public QuietTimeRuleStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        migrateLegacyIfNeeded();
    }

    public List<Rule> getAll() {
        ArrayList<Rule> rules = new ArrayList<>();
        String raw = prefs.getString(KEY_RULES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                rules.add(Rule.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return rules;
    }

    public Rule find(String id) {
        for (Rule rule : getAll()) {
            if (rule.id.equals(id)) {
                return rule;
            }
        }
        return null;
    }

    public void upsert(Rule rule) {
        ArrayList<Rule> rules = new ArrayList<>();
        boolean updated = false;
        for (Rule existing : getAll()) {
            if (existing.id.equals(rule.id)) {
                rules.add(rule);
                updated = true;
            } else {
                rules.add(existing);
            }
        }
        if (!updated) {
            rules.add(rule);
        }
        save(rules);
    }

    public void delete(String id) {
        ArrayList<Rule> rules = new ArrayList<>();
        for (Rule rule : getAll()) {
            if (!rule.id.equals(id)) {
                rules.add(rule);
            }
        }
        save(rules);
    }

    public void replaceAll(JSONArray array) {
        prefs.edit()
                .putString(KEY_RULES, array == null ? "[]" : array.toString())
                .putBoolean(KEY_LEGACY_MIGRATED, true)
                .apply();
    }

    public JSONArray toJsonArray() {
        JSONArray array = new JSONArray();
        for (Rule rule : getAll()) {
            array.put(rule.toJson());
        }
        return array;
    }

    public void ensureDefaultMinchaMaarivRule() {
        if (!getAll().isEmpty()) {
            return;
        }
        upsert(Rule.defaultMinchaMaariv());
    }

    private void save(List<Rule> rules) {
        JSONArray array = new JSONArray();
        for (Rule rule : rules) {
            array.put(rule.toJson());
        }
        prefs.edit().putString(KEY_RULES, array.toString()).apply();
    }

    private void migrateLegacyIfNeeded() {
        if (prefs.getBoolean(KEY_LEGACY_MIGRATED, false)) {
            return;
        }
        ReminderSettings settings = new ReminderSettings(context);
        SharedPreferences.Editor editor = prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true);
        if (settings.quietMinchaMaariv()) {
            JSONArray array = new JSONArray();
            array.put(Rule.defaultMinchaMaariv().toJson());
            editor.putString(KEY_RULES, array.toString());
        }
        editor.apply();
    }

    public static class Rule {
        public static final String MODE_FIXED = "fixed";
        public static final String MODE_ZMANIM = "zmanim";

        public final String id;
        public final String name;
        public final boolean enabled;
        public final String startMode;
        public final int startHour;
        public final int startMinute;
        public final String startZmanimKey;
        public final int startOffsetMinutes;
        public final String endMode;
        public final int endHour;
        public final int endMinute;
        public final String endZmanimKey;
        public final int endOffsetMinutes;

        public Rule(String id, String name, boolean enabled,
                    String startMode, int startHour, int startMinute, String startZmanimKey, int startOffsetMinutes,
                    String endMode, int endHour, int endMinute, String endZmanimKey, int endOffsetMinutes) {
            this.id = id == null || id.isEmpty() ? UUID.randomUUID().toString() : id;
            this.name = name == null || name.trim().isEmpty() ? "זמן שקט" : name.trim();
            this.enabled = enabled;
            this.startMode = MODE_ZMANIM.equals(startMode) ? MODE_ZMANIM : MODE_FIXED;
            this.startHour = clamp(startHour, 0, 23);
            this.startMinute = clamp(startMinute, 0, 59);
            this.startZmanimKey = startZmanimKey == null ? ZmanimHelper.KEY_SUNSET : startZmanimKey;
            this.startOffsetMinutes = clamp(startOffsetMinutes, -360, 360);
            this.endMode = MODE_ZMANIM.equals(endMode) ? MODE_ZMANIM : MODE_FIXED;
            this.endHour = clamp(endHour, 0, 23);
            this.endMinute = clamp(endMinute, 0, 59);
            this.endZmanimKey = endZmanimKey == null ? ZmanimHelper.KEY_TZAIS : endZmanimKey;
            this.endOffsetMinutes = clamp(endOffsetMinutes, -360, 360);
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id)
                        .put("name", name)
                        .put("enabled", enabled)
                        .put("startMode", startMode)
                        .put("startHour", startHour)
                        .put("startMinute", startMinute)
                        .put("startZmanimKey", startZmanimKey)
                        .put("startOffsetMinutes", startOffsetMinutes)
                        .put("endMode", endMode)
                        .put("endHour", endHour)
                        .put("endMinute", endMinute)
                        .put("endZmanimKey", endZmanimKey)
                        .put("endOffsetMinutes", endOffsetMinutes);
            } catch (JSONException ignored) {
            }
            return json;
        }

        public static Rule fromJson(JSONObject json) {
            return new Rule(
                    json.optString("id", UUID.randomUUID().toString()),
                    json.optString("name", "זמן שקט"),
                    json.optBoolean("enabled", true),
                    json.optString("startMode", MODE_FIXED),
                    json.optInt("startHour", 0),
                    json.optInt("startMinute", 0),
                    json.optString("startZmanimKey", ZmanimHelper.KEY_SUNSET),
                    json.optInt("startOffsetMinutes", 0),
                    json.optString("endMode", MODE_FIXED),
                    json.optInt("endHour", 0),
                    json.optInt("endMinute", 0),
                    json.optString("endZmanimKey", ZmanimHelper.KEY_TZAIS),
                    json.optInt("endOffsetMinutes", 0)
            );
        }

        public static Rule defaultMinchaMaariv() {
            return new Rule(
                    UUID.randomUUID().toString(),
                    "מנחה וערבית",
                    true,
                    MODE_ZMANIM,
                    0,
                    0,
                    ZmanimHelper.KEY_SUNSET,
                    -20,
                    MODE_ZMANIM,
                    0,
                    0,
                    ZmanimHelper.KEY_TZAIS,
                    10
            );
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
