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
    private static final String CHANNEL = "smart_alarm_ringing_v1";
    private static final int NOTIFICATION_ID = 0x534d5706;
    private AlertFeedback feedback;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int activeAlarmId;
    private long activeTargetAt;

    public static boolean start(Context context, int alarmId, long targetAt) {
        try {
            ContextCompat.startForegroundService(context, new Intent(context, SmartAlarmRingingService.class)
                    .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt));
            return true;
        } catch (RuntimeException error) {
            AppLog.w(context, "SmartAlarm ringing service start rejected; full-screen activity must own feedback: "
                    + error.getClass().getSimpleName());
            return false;
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, SmartAlarmRingingService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Smart Alarm ringing", NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFICATION_ID, notification(1, 0L));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        int alarmId = intent == null ? 1 : intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        long targetAt = intent == null ? 0L : intent.getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
        activeAlarmId = alarmId;
        activeTargetAt = targetAt;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, notification(alarmId, targetAt));
        if (feedback != null) feedback.stop();
        handler.removeCallbacksAndMessages(null);
        SmartAlarmStore settings = new SmartAlarmStore(this, alarmId);
        int alertDurationMs = settings.alertDurationSeconds() * 1000;
        holdCpuWhileRinging(alertDurationMs);
        // The full-screen notification can win the race and open the activity before this FGS
        // reaches onStartCommand.  Keep the service in that case as the screen guard, but leave
        // feedback to the already-visible activity so it is not started twice.
        if (!SmartAlarmAlertActivity.isShowing(alarmId, targetAt)) {
            feedback = AlertFeedback.startSmartAlarm(this, settings);
        }
        handler.postDelayed(() -> guardAlertScreen(alarmId, targetAt), 1_200L);
        handler.postDelayed(this::stopSelf, alertDurationMs + 1_000L);
        return START_NOT_STICKY;
    }

    private Notification notification(int alarmId, long targetAt) {
        Notification.Builder builder = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Smart Alarm").setContentText("ההתראה פעילה").setOngoing(true);
        if (targetAt > 0L) {
            builder.setContentIntent(SmartAlarmActions.openPendingIntent(this, alarmId, targetAt))
                    .addAction(SmartAlarmActions.openAction(this, alarmId, targetAt))
                    .addAction(SmartAlarmActions.snoozeAction(this, alarmId, targetAt))
                    .addAction(SmartAlarmActions.dismissAction(this, alarmId, targetAt));
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

    private void guardAlertScreen(int alarmId, long targetAt) {
        if (SmartAlarmAlertActivity.isShowing(alarmId, targetAt)) {
            // The activity now owns user feedback; the service stays alive only to guard its
            // foreground state and to recover it if Wear OS sends it back to Home.
            if (feedback != null) { feedback.stop(); feedback = null; }
            AppLog.d(this, "SmartAlarm screen guard confirmed visible id=" + alarmId);
        } else {
            ensureAlertScreen(alarmId, targetAt);
        }
        if (activeAlarmId == alarmId && activeTargetAt == targetAt)
            handler.postDelayed(() -> guardAlertScreen(alarmId, targetAt), 2_000L);
    }

    private void ensureAlertScreen(int alarmId, long targetAt) {
        Intent alert = new Intent(this, SmartAlarmAlertActivity.class)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt)
                .putExtra("reason", "ringing_service_fallback")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
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
        handler.removeCallbacksAndMessages(null);
        if (feedback != null) { feedback.stop(); feedback = null; }
        releaseWakeLock();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
