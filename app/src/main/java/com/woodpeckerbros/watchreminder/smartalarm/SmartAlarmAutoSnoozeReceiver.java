package com.woodpeckerbros.watchreminder.smartalarm;

import com.woodpeckerbros.watchreminder.reminder.*;

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
        handleTimeout(context, alarmId, targetAt,
                intent.getBooleanExtra("wake_check_escalation", false),
                intent.getLongExtra("feedback_end_at", 0L));
    }

    static void handleTimeout(Context context, int alarmId, long targetAt,
                              boolean wakeCheckEscalation, long feedbackEndAt) {
        SmartAlarmStateStore state = new SmartAlarmStateStore(context, alarmId);
        if (!state.presentationMatches(targetAt, feedbackEndAt) || state.presentationTimeoutHandled()) return;
        if (!wakeCheckEscalation && (!state.fired(targetAt) || state.dismissed(targetAt))) return;

        // Feedback always stops at the original expiry. Only the silent UI may gain time;
        // don't advance occurrence state or arm a snooze while the user is still completing it.
        SmartAlarmRingingService.stop(context, alarmId, targetAt);
        boolean interactionGraceVisible = SmartAlarmAlertActivity.closeAutoSnoozed(alarmId, targetAt);
        if (interactionGraceVisible) {
            SmartAlarmScheduler.scheduleAutoSnooze(context, alarmId, targetAt,
                    (int) (SmartAlarmPresentationTiming.INTERACTION_GRACE_MS / 1000L), wakeCheckEscalation);
            AppLog.d(context, "SmartAlarm feedback expired; silent task retained id=" + alarmId
                    + " target=" + targetAt + " feedback_end_at=" + feedbackEndAt);
            return;
        }
        if (!state.finishPresentationTimeout(targetAt, feedbackEndAt)) return;
        SmartAlarmScheduler.cancelAutoSnooze(context, alarmId);
        SmartAlarmActions.cancelNotification(context, alarmId);
        if (wakeCheckEscalation) {
            SmartAlarmWakeCheckReceiver.cancel(context, alarmId);
            SmartAlarmAttentionStore.releaseAfterTerminalAction(context, alarmId,
                    "WAKE_CHECK_ESCALATION_STOPPED");
            AppLog.w(context, "SmartAlarm unanswered wake check escalation stopped id=" + alarmId);
            return;
        }
        SmartAlarmStore settings = new SmartAlarmStore(context, alarmId);
        if (settings.systemTimerFallbackEnabled()) {
            SmartAlarmAlertActivity.startSystemTimerFallback(context, alarmId);
        }
        if (state.finalDeadlineDelivered()) {
            // The hard deadline has already rung. Its timeout may finish the final alert under
            // the established final-alarm lifecycle, but it must never recreate an early chain.
            state.dismiss(targetAt);
            SmartAlarmScheduler.scheduleNextAfterHandled(context, alarmId, targetAt);
            SmartAlarmAttentionStore.releaseAfterTerminalAction(context, alarmId,
                    "FINAL_DEADLINE_UNANSWERED_TIMEOUT");
            AppLog.w(context, "SmartAlarm final deadline unanswered timeout id=" + alarmId);
            return;
        }
        if (state.snoozeUsed() < settings.snoozeCount()) {
            AppLog.w(context, "EARLY_WAKE_ATTEMPT_UNANSWERED id=" + alarmId
                    + " used=" + state.snoozeUsed() + " of=" + settings.snoozeCount()
                    + " FINAL_DEADLINE_STILL_ARMED=true");
            AppLog.w(context, "EARLY_WAKE_AUTO_SNOOZE id=" + alarmId + " occurrence_id="
                    + alarmId + ":" + state.occurrenceTargetAt()
                    + " FINAL_DEADLINE_STILL_ARMED=true");
            SmartAlarmScheduler.scheduleSnooze(context, alarmId, targetAt, settings.snoozeMinutes());
        } else {
            AppLog.w(context, "EARLY_WAKE_ATTEMPT_UNANSWERED id=" + alarmId
                    + " reason=SNOOZES_EXHAUSTED FINAL_DEADLINE_STILL_ARMED=true");
            SmartAlarmScheduler.endEarlyWakeChain(context, alarmId, targetAt, "AUTO_SNOOZES_EXHAUSTED");
            SmartAlarmAttentionStore.releaseAfterTerminalAction(context, alarmId,
                    "SNOOZES_EXHAUSTED");
        }
    }
}
