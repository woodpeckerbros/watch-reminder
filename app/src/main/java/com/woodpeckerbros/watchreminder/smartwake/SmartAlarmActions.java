package com.woodpeckerbros.watchreminder.smartwake;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.woodpeckerbros.watchreminder.AppLanguage;
import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.R;

/** Notification fallback actions for devices that do not present a full-screen alarm UI. */
public final class SmartAlarmActions extends BroadcastReceiver {
    private static final String ACTION_SNOOZE = "smartwake.action.SNOOZE";
    private static final String ACTION_DISMISS = "smartwake.action.DISMISS";
    private static final int NOTIFICATION_BASE = 0x534d5704;

    static Notification.Action snoozeAction(Context context, int alarmId, long targetAt) {
        String title = AppLanguage.isEnglish(context) ? "Snooze" : "נודניק";
        return new Notification.Action.Builder(R.drawable.ic_notification, title,
                pending(context, ACTION_SNOOZE, alarmId, targetAt, 1)).build();
    }

    static Notification.Action dismissAction(Context context, int alarmId, long targetAt) {
        String title = AppLanguage.isEnglish(context) ? "Dismiss" : "כיבוי";
        return new Notification.Action.Builder(R.drawable.ic_notification, title,
                pending(context, ACTION_DISMISS, alarmId, targetAt, 2)).build();
    }

    static void cancelNotification(Context context, int alarmId) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_BASE + alarmId);
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        int alarmId = intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        long targetAt = intent.getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
        SmartAlarmStateStore state = new SmartAlarmStateStore(context, alarmId);
        if (!state.fired(targetAt) || state.dismissed(targetAt)) return;

        SmartAlarmRingingService.stop(context);
        SmartAlarmScheduler.cancelAutoSnooze(context, alarmId);
        cancelNotification(context, alarmId);
        if (ACTION_SNOOZE.equals(intent.getAction())) {
            SmartAlarmStore settings = new SmartAlarmStore(context, alarmId);
            if (state.snoozeUsed() < settings.snoozeCount()) {
                SmartAlarmScheduler.scheduleSnooze(context, alarmId, targetAt, settings.snoozeMinutes());
                AppLog.d(context, "SmartAlarm notification snooze id=" + alarmId);
                return;
            }
        }
        state.dismiss(targetAt);
        if (new SmartAlarmStore(context, alarmId).wakeCheckEnabled()) {
            SmartAlarmWakeCheckReceiver.schedule(context, alarmId, targetAt,
                    new SmartAlarmStore(context, alarmId).wakeCheckDelayMinutes());
        } else {
            SmartAlarmWakeCheckReceiver.cancel(context, alarmId);
        }
        SmartAlarmScheduler.scheduleNextAfterHandled(context, alarmId);
        AppLog.d(context, "SmartAlarm notification dismiss id=" + alarmId);
    }

    private static PendingIntent pending(Context context, String action, int alarmId, long targetAt, int kind) {
        Intent intent = new Intent(context, SmartAlarmActions.class).setAction(action)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt);
        int requestCode = 0x534d5800 | ((alarmId & 0xffff) << 2) | kind;
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
