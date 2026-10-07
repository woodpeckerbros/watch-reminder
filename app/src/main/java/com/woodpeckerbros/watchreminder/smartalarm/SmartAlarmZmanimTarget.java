package com.woodpeckerbros.watchreminder.smartalarm;

import java.util.Calendar;
import java.util.TimeZone;

/** Selects the next occurrence by the chosen zman's local calendar day. */
final class SmartAlarmZmanimTarget {
    interface ZmanTime {
        long at(long dayMillis);
    }

    private SmartAlarmZmanimTarget() {}

    static long next(int daysMask, int offsetMinutes, long now, TimeZone zone, ZmanTime zmanTime) {
        if (daysMask == 0) return Long.MAX_VALUE;
        Calendar day = Calendar.getInstance(zone);
        day.setTimeInMillis(now);
        day.set(Calendar.HOUR_OF_DAY, 12);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        for (int index = 0; index <= 7; index++) {
            if (index > 0) day.add(Calendar.DAY_OF_YEAR, 1);
            if ((daysMask & (1 << day.get(Calendar.DAY_OF_WEEK))) == 0) continue;
            long zmanAt = zmanTime.at(day.getTimeInMillis());
            if (zmanAt == Long.MAX_VALUE) continue;
            long targetAt = zmanAt + offsetMinutes * 60_000L;
            if (targetAt > now) return targetAt;
        }
        return Long.MAX_VALUE;
    }
}
