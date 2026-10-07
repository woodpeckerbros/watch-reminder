package com.woodpeckerbros.watchreminder.smartalarm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class SmartAlarmZmanimTargetTest {
    private static final TimeZone JERUSALEM = TimeZone.getTimeZone("Asia/Jerusalem");

    @Test public void fortyFiveMinutesBeforeSunriseUsesEachDaysOwnSunrise() {
        long mondayEarly = local(2026, Calendar.OCTOBER, 12, 5, 0);
        long mondaySunrise = local(2026, Calendar.OCTOBER, 12, 6, 38);
        long tuesdaySunrise = local(2026, Calendar.OCTOBER, 13, 6, 39);
        int everyDay = SmartAlarmStore.ALL_DAYS_MASK;
        SmartAlarmZmanimTarget.ZmanTime sunrise = day -> {
            Calendar calendar = Calendar.getInstance(JERUSALEM);
            calendar.setTimeInMillis(day);
            return calendar.get(Calendar.DAY_OF_MONTH) == 12 ? mondaySunrise : tuesdaySunrise;
        };

        assertEquals(local(2026, Calendar.OCTOBER, 12, 5, 53),
                SmartAlarmZmanimTarget.next(everyDay, -45, mondayEarly, JERUSALEM, sunrise));
        assertEquals(local(2026, Calendar.OCTOBER, 13, 5, 54),
                SmartAlarmZmanimTarget.next(everyDay, -45,
                        local(2026, Calendar.OCTOBER, 12, 5, 53), JERUSALEM, sunrise));
    }

    @Test public void selectedDaysBelongToZmanDayAndUnavailableDaysAreSkipped() {
        long monday = local(2026, Calendar.OCTOBER, 12, 12, 0);
        int tuesdayOnly = 1 << Calendar.TUESDAY;
        assertEquals(local(2026, Calendar.OCTOBER, 13, 5, 15),
                SmartAlarmZmanimTarget.next(tuesdayOnly, -45, monday, JERUSALEM,
                        day -> localDateAt(day, 6, 0)));
        assertEquals(Long.MAX_VALUE, SmartAlarmZmanimTarget.next(tuesdayOnly, -45,
                monday, JERUSALEM, day -> Long.MAX_VALUE));
    }

    @Test public void offsetCanPlaceAlarmOnPreviousCivilDay() {
        long mondayEvening = local(2026, Calendar.OCTOBER, 12, 20, 0);
        int tuesdayOnly = 1 << Calendar.TUESDAY;
        assertEquals(local(2026, Calendar.OCTOBER, 12, 22, 30),
                SmartAlarmZmanimTarget.next(tuesdayOnly, -90, mondayEvening, JERUSALEM,
                        day -> localDateAt(day, 0, 0)));
    }

    private static long localDateAt(long day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(JERUSALEM);
        calendar.setTimeInMillis(day);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        return calendar.getTimeInMillis();
    }

    private static long local(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(JERUSALEM);
        calendar.clear();
        calendar.set(year, month, day, hour, minute);
        return calendar.getTimeInMillis();
    }
}
