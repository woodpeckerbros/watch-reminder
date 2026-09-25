package com.woodpeckerbros.watchreminder.reminder;

import com.woodpeckerbros.watchreminder.AppLog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Releases the next queued alert when the current notification is dismissed externally. */
public final class ReminderAlertDismissedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String occurrenceId = intent == null
                ? null
                : intent.getStringExtra(ReminderScheduler.EXTRA_OCCURRENCE_ID);
        if (occurrenceId == null) {
            AppLog.w(context, "alert notification dismissed without occurrence");
            return;
        }

        ReminderAlertQueueStore queueStore = new ReminderAlertQueueStore(context);
        if (queueStore.getActiveAlert(occurrenceId) == null) {
            AppLog.d(context, "alert notification dismissal ignored inactive occurrence=" + occurrenceId);
            return;
        }

        AppLog.d(context, "alert notification dismissed occurrence=" + occurrenceId);
        if (!ReminderAlertActivity.closeExternallyDismissed(occurrenceId)) {
            queueStore.complete(occurrenceId);
            ReminderReceiver.dispatchNextQueued(context);
        }
    }
}
