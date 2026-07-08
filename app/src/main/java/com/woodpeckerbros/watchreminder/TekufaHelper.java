package com.woodpeckerbros.watchreminder;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class TekufaHelper {
    private static final TimeZone ISRAEL_TIME_ZONE = TimeZone.getTimeZone("Asia/Jerusalem");
    private static final long HALF_HOUR_MILLIS = 30 * 60_000L;
    private static final long JERUSALEM_LOCAL_MEAN_OFFSET_MILLIS = 21 * 60_000L;
    private static final int ANCHOR_YEAR = 2026;
    private static final int ANCHOR_MONTH = Calendar.JULY;
    private static final int ANCHOR_DAY = 8;
    private static final int ANCHOR_HOUR = 8;
    private static final int ANCHOR_MINUTE = 30;
    private static final int ANCHOR_SEASON_INDEX = 1;

    private TekufaHelper() {
    }

    public static Event next(long now) {
        Event best = null;
        for (Event event : upcoming(now)) {
            if (event.windowEndAt > now && (best == null || event.windowStartAt < best.windowStartAt)) {
                best = event;
            }
        }
        return best;
    }

    public static List<Event> upcoming(long now) {
        List<Event> events = new ArrayList<>();
        for (int offset = -24; offset <= 80; offset++) {
            Event event = eventAtOffset(offset);
            if (event.windowEndAt > now) {
                events.add(event);
            }
        }
        return events;
    }

    public static String summary(Context context, long now) {
        Event event = next(now);
        if (event == null) {
            return UiText.t(context, "לא נמצאה תקופה קרובה");
        }
        return name(context, event.seasonIndex)
                + ": "
                + NextReminderCalculator.formatDateTime(event.windowStartAt)
                + "-"
                + NextReminderCalculator.formatTime(event.windowEndAt)
                + " | "
                + UiText.t(context, "זמן התקופה")
                + " "
                + NextReminderCalculator.formatTime(event.localMeanAt)
                + " / "
                + NextReminderCalculator.formatTime(event.officialAt);
    }

    public static String name(Context context, int seasonIndex) {
        switch (seasonIndex) {
            case 0:
                return UiText.t(context, "תקופת ניסן");
            case 1:
                return UiText.t(context, "תקופת תמוז");
            case 2:
                return UiText.t(context, "תקופת תשרי");
            case 3:
                return UiText.t(context, "תקופת טבת");
            default:
                return UiText.t(context, "זמן התקופה");
        }
    }

    private static Event eventAtOffset(int offset) {
        Calendar official = anchor();
        int step = offset;
        int direction = step < 0 ? -1 : 1;
        for (int i = 0; i < Math.abs(step); i++) {
            official.add(Calendar.DAY_OF_YEAR, direction * 91);
            official.add(Calendar.HOUR_OF_DAY, direction * 7);
            official.add(Calendar.MINUTE, direction * 30);
        }
        long officialAt = ReminderScheduler.floorToMinute(official.getTimeInMillis());
        long localMeanAt = ReminderScheduler.floorToMinute(officialAt - JERUSALEM_LOCAL_MEAN_OFFSET_MILLIS);
        long lower = Math.min(officialAt, localMeanAt);
        long upper = Math.max(officialAt, localMeanAt);
        int seasonIndex = Math.floorMod(ANCHOR_SEASON_INDEX + offset, 4);
        return new Event(
                seasonIndex,
                localMeanAt,
                officialAt,
                ReminderScheduler.floorToMinute(lower - HALF_HOUR_MILLIS),
                ReminderScheduler.floorToMinute(upper + HALF_HOUR_MILLIS)
        );
    }

    private static Calendar anchor() {
        Calendar calendar = Calendar.getInstance(ISRAEL_TIME_ZONE);
        calendar.set(Calendar.YEAR, ANCHOR_YEAR);
        calendar.set(Calendar.MONTH, ANCHOR_MONTH);
        calendar.set(Calendar.DAY_OF_MONTH, ANCHOR_DAY);
        calendar.set(Calendar.HOUR_OF_DAY, ANCHOR_HOUR);
        calendar.set(Calendar.MINUTE, ANCHOR_MINUTE);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    static class Event {
        final int seasonIndex;
        final long localMeanAt;
        final long officialAt;
        final long windowStartAt;
        final long windowEndAt;

        Event(int seasonIndex, long localMeanAt, long officialAt, long windowStartAt, long windowEndAt) {
            this.seasonIndex = seasonIndex;
            this.localMeanAt = localMeanAt;
            this.officialAt = officialAt;
            this.windowStartAt = windowStartAt;
            this.windowEndAt = windowEndAt;
        }
    }
}
