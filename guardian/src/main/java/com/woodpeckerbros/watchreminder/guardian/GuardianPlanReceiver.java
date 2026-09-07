package com.woodpeckerbros.watchreminder.guardian;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class GuardianPlanReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (GuardianContract.ACTION_PLAN.equals(intent.getAction())) {
            GuardianStore.apply(context, intent.getStringExtra(GuardianContract.EXTRA_PAYLOAD));
        } else if (GuardianContract.ACTION_ACK.equals(intent.getAction())) {
            GuardianStore.acknowledge(context, intent.getStringExtra(GuardianContract.EXTRA_KEY));
        }
    }
}
