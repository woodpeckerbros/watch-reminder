package com.woodpeckerbros.watchreminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Debug-only entry point that publishes a normal reminder through its real full-screen channel. */
public final class ReminderDebugReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        long now = ReminderScheduler.floorToMinute(System.currentTimeMillis());
        String occurrenceId = "debug-reminder:" + System.currentTimeMillis();
        ReminderReceiver.showNotification(
                context,
                occurrenceId,
                "debug-reminder",
                AppLanguage.isEnglish(context) ? "Reminder screen test" : "בדיקת מסך תזכורת",
                now,
                now,
                -1,
                false
        );
    }
}
