package com.woodpeckerbros.watchreminder.smartalarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SmartWakeDirectBootPolicyTest {
    private static final long NOW = 10_000_000L;
    private static final long MONITOR = NOW + 30 * 60_000L;
    private static final long EARLIEST = NOW + 75 * 60_000L;
    private static final long DEADLINE = NOW + 120 * 60_000L;

    @Test public void rebootBeforeMonitoringRestoresMonitoringStartAndFinalDeadline() {
        assertEquals(SmartWakeDirectBootPolicy.Action.RESTORE_MONITORING_START,
                SmartWakeDirectBootPolicy.decide(true, false, MONITOR, EARLIEST, DEADLINE, NOW));
        assertEquals(SmartAlarmRecoveryPolicy.Action.RESTORE_FUTURE_DEADLINE,
                SmartAlarmRecoveryPolicy.decideLockedBoot(DEADLINE, NOW, false));
    }

    @Test public void rebootAfterMonitoringBeforeEarliestStartsOneCatchUpLeadIn() {
        long now = MONITOR + 5 * 60_000L;
        assertEquals(SmartWakeDirectBootPolicy.Action.START_MONITORING_CATCH_UP,
                SmartWakeDirectBootPolicy.decide(true, false, MONITOR, EARLIEST, DEADLINE, now));
    }

    @Test public void rebootDuringActiveWindowStartsOneCatchUpSession() {
        long now = EARLIEST + 5 * 60_000L;
        assertEquals(SmartWakeDirectBootPolicy.Action.START_MONITORING_CATCH_UP,
                SmartWakeDirectBootPolicy.decide(true, false, MONITOR, EARLIEST, DEADLINE, now));
    }

    @Test public void unlockMergesSameOccurrenceWithoutDuplicateListeners() {
        assertTrue(SmartWakeRuntimePolicy.isSameSession(7, DEADLINE, 7, DEADLINE));
        assertFalse(SmartWakeRuntimePolicy.shouldRegisterMotion(true, true, true));
    }

    @Test public void optionalInputFailureDoesNotDisarmFinalDeadline() {
        boolean healthServicesAvailable = false;
        boolean accelerometerAvailable = false;
        assertFalse(healthServicesAvailable);
        assertFalse(accelerometerAvailable);
        assertEquals(SmartAlarmRecoveryPolicy.Action.RESTORE_FUTURE_DEADLINE,
                SmartAlarmRecoveryPolicy.decideLockedBoot(DEADLINE, NOW, false));
    }

    @Test public void finalDeadlineSurvivesSmartWakeStartupFailure() {
        assertEquals(SmartWakeDirectBootPolicy.Action.START_MONITORING_CATCH_UP,
                SmartWakeDirectBootPolicy.decide(true, false, MONITOR, EARLIEST, DEADLINE,
                        EARLIEST));
        assertEquals(SmartAlarmRecoveryPolicy.Action.RESTORE_FUTURE_DEADLINE,
                SmartAlarmRecoveryPolicy.decideLockedBoot(DEADLINE, EARLIEST, false));
    }

    @Test public void deliveredOrCompletedOccurrenceDoesNotReviveAfterBoot() {
        assertEquals(SmartWakeDirectBootPolicy.Action.NO_MONITORING,
                SmartWakeDirectBootPolicy.decide(true, true, MONITOR, EARLIEST, DEADLINE, NOW));
        assertEquals(SmartAlarmRecoveryPolicy.Action.PRESERVE_ACTIVE_ALERT,
                SmartAlarmRecoveryPolicy.decideLockedBoot(DEADLINE, NOW, true));
    }

    @Test public void staleOrIncompleteDeviceProtectedShadowDoesNotReviveOldOccurrence() {
        assertEquals(SmartWakeDirectBootPolicy.Action.NO_MONITORING,
                SmartWakeDirectBootPolicy.decide(true, false, 0L, EARLIEST, DEADLINE, NOW));
        assertEquals(SmartWakeDirectBootPolicy.Action.NO_MONITORING,
                SmartWakeDirectBootPolicy.decide(true, false, MONITOR, DEADLINE + 1L, DEADLINE, NOW));
    }
}
