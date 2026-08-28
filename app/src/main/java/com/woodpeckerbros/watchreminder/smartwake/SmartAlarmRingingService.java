package com.woodpeckerbros.watchreminder.smartwake;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.woodpeckerbros.watchreminder.AlertFeedback;
import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.R;

public final class SmartAlarmRingingService extends Service {
    private static final String CHANNEL = "smart_alarm_ringing_v1";
    private static final int NOTIFICATION_ID = 0x534d5706;
    private AlertFeedback feedback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int alarmId;
    private long targetAt;

    public static void start(Context context, int alarmId, long targetAt) {
        ContextCompat.startForegroundService(context, new Intent(context, SmartAlarmRingingService.class)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, SmartAlarmRingingService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Smart Alarm ringing", NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFICATION_ID, new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Smart Alarm").setContentText("ההתראה פעילה").setOngoing(true).build());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        alarmId = intent == null ? 1 : intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        targetAt = intent == null ? 0L : intent.getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
        if (feedback != null) feedback.stop();
        handler.removeCallbacksAndMessages(null);
        SmartAlarmStore settings = new SmartAlarmStore(this, alarmId);
        feedback = AlertFeedback.startSmartAlarm(this, settings);
        handler.postDelayed(this::handleUnansweredAlarm, settings.alertDurationSeconds() * 1000L + 500L);
        return START_NOT_STICKY;
    }

    private void handleUnansweredAlarm() {
        SmartAlarmStateStore state = new SmartAlarmStateStore(this, alarmId);
        SmartAlarmStore settings = new SmartAlarmStore(this, alarmId);
        if (targetAt > 0L && state.fired(targetAt) && !state.dismissed(targetAt)
                && state.snoozeUsed() < settings.snoozeCount()) {
            AppLog.w(this, "SmartAlarm unanswered; auto-snooze id=" + alarmId
                    + " used=" + state.snoozeUsed() + " of=" + settings.snoozeCount());
            SmartAlarmScheduler.scheduleSnooze(this, alarmId, targetAt, settings.snoozeMinutes());
        } else if (targetAt > 0L && state.fired(targetAt) && !state.dismissed(targetAt)) {
            AppLog.w(this, "SmartAlarm unanswered; snoozes exhausted id=" + alarmId);
            state.dismiss(targetAt);
            SmartAlarmScheduler.scheduleNextAfterHandled(this, alarmId);
        }
        SmartAlarmActions.cancelNotification(this, alarmId);
        stopSelf();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (feedback != null) { feedback.stop(); feedback = null; }
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
