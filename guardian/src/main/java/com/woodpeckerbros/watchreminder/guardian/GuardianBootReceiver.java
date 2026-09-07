package com.woodpeckerbros.watchreminder.guardian;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;

public final class GuardianBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        UserManager userManager = context.getSystemService(UserManager.class);
        if (userManager != null && !userManager.isUserUnlocked()) return;
        GuardianStore.rescheduleStored(context);
        requestFreshPlan(context);
    }

    static void requestFreshPlan(Context context) {
        Intent request = new Intent(GuardianContract.ACTION_REQUEST_PLAN)
                .setComponent(new ComponentName(GuardianContract.MAIN_PACKAGE,
                        GuardianContract.MAIN_PACKAGE + ".GuardianPlanRequestReceiver"))
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(request);
    }
}
