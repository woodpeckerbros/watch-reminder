package com.woodpeckerbros.watchreminder.smartalarm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SmartAlarmRecoveryPolicyTest {
    private static final long NOW = 10_000_000L;

    @Test public void futureFinalDeadlineIsRestoredIndependentlyOfDetector() {
        assertEquals(SmartAlarmRecoveryPolicy.Action.RESTORE_FUTURE_DEADLINE,
                SmartAlarmRecoveryPolicy.decide(true, NOW + 60_000L, NOW, false, false));
    }

    @Test public void detectorFailureCannotSuppressFutureFinalDeadline() {
        boolean detectorReturnedWake = false;
        assertEquals(false, detectorReturnedWake);
        assertEquals(SmartAlarmRecoveryPolicy.Action.RESTORE_FUTURE_DEADLINE,
                SmartAlarmRecoveryPolicy.decide(true, NOW + 60_000L, NOW, false, false));
    }

    @Test public void missingHealthServicesCannotSuppressFutureFinalDeadline() {
        boolean healthServicesAvailable = false;
        assertEquals(false, healthServicesAvailable);
        assertEquals(SmartAlarmRecoveryPolicy.Action.RESTORE_FUTURE_DEADLINE,
                SmartAlarmRecoveryPolicy.decide(true, NOW + 60_000L, NOW, false, false));
    }

    @Test public void failedMotionRegistrationCannotSuppressFutureFinalDeadline() {
        boolean motionSensorsRegistered = false;
        assertEquals(false, motionSensorsRegistered);
        assertEquals(SmartAlarmRecoveryPolicy.Action.RESTORE_FUTURE_DEADLINE,
                SmartAlarmRecoveryPolicy.decide(true, NOW + 60_000L, NOW, false, false));
    }

    @Test public void recentMissedDeadlineIsDeliveredInsteadOfSilentlySkipped() {
        assertEquals(SmartAlarmRecoveryPolicy.Action.DELIVER_RECENT_MISSED_DEADLINE,
                SmartAlarmRecoveryPolicy.decide(true, NOW - 90_000L, NOW, false, false));
    }

    @Test public void expiredMissedDeadlineUsesExplicitNextOccurrencePolicy() {
        assertEquals(SmartAlarmRecoveryPolicy.Action.RESCHEDULE_NEXT_OCCURRENCE,
                SmartAlarmRecoveryPolicy.decide(true,
                        NOW - SmartAlarmRecoveryPolicy.MISSED_DEADLINE_CATCH_UP_MS - 1L,
                        NOW, false, false));
    }

    @Test public void activeDeliveredAlertIsNotRecreatedByRecovery() {
        assertEquals(SmartAlarmRecoveryPolicy.Action.PRESERVE_ACTIVE_ALERT,
                SmartAlarmRecoveryPolicy.decide(true, NOW - 1L, NOW, true, false));
    }

    @Test public void dismissedOccurrenceIsNotRecreatedByRecoveryOrBillingReconciliation() {
        assertEquals(SmartAlarmRecoveryPolicy.Action.RESCHEDULE_NEXT_OCCURRENCE,
                SmartAlarmRecoveryPolicy.decide(true, NOW - 1L, NOW, true, true));
    }

    @Test public void rebootBeforeDeadlineRestoresFutureFinalAlarm() {
        assertEquals(SmartAlarmRecoveryPolicy.Action.RESTORE_FUTURE_DEADLINE,
                SmartAlarmRecoveryPolicy.decideLockedBoot(NOW + 5 * 60_000L, NOW, false));
    }

    @Test public void lockedBootCatchUpDeliversRecentDeadline() {
        assertEquals(SmartAlarmRecoveryPolicy.Action.DELIVER_RECENT_MISSED_DEADLINE,
                SmartAlarmRecoveryPolicy.decideLockedBoot(NOW - 5 * 60_000L, NOW, false));
    }

    @Test public void lockedBootDoesNotDuplicateAlreadyDeliveredAlarm() {
        assertEquals(SmartAlarmRecoveryPolicy.Action.PRESERVE_ACTIVE_ALERT,
                SmartAlarmRecoveryPolicy.decideLockedBoot(NOW + 60_000L, NOW, true));
    }

    @Test public void unlockedRecoveryFinalizesMatchingDirectBootDeliveryOnlyOnce() {
        assertEquals(true, SmartAlarmRecoveryPolicy.shouldFinalizeDirectBootDelivery(true, true));
        assertEquals(false, SmartAlarmRecoveryPolicy.shouldFinalizeDirectBootDelivery(true, false));
        assertEquals(false, SmartAlarmRecoveryPolicy.shouldFinalizeDirectBootDelivery(false, true));
    }
}
