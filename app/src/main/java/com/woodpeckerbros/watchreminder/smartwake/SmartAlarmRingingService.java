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
import com.woodpeckerbros.watchreminder.R;

public final class SmartAlarmRingingService extends Service {
    private static final String CHANNEL = "smart_alarm_ringing_v1";
    private static final int NOTIFICATION_ID = 0x534d5706;
    private AlertFeedback feedback;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static void start(Context context) {
        ContextCompat.startForegroundService(context, new Intent(context, SmartAlarmRingingService.class));
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
        SmartAlarmStore settings = new SmartAlarmStore(this);
        feedback = AlertFeedback.startSmartAlarm(this, settings);
        handler.postDelayed(this::stopSelf, settings.alertDurationSeconds() * 1000L + 500L);
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (feedback != null) { feedback.stop(); feedback = null; }
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
