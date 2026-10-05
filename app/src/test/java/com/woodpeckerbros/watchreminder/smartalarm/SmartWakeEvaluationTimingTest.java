package com.woodpeckerbros.watchreminder.smartalarm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SmartWakeEvaluationTimingTest {
    @Test public void onTimeCallbackHasNoLatenessOrSuspendGap() {
        SmartWakeEvaluationTiming timing = SmartWakeEvaluationTiming.of(5_000L, 5_000L,
                5_000L, 5_000L);
        assertEquals(0L, timing.lateByMs);
        assertEquals(0L, timing.uptimeLateByMs);
        assertEquals(0L, timing.suspendGapMs);
    }

    @Test public void deepSleepDelayIsSeparatedFromLooperDelay() {
        SmartWakeEvaluationTiming timing = SmartWakeEvaluationTiming.of(5_000L, 5_000L,
                17_000L, 6_000L);
        assertEquals(12_000L, timing.lateByMs);
        assertEquals(1_000L, timing.uptimeLateByMs);
        assertEquals(11_000L, timing.suspendGapMs);
    }

    @Test public void blockedMainLooperHasLatenessWithoutSuspendGap() {
        SmartWakeEvaluationTiming timing = SmartWakeEvaluationTiming.of(5_000L, 5_000L,
                12_000L, 12_000L);
        assertEquals(7_000L, timing.lateByMs);
        assertEquals(7_000L, timing.uptimeLateByMs);
        assertEquals(0L, timing.suspendGapMs);
    }

    @Test public void sessionLatenessAverageAndMaximumAreDeterministic() {
        SmartWakeEvaluationTiming.LatenessStats normal =
                new SmartWakeEvaluationTiming.LatenessStats();
        SmartWakeEvaluationTiming.LatenessStats candidate =
                new SmartWakeEvaluationTiming.LatenessStats();
        assertEquals(-1L, normal.average());
        normal.add(0L);
        normal.add(4_000L);
        candidate.add(12_000L);
        assertEquals(2_000L, normal.average());
        assertEquals(4_000L, normal.maxMs);
        assertEquals(12_000L, candidate.average());
        assertEquals(12_000L, candidate.maxMs);
    }
}
