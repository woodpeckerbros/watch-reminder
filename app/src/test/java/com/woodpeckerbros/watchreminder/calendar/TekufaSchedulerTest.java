package com.woodpeckerbros.watchreminder.calendar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class TekufaSchedulerTest {
    private static final TimeZone ISRAEL = TimeZone.getTimeZone("Asia/Jerusalem");

    @Test public void schedulesEndNoticeAfterTheTekufaWindowStarts() {
        TekufaHelper.Event event = TekufaHelper.onCivilDay(day(2026, Calendar.OCTOBER, 7), ISRAEL);

        TekufaScheduler.ScheduledEvent scheduled = TekufaScheduler.nextEvent(event.windowStartAt);

        assertEquals(TekufaScheduler.KIND_END, scheduled.kind);
        assertEquals(event.windowEndAt, scheduled.triggerAt);
        assertEquals(event.windowEndAt, scheduled.tekufa.windowEndAt);
    }

    private static long day(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(ISRAEL);
        calendar.clear();
        calendar.set(year, month, day, 12, 0, 0);
        return calendar.getTimeInMillis();
    }
}
