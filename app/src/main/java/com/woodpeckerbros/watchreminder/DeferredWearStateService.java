package com.woodpeckerbros.watchreminder;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class DeferredWearStateService extends Service {
    private static final String CHANNEL_ID = "deferred_wear_state";
    private static final int NOTIFICATION_ID = 2002;
    private static final long MAX_MONITORING_MS = 5 * 60_000L;

    private SensorManager sensorManager;
    private Sensor offBodySensor;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> {
        AppLog.d(this, "DeferredWearStateService bounded monitoring timeout");
        stopSelf();
    };

    private final SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            boolean onBody = event.values != null && event.values.length > 0 && event.values[0] == 1.0f;
            new WearStateStore(DeferredWearStateService.this).setOffBody(!onBody);
            AppLog.d(DeferredWearStateService.this, "DeferredWearStateService onBody=" + onBody);
            if (onBody) {
                DeferredWearRetryReceiver.cancel(DeferredWearStateService.this);
                DeferredReminderDispatcher.run(DeferredWearStateService.this);
                stopSelf();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    public static void start(Context context) {
        Intent intent = new Intent(context, DeferredWearStateService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppLog.d(this, "DeferredWearStateService onCreate");
        createChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(UiText.t(this, "ממתין לענידת השעון"))
                .setOngoing(true)
                .setShowWhen(false)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        registerOffBodySensor();
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, MAX_MONITORING_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        registerOffBodySensor();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(listener);
        }
        handler.removeCallbacks(timeout);
        if (new ReminderAlertQueueStore(this).hasDeferredAlerts()) {
            DeferredWearRetryReceiver.schedule(this);
        } else {
            DeferredWearRetryReceiver.cancel(this);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerOffBodySensor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            AppLog.w(this, "DeferredWearStateService missing BODY_SENSORS");
            return;
        }
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        offBodySensor = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true);
        if (sensorManager == null || offBodySensor == null) {
            AppLog.w(this, "DeferredWearStateService no off-body sensor");
            return;
        }
        sensorManager.unregisterListener(listener);
        sensorManager.registerListener(listener, offBodySensor, SensorManager.SENSOR_DELAY_NORMAL);
        AppLog.d(this, "DeferredWearStateService sensor registered");
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                UiText.t(this, "המתנה לענידת השעון"),
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }
}
