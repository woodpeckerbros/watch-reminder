package com.woodpeckerbros.watchreminder;

import android.app.AlarmManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

public final class AlarmScheduleMigration {
    private static final String PREFS_NAME = "alarm_schedule_migration";
    private static final String KEY_VERSION = "version";
    private static final int CURRENT_VERSION = 1;

    private AlarmScheduleMigration() {
    }

    public static void clearLegacyAlarmsOnce(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getInt(KEY_VERSION, 0) >= CURRENT_VERSION) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (manager != null) {
                manager.cancelAll();
                AppLog.d(context, "AlarmScheduleMigration cleared legacy alarms");
            }
        }
        prefs.edit().putInt(KEY_VERSION, CURRENT_VERSION).commit();
    }
}
