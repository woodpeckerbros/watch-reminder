package com.woodpeckerbros.watchreminder.guardian;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** Debug-only ADB entry point for verifying recovery from a force-stopped main package. */
public final class GuardianProbeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent source) {
        Intent probe = new Intent("com.woodpeckerbros.watchreminder.guardian.DEBUG_WAKE_PROBE")
                .setComponent(new ComponentName(GuardianContract.MAIN_PACKAGE,
                        GuardianContract.MAIN_PACKAGE + ".GuardianWakeProbeReceiver"))
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(probe);
    }
}
