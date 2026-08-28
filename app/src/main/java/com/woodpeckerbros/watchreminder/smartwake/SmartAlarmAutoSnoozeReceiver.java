package com.woodpeckerbros.watchreminder.smartwake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.woodpeckerbros.watchreminder.AppLog;

/** Reliable AlarmManager-backed unanswered-alarm handling, matching regular reminders. */
public final class SmartAlarmAutoSnoozeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        int alarmId = intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        long targetAt = intent.getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
        SmartAlarmStateStore state = new SmartAlarmStateStore(context, alarmId);
        if (!state.fired(targetAt) || state.dismissed(targetAt)) {
            AppLog.w(context, "SmartAlarm auto-snooze skipped stale id=" + alarmId + " target=" + targetAt);
            return;
        }

        SmartAlarmStore settings = new SmartAlarmStore(context, alarmId);
        SmartAlarmRingingService.stop(context);
        SmartAlarmActions.cancelNotification(context, alarmId);
        SmartAlarmAlertActivity.closeAutoSnoozed(alarmId, targetAt);
        if (state.snoozeUsed() < settings.snoozeCount()) {
            AppLog.w(context, "SmartAlarm unanswered; auto-snooze id=" + alarmId
                    + " used=" + state.snoozeUsed() + " of=" + settings.snoozeCount());
            SmartAlarmScheduler.scheduleSnooze(context, alarmId, targetAt, settings.snoozeMinutes());
        } else {
            AppLog.w(context, "SmartAlarm unanswered; snoozes exhausted id=" + alarmId);
            state.dismiss(targetAt);
            SmartAlarmScheduler.scheduleNextAfterHandled(context, alarmId);
        }
    }
}
