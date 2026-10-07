package com.woodpeckerbros.watchreminder.calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class TekufaHelperTest {
    private static final TimeZone ISRAEL = TimeZone.getTimeZone("Asia/Jerusalem");

    @Test public void returnsTekufaOnlyForTheCivilDayOfItsActualTime() {
        TekufaHelper.Event event = TekufaHelper.onCivilDay(day(2026, Calendar.OCTOBER, 7), ISRAEL);

        assertEquals(2, event.seasonIndex());
        assertTime(15, 39, event.localMeanAt());
        assertTime(16, 0, event.officialAt());
        assertNull(TekufaHelper.onCivilDay(day(2026, Calendar.OCTOBER, 6), ISRAEL));
        assertNull(TekufaHelper.onCivilDay(day(2026, Calendar.OCTOBER, 8), ISRAEL));
    }

    private static long day(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(ISRAEL);
        calendar.clear();
        calendar.set(year, month, day, 12, 0, 0);
        return calendar.getTimeInMillis();
    }

    private static void assertTime(int hour, int minute, long at) {
        Calendar calendar = Calendar.getInstance(ISRAEL);
        calendar.setTimeInMillis(at);
        assertEquals(hour, calendar.get(Calendar.HOUR_OF_DAY));
        assertEquals(minute, calendar.get(Calendar.MINUTE));
    }
}
