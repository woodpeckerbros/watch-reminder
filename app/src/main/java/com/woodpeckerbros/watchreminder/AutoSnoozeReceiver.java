package com.woodpeckerbros.watchreminder;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AutoSnoozeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String occurrenceId = intent.getStringExtra(ReminderScheduler.EXTRA_OCCURRENCE_ID);
        String reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID);
        String reminderName = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_NAME);
        long originalScheduledAt = intent.getLongExtra(ReminderScheduler.EXTRA_ORIGINAL_SCHEDULED_AT, System.currentTimeMillis());
        if (occurrenceId == null || reminderId == null || reminderName == null) {
            AppLog.w(context, "AutoSnoozeReceiver missing extras");
            return;
        }

        ReminderEventStore eventStore = new ReminderEventStore(context);
        ReminderEventStore.Event event = eventStore.find(occurrenceId);
        if (event == null || !ReminderEventStore.STATUS_FIRED.equals(event.status)) {
            AppLog.w(context, "AutoSnoozeReceiver skipped event status occurrence=" + occurrenceId);
            return;
        }

        int snoozeMinutes = new ReminderSettings(context).autoSnoozeMinutes();
        AppLog.w(context, "AutoSnoozeReceiver snooze occurrence=" + occurrenceId + " minutes=" + snoozeMinutes);
        ReminderAlertQueueStore queueStore = new ReminderAlertQueueStore(context);
        ReminderAlertQueueStore.QueuedAlert activeAlert = queueStore.getActiveAlert(occurrenceId);
        if (WearStateGate.shouldDeferKnown(context) && queueStore.moveActiveToDeferred(occurrenceId)) {
            AppLog.w(context, "AutoSnoozeReceiver deferred without retry alarm occurrence=" + occurrenceId);
            cancelNotification(context, occurrenceId);
            WearStateGate.defer(context, reminderId, reminderName, originalScheduledAt);
            ComplicationRefresh.request(context);
            ReminderAlertActivity.closeAutoSnoozed(occurrenceId);
            return;
        }
        if (activeAlert != null) {
            originalScheduledAt = activeAlert.latestOriginalScheduledAt();
        }
        long nextScheduledAt = ReminderScheduler.scheduleSnooze(context, reminderId, reminderName, snoozeMinutes, originalScheduledAt);
        if (activeAlert == null) {
            eventStore.markAutoSnoozed(occurrenceId, snoozeMinutes, nextScheduledAt);
        } else {
            for (String id : activeAlert.occurrenceIds) {
                eventStore.markAutoSnoozed(id, snoozeMinutes, nextScheduledAt);
            }
        }
        cancelNotification(context, occurrenceId);
        ComplicationRefresh.request(context);
        if (!ReminderAlertActivity.closeAutoSnoozed(occurrenceId)) {
            queueStore.complete(occurrenceId);
            ReminderReceiver.dispatchNextQueued(context);
        }
    }

    private void cancelNotification(Context context, String occurrenceId) {
        if (occurrenceId == null) {
            return;
        }
        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(occurrenceId.hashCode());
            }
        } catch (Exception ignored) {
        }
    }
}
