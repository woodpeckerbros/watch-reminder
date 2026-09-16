package com.woodpeckerbros.watchreminder.smartalarm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SmartAlarmReceiverTest {
    @Test public void deadlineHasUnconditionalFinalWakeReason() {
        assertEquals("WAKE_FINAL_DEADLINE", SmartAlarmReceiver.diagnosticWakeReason("deadline"));
    }

    @Test public void detectorReasonIsPreservedThroughDelivery() {
        assertEquals("WAKE_LIGHT_SLEEP_OPPORTUNITY",
                SmartAlarmReceiver.diagnosticWakeReason("WAKE_LIGHT_SLEEP_OPPORTUNITY"));
    }
}
