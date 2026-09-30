package com.woodpeckerbros.watchreminder.entitlement;

import com.android.billingclient.api.Purchase;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class TrialPolicyTest {
    private static final long START = 1_700_000_000_000L;

    @Test public void freshUserStartsFourteenDayTrial() {
        TrialPolicy.Snapshot snapshot = TrialPolicy.evaluate(0L, 0L, START, false);
        assertTrue(snapshot.featureAccessGranted);
        assertEquals(START, snapshot.trialStartedAt);
        assertEquals(TrialPolicy.TRIAL_DURATION_MS, snapshot.remainingMillis);
    }

    @Test public void trialIsActiveBeforeFourteenDaysAndRestartKeepsOriginalStart() {
        long later = START + TrialPolicy.TRIAL_DURATION_MS - 1L;
        TrialPolicy.Snapshot snapshot = TrialPolicy.evaluate(START, START + 2_000L, later, false);
        assertTrue(snapshot.featureAccessGranted);
        assertEquals(START, snapshot.trialStartedAt);
    }

    @Test public void trialExpiresAtFourteenDaysExactly() {
        assertFalse(TrialPolicy.evaluate(START, START, START + TrialPolicy.TRIAL_DURATION_MS, false)
                .featureAccessGranted);
    }

    @Test public void warnsExactlyOneDayBeforeExpiryOnlyWhileTrialIsActive() {
        long warningAt = START + TrialPolicy.TRIAL_DURATION_MS
                - TrialPolicy.EXPIRY_WARNING_BEFORE_MS;
        assertEquals(warningAt, TrialPolicy.expiryWarningAt(
                TrialPolicy.evaluate(START, START, START, false)));
        assertFalse(TrialPolicy.inExpiryWarningWindow(
                TrialPolicy.evaluate(START, START, warningAt - 1, false)));
        assertTrue(TrialPolicy.inExpiryWarningWindow(
                TrialPolicy.evaluate(START, START, warningAt, false)));
        assertFalse(TrialPolicy.inExpiryWarningWindow(
                TrialPolicy.evaluate(START, START, START + TrialPolicy.TRIAL_DURATION_MS, false)));
        assertFalse(TrialPolicy.inExpiryWarningWindow(
                TrialPolicy.evaluate(START, START, warningAt, true)));
    }

    @Test public void clockRollbackCannotExtendTrial() {
        long afterExpiry = START + TrialPolicy.TRIAL_DURATION_MS + 60_000L;
        TrialPolicy.Snapshot snapshot = TrialPolicy.evaluate(START, afterExpiry, START + 60_000L, false);
        assertFalse(snapshot.featureAccessGranted);
        assertEquals(afterExpiry, snapshot.effectiveNow);
    }

    @Test public void purchasedLifetimeOverridesExpiredTrial() {
        assertTrue(TrialPolicy.evaluate(START, START, START + TrialPolicy.TRIAL_DURATION_MS + 1L, true)
                .featureAccessGranted);
    }

    @Test public void pendingPurchaseDoesNotUnlockButRestoredPurchasedDoes() {
        assertFalse(PurchaseEntitlementPolicy.grantsLifetime(EntitlementManager.LIFETIME_PRODUCT_ID,
                Purchase.PurchaseState.PENDING));
        assertTrue(PurchaseEntitlementPolicy.grantsLifetime(EntitlementManager.LIFETIME_PRODUCT_ID,
                Purchase.PurchaseState.PURCHASED));
    }

    @Test public void billingOutageKeepsLastVerifiedLifetimeAndSuccessfulRestoreUnlocks() {
        assertTrue(PurchaseEntitlementPolicy.lifetimeAfterPurchaseQuery(true, false, false));
        assertTrue(PurchaseEntitlementPolicy.lifetimeAfterPurchaseQuery(false, true, true));
    }

    @Test public void entitlementTransitionResumesReminderSchedulingOnlyWhenAccessReturns() {
        assertTrue(PurchaseEntitlementPolicy.shouldResumeReminderDelivery(false, true));
        assertFalse(PurchaseEntitlementPolicy.shouldResumeReminderDelivery(true, true));
    }
}
