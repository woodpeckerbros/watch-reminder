package com.woodpeckerbros.watchreminder.smartalarm;

import com.woodpeckerbros.watchreminder.reminder.*;

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
import com.woodpeckerbros.watchreminder.reminder.WearStateStore;

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
    private static final String ACTION_WINDOW_START = "smartwake.WINDOW_START";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope, stepDetector;
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
            long now = System.currentTimeMillis();
            for (SampleDataPoint<Double> point : points) {
                for (MonitorSession session : sessions.values()) session.detector.addHeartRate(point.getValue(), now);
            }
            if (!points.isEmpty()) SmartWakeSamplingProfile.recordHeartRateDelivery(SmartWakeMonitoringService.this, now);
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
        start(context, alarmId, targetAt, targetAt);
    }
    public static void start(Context context, int alarmId, long targetAt, long wakeWindowStartAt) {
        Intent intent = new Intent(context, SmartWakeMonitoringService.class)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId).putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt)
                .putExtra(SmartAlarmScheduler.EXTRA_WAKE_WINDOW_START_AT, wakeWindowStartAt);
        ContextCompat.startForegroundService(context, intent);
    }
    public static void stop(Context context, int alarmId) {
        if (!active) return;
        context.startService(new Intent(context, SmartWakeMonitoringService.class).setAction(ACTION_STOP)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId));
    }
    public static void windowStarted(Context context, int alarmId, long targetAt, long wakeWindowStartAt) {
        Intent intent = new Intent(context, SmartWakeMonitoringService.class).setAction(ACTION_WINDOW_START)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt)
                .putExtra(SmartAlarmScheduler.EXTRA_WAKE_WINDOW_START_AT, wakeWindowStartAt);
        ContextCompat.startForegroundService(context, intent);
    }
    public static void updateActivity(Context context, SmartWakeDetector.UserActivity activity) {
        if (!active) return;
        context.startService(new Intent(context, SmartWakeMonitoringService.class).setAction(ACTION_ACTIVITY)
                .putExtra("user_activity", activity.name()));
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
            SmartWakeDetector.UserActivity activity = userActivity(intent.getStringExtra("user_activity"));
            long now = System.currentTimeMillis();
            for (MonitorSession session : sessions.values()) session.detector.setUserActivity(activity, now);
            return START_NOT_STICKY;
        }
        boolean windowStartCheckpoint = intent != null && ACTION_WINDOW_START.equals(intent.getAction());
        long targetAt = intent == null ? 0L : intent.getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
        long wakeWindowStartAt = intent == null ? targetAt
                : intent.getLongExtra(SmartAlarmScheduler.EXTRA_WAKE_WINDOW_START_AT, targetAt);
        int alarmId = intent == null ? 1 : intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        if (targetAt <= System.currentTimeMillis() || new SmartAlarmStateStore(this, alarmId).fired(targetAt)) {
            if (sessions.isEmpty()) stopSelf();
            return START_NOT_STICKY;
        }
        MonitorSession existing = sessions.get(alarmId);
        if (existing != null && existing.targetAt == targetAt) {
            // Recovery can redeliver the same monitor intent. Its evaluator is already running;
            // restarting its 30-second timer here creates blind spots in the wake window.
            if (windowStartCheckpoint) evaluateSessions(System.currentTimeMillis(), true);
            else AppLog.d(this, "SmartWake monitoring continued id=" + alarmId + " target=" + targetAt
                    + " evaluationSchedule=retained");
            return START_REDELIVER_INTENT;
        }
        long now = System.currentTimeMillis();
        SmartWakeDetector detector = new SmartWakeDetector(now);
        detector.setUserActivity(userActivity(new WearStateStore(this).userActivityState()), now);
        boolean firstSession = sessions.isEmpty();
        sessions.put(alarmId, new MonitorSession(alarmId, targetAt, wakeWindowStartAt, detector));
        if (firstSession) { registerMotion(); registerHeartRate(); }
        handler.removeCallbacks(evaluateRunnable); handler.postDelayed(evaluateRunnable, 30_000L);
        AppLog.d(this, "SmartWake monitoring started id=" + alarmId + " wakeWindow=" + wakeWindowStartAt + " target=" + targetAt + " accel=" + (accelerometer != null)
                + " gyro=" + (gyroscope != null) + " steps=" + (stepDetector != null)
                + "; live sleep stages LIGHT/DEEP/REM unavailable in Health Services 1.1 API");
        if (windowStartCheckpoint) {
            AppLog.w(this, "SmartWake baseline checkpoint started monitor at window boundary id=" + alarmId
                    + "; baseline cannot be valid yet (monitor start was delayed)");
            evaluateSessions(now, true);
        }
        return START_REDELIVER_INTENT;
    }

    private void registerMotion() {
        if (sensorManager == null) return;
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        // Preserve the exact sampling rate and every sample used by the detector, while allowing
        // the sensor hub to deliver samples in batches and wake the CPU much less often.
        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, 100_000, 5_000_000);
        if (gyroscope != null) sensorManager.registerListener(this, gyroscope, 200_000, 5_000_000);
        if (stepDetector != null) {
            try {
                sensorManager.registerListener(this, stepDetector, 1_000_000, 5_000_000);
            } catch (SecurityException exception) {
                stepDetector = null;
                AppLog.w(this, "SmartWake step detector unavailable: ACTIVITY_RECOGNITION not granted");
            }
        }
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
            evaluateSessions(System.currentTimeMillis(), false);
            if (sessions.isEmpty()) stopSelf(); else handler.postDelayed(this, 30_000L);
        }
    };

    private void evaluateSessions(long now, boolean windowStartCheckpoint) {
        for (MonitorSession session : new ArrayList<>(sessions.values())) {
            if (now >= session.targetAt) { sessions.remove(session.alarmId); continue; }
            SmartWakeDetector.Decision decision = session.detector.evaluate(now);
            AppLog.d(this, "SmartWake summary id=" + session.alarmId + " " + decision.summary(now));
            AppLog.d(this, "SmartWake score id=" + session.alarmId + "\n" + decision.telemetry());
            if (windowStartCheckpoint) {
                if (decision.baselineReady) AppLog.d(this, "SmartWake baseline ready at window start id=" + session.alarmId);
                else AppLog.w(this, "SmartWake baseline failure at window start id=" + session.alarmId
                        + " status=" + decision.baselineStatus + " samples HR=" + decision.baselineHeartRateSamples
                        + " accel=" + decision.baselineAccelerometerSamples + " gyro=" + decision.baselineGyroscopeSamples);
            }
            if (decision.shouldWake && now >= session.wakeWindowStartAt) {
                sessions.remove(session.alarmId);
                SmartAlarmScheduler.scheduleDetectedFire(this, session.alarmId, session.targetAt);
            } else if (decision.shouldWake) AppLog.d(this,
                    "SmartWake candidate held until wake window id=" + session.alarmId + " at=" + session.wakeWindowStartAt);
        }
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (sessions.isEmpty() || event.values.length == 0) return;
        long now = System.currentTimeMillis();
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            for (MonitorSession session : sessions.values()) session.detector.addHeartRate(event.values[0], now);
            SmartWakeSamplingProfile.recordHeartRateDelivery(this, now);
        }
        else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && event.values.length >= 3) {
            gravityX = .8f * gravityX + .2f * event.values[0]; gravityY = .8f * gravityY + .2f * event.values[1]; gravityZ = .8f * gravityZ + .2f * event.values[2];
            double linear = Math.sqrt(Math.pow(event.values[0]-gravityX,2)+Math.pow(event.values[1]-gravityY,2)+Math.pow(event.values[2]-gravityZ,2));
            for (MonitorSession session : sessions.values()) session.detector.addAccelerometerMotion(linear, now);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && event.values.length >= 3) {
            double rotation = Math.sqrt(event.values[0]*event.values[0]+event.values[1]*event.values[1]+event.values[2]*event.values[2]);
            for (MonitorSession session : sessions.values()) session.detector.addGyroscopeMotion(rotation, now);
        } else if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            for (MonitorSession session : sessions.values()) session.detector.addStep(now);
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

    private static SmartWakeDetector.UserActivity userActivity(String value) {
        if (value == null) return SmartWakeDetector.UserActivity.UNKNOWN;
        try { return SmartWakeDetector.UserActivity.valueOf(value); }
        catch (IllegalArgumentException ignored) { return SmartWakeDetector.UserActivity.UNKNOWN; }
    }

    private static final class MonitorSession {
        final int alarmId; final long targetAt, wakeWindowStartAt; final SmartWakeDetector detector;
        MonitorSession(int alarmId, long targetAt, long wakeWindowStartAt, SmartWakeDetector detector) {
            this.alarmId = alarmId; this.targetAt = targetAt; this.wakeWindowStartAt = wakeWindowStartAt; this.detector = detector;
        }
    }
}
