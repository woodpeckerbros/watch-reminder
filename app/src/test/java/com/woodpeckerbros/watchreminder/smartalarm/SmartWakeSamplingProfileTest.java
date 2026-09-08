package com.woodpeckerbros.watchreminder.smartalarm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SmartWakeSamplingProfileTest {
    @Test public void startupBufferHasSafeDefaultAndAdaptsToSparseHeartRate() {
        assertEquals(5 * 60_000L, SmartWakeSamplingProfile.startBufferMs(0));
        assertEquals(5 * 60_000L, SmartWakeSamplingProfile.startBufferMs(2 * 60_000L));
        assertEquals(10 * 60_000L, SmartWakeSamplingProfile.startBufferMs(5 * 60_000L));
        assertEquals(15 * 60_000L, SmartWakeSamplingProfile.startBufferMs(20 * 60_000L));
    }
}
