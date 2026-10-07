package com.woodpeckerbros.watchreminder.smartalarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The same occurrence policy is used by locked-boot and unlocked reconciliation. */
public class SmartWakeMonitoringRecoveryTest {
    private static final long DEADLINE = 10_000_000L;
    private static final long EARLIEST = DEADLINE - 30 * 60_000L;
    private static final long MONITOR = EARLIEST - 45 * 60_000L;

    private static SmartWakeDirectBootPolicy.Action at(long now) {
        return SmartWakeDirectBootPolicy.decide(true, false, MONITOR, EARLIEST, DEADLINE, now);
    }

    @Test public void occurrenceUsesFortyFiveMinuteMonitoringLeadIn() {
        assertEquals(MONITOR, EARLIEST - SmartWakeSamplingProfile.monitorLeadTime(0L));
        assertEquals(DEADLINE + 60_000L, SmartWakeRuntimePolicy.hardStopAt(DEADLINE));
    }

    @Test public void normalMonitoringAlarmIsRestoredUntilItFires() {
        assertEquals(SmartWakeDirectBootPolicy.Action.RESTORE_MONITORING_START, at(MONITOR - 1L));
        assertEquals(SmartWakeDirectBootPolicy.Action.START_MONITORING_CATCH_UP, at(MONITOR));
    }

    @Test public void missedStartBeforeEarliestRequiresImmediateCatchUp() {
        assertEquals(SmartWakeDirectBootPolicy.Action.START_MONITORING_CATCH_UP,
                at(EARLIEST - 1L));
    }

    @Test public void missedStartInsideAllowedWindowRequiresDegradedCatchUp() {
        assertEquals(SmartWakeDirectBootPolicy.Action.START_MONITORING_CATCH_UP, at(EARLIEST));
        assertEquals(SmartWakeDirectBootPolicy.Action.START_MONITORING_CATCH_UP,
                at(DEADLINE - 1L));
    }

    @Test public void rebootBeforeAndAfterMonitorUsesSameOccurrencePlan() {
        assertEquals(SmartWakeDirectBootPolicy.Action.RESTORE_MONITORING_START, at(MONITOR - 1L));
        assertEquals(SmartWakeDirectBootPolicy.Action.START_MONITORING_CATCH_UP,
                at(MONITOR + 1L));
        assertEquals(SmartWakeDirectBootPolicy.Action.START_MONITORING_CATCH_UP,
                at(EARLIEST + 1L));
    }

    @Test public void duplicateRecoveryKeepsOneSessionIdentity() {
        assertTrue(SmartWakeRuntimePolicy.isSameSession(2, DEADLINE, 2, DEADLINE));
        assertFalse(SmartWakeRuntimePolicy.isSameSession(2, DEADLINE, 2, DEADLINE + 1L));
        assertFalse(SmartWakeRuntimePolicy.shouldRegisterMotion(true, true, true));
    }

    @Test public void finalDeadlineIsIndependentOfMonitoringCatchUp() {
        assertEquals(SmartAlarmRecoveryPolicy.Action.RESTORE_FUTURE_DEADLINE,
                SmartAlarmRecoveryPolicy.decideLockedBoot(DEADLINE, EARLIEST + 1L, false));
        assertEquals(SmartWakeDirectBootPolicy.Action.NO_MONITORING, at(DEADLINE));
    }
}
