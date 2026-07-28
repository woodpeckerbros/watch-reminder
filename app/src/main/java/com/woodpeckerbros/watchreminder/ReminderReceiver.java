package com.woodpeckerbros.watchreminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "reminder_alerts_no_system_vibration_v2";

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pendingResult = goAsync();
        dispatchIntent(context, intent, "receiver", pendingResult::finish);
    }

    public static void dispatchIntent(Context context, Intent intent, String source, Runnable completion) {
        if (intent == null) {
            AppLog.w(context, source + " dispatch null intent");
            finish(completion);
            return;
        }
        String reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID);
        String reminderName = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_NAME);
        long scheduledAt = ReminderScheduler.floorToMinute(intent.getLongExtra(ReminderScheduler.EXTRA_SCHEDULED_AT, System.currentTimeMillis()));
        long originalScheduledAt = ReminderScheduler.floorToMinute(intent.getLongExtra(ReminderScheduler.EXTRA_ORIGINAL_SCHEDULED_AT, scheduledAt));
        int day = intent.getIntExtra(ReminderScheduler.EXTRA_DAY, -1);
        boolean isSnooze = intent.getBooleanExtra(ReminderScheduler.EXTRA_IS_SNOOZE, false);
        AppLog.d(context, source + " dispatch id=" + reminderId
                + " name=" + reminderName
                + " at=" + NextReminderCalculator.formatDateTime(scheduledAt)
                + " original=" + NextReminderCalculator.formatDateTime(originalScheduledAt)
                + " now=" + NextReminderCalculator.formatDateTime(System.currentTimeMillis())
                + " delayMs=" + (System.currentTimeMillis() - scheduledAt)
                + " day=" + day
                + " snooze=" + isSnooze);
        if (reminderId == null) {
            AppLog.w(context, source + " skipped missing reminderId");
            finish(completion);
            return;
        }
        if (reminderName == null || reminderName.trim().isEmpty()) {
            reminderName = "תזכורת";
        }

        Reminder reminder = new ReminderStore(context).find(reminderId);
        AppLog.d(context, source + " loaded reminder exists=" + (reminder != null)
                + " enabled=" + (reminder != null && reminder.enabled)
                + " critical=" + (reminder != null && reminder.critical));
        if (reminder != null && reminder.name != null && !reminder.name.trim().isEmpty()) {
            reminderName = reminder.name;
        }
        if (reminder != null && reminder.critical) {
            fire(context, reminderId, reminderName, scheduledAt, originalScheduledAt, day, isSnooze);
            finish(completion);
            return;
        }

        final String finalReminderName = reminderName;
        WearStateGate.evaluate(context, defer -> {
            try {
                AppLog.d(context, source + " wear gate result id=" + reminderId + " defer=" + defer);
                if (defer) {
                    deferToQueue(context, reminderId, finalReminderName, scheduledAt, originalScheduledAt, day, isSnooze);
                } else {
                    fireAfterWearStateEvaluation(
                            context,
                            reminderId,
                            finalReminderName,
                            scheduledAt,
                            originalScheduledAt,
                            day,
                            isSnooze
                    );
                }
            } finally {
                finish(completion);
            }
        });
    }

    private static void finish(Runnable completion) {
        if (completion != null) {
            completion.run();
        }
    }

    public static void fire(Context context, String reminderId, String reminderName, long scheduledAt, int day, boolean isSnooze) {
        fire(context, reminderId, reminderName, scheduledAt, scheduledAt, day, isSnooze);
    }

    public static void fire(Context context, String reminderId, String reminderName, long scheduledAt, long originalScheduledAt, int day, boolean isSnooze) {
        fire(context, reminderId, reminderName, scheduledAt, originalScheduledAt, day, isSnooze, true);
    }

    private static void fireAfterWearStateEvaluation(Context context, String reminderId, String reminderName, long scheduledAt, long originalScheduledAt, int day, boolean isSnooze) {
        fire(context, reminderId, reminderName, scheduledAt, originalScheduledAt, day, isSnooze, false);
    }

    private static void fire(Context context, String reminderId, String reminderName, long scheduledAt, long originalScheduledAt, int day, boolean isSnooze, boolean checkKnownWearState) {
        Reminder reminder = new ReminderStore(context).find(reminderId);
        if (reminder == null || !reminder.enabled) {
            AppLog.w(context, "fire skipped missing/disabled id=" + reminderId);
            new ReminderSnoozeStore(context).delete(reminderId);
            return;
        }
        if (reminder.isAnnualEvent()) {
            reminderName = AnnualReminderHelper.displayName(reminder, originalScheduledAt);
        } else if (reminder.name != null && !reminder.name.trim().isEmpty()) {
            reminderName = reminder.name;
        }
        if (checkKnownWearState && !reminder.critical && WearStateGate.shouldDeferKnown(context)) {
            AppLog.w(context, "fire deferred by wear state id=" + reminderId + " at=" + NextReminderCalculator.formatDateTime(scheduledAt));
            deferToQueue(context, reminderId, reminderName, scheduledAt, originalScheduledAt, day, isSnooze);
            return;
        }
        fireNow(context, reminder, reminderId, reminderName, scheduledAt, originalScheduledAt, day, isSnooze, false);
    }

    private static void deferToQueue(Context context, String reminderId, String reminderName, long scheduledAt, long originalScheduledAt, int day, boolean isSnooze) {
        Reminder reminder = new ReminderStore(context).find(reminderId);
        if (reminder == null || !reminder.enabled) {
            AppLog.w(context, "defer skipped missing/disabled id=" + reminderId);
            return;
        }
        if (reminder.isAnnualEvent()) {
            reminderName = AnnualReminderHelper.displayName(reminder, originalScheduledAt);
        } else if (reminder.name != null && !reminder.name.trim().isEmpty()) {
            reminderName = reminder.name;
        }
        fireNow(context, reminder, reminderId, reminderName, scheduledAt, originalScheduledAt, day, isSnooze, true);
        WearStateGate.defer(context, reminderId, reminderName, originalScheduledAt);
    }

    private static void fireNow(Context context, Reminder reminder, String reminderId, String reminderName, long scheduledAt, long originalScheduledAt, int day, boolean isSnooze, boolean deferred) {
        ReminderEventStore eventStore = new ReminderEventStore(context);
        if (!reminder.isPeriodic() && !reminder.isAnnualEvent() && eventStore.hasDoneOnDay(reminderId, scheduledAt)) {
            AppLog.w(context, "fire skipped already done id=" + reminderId + " at=" + NextReminderCalculator.formatDateTime(scheduledAt));
            new ReminderSnoozeStore(context).delete(reminderId);
            ReminderScheduler.cancelDeferredRetry(context, reminderId);
            return;
        }
        ReminderEventStore.Event existing = eventStore.findReminderOccurrence(reminderId, scheduledAt);
        boolean pendingSnooze = existing != null
                && (ReminderEventStore.STATUS_SNOOZED.equals(existing.status)
                || ReminderEventStore.STATUS_AUTO_SNOOZED.equals(existing.status));
        if (existing == null && eventStore.hasReminderOccurrence(reminderId, scheduledAt)) {
            AppLog.w(context, "fire skipped handled occurrence id=" + reminderId + " at=" + NextReminderCalculator.formatDateTime(scheduledAt));
            if (isSnooze) {
                new ReminderSnoozeStore(context).delete(reminderId);
            }
            ReminderScheduler.cancelDeferredRetry(context, reminderId);
            return;
        }
        if (existing != null && !(isSnooze && pendingSnooze)) {
            AppLog.w(context, "fire skipped existing event id=" + reminderId + " status=" + existing.status);
            return;
        }
        if (isSnooze) {
            new ReminderSnoozeStore(context).delete(reminderId);
        }
        String occurrenceId = reminderId + ":" + scheduledAt + ":" + System.currentTimeMillis();
        eventStore.markFired(occurrenceId, reminderId, reminderName, reminder.description, scheduledAt, isSnooze);
        AppLog.d(context, "fire markFired occurrence=" + occurrenceId + " name=" + reminderName + " at=" + NextReminderCalculator.formatDateTime(scheduledAt));
        if (!isSnooze && (day != -1 || reminder.isPeriodic() || reminder.isAnnualEvent())) {
            ReminderScheduler.scheduleNext(context, reminder, day);
        }
        ReminderAlertQueueStore.QueuedAlert alert = new ReminderAlertQueueStore.QueuedAlert(
                occurrenceId,
                reminderId,
                reminderName,
                scheduledAt,
                originalScheduledAt,
                day,
                isSnooze
        );
        if (deferred) {
            new ReminderAlertQueueStore(context).enqueueDeferred(alert);
            AppLog.w(context, "fire queued deferred occurrence=" + occurrenceId);
            return;
        }
        if (!new ReminderAlertQueueStore(context).claimOrEnqueue(alert)) {
            AppLog.w(context, "fire queued behind active occurrence=" + occurrenceId);
            return;
        }
        ReminderScheduler.scheduleAutoSnooze(context, occurrenceId, reminderId, reminderName, originalScheduledAt);
        showNotification(context, occurrenceId, reminderId, reminderName, scheduledAt, originalScheduledAt, day, isSnooze);

    }

    public static void dispatchNextQueued(Context context) {
        if (WearStateGate.shouldDeferKnown(context)) {
            AppLog.d(context, "dispatchNextQueued deferred by wear state");
            DeferredWearStateService.start(context);
            return;
        }
        ReminderAlertQueueStore.QueuedAlert alert = new ReminderAlertQueueStore(context).popNext();
        if (alert == null) {
            AppLog.d(context, "dispatchNextQueued empty");
            return;
        }
        AppLog.d(context, "dispatchNextQueued occurrence=" + alert.occurrenceId + " name=" + alert.reminderName);
        ReminderScheduler.scheduleAutoSnooze(context, alert.occurrenceId, alert.reminderId, alert.reminderName, alert.originalScheduledAt);
        showNotification(context, alert.occurrenceId, alert.reminderId, alert.reminderName, alert.scheduledAt, alert.originalScheduledAt, alert.day, alert.snooze);
    }

    static void showNotification(Context context, String occurrenceId, String reminderId, String reminderName, long scheduledAt, long originalScheduledAt, int day, boolean isSnooze) {
        createChannel(context);
        AppLog.d(context, "showNotification occurrence=" + occurrenceId + " name=" + reminderName + " at=" + NextReminderCalculator.formatDateTime(scheduledAt));
        ReminderAlertQueueStore.QueuedAlert activeAlert = new ReminderAlertQueueStore(context).getActiveAlert(occurrenceId);
        int alertCount = activeAlert == null ? 1 : activeAlert.count();
        AppLog.d(context, "showNotification activeAlert=" + (activeAlert != null)
                + " count=" + alertCount
                + " fullScreen=" + AppLog.fullScreenIntentAllowed(context)
                + " notifications=" + AppLog.notificationPermissionAllowed(context));
        String notificationText = alertCount > 1
                ? reminderName + " (" + alertCount + " תאריכים שפוספסו)"
                : reminderName;
        Intent alertIntent = new Intent(context, ReminderAlertActivity.class)
                .putExtra(ReminderScheduler.EXTRA_OCCURRENCE_ID, occurrenceId)
                .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
                .putExtra(ReminderScheduler.EXTRA_REMINDER_NAME, reminderName)
                .putExtra(ReminderScheduler.EXTRA_SCHEDULED_AT, scheduledAt)
                .putExtra(ReminderScheduler.EXTRA_ORIGINAL_SCHEDULED_AT, originalScheduledAt)
                .putExtra(ReminderScheduler.EXTRA_DAY, day)
                .putExtra(ReminderScheduler.EXTRA_IS_SNOOZE, isSnooze)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                occurrenceId.hashCode(),
                alertIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(notificationText)
                .setStyle(new Notification.BigTextStyle().bigText(notificationText))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .setVibrate(new long[]{0})
                .setSound(null)
                .setDefaults(0)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            AppLog.w(context, "showNotification skipped no NotificationManager occurrence=" + occurrenceId);
            return;
        }
        manager.notify(occurrenceId.hashCode(), builder.build());
        AppLog.d(context, "showNotification notified occurrence=" + occurrenceId + " notificationId=" + occurrenceId.hashCode());
    }

    private static void createChannel(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            AppLog.w(context, "createChannel skipped no NotificationManager");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            for (NotificationChannel existing : manager.getNotificationChannels()) {
                if (existing.getId().startsWith("reminder_alerts") && !CHANNEL_ID.equals(existing.getId())) {
                    manager.deleteNotificationChannel(existing.getId());
                }
            }
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                UiText.t(context, "תזכורות רגילות"),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Reminder alerts");
        channel.enableVibration(false);
        channel.setVibrationPattern(new long[]{0});
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }
}
