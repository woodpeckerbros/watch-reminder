package com.woodpeckerbros.watchreminder;

import android.content.Context;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class QuietTimeHelper {
    private static final int MAX_ADJUST_PASSES = 8;

    private QuietTimeHelper() {
    }

    public static long adjust(Context context, long scheduledAt) {
        if (context == null || !new ReminderSettings(context).quietMinchaMaariv()) {
            return scheduledAt;
        }
        List<QuietTimeRuleStore.Rule> rules = new QuietTimeRuleStore(context).getAll();
        long adjusted = scheduledAt;
        for (int pass = 0; pass < MAX_ADJUST_PASSES; pass++) {
            long next = adjustOnce(context, rules, adjusted);
            if (next == adjusted) {
                return adjusted;
            }
            adjusted = next;
        }
        return adjusted;
    }

    public static long adjust(Context context, long scheduledAt, Reminder reminder) {
        if (reminder != null && reminder.critical) {
            return scheduledAt;
        }
        return adjust(context, scheduledAt);
    }

    private static long adjustOnce(Context context, List<QuietTimeRuleStore.Rule> rules, long scheduledAt) {
        long bestEnd = scheduledAt;
        for (QuietTimeRuleStore.Rule rule : rules) {
            if (!rule.enabled) {
                continue;
            }
            Window window = windowFor(context, rule, scheduledAt);
            if (window == null) {
                continue;
            }
            if (scheduledAt >= window.start && scheduledAt <= window.end && window.end > bestEnd) {
                bestEnd = window.end;
            }
        }
        return bestEnd == scheduledAt ? scheduledAt : ReminderScheduler.ceilToMinute(bestEnd);
    }

    private static Window windowFor(Context context, QuietTimeRuleStore.Rule rule, long scheduledAt) {
        Window today = windowForDay(context, rule, scheduledAt);
        if (today != null && scheduledAt >= today.start && scheduledAt <= today.end) {
            return today;
        }
        Window yesterday = windowForDay(context, rule, scheduledAt - 24 * 60 * 60_000L);
        if (yesterday != null && scheduledAt >= yesterday.start && scheduledAt <= yesterday.end) {
            return yesterday;
        }
        return today;
    }

    private static Window windowForDay(Context context, QuietTimeRuleStore.Rule rule, long dayMillis) {
        long start = boundaryFor(context, dayMillis, rule.startMode, rule.startHour, rule.startMinute, rule.startZmanimKey, rule.startOffsetMinutes);
        long end = boundaryFor(context, dayMillis, rule.endMode, rule.endHour, rule.endMinute, rule.endZmanimKey, rule.endOffsetMinutes);
        if (start == Long.MAX_VALUE || end == Long.MAX_VALUE) {
            return null;
        }
        if (end < start) {
            end += 24 * 60 * 60_000L;
        }
        return new Window(start, end);
    }

    private static long boundaryFor(Context context, long dayMillis, String mode, int hour, int minute, String zmanimKey, int offsetMinutes) {
        if (QuietTimeRuleStore.Rule.MODE_ZMANIM.equals(mode)) {
            long base = ZmanimHelper.timeForKey(context, zmanimKey, dayMillis);
            return base == Long.MAX_VALUE ? Long.MAX_VALUE : base + offsetMinutes * 60_000L;
        }
        TimeZone timeZone = TimeZone.getDefault();
        try {
            ZmanimSettings settings = new ZmanimSettings(context);
            timeZone = TimeZone.getTimeZone(settings.timeZoneId());
        } catch (Exception ignored) {
        }
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(dayMillis);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static class Window {
        final long start;
        final long end;

        Window(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }
}
