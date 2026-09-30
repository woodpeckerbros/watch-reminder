package com.woodpeckerbros.watchreminder.entitlement;

import com.android.billingclient.api.Purchase;

import org.junit.Test;
import java.util.Calendar;
import java.util.TimeZone;

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
        TimeZone zone = TimeZone.getTimeZone("UTC");
        long trialStart = localExpiryAt(zone, 14, 0) - TrialPolicy.TRIAL_DURATION_MS;
        long warningAt = trialStart + TrialPolicy.TRIAL_DURATION_MS
                - TrialPolicy.EXPIRY_WARNING_BEFORE_MS;
        assertEquals(warningAt, TrialPolicy.expiryWarningAt(
                TrialPolicy.evaluate(trialStart, trialStart, trialStart, false), zone));
        assertFalse(TrialPolicy.inExpiryWarningWindow(
                TrialPolicy.evaluate(trialStart, trialStart, warningAt - 1, false), zone));
        assertTrue(TrialPolicy.inExpiryWarningWindow(
                TrialPolicy.evaluate(trialStart, trialStart, warningAt, false), zone));
        assertFalse(TrialPolicy.inExpiryWarningWindow(
                TrialPolicy.evaluate(trialStart, trialStart, trialStart + TrialPolicy.TRIAL_DURATION_MS, false), zone));
        assertFalse(TrialPolicy.inExpiryWarningWindow(
                TrialPolicy.evaluate(trialStart, trialStart, warningAt, true), zone));
    }

    @Test public void overnightExpiryWarnsDuringLocalDaytime() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Jerusalem");
        assertLocalWarning(zone, 2, 0, 14, 0, 1);
        assertLocalWarning(zone, 7, 0, 18, 0, 1);
        assertLocalWarning(zone, 21, 0, 9, 0, 0);
    }

    private static void assertLocalWarning(TimeZone zone, int expiryHour, int expiryMinute,
                                           int warningHour, int warningMinute, int daysBefore) {
        long expiryAt = localExpiryAt(zone, expiryHour, expiryMinute);
        long trialStart = expiryAt - TrialPolicy.TRIAL_DURATION_MS;
        Calendar expected = Calendar.getInstance(zone);
        expected.setTimeInMillis(expiryAt);
        expected.add(Calendar.DAY_OF_YEAR, -daysBefore);
        expected.set(Calendar.HOUR_OF_DAY, warningHour);
        expected.set(Calendar.MINUTE, warningMinute);
        assertEquals(expected.getTimeInMillis(), TrialPolicy.expiryWarningAt(
                TrialPolicy.evaluate(trialStart, trialStart, trialStart, false), zone));
    }

    private static long localExpiryAt(TimeZone zone, int hour, int minute) {
        Calendar expiry = Calendar.getInstance(zone);
        expiry.clear();
        expiry.set(2026, Calendar.NOVEMBER, 20, hour, minute, 0);
        return expiry.getTimeInMillis();
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

    @Test public void verifiedPurchaseReconcilesSchedulesEvenDuringActiveTrial() {
        assertTrue(PurchaseEntitlementPolicy.shouldReconcileDeliveries(false, true, true));
        assertTrue(PurchaseEntitlementPolicy.shouldReconcileDeliveries(true, true, true));
        assertTrue(PurchaseEntitlementPolicy.shouldReconcileDeliveries(true, false, false));
        assertFalse(PurchaseEntitlementPolicy.shouldReconcileDeliveries(true, true, false));
        assertFalse(PurchaseEntitlementPolicy.shouldReconcileDeliveries(false, false, false));
    }
}
