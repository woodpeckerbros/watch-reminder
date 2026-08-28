package com.woodpeckerbros.watchreminder.smartwake;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.health.services.client.HealthServices;
import androidx.health.services.client.MeasureCallback;
import androidx.health.services.client.MeasureClient;
import androidx.health.services.client.data.Availability;
import androidx.health.services.client.data.DataPointContainer;
import androidx.health.services.client.data.DataType;
import androidx.health.services.client.data.DeltaDataType;
import androidx.health.services.client.data.SampleDataPoint;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.R;
import com.woodpeckerbros.watchreminder.WearStateStore;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SmartWakeMonitoringService extends Service implements SensorEventListener {
    private static volatile boolean active;
    private static final String CHANNEL = "smart_wake_monitoring_v1";
    private static final int NOTIFICATION_ID = 0x534d5705;
    private static final String ACTION_STOP = "smartwake.STOP";
    private static final String ACTION_ACTIVITY = "smartwake.ACTIVITY";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope;
    private final Map<Integer, MonitorSession> sessions = new ConcurrentHashMap<>();
    private MeasureClient measureClient;
    private float gravityX, gravityY, gravityZ;
    private long lastHeartRateLogAt;

    private final MeasureCallback heartRateCallback = new MeasureCallback() {
        @Override public void onAvailabilityChanged(DeltaDataType<?, ?> type, Availability availability) {
            AppLog.d(SmartWakeMonitoringService.this, "SmartWake HR availability=" + availability);
        }
        @Override public void onDataReceived(DataPointContainer data) {
            List<SampleDataPoint<Double>> points = data.getData(DataType.HEART_RATE_BPM);
            for (SampleDataPoint<Double> point : points) {
                for (MonitorSession session : sessions.values()) session.detector.addHeartRate(point.getValue());
            }
            long now = System.currentTimeMillis();
            if (!points.isEmpty() && now - lastHeartRateLogAt >= 60_000L) {
                lastHeartRateLogAt = now;
                AppLog.d(SmartWakeMonitoringService.this,
                        "SmartWake live HR samples=" + points.size() + " bpm=" + points.get(points.size() - 1).getValue());
            }
        }
        @Override public void onRegistrationFailed(Throwable throwable) {
            AppLog.e(SmartWakeMonitoringService.this, "SmartWake HR registration failed", throwable);
            registerDirectHeartRateFallback();
        }
    };

    public static void start(Context context, int alarmId, long targetAt) {
        Intent intent = new Intent(context, SmartWakeMonitoringService.class)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId).putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt);
        ContextCompat.startForegroundService(context, intent);
    }
    public static void stop(Context context, int alarmId) {
        if (!active) return;
        context.startService(new Intent(context, SmartWakeMonitoringService.class).setAction(ACTION_STOP)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId));
    }
    public static void updateActivity(Context context, boolean asleep) {
        if (!active) return;
        context.startService(new Intent(context, SmartWakeMonitoringService.class).setAction(ACTION_ACTIVITY).putExtra("asleep", asleep));
    }

    @Override public void onCreate() {
        super.onCreate();
        active = true;
        createChannel();
        startForeground(NOTIFICATION_ID, new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Smart Alarm").setContentText("מנטר חלון התעוררות").setOngoing(true).build());
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            sessions.remove(intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1));
            if (sessions.isEmpty()) stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_ACTIVITY.equals(intent.getAction())) {
            boolean asleep = intent.getBooleanExtra("asleep", false);
            for (MonitorSession session : sessions.values()) session.detector.setAsleep(asleep);
            return START_NOT_STICKY;
        }
        long targetAt = intent == null ? 0L : intent.getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
        int alarmId = intent == null ? 1 : intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        if (targetAt <= System.currentTimeMillis() || new SmartAlarmStateStore(this, alarmId).fired(targetAt)) {
            if (sessions.isEmpty()) stopSelf();
            return START_NOT_STICKY;
        }
        SmartWakeDetector detector = new SmartWakeDetector(System.currentTimeMillis());
        detector.setAsleep(new WearStateStore(this).asleep());
        boolean firstSession = sessions.isEmpty();
        sessions.put(alarmId, new MonitorSession(alarmId, targetAt, detector));
        if (firstSession) { registerMotion(); registerHeartRate(); }
        handler.removeCallbacks(evaluateRunnable); handler.postDelayed(evaluateRunnable, 30_000L);
        AppLog.d(this, "SmartWake monitoring started id=" + alarmId + " target=" + targetAt + " accel=" + (accelerometer != null)
                + " gyro=" + (gyroscope != null) + "; live sleep stages LIGHT/DEEP/REM unavailable in Health Services 1.1 API");
        return START_REDELIVER_INTENT;
    }

    private void registerMotion() {
        if (sensorManager == null) return;
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        // Preserve the exact sampling rate and every sample used by the detector, while allowing
        // the sensor hub to deliver samples in batches and wake the CPU much less often.
        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, 100_000, 5_000_000);
        if (gyroscope != null) sensorManager.registerListener(this, gyroscope, 200_000, 5_000_000);
    }

    private void registerHeartRate() {
        if (checkSelfPermission(android.Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            AppLog.w(this, "SmartWake HR unavailable: BODY_SENSORS not granted"); return;
        }
        measureClient = HealthServices.getClient(this).getMeasureClient();
        Futures.addCallback(measureClient.getCapabilitiesAsync(), new FutureCallback<>() {
            @Override public void onSuccess(androidx.health.services.client.data.MeasureCapabilities capabilities) {
                AppLog.d(SmartWakeMonitoringService.this, "SmartWake Measure capabilities=" + capabilities.getSupportedDataTypesMeasure());
                if (capabilities.getSupportedDataTypesMeasure().contains(DataType.HEART_RATE_BPM))
                    measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, heartRateCallback);
                else registerDirectHeartRateFallback();
            }
            @Override public void onFailure(Throwable throwable) {
                AppLog.e(SmartWakeMonitoringService.this, "SmartWake capability query failed", throwable);
                registerDirectHeartRateFallback();
            }
        }, MoreExecutors.directExecutor());
    }

    private void registerDirectHeartRateFallback() {
        if (sensorManager == null) return;
        Sensor heart = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if (heart != null) sensorManager.registerListener(this, heart, 1_000_000);
        AppLog.d(this, "SmartWake direct HR fallback available=" + (heart != null));
    }

    private final Runnable evaluateRunnable = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            for (MonitorSession session : new ArrayList<>(sessions.values())) {
                if (now >= session.targetAt) { sessions.remove(session.alarmId); continue; }
                SmartWakeDetector.Decision decision = session.detector.evaluate(now);
                AppLog.d(SmartWakeMonitoringService.this, "SmartWake id=" + session.alarmId + " score=" + decision.score
                        + " hr=" + decision.heartRateMean + " hrvProxy=" + decision.heartRateVariability
                        + " slope=" + decision.heartRateSlope + " bursts=" + decision.movementBursts
                        + " energy=" + decision.movementEnergy);
                if (decision.shouldWake) {
                    sessions.remove(session.alarmId);
                    SmartAlarmReceiver.fire(SmartWakeMonitoringService.this, session.alarmId,
                            session.targetAt, "estimated_wake_window");
                }
            }
            if (sessions.isEmpty()) stopSelf(); else handler.postDelayed(this, 30_000L);
        }
    };

    @Override public void onSensorChanged(SensorEvent event) {
        if (sessions.isEmpty() || event.values.length == 0) return;
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            for (MonitorSession session : sessions.values()) session.detector.addHeartRate(event.values[0]);
        }
        else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && event.values.length >= 3) {
            gravityX = .8f * gravityX + .2f * event.values[0]; gravityY = .8f * gravityY + .2f * event.values[1]; gravityZ = .8f * gravityZ + .2f * event.values[2];
            double linear = Math.sqrt(Math.pow(event.values[0]-gravityX,2)+Math.pow(event.values[1]-gravityY,2)+Math.pow(event.values[2]-gravityZ,2));
            for (MonitorSession session : sessions.values()) session.detector.addMotion(linear);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && event.values.length >= 3) {
            double rotation = Math.sqrt(event.values[0]*event.values[0]+event.values[1]*event.values[1]+event.values[2]*event.values[2]);
            for (MonitorSession session : sessions.values()) session.detector.addMotion(rotation * 0.35);
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override public void onDestroy() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (measureClient != null) measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, heartRateCallback);
        super.onDestroy();
    }
    private void createChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Smart Wake monitoring", NotificationManager.IMPORTANCE_LOW));
    }
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    private static final class MonitorSession {
        final int alarmId; final long targetAt; final SmartWakeDetector detector;
        MonitorSession(int alarmId, long targetAt, SmartWakeDetector detector) {
            this.alarmId = alarmId; this.targetAt = targetAt; this.detector = detector;
        }
    }
}
