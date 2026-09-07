package com.woodpeckerbros.watchreminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Guardian entry point when the main package's own AlarmManager entries disappeared. */
public final class GuardianRecoveryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent source) {
        if (source == null || !GuardianBridge.ACTION_RECOVER.equals(source.getAction())) return;
        PendingResult result = goAsync();
        String id = source.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID);
        String name = source.getStringExtra(ReminderScheduler.EXTRA_REMINDER_NAME);
        long scheduledAt = source.getLongExtra(ReminderScheduler.EXTRA_SCHEDULED_AT, 0L);
        long originalAt = source.getLongExtra(ReminderScheduler.EXTRA_ORIGINAL_SCHEDULED_AT, scheduledAt);
        int day = source.getIntExtra(ReminderScheduler.EXTRA_DAY, -1);
        boolean snooze = source.getBooleanExtra(ReminderScheduler.EXTRA_IS_SNOOZE, false);
        AppLog.w(context, "guardian recovery received id=" + id + " at="
                + NextReminderCalculator.formatDateTime(scheduledAt));
        Intent reminder = new Intent(context, ReminderReceiver.class)
                .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, id)
                .putExtra(ReminderScheduler.EXTRA_REMINDER_NAME, name)
                .putExtra(ReminderScheduler.EXTRA_SCHEDULED_AT, scheduledAt)
                .putExtra(ReminderScheduler.EXTRA_ORIGINAL_SCHEDULED_AT, originalAt)
                .putExtra(ReminderScheduler.EXTRA_DAY, day)
                .putExtra(ReminderScheduler.EXTRA_IS_SNOOZE, snooze);
        ReminderReceiver.dispatchIntent(context, reminder, "guardian", () ->
                new Thread(() -> {
                    try { BootReceiver.recover(context.getApplicationContext(), false); }
                    finally { result.finish(); }
                }, "wr-guardian-recovery").start());
    }
}
