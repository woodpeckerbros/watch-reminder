package com.woodpeckerbros.watchreminder;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AppLog {
    private static final String TAG = "WatchReminder";
    private static final String PREFS_NAME = "app_logs";
    private static final String KEY_TEXT = "text";
    private static final int MAX_CHARS = 160_000;
    private static final ExecutorService LOG_WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "wr-log-writer");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile int clearGeneration;

    private AppLog() {
    }

    public static void d(Context context, String message) {
        Log.d(TAG, message);
        append(context, "D", message);
    }

    public static void w(Context context, String message) {
        Log.w(TAG, message);
        append(context, "W", message);
    }

    public static void e(Context context, String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
        append(context, "E", message + " | " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
    }

    public static String exportText(Context context) {
        flushPendingWrites();
        StringBuilder builder = new StringBuilder();
        builder.append("WristRemind diagnostics\n");
        builder.append("Generated: ").append(format(System.currentTimeMillis())).append('\n');
        builder.append("SDK: ").append(Build.VERSION.SDK_INT).append('\n');
        builder.append("Exact alarms: ").append(ReminderScheduler.canScheduleExactAlarms(context)).append('\n');
        builder.append("Notifications: ").append(notificationPermissionAllowed(context)).append('\n');
        builder.append("Full screen intent: ").append(fullScreenIntentAllowed(context)).append('\n');
        builder.append("Battery optimization ignored: ").append(batteryOptimizationIgnored(context)).append('\n');
        builder.append("Foreground service enabled: ").append(new ReminderSettings(context).serviceEnabled()).append('\n');
        builder.append('\n').append("Logs:\n");
        builder.append(text(context));
        builder.append('\n').append("Computed reminder state:\n");
        builder.append(computedReminderState(context));
        builder.append('\n').append("Reminders:\n").append(pref(context, "reminders"));
        builder.append('\n').append("Events:\n").append(pref(context, "reminder_events"));
        builder.append('\n').append("Snoozes:\n").append(pref(context, "reminder_snoozes"));
        builder.append('\n').append("Due checker:\n").append(pref(context, "reminder_due_checker"));
        builder.append('\n').append("Alert queue:\n").append(pref(context, "reminder_alert_queue"));
        builder.append('\n').append("Wear state:\n").append(pref(context, "wear_state"));
        builder.append('\n').append("Settings:\n").append(pref(context, "reminder_settings"));
        builder.append('\n').append("Zmanim settings:\n").append(pref(context, "zmanim_settings"));
        builder.append('\n').append("Daf yomi state:\n").append(pref(context, "daf_yomi_state"));
        builder.append('\n').append("Omer state:\n").append(pref(context, "omer_state"));
        builder.append('\n').append("Quiet time rules:\n").append(pref(context, "quiet_time_rules"));
        return builder.toString();
    }

    public static void share(Context context) {
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "WristRemind logs")
                .putExtra(Intent.EXTRA_TEXT, exportText(context));
        context.startActivity(Intent.createChooser(intent, "שליחת לוגים"));
    }

    public static String text(Context context) {
        flushPendingWrites();
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TEXT, "");
    }

    public static void clear(Context context) {
        clearGeneration++;
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_TEXT)
                .apply();
    }

    private static void append(Context context, String level, String message) {
        Context appContext = context.getApplicationContext();
        int generation = clearGeneration;
        LOG_WRITER.execute(() -> appendNow(appContext, generation, level, message));
    }

    private static void appendNow(Context context, int generation, String level, String message) {
        try {
            if (generation != clearGeneration) {
                return;
            }
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String line = format(System.currentTimeMillis()) + " " + level + " " + message + "\n";
            String next = prefs.getString(KEY_TEXT, "") + line;
            if (next.length() > MAX_CHARS) {
                next = next.substring(next.length() - MAX_CHARS);
            }
            prefs.edit().putString(KEY_TEXT, next).apply();
        } catch (Exception ignored) {
        }
    }

    private static void flushPendingWrites() {
        try {
            Future<?> future = LOG_WRITER.submit(() -> {
            });
            future.get(1200, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        }
    }

    private static String format(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date(time));
    }

    public static boolean notificationPermissionAllowed(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean fullScreenIntentAllowed(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null && manager.canUseFullScreenIntent();
    }

    private static boolean batteryOptimizationIgnored(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        android.os.PowerManager powerManager = (android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager != null && powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    private static String computedReminderState(Context context) {
        StringBuilder builder = new StringBuilder();
        try {
            ReminderStore store = new ReminderStore(context);
            ReminderSnoozeStore snoozeStore = new ReminderSnoozeStore(context);
            ReminderEventStore eventStore = new ReminderEventStore(context);
            java.util.List<Reminder> reminders = store.getAll();
            builder.append("count=").append(reminders.size()).append('\n');
            NextReminderCalculator.NextReminder next = NextReminderCalculator.next(context, reminders, snoozeStore, eventStore, true);
            builder.append("globalNext=").append(next == null ? "none" : next.reminderId + " " + next.reminderName + " " + NextReminderCalculator.formatDateTime(next.scheduledAt) + " snoozed=" + next.snoozed).append('\n');
            for (Reminder reminder : reminders) {
                NextReminderCalculator.NextReminder itemNext = NextReminderCalculator.nextForReminder(context, reminder, snoozeStore, eventStore, true);
                long regularAt = NextReminderCalculator.nextRegularAt(context, reminder, eventStore);
                long snoozeAt = NextReminderCalculator.pendingSnoozeAt(reminder.id, snoozeStore);
                builder.append("reminder id=").append(reminder.id)
                        .append(" name=").append(reminder.name)
                        .append(" enabled=").append(reminder.enabled)
                        .append(" critical=").append(reminder.critical)
                        .append(" oneTime=").append(reminder.isOneTime())
                        .append(" periodic=").append(reminder.isPeriodic())
                        .append(" annual=").append(reminder.isAnnualEvent())
                        .append(" useZmanim=").append(reminder.useZmanim)
                        .append(" days=").append(reminder.days)
                        .append(" time=").append(reminder.hour).append(':').append(reminder.minute)
                        .append(" next=").append(itemNext == null ? "none" : NextReminderCalculator.formatDateTime(itemNext.scheduledAt) + " snoozed=" + itemNext.snoozed)
                        .append(" regularAt=").append(regularAt == Long.MAX_VALUE ? "MAX" : NextReminderCalculator.formatDateTime(regularAt))
                        .append(" snoozeAt=").append(snoozeAt == Long.MAX_VALUE ? "none" : NextReminderCalculator.formatDateTime(snoozeAt))
                        .append('\n');
            }
        } catch (Exception exception) {
            builder.append("computed state failed: ").append(exception.getClass().getSimpleName()).append(": ").append(exception.getMessage()).append('\n');
        }
        return builder.toString();
    }

    private static String pref(Context context, String name) {
        return context.getApplicationContext()
                .getSharedPreferences(name, Context.MODE_PRIVATE)
                .getAll()
                .toString();
    }
}
