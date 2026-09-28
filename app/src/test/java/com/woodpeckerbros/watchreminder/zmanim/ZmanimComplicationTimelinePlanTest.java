package com.woodpeckerbros.watchreminder.zmanim;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ZmanimComplicationTimelinePlanTest {
    @Test public void nearestZmanChangesOnlyAtMidpoints() {
        List<ZmanimComplicationTimelinePlan.Window> windows =
                ZmanimComplicationTimelinePlan.forInterval(0L, 1_000L, Arrays.asList(
                        new ZmanimComplicationTimelinePlan.Event("sunrise", 100L),
                        new ZmanimComplicationTimelinePlan.Event("chatzos", 300L),
                        new ZmanimComplicationTimelinePlan.Event("sunset", 900L)
                ));

        assertEquals(3, windows.size());
        assertWindow(windows.get(0), "sunrise", 0L, 200L);
        assertWindow(windows.get(1), "chatzos", 200L, 600L);
        assertWindow(windows.get(2), "sunset", 600L, 1_000L);
    }

    @Test public void duplicateMinuteDoesNotCreateAnEmptyTimelineEntry() {
        List<ZmanimComplicationTimelinePlan.Window> windows =
                ZmanimComplicationTimelinePlan.forInterval(0L, 500L, Arrays.asList(
                        new ZmanimComplicationTimelinePlan.Event("first", 100L),
                        new ZmanimComplicationTimelinePlan.Event("same-minute", 100L),
                        new ZmanimComplicationTimelinePlan.Event("next", 300L)
                ));

        assertEquals(2, windows.size());
        assertWindow(windows.get(0), "first", 0L, 200L);
        assertWindow(windows.get(1), "next", 200L, 500L);
    }

    @Test public void eventFromPreviousCalculationDayCanRemainNearestAfterMidnight() {
        List<ZmanimComplicationTimelinePlan.Window> windows =
                ZmanimComplicationTimelinePlan.forInterval(1_000L, 2_000L, Arrays.asList(
                        new ZmanimComplicationTimelinePlan.Event("previous-day-chatzos", 1_100L),
                        new ZmanimComplicationTimelinePlan.Event("alos", 1_500L)
                ));

        assertEquals(2, windows.size());
        assertWindow(windows.get(0), "previous-day-chatzos", 1_000L, 1_300L);
        assertWindow(windows.get(1), "alos", 1_300L, 2_000L);
    }

    private static void assertWindow(ZmanimComplicationTimelinePlan.Window window,
                                     String key, long start, long end) {
        assertEquals(key, window.event.key);
        assertEquals(start, window.startAt);
        assertEquals(end, window.endAt);
    }
}
