package com.woodpeckerbros.watchreminder.guardian;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** First asks the main app to recover; a guardian-owned popup follows if no ACK arrives. */
public final class GuardianFallbackReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent source) {
        if (source == null) return;
        Intent recover = new Intent(GuardianContract.ACTION_RECOVER)
                .setComponent(new ComponentName(GuardianContract.MAIN_PACKAGE,
                        GuardianContract.MAIN_PACKAGE + ".GuardianRecoveryReceiver"))
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtras(source);
        context.sendBroadcast(recover);
        GuardianStore.scheduleGuardianAlert(context, source);
        android.util.Log.w("ZmanioGuardian", "fallback recovery requested key="
                + source.getStringExtra(GuardianContract.EXTRA_KEY));
    }
}
