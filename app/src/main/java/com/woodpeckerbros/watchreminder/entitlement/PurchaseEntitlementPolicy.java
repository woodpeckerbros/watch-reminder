package com.woodpeckerbros.watchreminder.entitlement;

import com.android.billingclient.api.Purchase;

/** Testable purchase-state rules used before any local entitlement cache is changed. */
public final class PurchaseEntitlementPolicy {
    private PurchaseEntitlementPolicy() { }

    public static boolean grantsLifetime(String productId, int purchaseState) {
        return EntitlementManager.LIFETIME_PRODUCT_ID.equals(productId)
                && purchaseState == Purchase.PurchaseState.PURCHASED;
    }

    public static boolean lifetimeAfterPurchaseQuery(boolean cachedLifetime, boolean querySucceeded,
                                                     boolean confirmedOwned) {
        return querySucceeded ? confirmedOwned : cachedLifetime;
    }

    public static boolean shouldResumeReminderDelivery(boolean wasGranted, boolean nowGranted) {
        return !wasGranted && nowGranted;
    }
}
