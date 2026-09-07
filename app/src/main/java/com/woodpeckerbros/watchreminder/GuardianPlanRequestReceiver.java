package com.woodpeckerbros.watchreminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class GuardianPlanRequestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent != null && GuardianBridge.ACTION_REQUEST_PLAN.equals(intent.getAction())) {
            GuardianBridge.sync(context, true);
        }
    }
}
