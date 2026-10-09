package com.woodpeckerbros.watchreminder.smartalarm;

import com.woodpeckerbros.watchreminder.reminder.*;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ActivityOptions;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.woodpeckerbros.watchreminder.reminder.AlertFeedback;
import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.R;

public final class SmartAlarmRingingService extends Service {
    static final long DIRECT_BOOT_ALERT_DURATION_MS = 120_000L;
    private static final String CHANNEL = "smart_alarm_ringing_v1";
    private static final int NOTIFICATION_ID = 0x534d5706;
    private static final long FULL_SCREEN_REPOST_INTERVAL_MS = 8_000L;
    private static final String EXTRA_DIRECT_BOOT_FALLBACK = "direct_boot_fallback";
    private static java.lang.ref.WeakReference<SmartAlarmRingingService> activeService;
    private static final long UI_FEEDBACK_FALLBACK_DELAY_MS = 750L;
    private AlertFeedback feedback;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int activeAlarmId;
    private long activeTargetAt;
    private boolean activeWakeCheckEscalation;
    private long lastFullScreenRepostAt;

    public static boolean start(Context context, int alarmId, long targetAt) {
        return start(context, alarmId, targetAt, false);
    }

    public static boolean start(Context context, int alarmId, long targetAt,
                                boolean wakeCheckEscalation) {
        try {
            ContextCompat.startForegroundService(context, new Intent(context, SmartAlarmRingingService.class)
                    .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt)
                    .putExtra("feedback_end_at", new SmartAlarmStateStore(context, alarmId).feedbackEndAt(targetAt))
                    .putExtra("wake_check_escalation", wakeCheckEscalation));
            return true;
        } catch (RuntimeException error) {
            AppLog.w(context, "SmartAlarm ringing service start rejected; full-screen activity must own feedback: "
                    + error.getClass().getSimpleName());
            return false;
        }
    }

    static boolean startDirectBoot(Context context, int alarmId, long targetAt) {
        try {
            ContextCompat.startForegroundService(context, new Intent(context, SmartAlarmRingingService.class)
                    .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt)
                    .putExtra(EXTRA_DIRECT_BOOT_FALLBACK, true));
            return true;
        } catch (RuntimeException error) {
            AppLog.e(context, "SmartAlarm direct-boot ringing service start rejected", error);
            return false;
        }
    }

    public static void stop(Context context) {
        SmartAlarmRingingService service = activeService == null ? null : activeService.get();
        if (service != null) service.stopOwnedFeedback();
        context.stopService(new Intent(context, SmartAlarmRingingService.class));
    }

    /** Late Activity cleanup must never stop a newer snooze/final presentation. */
    static void stop(Context context, int alarmId, long targetAt) {
        SmartAlarmRingingService service = activeService == null ? null : activeService.get();
        if (service != null && service.activeAlarmId == alarmId && service.activeTargetAt == targetAt) {
            stop(context);
        }
    }

    static void quiesce(int alarmId, long targetAt) {
        SmartAlarmRingingService service = activeService == null ? null : activeService.get();
        if (service != null && service.activeAlarmId == alarmId && service.activeTargetAt == targetAt) {
            service.stopOwnedFeedback();
        }
    }

    private void stopOwnedFeedback() {
        handler.removeCallbacksAndMessages(null);
        if (feedback != null) { feedback.stop(); feedback = null; }
        releaseWakeLock();
    }

    @Override public void onCreate() {
        super.onCreate();
        activeService = new java.lang.ref.WeakReference<>(this);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Smart Alarm ringing", NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFICATION_ID, notification(1, 0L, false));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        int alarmId = intent == null ? 1 : intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        long targetAt = intent == null ? 0L : intent.getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
        boolean wakeCheckEscalation = intent != null
                && intent.getBooleanExtra("wake_check_escalation", false);
        boolean directBootFallback = intent != null
                && intent.getBooleanExtra(EXTRA_DIRECT_BOOT_FALLBACK, false);
        if (!directBootFallback) {
            SmartAlarmStateStore state = new SmartAlarmStateStore(this, alarmId);
            long requestedFeedbackEndAt = intent == null ? 0L : intent.getLongExtra("feedback_end_at", 0L);
            if (!state.presentationMatches(targetAt, requestedFeedbackEndAt)
                    || state.presentationTimeoutHandled()
                    || (!wakeCheckEscalation && (!state.fired(targetAt) || state.dismissed(targetAt)))) {
                AppLog.d(this, "SmartAlarm stale ringing service start ignored id=" + alarmId
                        + " target=" + targetAt);
                if (activeTargetAt == 0L) stopSelf(startId);
                return START_NOT_STICKY;
            }
        }
        activeAlarmId = alarmId;
        activeTargetAt = targetAt;
        activeWakeCheckEscalation = wakeCheckEscalation;
        // Let SystemUI process the original exact-alarm full-screen notification first. If a
        // competing morning card takes the screen, a fresh post follows only after this interval.
        lastFullScreenRepostAt = android.os.SystemClock.uptimeMillis();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, directBootFallback
                ? directBootNotification() : notification(alarmId, targetAt, wakeCheckEscalation));
        if (feedback != null) feedback.stop();
        handler.removeCallbacksAndMessages(null);
        if (directBootFallback) {
            int durationMs = (int) DIRECT_BOOT_ALERT_DURATION_MS;
            holdCpuWhileRinging(durationMs);
            feedback = AlertFeedback.startEmergencyAlarm(this, durationMs);
            handler.postDelayed(this::stopSelf, DIRECT_BOOT_ALERT_DURATION_MS + 1_000L);
            AppLog.w(this, "SmartAlarm direct-boot bounded ringing started id=" + alarmId
                    + " target=" + targetAt + " durationMs=" + durationMs);
            return START_NOT_STICKY;
        }
        SmartAlarmStore settings = new SmartAlarmStore(this, alarmId);
        int alertDurationMs = SmartAlarmPresentationTiming.feedbackRemaining(System.currentTimeMillis(),
                new SmartAlarmStateStore(this, alarmId).feedbackEndAt(targetAt),
                settings.alertDurationSeconds() * 1000);
        if (alertDurationMs <= 0) { stopSelf(); return START_NOT_STICKY; }
        holdCpuWhileRinging(alertDurationMs);
        // The full-screen notification can win the race and open the activity before this FGS
        // reaches onStartCommand.  Keep the service in that case as the screen guard, but leave
        // feedback to the already-visible activity so it is not started twice.
        if (!SmartAlarmAlertActivity.hasPresentation(alarmId, targetAt)) {
            ensureAlertScreen(alarmId, targetAt, wakeCheckEscalation);
        }
        // Give the controls their first frame before ringing. If the system suppresses the UI,
        // still ring with a bounded fallback rather than dropping the alarm altogether.
        handler.postDelayed(() -> {
            if (!SmartAlarmAlertActivity.isShowing(alarmId, targetAt)) {
                int remaining = SmartAlarmPresentationTiming.feedbackRemaining(System.currentTimeMillis(),
                        new SmartAlarmStateStore(this, alarmId).feedbackEndAt(targetAt),
                        settings.alertDurationSeconds() * 1000);
                feedback = AlertFeedback.startSmartAlarm(this, settings, remaining);
                AppLog.w(this, "SmartAlarm feedback fallback; controls not visible id=" + alarmId
                        + " target=" + targetAt + " remainingMs=" + remaining);
            }
        }, UI_FEEDBACK_FALLBACK_DELAY_MS);
        handler.postDelayed(() -> guardAlertScreen(
                alarmId, targetAt, wakeCheckEscalation), 1_200L);
        handler.postDelayed(this::stopSelf, alertDurationMs + 1_000L);
        return START_NOT_STICKY;
    }

    private Notification directBootNotification() {
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Smart Alarm")
                .setContentText("ההתראה פעילה לאחר הפעלה מחדש")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
    }

    private Notification notification(int alarmId, long targetAt, boolean wakeCheckEscalation) {
        Notification.Builder builder = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Smart Alarm").setContentText("ההתראה פעילה").setOngoing(true);
        if (targetAt > 0L) {
            builder.setContentIntent(SmartAlarmActions.openPendingIntent(
                    this, alarmId, targetAt, wakeCheckEscalation));
            if (wakeCheckEscalation) {
                builder.addAction(SmartAlarmActions.dismissAction(
                        this, alarmId, targetAt, true));
            } else {
                builder.addAction(SmartAlarmActions.openAction(this, alarmId, targetAt));
                if (!new SmartAlarmStateStore(this, alarmId).finalDeadlineDelivered()) {
                    builder.addAction(SmartAlarmActions.snoozeAction(this, alarmId, targetAt));
                }
                builder.addAction(SmartAlarmActions.dismissAction(this, alarmId, targetAt));
            }
        }
        return builder.build();
    }

    private void holdCpuWhileRinging(int alertDurationMs) {
        releaseWakeLock();
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager == null) {
            AppLog.w(this, "SmartAlarm partial wake lock unavailable");
            return;
        }
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                getPackageName() + ":SmartAlarmRinging");
        wakeLock.setReferenceCounted(false);
        long timeoutMs = Math.max(5_000L, alertDurationMs + 2_000L);
        wakeLock.acquire(timeoutMs);
        AppLog.d(this, "SmartAlarm partial wake lock acquired timeoutMs=" + timeoutMs);
    }

    private void releaseWakeLock() {
        if (wakeLock == null) return;
        if (wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    private void guardAlertScreen(int alarmId, long targetAt, boolean wakeCheckEscalation) {
        SmartAlarmStateStore state = new SmartAlarmStateStore(this, alarmId);
        if (state.presentationTimeoutHandled()
                || System.currentTimeMillis() >= state.feedbackEndAt(targetAt)
                || (!wakeCheckEscalation && (!state.fired(targetAt) || state.dismissed(targetAt)))) {
            stopSelf();
            return;
        }
        if (SmartAlarmAlertActivity.isShowing(alarmId, targetAt)) {
            // The activity now owns user feedback; the service stays alive only to guard its
            // foreground state and to recover it if Wear OS sends it back to Home.
            if (feedback != null) { feedback.stop(); feedback = null; }
            AppLog.d(this, "SmartAlarm screen guard confirmed visible id=" + alarmId);
        } else {
            ensureAlertScreen(alarmId, targetAt, wakeCheckEscalation);
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastFullScreenRepostAt >= FULL_SCREEN_REPOST_INTERVAL_MS) {
                lastFullScreenRepostAt = now;
                if (!wakeCheckEscalation) SmartAlarmReceiver.repostFullScreen(this, alarmId, targetAt);
            }
        }
        if (activeAlarmId == alarmId && activeTargetAt == targetAt
                && activeWakeCheckEscalation == wakeCheckEscalation)
            handler.postDelayed(() -> guardAlertScreen(
                    alarmId, targetAt, wakeCheckEscalation), 2_000L);
    }

    private void ensureAlertScreen(int alarmId, long targetAt, boolean wakeCheckEscalation) {
        Intent alert = new Intent(this, SmartAlarmAlertActivity.class)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt)
                .putExtra("reason", "ringing_service_fallback")
                .putExtra("wake_check_escalation", wakeCheckEscalation)
                // Recreate the dedicated alarm task.  OnePlus Health can place its sleep-report
                // full-screen Activity above ours without reliably pausing our Activity; merely
                // addressing the existing task then returns START_TASK_TO_FRONT but leaves the
                // system overlay on top.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle creatorOptions = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            creatorOptions = options.toBundle();
        }
        PendingIntent open = PendingIntent.getActivity(this, 0x534d5900 + alarmId, alert,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE, creatorOptions);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                open.send(this, 0, null, null, null, null, options.toBundle());
            } else {
                open.send();
            }
            AppLog.w(this, "SmartAlarm screen fallback launch sent id=" + alarmId);
        } catch (PendingIntent.CanceledException | RuntimeException error) {
            AppLog.e(this, "SmartAlarm screen fallback launch failed id=" + alarmId, error);
        }
    }

    @Override public void onDestroy() {
        stopOwnedFeedback();
        if (activeService != null && activeService.get() == this) activeService = null;
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
