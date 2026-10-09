package com.woodpeckerbros.watchreminder.smartalarm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Deterministic lifecycle coverage for the early-attempt/final-guarantee separation. */
public class SmartAlarmFinalDeadlinePolicyTest {
    private static final long NOW = 1_000_000L;
    private static final long FINAL = NOW + 30 * 60_000L;

    @Test public void earlyDeliveryKeepsFinalDeadlineArmed() {
        assertTrue(SmartAlarmFinalDeadlinePolicy.mustKeepFinalDeadline(false, false));
    }

    @Test public void unansweredAndMultipleAutoSnoozesKeepFinalDeadlineArmed() {
        assertTrue(SmartAlarmFinalDeadlinePolicy.mustKeepFinalDeadline(false, false));
        assertTrue(SmartAlarmFinalDeadlinePolicy.earlySnoozeFitsBeforeFinalDeadline(NOW + 5 * 60_000L, FINAL));
        assertTrue(SmartAlarmFinalDeadlinePolicy.earlySnoozeFitsBeforeFinalDeadline(NOW + 10 * 60_000L, FINAL));
    }

    @Test public void earlyChainExhaustionIsNotTerminal() {
        assertTrue(SmartAlarmFinalDeadlinePolicy.mustKeepFinalDeadline(false, false));
    }

    @Test public void awakeConfirmationCancelsOnlyTheFinalGuarantee() {
        assertFalse(SmartAlarmFinalDeadlinePolicy.mustKeepFinalDeadline(true, false));
    }

    @Test public void incompleteDismissTaskAndSnoozeKeepFinalGuarantee() {
        assertTrue(SmartAlarmFinalDeadlinePolicy.mustKeepFinalDeadline(false, false));
    }

    @Test public void finalDeliveryEndsTheArmedGuaranteeWithoutPretendingAwakeConfirmation() {
        assertFalse(SmartAlarmFinalDeadlinePolicy.mustKeepFinalDeadline(false, true));
    }

    @Test public void restartAndDirectBootRecoveryPreservePendingFinalDeadline() {
        assertTrue(SmartAlarmFinalDeadlinePolicy.shouldRestoreFinalDeadlineAfterEarlyState(
                false, false, FINAL, NOW));
        assertFalse(SmartAlarmFinalDeadlinePolicy.shouldRestoreFinalDeadlineAfterEarlyState(
                true, false, FINAL, NOW));
        assertFalse(SmartAlarmFinalDeadlinePolicy.shouldRestoreFinalDeadlineAfterEarlyState(
                false, true, FINAL, NOW));
    }

    @Test public void finalDeadlineIdentityRemainsTheOriginalOccurrence() {
        assertTrue(SmartAlarmFinalDeadlinePolicy.isFinalDeadlineDelivery("deadline"));
        assertFalse(SmartAlarmFinalDeadlinePolicy.isFinalDeadlineDelivery("early_snooze"));
    }

    @Test public void finalDeadlineIsDeliveredExactlyOnce() {
        assertTrue(SmartAlarmFinalDeadlinePolicy.mayDeliverFinalDeadline(false, false));
        assertFalse(SmartAlarmFinalDeadlinePolicy.mayDeliverFinalDeadline(false, true));
        assertFalse(SmartAlarmFinalDeadlinePolicy.mayDeliverFinalDeadline(true, false));
    }

    @Test public void staleEarlySnoozeIsIgnoredAfterFinalDeadlineDelivery() {
        assertFalse(SmartAlarmFinalDeadlinePolicy.mustIgnoreEarlySnoozeAfterFinalDeadline(false));
        assertTrue(SmartAlarmFinalDeadlinePolicy.mustIgnoreEarlySnoozeAfterFinalDeadline(true));
    }

    @Test public void directBootRestoresFinalButNotAnotherSmartWakeAttemptAfterEarlyDelivery() {
        assertTrue(SmartAlarmFinalDeadlinePolicy.mayRestoreSmartWakeMonitoring(true, false));
        assertFalse(SmartAlarmFinalDeadlinePolicy.mayRestoreSmartWakeMonitoring(true, true));
    }
}
