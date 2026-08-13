package com.woodpeckerbros.watchreminder;

import com.kosherjava.zmanim.util.GeoLocation;

import org.junit.Test;

import java.util.TimeZone;

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
}
