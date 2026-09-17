package com.woodpeckerbros.watchreminder.smartalarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SmartAlarmReceiverTest {
    @Test public void deadlineHasUnconditionalFinalWakeReason() {
        assertEquals("WAKE_FINAL_DEADLINE", SmartAlarmReceiver.diagnosticWakeReason("deadline"));
    }

    @Test public void detectorReasonIsPreservedThroughDelivery() {
        assertEquals("WAKE_LIGHT_SLEEP_OPPORTUNITY",
                SmartAlarmReceiver.diagnosticWakeReason("WAKE_LIGHT_SLEEP_OPPORTUNITY"));
    }

    @Test public void candidateOrInternalWakeBeforeDeliveryDoesNotCancelFinalDeadline() {
        assertFalse(SmartAlarmReceiver.shouldCancelFinalDeadline(
                "WAKE_LIGHT_SLEEP_OPPORTUNITY", false));
    }

    @Test public void durableEarlyAlertMayCancelFinalDeadline() {
        assertTrue(SmartAlarmReceiver.shouldCancelFinalDeadline(
                "WAKE_LIGHT_SLEEP_OPPORTUNITY", true));
    }

    @Test public void finalDeadlineDeliveryNeverCancelsItPrematurely() {
        assertFalse(SmartAlarmReceiver.shouldCancelFinalDeadline("deadline", true));
    }

    @Test public void wakeCheckEscalationDoesNotTouchNextOccurrenceDeadline() {
        assertFalse(SmartAlarmReceiver.shouldCancelFinalDeadline("wake_check_escalation", true));
    }
}
