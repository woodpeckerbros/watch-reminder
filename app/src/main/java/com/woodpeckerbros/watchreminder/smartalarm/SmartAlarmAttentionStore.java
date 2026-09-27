package com.woodpeckerbros.watchreminder.smartalarm;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.reminder.ReminderReceiver;

/**
 * Authoritative, process-independent owner of Zmanio's interactive alert surface.
 *
 * A Smart Alarm claims this before posting its full-screen notification and keeps ownership
 * through its entire automatic-snooze chain. Normal reminders consult this one store rather
 * than individual Activities, so recreation and foreground-service changes cannot create a
 * competing full-screen alert.
 */
public final class SmartAlarmAttentionStore {
    private static final String PREFS = "smart_alarm_attention";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_ALARM_ID = "alarm_id";
    private static final String KEY_TARGET_AT = "target_at";
    private static final long DEADLINE_RACE_WINDOW_MS = 5_000L;

    private SmartAlarmAttentionStore() {
    }

    public static void claimRinging(Context context, int alarmId, long targetAt) {
        claim(context, alarmId, targetAt, "RINGING");
        // A normal alert may have won a few milliseconds of the same-timestamp race. Move it
        // back to the durable normal-reminder queue before SystemUI can keep it above the alarm.
        ReminderReceiver.deferInteractiveAlertForSmartAlarm(context);
    }

    public static void retainForWakeCheck(Context context, int alarmId, long targetAt) {
        claim(context, alarmId, targetAt, "WAKE_CHECK_PENDING");
    }

    public static void advanceToSnooze(Context context, int alarmId, long targetAt) {
        if (ownerAlarmId(context) == alarmId) {
            claim(context, alarmId, targetAt, "AUTO_SNOOZE_CHAIN");
        }
    }

    public static boolean ownsInteractiveAttention(Context context) {
        return SmartAlarmAlertPriority.normalReminderMustDefer(isActive(context),
                finalDeadlineImminent(context, System.currentTimeMillis()));
    }

    public static boolean isActive(Context context) {
        return prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    public static void releaseAfterTerminalAction(Context context, int alarmId, String reason) {
        if (ownerAlarmId(context) != alarmId) {
            return;
        }
        prefs(context).edit().clear().commit();
        AppLog.d(context, "SmartAlarm attention released id=" + alarmId + " reason=" + reason);
        // Let the terminal Smart Alarm/Wake Check activity finish before a queued normal
        // reminder is allowed to take the full-screen surface.
        new Handler(Looper.getMainLooper()).post(
                () -> ReminderReceiver.dispatchNextQueued(context.getApplicationContext()));
    }

    private static void claim(Context context, int alarmId, long targetAt, String lifecycle) {
        prefs(context).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putInt(KEY_ALARM_ID, alarmId)
                .putLong(KEY_TARGET_AT, targetAt)
                .commit();
        AppLog.d(context, "SmartAlarm attention active lifecycle=" + lifecycle + " id=" + alarmId
                + " target=" + targetAt);
    }

    private static int ownerAlarmId(Context context) {
        return prefs(context).getInt(KEY_ALARM_ID, -1);
    }

    private static boolean finalDeadlineImminent(Context context, long now) {
        for (int alarmId : SmartAlarmStore.ids(context)) {
            SmartAlarmStateStore state = new SmartAlarmStateStore(context, alarmId);
            long targetAt = state.targetAt();
            if (targetAt >= now - DEADLINE_RACE_WINDOW_MS
                    && targetAt <= now + DEADLINE_RACE_WINDOW_MS
                    && !state.fired(targetAt) && !state.dismissed(targetAt)) {
                return true;
            }
        }
        return false;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
