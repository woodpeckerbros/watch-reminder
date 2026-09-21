package com.woodpeckerbros.watchreminder;

import com.woodpeckerbros.watchreminder.reminder.*;
import com.woodpeckerbros.watchreminder.zmanim.*;

import com.kosherjava.zmanim.util.GeoLocation;
import com.kosherjava.zmanim.ComplexZmanimCalendar;

import org.junit.Test;

import java.util.TimeZone;
import java.util.Calendar;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ZmanimSafetyTest {
    @Test
    public void negativeOrNonFiniteElevationFallsBackToSeaLevel() {
        assertEquals(0.0, ZmanimSettings.sanitizeElevation(-12.4), 0.0);
        assertEquals(0.0, ZmanimSettings.sanitizeElevation(Double.NaN), 0.0);
        assertEquals(0.0, ZmanimSettings.sanitizeElevation(Double.POSITIVE_INFINITY), 0.0);
        assertEquals(42.5, ZmanimSettings.sanitizeElevation(42.5), 0.0);
    }

    @Test
    public void sanitizedNegativeGpsElevationIsAcceptedByZmanimLibrary() {
        GeoLocation location = new GeoLocation(
                "test",
                31.77,
                35.21,
                ZmanimSettings.sanitizeElevation(-18.0),
                TimeZone.getTimeZone("Asia/Jerusalem")
        );
        assertEquals(0.0, location.getElevation(), 0.0);
    }

    @Test
    public void invalidCoordinatesFallBackToKnownSafeLocation() {
        assertEquals(ZmanimSettings.DEFAULT_LATITUDE, ZmanimSettings.sanitizeLatitude(91), 0.0);
        assertEquals(ZmanimSettings.DEFAULT_LONGITUDE, ZmanimSettings.sanitizeLongitude(Double.NaN), 0.0);
        assertEquals(31.77, ZmanimSettings.sanitizeLatitude(31.77), 0.0);
        assertEquals(35.21, ZmanimSettings.sanitizeLongitude(35.21), 0.0);
    }

    @Test
    public void missingSnoozeAndMissingZmanAreNotReportedAsSnoozed() {
        assertFalse(NextReminderCalculator.isRealSnoozeCandidate(Long.MAX_VALUE, Long.MAX_VALUE));
        assertFalse(NextReminderCalculator.isRealSnoozeCandidate(Long.MAX_VALUE, 1_000));
        assertTrue(NextReminderCalculator.isRealSnoozeCandidate(900, 1_000));
        assertTrue(NextReminderCalculator.isRealSnoozeCandidate(1_000, Long.MAX_VALUE));
    }

    @Test
    public void ohrHachaimShabbatTimesMatchTelAvivReference() {
        int[][] dates = {
                {Calendar.AUGUST, 15},
                {Calendar.AUGUST, 22},
                {Calendar.AUGUST, 29},
                {Calendar.SEPTEMBER, 5}
        };
        String[] shabbatEnds = {"19:56", "19:49", "19:40", "19:32"};
        String[] rabbeinuTam = {"20:47", "20:38", "20:28", "20:18"};

        for (int i = 0; i < dates.length; i++) {
            ComplexZmanimCalendar calendar = telAvivCalendar(2026, dates[i][0], dates[i][1]);
            assertTime(shabbatEnds[i], ZmanimHelper.ohrHachaimZman(calendar, ZmanimHelper.KEY_SHABBAT_END));
            assertTime(rabbeinuTam[i], ZmanimHelper.ohrHachaimZman(calendar, ZmanimHelper.KEY_RABBEINU_TAM));
        }
    }

    @Test
    public void ohrHachaimNightfallUsesThirteenAndHalfSeasonalMinutes() {
        ComplexZmanimCalendar calendar = telAvivCalendar(2026, Calendar.AUGUST, 15);
        Date sunrise = calendar.getSunrise();
        Date sunset = calendar.getSunset();
        long expected = sunset.getTime() + Math.round((sunset.getTime() - sunrise.getTime()) * 13.5 / 720.0);
        expected = ReminderScheduler.ceilToMinute(expected);

        assertEquals(expected, ZmanimHelper.ohrHachaimZman(calendar, ZmanimHelper.KEY_TZAIS).getTime());
    }

    @Test
    public void candleLightingIsTwentyMinutesBeforeElevationAdjustedSunset() {
        ComplexZmanimCalendar calendar = telAvivCalendar(2026, Calendar.AUGUST, 14);
        long expected = ReminderScheduler.ceilToMinute(calendar.getSunset().getTime() - 20 * 60_000L);

        assertEquals(expected, ZmanimHelper.ohrHachaimZman(calendar, ZmanimHelper.KEY_CANDLE_LIGHTING).getTime());
    }

    @Test
    public void yomKippur5787MatchesOhrHachaimTelAvivReferenceWithinLocationRounding() {
        ComplexZmanimCalendar entry = telAvivCalendarAtSeaLevel(2026, Calendar.SEPTEMBER, 20);
        ComplexZmanimCalendar exit = telAvivCalendarAtSeaLevel(2026, Calendar.SEPTEMBER, 21);

        assertTimeWithinMinutes("18:21", ZmanimHelper.ohrHachaimZman(entry, ZmanimHelper.KEY_CANDLE_LIGHTING), 1);
        assertTimeWithinMinutes("19:11", ZmanimHelper.ohrHachaimZman(exit, ZmanimHelper.KEY_SHABBAT_END), 1);
        assertTimeWithinMinutes("19:54", ZmanimHelper.ohrHachaimZman(exit, ZmanimHelper.KEY_RABBEINU_TAM), 1);
    }

    private ComplexZmanimCalendar telAvivCalendarAtSeaLevel(int year, int month, int day) {
        TimeZone timeZone = TimeZone.getTimeZone("Asia/Jerusalem");
        GeoLocation location = new GeoLocation("תל אביב", 32.0853, 34.7818, 0, timeZone);
        ComplexZmanimCalendar calendar = new ComplexZmanimCalendar(location);
        calendar.getCalendar().setTimeZone(timeZone);
        calendar.getCalendar().set(year, month, day);
        return calendar;
    }

    private ComplexZmanimCalendar telAvivCalendar(int year, int month, int day) {
        TimeZone timeZone = TimeZone.getTimeZone("Asia/Jerusalem");
        GeoLocation location = new GeoLocation("תל אביב", 32.0853, 34.7818, 30, timeZone);
        ComplexZmanimCalendar calendar = new ComplexZmanimCalendar(location);
        calendar.getCalendar().setTimeZone(timeZone);
        calendar.getCalendar().set(year, month, day);
        return calendar;
    }

    private void assertTime(String expected, Date actual) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jerusalem"));
        calendar.setTime(actual);
        assertEquals(expected, String.format(java.util.Locale.US, "%02d:%02d",
                calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE)));
    }

    private void assertTimeWithinMinutes(String expected, Date actual, int toleranceMinutes) {
        String[] parts = expected.split(":");
        int expectedMinutes = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jerusalem"));
        calendar.setTime(actual);
        int actualMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        assertTrue("expected " + expected + " within " + toleranceMinutes + " minute(s), but was "
                        + String.format(java.util.Locale.US, "%02d:%02d",
                        calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE)),
                Math.abs(expectedMinutes - actualMinutes) <= toleranceMinutes);
    }
}
