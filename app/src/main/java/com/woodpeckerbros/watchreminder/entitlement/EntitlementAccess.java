package com.woodpeckerbros.watchreminder.entitlement;

import android.content.Context;

/** Safe synchronous boundary check for receivers, schedulers and foreground services. */
public final class EntitlementAccess {
    private EntitlementAccess() { }

    public static boolean isFeatureAccessGranted(Context context) {
        return new EntitlementStore(context).hasFeatureAccess();
    }
}
