package com.woodpeckerbros.watchreminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Debug-only entry point that publishes a normal reminder through its real full-screen channel. */
public final class ReminderDebugReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int delaySeconds = intent.getIntExtra("delay_seconds", 0);
        if (delaySeconds > 0) {
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms == null) return;
            long at = System.currentTimeMillis() + delaySeconds * 1000L;
            Intent fire = new Intent(context, ReminderDebugReceiver.class)
                    .setAction("com.woodpeckerbros.watchreminder.DEBUG_REMINDER_ALERT");
            PendingIntent operation = PendingIntent.getBroadcast(context, 0x52444247, fire,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(at, operation), operation);
            AppLog.d(context, "normal debug reminder scheduled at=" + at);
            return;
        }
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
