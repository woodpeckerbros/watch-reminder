package com.woodpeckerbros.watchreminder.zmanim;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class ZmanimUpcomingTimeTest {
    @Test public void elapsedTimeIsNeverChosenEvenIfItIsCloser() {
        ZmanimUpcomingTime.Event next = ZmanimUpcomingTime.nextAfter(101L, Arrays.asList(
                new ZmanimUpcomingTime.Event("passed", 100L, 0L),
                new ZmanimUpcomingTime.Event("future", 300L, 0L)));
        assertEquals("future", next.key);
    }

    @Test public void exactBoundaryAdvancesAndCanChooseTomorrow() {
        ZmanimUpcomingTime.Event next = ZmanimUpcomingTime.nextAfter(900L, Arrays.asList(
                new ZmanimUpcomingTime.Event("last-today", 900L, 0L),
                new ZmanimUpcomingTime.Event("first-tomorrow", 1_200L, 1L)));
        assertEquals("first-tomorrow", next.key);
        assertEquals(1L, next.calculationDay);
    }

    @Test public void priorCalculationDayMidnightMayBeNextToday() {
        ZmanimUpcomingTime.Event next = ZmanimUpcomingTime.nextAfter(1_010L, Arrays.asList(
                new ZmanimUpcomingTime.Event("previous-night-midnight", 1_100L, 0L),
                new ZmanimUpcomingTime.Event("dawn", 1_500L, 1L)));
        assertEquals("previous-night-midnight", next.key);
    }
}
