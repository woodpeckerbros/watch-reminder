package com.woodpeckerbros.watchreminder.reminder;

import com.woodpeckerbros.watchreminder.*;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps the user-enabled reminder subsystem observable to the OS. AlarmManager remains the
 * delivery mechanism; this service only performs a deferred, non-waking health check each hour.
 */
public final class ReminderMonitoringService extends Service {
    static final long HEALTH_CHECK_INTERVAL_MS = 60 * 60_000L;
    private static final long DEFERRED_INITIAL_HEALTH_CHECK_MS = 30_000L;
    private static final String CHANNEL_ID = "reminder_monitoring";
    private static final int NOTIFICATION_ID = 2002;
    private static final String EXTRA_DEFER_INITIAL_HEALTH_CHECK = "defer_initial_health_check";
    private static final String EXTRA_REFRESH_NOTIFICATION = "refresh_notification";
    private static final String MONITORING_TEXT_HEBREW = "ניטור תזכורות פעיל";
    private static final String MONITORING_TEXT_ENGLISH = "Active reminder monitoring";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService maintenanceExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "wr-reminder-monitoring");
        thread.setDaemon(true);
        return thread;
    });
    private boolean foregroundStarted;
    private boolean healthCheckInFlight;
    private final Runnable healthCheckRunnable = this::runHealthCheck;

    public static boolean isRequired(Context context) {
        if (!new ReminderSettings(context).serviceEnabled()) return false;
        for (Reminder reminder : new ReminderStore(context).getAll()) {
            if (reminder.enabled) return true;
        }
        return false;
    }

    public static void start(Context context) {
        start(context, false);
    }

    /** Starts the FGS immediately but leaves its first disk/schedule verification for later. */
    public static void startDeferredHealthCheck(Context context) {
        start(context, true);
    }

    private static void start(Context context, boolean deferInitialHealthCheck) {
        if (!isRequired(context)) {
            stop(context);
            return;
        }
        try {
            Intent intent = new Intent(context, ReminderMonitoringService.class)
                    .putExtra(EXTRA_DEFER_INITIAL_HEALTH_CHECK, deferInitialHealthCheck);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
            else context.startService(intent);
            AppLog.d(context, "ReminderMonitoringService start requested");
        } catch (Exception exception) {
            AppLog.e(context, "ReminderMonitoringService start failed", exception);
        }
    }

    public static void stop(Context context) {
        try { context.stopService(new Intent(context, ReminderMonitoringService.class)); }
        catch (Exception ignored) { }
    }

    /** Refreshes the foreground notification after the user changes Zmanio's language. */
    public static void refreshNotification(Context context) {
        if (!isRequired(context)) return;
        try {
            Intent intent = new Intent(context, ReminderMonitoringService.class)
                    .putExtra(EXTRA_REFRESH_NOTIFICATION, true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (Exception exception) {
            AppLog.e(context, "ReminderMonitoringService notification refresh failed", exception);
        }
    }

    /** Included in exported diagnostics for the 24-hour battery test. */
    public static String diagnosticSummary(Context context) {
        ReminderMonitoringState state = new ReminderMonitoringState(context);
        int active = 0;
        for (Reminder reminder : new ReminderStore(context).getAll()) if (reminder.enabled) active++;
        boolean running = false;
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo info : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (ReminderMonitoringService.class.getName().equals(info.service.getClassName())) {
                    running = true;
                    break;
                }
            }
        }
        return "Reminder monitoring running: " + running + '\n'
                + "Monitoring service start: " + format(state.startedAt()) + '\n'
                + "Hourly checks completed: " + state.checkCount() + '\n'
                + "Alarms repaired: " + state.repairCount() + '\n'
                + "Last health check: " + format(state.lastCheckAt()) + '\n'
                + "Next health check expected: " + format(state.nextCheckAt()) + '\n'
                + "Active reminders: " + active + '\n';
    }

    private static String format(long time) {
        return time == 0L ? "never" : NextReminderCalculator.formatDateTime(time);
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForegroundWithMonitoringNotification();
        ReminderMonitoringState state = new ReminderMonitoringState(this);
        boolean recreated = state.startedAt() != 0L;
        state.markStarted(System.currentTimeMillis());
        AppLog.d(this, "ReminderMonitoringService startForeground");
        if (recreated) AppLog.d(this, "ReminderMonitoringService restart after process recreation");
    }

    private void startForegroundWithMonitoringNotification() {
        Notification.Builder notificationBuilder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(monitoringTextForAppLanguage(new ReminderSettings(this).language()))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true).setShowWhen(false).setOnlyAlertOnce(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            notificationBuilder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        Notification notification = notificationBuilder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else startForeground(NOTIFICATION_ID, notification);
        foregroundStarted = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRequired(this)) { stopCleanly(); return START_NOT_STICKY; }
        if (intent != null && intent.getBooleanExtra(EXTRA_REFRESH_NOTIFICATION, false)) {
            startForegroundWithMonitoringNotification();
        }
        handler.removeCallbacks(healthCheckRunnable);
        long initialDelay = intent != null && intent.getBooleanExtra(EXTRA_DEFER_INITIAL_HEALTH_CHECK, false)
                ? DEFERRED_INITIAL_HEALTH_CHECK_MS : 0L;
        handler.postDelayed(healthCheckRunnable, initialDelay);
        return START_STICKY;
    }

    private void runHealthCheck() {
        if (healthCheckInFlight) return;
        healthCheckInFlight = true;
        maintenanceExecutor.execute(() -> {
            try {
                if (!isRequired(this)) {
                    handler.post(this::stopCleanly);
                    return;
                }
                long started = System.currentTimeMillis();
                int activeCount = 0;
                for (Reminder reminder : new ReminderStore(this).getAll()) if (reminder.enabled) activeCount++;
                AppLog.d(this, "ReminderMonitoring health check start active=" + activeCount);
                boolean repaired = ReminderScheduler.ensureNearestScheduled(this);
                // The watchdog is an independent recovery alarm. Do not churn it hourly; only
                // refresh it when this check actually repaired the reminder AlarmClock.
                if (repaired) ReminderScheduler.scheduleWatchdog(this);
                long nextAt = started + HEALTH_CHECK_INTERVAL_MS;
                new ReminderMonitoringState(this).recordCheck(started, nextAt, repaired);
                AppLog.d(this, "ReminderMonitoring health check end active=" + activeCount + " repaired=" + repaired);
                // Handler delay is intentionally in-process only: it is deferred while the watch
                // sleeps, rather than creating an alarm that wakes the CPU for maintenance.
                handler.postDelayed(healthCheckRunnable, HEALTH_CHECK_INTERVAL_MS);
            } finally {
                healthCheckInFlight = false;
            }
        });
    }

    private void stopCleanly() {
        handler.removeCallbacks(healthCheckRunnable);
        maintenanceExecutor.shutdownNow();
        new ReminderMonitoringState(this).clearMaintenanceSchedule();
        if (foregroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE);
            else stopForeground(true);
            foregroundStarted = false;
        }
        stopSelf();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(healthCheckRunnable);
        new ReminderMonitoringState(this).clearMaintenanceSchedule();
        if (foregroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE);
            else stopForeground(true);
            foregroundStarted = false;
        }
        super.onDestroy();
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // Some OEM launchers treat clearing recents as a service hint. Reassert all available
        // in-package recovery paths while callbacks are still allowed.
        AppLog.w(this, "ReminderMonitoringService task removed; reasserting recovery paths");
        ReminderScheduler.scheduleNearest(this);
        ReminderScheduler.scheduleWatchdog(this);
        ReminderRecoveryJobService.schedule(this);
        super.onTaskRemoved(rootIntent);
    }
    @Override public IBinder onBind(Intent intent) { return null; }

    /**
     * This foreground notification must follow the language explicitly selected in Zmanio.
     * In particular, its wording must not change merely because Wear OS has a different locale.
     * "Auto" is kept deterministic here: English is the neutral fallback until the user chooses
     * Hebrew or English in Zmanio's language setting.
     */
    static String monitoringTextForAppLanguage(String language) {
        return ReminderSettings.LANGUAGE_HEBREW.equals(language)
                ? MONITORING_TEXT_HEBREW
                : MONITORING_TEXT_ENGLISH;
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                UiText.t(this, "ניטור תזכורות"), NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        channel.enableVibration(false);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }
}
