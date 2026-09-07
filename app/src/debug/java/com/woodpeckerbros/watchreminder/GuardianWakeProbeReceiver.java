package com.woodpeckerbros.watchreminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Debug-only cross-package wake probe. It never dispatches a user reminder. */
public final class GuardianWakeProbeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        AppLog.w(context, "guardian debug wake probe received");
    }
}
