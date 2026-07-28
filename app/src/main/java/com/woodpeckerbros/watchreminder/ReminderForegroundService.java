package com.woodpeckerbros.watchreminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class ReminderForegroundService extends Service {
    private static final String CHANNEL_ID = "reminder_service";
    private static final int NOTIFICATION_ID = 2001;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastCheckAt;

    private final Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            ReminderSettings settings = new ReminderSettings(ReminderForegroundService.this);
            if (!settings.serviceEnabled()) {
                stopSelf();
                return;
            }
            long now = System.currentTimeMillis();
            ReminderDueChecker.dispatchDue(ReminderForegroundService.this, lastCheckAt, now);
            lastCheckAt = now;
            handler.postDelayed(this, settings.checkIntervalMs());
        }
    };

    public static void start(Context context) {
        if (!new ReminderSettings(context).serviceEnabled()) {
            stop(context);
            return;
        }
        Intent intent = new Intent(context, ReminderForegroundService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            AppLog.d(context, "foreground service start requested");
        } catch (Exception exception) {
            AppLog.e(context, "foreground service start failed", exception);
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, ReminderForegroundService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("בודק תזכורות ברקע")
                .setOngoing(true)
                .setShowWhen(false)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        lastCheckAt = System.currentTimeMillis() - new ReminderSettings(this).dueLookbackMs();
        handler.post(checkRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!new ReminderSettings(this).serviceEnabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        new ReminderStore(this).rescheduleAll();
        ReminderScheduler.scheduleWatchdog(this);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(checkRunnable);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                UiText.t(this, "בדיקת רקע לתזכורות"),
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }

}
