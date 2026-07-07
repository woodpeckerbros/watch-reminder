package com.woodpeckerbros.watchreminder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Reminder {
    public static final String PERIOD_UNIT_HOURS = "hours";
    public static final String PERIOD_UNIT_DAYS = "days";
    public static final String PERIOD_UNIT_WEEKS = "weeks";
    public static final String PERIOD_UNIT_MONTHS = "months";
    public static final String PERIOD_UNIT_YEARS = "years";

    public final String id;
    public final String name;
    public final String description;
    public final int hour;
    public final int minute;
    public final Set<Integer> days;
    public final boolean enabled;
    public final long oneTimeAt;
    public final boolean useZmanim;
    public final String zmanimKey;
    public final int zmanimOffsetMinutes;
    public final boolean critical;
    public final boolean periodic;
    public final boolean periodicHebrew;
    public final int periodicDayOfWeek;
    public final int periodicInterval;
    public final String periodicUnit;
    public final int periodicStartYear;
    public final int periodicStartMonth;
    public final int periodicStartDay;
    public final int periodicEndHour;
    public final int periodicEndMinute;
    public final boolean annualEvent;
    public final boolean annualHebrew;
    public final int annualMonth;
    public final int annualDay;
    public final int annualAdvanceHours;
    public final int annualCounter;
    public final int annualCounterYear;

    public Reminder(String id, String name, int hour, int minute, Set<Integer> days) {
        this(id, name, hour, minute, days, true, 0, false, ZmanimHelper.KEY_CHATZOS, 0, false);
    }

    public Reminder(String id, String name, int hour, int minute, Set<Integer> days, boolean enabled, long oneTimeAt) {
        this(id, name, hour, minute, days, enabled, oneTimeAt, false, ZmanimHelper.KEY_CHATZOS, 0, false);
    }

    public Reminder(String id, String name, int hour, int minute, Set<Integer> days, boolean enabled, long oneTimeAt,
                    boolean useZmanim, String zmanimKey, int zmanimOffsetMinutes) {
        this(id, name, hour, minute, days, enabled, oneTimeAt, useZmanim, zmanimKey, zmanimOffsetMinutes, false);
    }

    public Reminder(String id, String name, int hour, int minute, Set<Integer> days, boolean enabled, long oneTimeAt,
                    boolean useZmanim, String zmanimKey, int zmanimOffsetMinutes, boolean critical) {
        this(id, name, hour, minute, days, enabled, oneTimeAt, useZmanim, zmanimKey, zmanimOffsetMinutes, critical,
                false, false, 0, 1, PERIOD_UNIT_DAYS, 0, 0, 0,
                23, 59, false, false, 0, 0, 0, 1, 0);
    }

    public Reminder(String id, String name, int hour, int minute, Set<Integer> days, boolean enabled, long oneTimeAt,
                    boolean useZmanim, String zmanimKey, int zmanimOffsetMinutes, boolean critical,
                    boolean periodic, boolean periodicHebrew, int periodicDayOfWeek, int periodicInterval,
                    String periodicUnit, int periodicStartYear, int periodicStartMonth, int periodicStartDay) {
        this(id, name, hour, minute, days, enabled, oneTimeAt, useZmanim, zmanimKey, zmanimOffsetMinutes, critical,
                periodic, periodicHebrew, periodicDayOfWeek, periodicInterval, periodicUnit, periodicStartYear, periodicStartMonth, periodicStartDay,
                23, 59, false, false, 0, 0, 0, 1, 0);
    }

    public Reminder(String id, String name, int hour, int minute, Set<Integer> days, boolean enabled, long oneTimeAt,
                    boolean useZmanim, String zmanimKey, int zmanimOffsetMinutes, boolean critical,
                    boolean periodic, boolean periodicHebrew, int periodicDayOfWeek, int periodicInterval,
                    String periodicUnit, int periodicStartYear, int periodicStartMonth, int periodicStartDay,
                    boolean annualEvent, boolean annualHebrew, int annualMonth, int annualDay, int annualAdvanceHours,
                    int annualCounter, int annualCounterYear) {
        this(id, name, hour, minute, days, enabled, oneTimeAt, useZmanim, zmanimKey, zmanimOffsetMinutes, critical,
                periodic, periodicHebrew, periodicDayOfWeek, periodicInterval, periodicUnit, periodicStartYear, periodicStartMonth, periodicStartDay,
                23, 59, annualEvent, annualHebrew, annualMonth, annualDay, annualAdvanceHours, annualCounter, annualCounterYear, "");
    }

    public Reminder(String id, String name, int hour, int minute, Set<Integer> days, boolean enabled, long oneTimeAt,
                    boolean useZmanim, String zmanimKey, int zmanimOffsetMinutes, boolean critical,
                    boolean periodic, boolean periodicHebrew, int periodicDayOfWeek, int periodicInterval,
                    String periodicUnit, int periodicStartYear, int periodicStartMonth, int periodicStartDay,
                    int periodicEndHour, int periodicEndMinute,
                    boolean annualEvent, boolean annualHebrew, int annualMonth, int annualDay, int annualAdvanceHours,
                    int annualCounter, int annualCounterYear) {
        this(id, name, hour, minute, days, enabled, oneTimeAt, useZmanim, zmanimKey, zmanimOffsetMinutes, critical,
                periodic, periodicHebrew, periodicDayOfWeek, periodicInterval, periodicUnit, periodicStartYear, periodicStartMonth, periodicStartDay,
                periodicEndHour, periodicEndMinute, annualEvent, annualHebrew, annualMonth, annualDay, annualAdvanceHours, annualCounter, annualCounterYear, "");
    }

    public Reminder(String id, String name, int hour, int minute, Set<Integer> days, boolean enabled, long oneTimeAt,
                    boolean useZmanim, String zmanimKey, int zmanimOffsetMinutes, boolean critical,
                    boolean periodic, boolean periodicHebrew, int periodicDayOfWeek, int periodicInterval,
                    String periodicUnit, int periodicStartYear, int periodicStartMonth, int periodicStartDay,
                    int periodicEndHour, int periodicEndMinute,
                    boolean annualEvent, boolean annualHebrew, int annualMonth, int annualDay, int annualAdvanceHours,
                    int annualCounter, int annualCounterYear, String description) {
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.name = name == null ? "תזכורת" : name;
        this.description = description == null ? "" : description.trim();
        this.hour = hour;
        this.minute = minute;
        this.days = days;
        this.enabled = enabled;
        this.oneTimeAt = oneTimeAt;
        this.useZmanim = useZmanim;
        this.zmanimKey = zmanimKey == null || zmanimKey.trim().isEmpty() ? ZmanimHelper.KEY_CHATZOS : zmanimKey;
        this.zmanimOffsetMinutes = zmanimOffsetMinutes;
        this.critical = critical;
        this.periodic = periodic;
        this.periodicHebrew = periodicHebrew;
        this.periodicDayOfWeek = periodicDayOfWeek == 0 ? java.util.Calendar.SUNDAY : periodicDayOfWeek;
        this.periodicInterval = Math.max(1, periodicInterval);
        this.periodicUnit = normalizePeriodicUnit(periodicUnit);
        this.periodicStartYear = periodicStartYear;
        this.periodicStartMonth = periodicStartMonth;
        this.periodicStartDay = periodicStartDay;
        this.periodicEndHour = Math.max(0, Math.min(23, periodicEndHour));
        this.periodicEndMinute = Math.max(0, Math.min(59, periodicEndMinute));
        this.annualEvent = annualEvent;
        this.annualHebrew = annualHebrew;
        this.annualMonth = annualMonth;
        this.annualDay = annualDay;
        this.annualAdvanceHours = Math.max(0, annualAdvanceHours);
        this.annualCounter = Math.max(0, Math.min(1000, annualCounter));
        this.annualCounterYear = annualCounterYear;
    }

    public boolean isOneTime() {
        return oneTimeAt > 0 && !periodic && !annualEvent;
    }

    public boolean isPeriodic() {
        return periodic;
    }

    public boolean isAnnualEvent() {
        return annualEvent;
    }

    public JSONObject toJson() throws JSONException {
        ArrayList<Integer> sortedDays = new ArrayList<>(days);
        Collections.sort(sortedDays);
        JSONArray dayArray = new JSONArray();
        for (Integer day : sortedDays) {
            dayArray.put(day);
        }
        return new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("description", description)
                .put("hour", hour)
                .put("minute", minute)
                .put("days", dayArray)
                .put("enabled", enabled)
                .put("oneTimeAt", oneTimeAt)
                .put("useZmanim", useZmanim)
                .put("zmanimKey", zmanimKey)
                .put("zmanimOffsetMinutes", zmanimOffsetMinutes)
                .put("critical", critical)
                .put("periodic", periodic)
                .put("periodicHebrew", periodicHebrew)
                .put("periodicDayOfWeek", periodicDayOfWeek)
                .put("periodicInterval", periodicInterval)
                .put("periodicUnit", periodicUnit)
                .put("periodicStartYear", periodicStartYear)
                .put("periodicStartMonth", periodicStartMonth)
                .put("periodicStartDay", periodicStartDay)
                .put("periodicEndHour", periodicEndHour)
                .put("periodicEndMinute", periodicEndMinute)
                .put("annualEvent", annualEvent)
                .put("annualHebrew", annualHebrew)
                .put("annualMonth", annualMonth)
                .put("annualDay", annualDay)
                .put("annualAdvanceHours", annualAdvanceHours)
                .put("annualCounter", annualCounter)
                .put("annualCounterYear", annualCounterYear);
    }

    public static Reminder fromJson(JSONObject json) throws JSONException {
        JSONArray dayArray = json.optJSONArray("days");
        Set<Integer> days = new HashSet<>();
        if (dayArray != null) {
            for (int i = 0; i < dayArray.length(); i++) {
                days.add(dayArray.getInt(i));
            }
        }
        return new Reminder(
                json.getString("id"),
                json.getString("name"),
                json.getInt("hour"),
                json.getInt("minute"),
                days,
                json.optBoolean("enabled", true),
                json.optLong("oneTimeAt", 0),
                json.optBoolean("useZmanim", false),
                json.optString("zmanimKey", ZmanimHelper.KEY_CHATZOS),
                json.optInt("zmanimOffsetMinutes", 0),
                json.optBoolean("critical", false),
                json.optBoolean("periodic", false),
                json.optBoolean("periodicHebrew", false),
                json.optInt("periodicDayOfWeek", java.util.Calendar.SUNDAY),
                json.optInt("periodicInterval", 1),
                json.optString("periodicUnit", PERIOD_UNIT_DAYS),
                json.optInt("periodicStartYear", 0),
                json.optInt("periodicStartMonth", 0),
                json.optInt("periodicStartDay", 0),
                json.optInt("periodicEndHour", 23),
                json.optInt("periodicEndMinute", 59),
                json.optBoolean("annualEvent", false),
                json.optBoolean("annualHebrew", false),
                json.optInt("annualMonth", 0),
                json.optInt("annualDay", 0),
                json.optInt("annualAdvanceHours", 0),
                json.has("annualCounter") ? json.optInt("annualCounter", 1) : 1,
                json.optInt("annualCounterYear", 0),
                json.optString("description", "")
        );
    }

    private static String normalizePeriodicUnit(String unit) {
        if (PERIOD_UNIT_HOURS.equals(unit)
                || PERIOD_UNIT_DAYS.equals(unit)
                || PERIOD_UNIT_WEEKS.equals(unit)
                || PERIOD_UNIT_MONTHS.equals(unit)
                || PERIOD_UNIT_YEARS.equals(unit)) {
            return unit;
        }
        return PERIOD_UNIT_DAYS;
    }
}
