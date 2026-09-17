package com.woodpeckerbros.watchreminder.reminder;

import static org.junit.Assert.*;

import org.junit.Test;

public class ReminderMonitoringPolicyTest {
    private static final long NORMAL = 1_000_000L;
    private static final long SMART = 2_000_000L;

    @Test public void futureNormalReminderRequiresProtection() {
        ReminderMonitoringPolicy.Requirement result = ReminderMonitoringPolicy.decide(NORMAL, 0, false, false);
        assertTrue(result.required);
        assertEquals(ReminderMonitoringPolicy.Reason.NORMAL_REMINDER, result.reason);
        assertEquals(NORMAL, result.nextDeliveryAt);
    }

    @Test public void futureSmartAlarmRequiresProtectionEvenBeforeMonitoringStarts() {
        ReminderMonitoringPolicy.Requirement result = ReminderMonitoringPolicy.decide(0, SMART, false, false);
        assertTrue(result.required);
        assertEquals(ReminderMonitoringPolicy.Reason.SMART_ALARM, result.reason);
        assertEquals(SMART, result.nextDeliveryAt);
    }

    @Test public void completedOccurrenceWithTomorrowStillArmedKeepsProtection() {
        assertTrue(ReminderMonitoringPolicy.decide(0, SMART, false, false).required);
    }

    @Test public void dismissingNormalDoesNotStopProtectionForSmartAlarm() {
        assertTrue(ReminderMonitoringPolicy.decide(0, SMART, false, false).required);
    }

    @Test public void activeWakeAndWakeCheckAreDeliveryObligations() {
        assertEquals(ReminderMonitoringPolicy.Reason.SMART_WAKE,
                ReminderMonitoringPolicy.decide(0, 0, true, false).reason);
        assertEquals(ReminderMonitoringPolicy.Reason.WAKE_CHECK,
                ReminderMonitoringPolicy.decide(0, 0, false, true).reason);
    }

    @Test public void noDeliveryObligationAllowsCleanStop() {
        ReminderMonitoringPolicy.Requirement result = ReminderMonitoringPolicy.decide(0, 0, false, false);
        assertFalse(result.required);
        assertEquals(ReminderMonitoringPolicy.Reason.NONE, result.reason);
    }

    @Test public void multipleObligationsAreExplicitAndEarliestWins() {
        ReminderMonitoringPolicy.Requirement result = ReminderMonitoringPolicy.decide(NORMAL, SMART, true, false);
        assertTrue(result.required);
        assertEquals(ReminderMonitoringPolicy.Reason.MULTIPLE, result.reason);
        assertEquals(NORMAL, result.nextDeliveryAt);
    }

    @Test public void processOrPackageRecreationStillRequiresFutureSmartAlarmProtection() {
        // The policy is recomputed from persisted schedule state after recreation; it must not
        // depend on an in-memory SmartWakeMonitoringService instance being present.
        ReminderMonitoringPolicy.Requirement result = ReminderMonitoringPolicy.decide(
                Long.MAX_VALUE, SMART, false, false);
        assertTrue(result.required);
        assertEquals(ReminderMonitoringPolicy.Reason.SMART_ALARM, result.reason);
    }

    @Test public void smartAlarmHoursBeforeMonitoringStillDoesNotStartSensors() {
        ReminderMonitoringPolicy.Requirement result = ReminderMonitoringPolicy.decide(0, SMART, false, false);
        assertTrue(result.required);
        assertFalse(result.activeSmartWake);
    }

    @Test public void smartWakeEndingKeepsProtectionWhenFutureSmartAlarmRemains() {
        ReminderMonitoringPolicy.Requirement result = ReminderMonitoringPolicy.decide(0, SMART, false, false);
        assertTrue(result.required);
        assertTrue(result.futureSmartAlarm);
    }

    @Test public void activeSmartWakeCanProtectDuringATransientSession() {
        ReminderMonitoringPolicy.Requirement result = ReminderMonitoringPolicy.decide(0, 0, true, false);
        assertTrue(result.required);
        assertTrue(result.activeSmartWake);
    }

    @Test public void repeatedEnsureEvaluationIsIdempotent() {
        ReminderMonitoringPolicy.Requirement first = ReminderMonitoringPolicy.decide(0, SMART, false, false);
        ReminderMonitoringPolicy.Requirement second = ReminderMonitoringPolicy.decide(0, SMART, false, false);
        assertEquals(first.required, second.required);
        assertEquals(first.reason, second.reason);
        assertEquals(first.nextDeliveryAt, second.nextDeliveryAt);
    }

    @Test public void disabledSmartAlarmAndNoOtherObligationAllowsStop() {
        assertFalse(ReminderMonitoringPolicy.decide(Long.MAX_VALUE, Long.MAX_VALUE, false, false).required);
    }

    @Test public void requirementTelemetryFlagsDescribeAllObligations() {
        ReminderMonitoringPolicy.Requirement result = ReminderMonitoringPolicy.decide(NORMAL, SMART, true, true);
        assertTrue(result.futureNormalReminders);
        assertTrue(result.futureSmartAlarm);
        assertTrue(result.activeSmartWake);
        assertTrue(result.pendingWakeCheck);
    }
}
