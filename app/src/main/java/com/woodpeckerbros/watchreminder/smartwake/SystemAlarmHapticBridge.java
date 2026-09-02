package com.woodpeckerbros.watchreminder.smartwake;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.AlarmClock;

import com.woodpeckerbros.watchreminder.AppLog;

import java.util.Calendar;
import java.util.List;

/**
 * Optional bridge to the device Clock app. The system alarm is silent, but asks the Clock app
 * to vibrate using its privileged alarm path, which can remain available during Bedtime mode.
 */
final class SystemAlarmHapticBridge {
    private SystemAlarmHapticBridge() {}

    static void schedule(Context context, int alarmId, long targetAt) {
        if (targetAt <= System.currentTimeMillis()) return;
        SmartAlarmStore settings = new SmartAlarmStore(context, alarmId);
        if (!settings.systemTimerFallbackEnabled()) return;

        SmartAlarmStateStore state = new SmartAlarmStateStore(context, alarmId);
        if (state.systemAlarmTargetAt() == targetAt) {
            AppLog.d(context, "SmartAlarm system haptic alarm already requested id=" + alarmId
                    + " target=" + targetAt);
            return;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(targetAt);
        Intent alarm = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, calendar.get(Calendar.HOUR_OF_DAY))
                .putExtra(AlarmClock.EXTRA_MINUTES, calendar.get(Calendar.MINUTE))
                .putExtra(AlarmClock.EXTRA_MESSAGE, label(alarmId, targetAt))
                .putExtra(AlarmClock.EXTRA_RINGTONE, AlarmClock.VALUE_RINGTONE_SILENT)
                .putExtra(AlarmClock.EXTRA_VIBRATE, true)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ComponentName handler = publicClockHandler(context, alarm);
        if (handler == null) {
            AppLog.w(context, "SmartAlarm system haptic alarm unavailable without a direct Clock handler id=" + alarmId);
            return;
        }
        alarm.setComponent(handler);
        try {
            PendingIntent.getActivity(context, requestCode(alarmId), alarm,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE).send();
            state.setSystemAlarmTargetAt(targetAt);
            AppLog.d(context, "SmartAlarm system haptic alarm requested id=" + alarmId
                    + " target=" + targetAt);
        } catch (PendingIntent.CanceledException | RuntimeException error) {
            AppLog.e(context, "SmartAlarm system haptic alarm request failed id=" + alarmId, error);
        }
    }

    static void cancel(Context context, int alarmId) {
        SmartAlarmStateStore state = new SmartAlarmStateStore(context, alarmId);
        long targetAt = state.systemAlarmTargetAt();
        if (targetAt == 0L) return;
        Intent dismiss = new Intent(AlarmClock.ACTION_DISMISS_ALARM)
                .putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_LABEL)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label(alarmId, targetAt))
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ComponentName handler = publicClockHandler(context, dismiss);
        if (handler == null) {
            AppLog.w(context, "SmartAlarm system haptic alarm dismiss unavailable without a direct Clock handler id=" + alarmId);
            state.setSystemAlarmTargetAt(0L);
            return;
        }
        dismiss.setComponent(handler);
        try {
            PendingIntent.getActivity(context, requestCode(alarmId) + 1, dismiss,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE).send();
            AppLog.d(context, "SmartAlarm system haptic alarm dismiss requested id=" + alarmId
                    + " target=" + targetAt);
        } catch (PendingIntent.CanceledException | RuntimeException error) {
            AppLog.e(context, "SmartAlarm system haptic alarm dismiss failed id=" + alarmId, error);
        } finally {
            state.setSystemAlarmTargetAt(0L);
        }
    }

    private static String label(int alarmId, long targetAt) {
        return "Zmanio haptic " + alarmId + " " + targetAt;
    }

    private static int requestCode(int alarmId) {
        return 0x534d6a00 | ((alarmId & 0xffff) << 1);
    }

    /**
     * A resolver is not an alarm provider: launching it from a background alarm interrupts the
     * user and may require a manual selection. Use this optional bridge only when Android exposes
     * an actual Clock activity for the public AlarmClock intent.
     */
    private static ComponentName publicClockHandler(Context context, Intent intent) {
        List<ResolveInfo> candidates = context.getPackageManager().queryIntentActivities(
                intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo candidate : candidates) {
            if (candidate.activityInfo == null) continue;
            String packageName = candidate.activityInfo.packageName;
            String className = candidate.activityInfo.name;
            if (isResolver(packageName) || isResolver(className)) continue;
            return new ComponentName(packageName, className);
        }
        return null;
    }

    private static boolean isResolver(String value) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains("resolver");
    }
}
