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
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserManager;

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
import com.woodpeckerbros.watchreminder.entitlement.EntitlementAccess;
import com.woodpeckerbros.watchreminder.reminder.WearStateStore;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.ref.WeakReference;

public final class SmartWakeMonitoringService extends Service implements SensorEventListener {
    private static volatile boolean active;
    private static volatile WeakReference<SmartWakeMonitoringService> runningService = new WeakReference<>(null);
    private static final String CHANNEL = "smart_wake_monitoring_v1";
    private static final int NOTIFICATION_ID = 0x534d5705;
    private static final String ACTION_STOP = "smartwake.STOP";
    private static final String ACTION_ACTIVITY = "smartwake.ACTIVITY";
    private static final String EXTRA_ACTIVITY_STATE = "user_activity";
    private static final String EXTRA_ACTIVITY_STATE_CHANGE_AT = "user_activity_state_change_at";
    private static final String EXTRA_ACTIVITY_CALLBACK_RECEIVED_AT = "user_activity_callback_received_at";
    private static final String ACTION_WINDOW_START = "smartwake.WINDOW_START";
    private static final String ACTION_HARD_STOP = "smartwake.HARD_STOP";
    static final String EXTRA_DIRECT_BOOT_RECOVERY = "smartwake.direct_boot_recovery";
    private static final String EXTRA_MONITORING_START_SOURCE = "smartwake.monitoring_start_source";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope, stepDetector;
    private Sensor directHeartRateSensor;
    private boolean motionRegistered, activeWindowSampling;
    private boolean accelerometerRegistered, gyroscopeRegistered, stepDetectorRegistered;
    private boolean directHeartRateRegistered, heartRateMeasureRegistrationRequested;
    private boolean destroyed;
    private boolean resourcesReleased = true;
    private final Map<Integer, MonitorSession> sessions = new ConcurrentHashMap<>();
    private MeasureClient measureClient;
    private float gravityX, gravityY, gravityZ;
    private long lastHeartRateLogAt;
    private long accelerometerRegistrationCount, gyroscopeRegistrationCount;
    private long accelerometerSampleCount, gyroscopeSampleCount, stepSampleCount;
    private long serviceStartCommandCount;
    private long serviceCreatedAt;
    private long evaluationIntervalMs = SmartWakeEvaluationCadence.Mode.NORMAL.intervalMs;
    private SmartWakeEvaluationCadence.Mode evaluationMode = SmartWakeEvaluationCadence.Mode.NORMAL;
    private long nextEvaluationDueAtElapsedMs = Long.MIN_VALUE;
    private long nextEvaluationDueAtUptimeMs = Long.MIN_VALUE;
    private SmartWakeCandidateWakeLock candidateWakeLock;
    private HandlerThread candidateLockTimeoutThread;
    private volatile long lastAccelEventAtElapsedMs = -1L, lastAccelCallbackAtElapsedMs = -1L;
    private volatile long lastGyroEventAtElapsedMs = -1L, lastGyroCallbackAtElapsedMs = -1L;
    private volatile long lastStepEventAtElapsedMs = -1L, lastStepCallbackAtElapsedMs = -1L;
    private volatile long lastHrEventAtElapsedMs = -1L, lastHrCallbackAtElapsedMs = -1L;

    private final MeasureCallback heartRateCallback = new MeasureCallback() {
        @Override public void onAvailabilityChanged(DeltaDataType<?, ?> type, Availability availability) {
            AppLog.d(SmartWakeMonitoringService.this, "SmartWake HR availability=" + availability);
        }
        @Override public void onDataReceived(DataPointContainer data) {
            if (destroyed) return;
            List<SampleDataPoint<Double>> points = data.getData(DataType.HEART_RATE_BPM);
            long now = System.currentTimeMillis();
            for (SampleDataPoint<Double> point : points) {
                long sampleElapsedAt = point.getTimeDurationFromBoot().toMillis();
                lastHrEventAtElapsedMs = sampleElapsedAt;
                lastHrCallbackAtElapsedMs = SystemClock.elapsedRealtime();
                long sampleAt = epochMillisFromBootDuration(sampleElapsedAt, now);
                SelfStimulusContamination.Taint taint = SelfStimulusTracker.taintAt(sampleElapsedAt);
                for (MonitorSession session : sessions.values()) {
                    session.detector.addHeartRate(point.getValue(), sampleAt, taint.cardio);
                }
            }
            if (!points.isEmpty() && !hasDirectBootSession()) {
                SmartWakeSamplingProfile.recordHeartRateDelivery(SmartWakeMonitoringService.this, now);
            }
            if (!points.isEmpty() && now - lastHeartRateLogAt >= 60_000L) {
                lastHeartRateLogAt = now;
                long lastSampleAt = epochMillisFromBootDuration(
                        points.get(points.size() - 1).getTimeDurationFromBoot().toMillis(), now);
                AppLog.d(SmartWakeMonitoringService.this,
                        "SmartWake live HR samples=" + points.size() + " bpm="
                                + points.get(points.size() - 1).getValue()
                                + " sampleAgeMs=" + Math.max(0L, now - lastSampleAt));
            }
        }
        @Override public void onRegistrationFailed(Throwable throwable) {
            if (destroyed) return;
            AppLog.e(SmartWakeMonitoringService.this, "SmartWake HR registration failed", throwable);
            registerDirectHeartRateFallback();
        }
    };

    public static void start(Context context, int alarmId, long targetAt) {
        start(context, alarmId, targetAt, targetAt);
    }
    /** True only while the bounded sensor-monitoring service has an active process instance. */
    public static boolean isActive() {
        return active;
    }
    /** A process-local check; onStartCommand also rejects duplicate occurrence starts. */
    static boolean isMonitoringOccurrence(int alarmId, long targetAt) {
        SmartWakeMonitoringService service = runningService.get();
        MonitorSession session = service == null ? null : service.sessions.get(alarmId);
        return session != null && SmartWakeRuntimePolicy.isSameSession(
                session.alarmId, session.targetAt, alarmId, targetAt);
    }
    public static void start(Context context, int alarmId, long targetAt, long wakeWindowStartAt) {
        start(context, alarmId, targetAt, wakeWindowStartAt, "OTHER", 0L);
    }
    static void start(Context context, int alarmId, long targetAt, long wakeWindowStartAt,
                      String source, long expectedMonitoringStartAt) {
        if (!EntitlementAccess.isFeatureAccessGranted(context)) return;
        Intent intent = new Intent(context, SmartWakeMonitoringService.class)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId).putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt)
                .putExtra(SmartAlarmScheduler.EXTRA_WAKE_WINDOW_START_AT, wakeWindowStartAt)
                .putExtra(SmartAlarmScheduler.EXTRA_EXPECTED_MONITORING_START_AT, expectedMonitoringStartAt)
                .putExtra(EXTRA_MONITORING_START_SOURCE, source);
        ContextCompat.startForegroundService(context, intent);
    }
    /** Starts only an already-armed device-protected occurrence while credential storage is locked. */
    static void startDirectBoot(Context context, int alarmId, long targetAt, long wakeWindowStartAt) {
        startDirectBoot(context, alarmId, targetAt, wakeWindowStartAt, "OTHER", 0L);
    }
    static void startDirectBoot(Context context, int alarmId, long targetAt, long wakeWindowStartAt,
                                String source, long expectedMonitoringStartAt) {
        if (!SmartAlarmBootStore.supportsSmartWake(context, alarmId, targetAt)) {
            AppLog.w(context, "SmartWake direct-boot start rejected: shadow missing or stale id="
                    + alarmId + " target=" + targetAt);
            return;
        }
        Intent intent = new Intent(context, SmartWakeMonitoringService.class)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt)
                .putExtra(SmartAlarmScheduler.EXTRA_WAKE_WINDOW_START_AT, wakeWindowStartAt)
                .putExtra(SmartAlarmScheduler.EXTRA_EXPECTED_MONITORING_START_AT, expectedMonitoringStartAt)
                .putExtra(EXTRA_MONITORING_START_SOURCE, source)
                .putExtra(EXTRA_DIRECT_BOOT_RECOVERY, true);
        ContextCompat.startForegroundService(context, intent);
    }
    public static void stop(Context context, int alarmId) {
        SmartAlarmScheduler.cancelHardStop(context, alarmId);
        if (!active) return;
        context.startService(new Intent(context, SmartWakeMonitoringService.class).setAction(ACTION_STOP)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId));
    }
    static void releaseCandidateObservation(Context context, int alarmId, String reason) {
        SmartWakeMonitoringService service = runningService.get();
        if (service == null) return;
        // Bind cancellation to this occurrence, without replacing the service's restart intent.
        MonitorSession session = service.sessions.get(alarmId);
        if (session == null) return;
        try {
            service.candidateWakeLock.stop(session.wakeLockStats, "ALARM_" + reason);
        } catch (RuntimeException error) {
            // Optional observation cleanup must never interrupt cancellation/alarm delivery.
            AppLog.e(context, "Candidate wakelock cleanup failed; timeout retained", error);
        }
    }
    static void hardStop(Context context, int alarmId, long targetAt) {
        if (!active) return;
        context.startService(new Intent(context, SmartWakeMonitoringService.class)
                .setAction(ACTION_HARD_STOP)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt));
    }
    public static void windowStarted(Context context, int alarmId, long targetAt, long wakeWindowStartAt) {
        if (!EntitlementAccess.isFeatureAccessGranted(context)) return;
        Intent intent = new Intent(context, SmartWakeMonitoringService.class).setAction(ACTION_WINDOW_START)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt)
                .putExtra(SmartAlarmScheduler.EXTRA_WAKE_WINDOW_START_AT, wakeWindowStartAt)
                .putExtra(EXTRA_MONITORING_START_SOURCE, "SCHEDULED_ALARM");
        ContextCompat.startForegroundService(context, intent);
    }
    static void windowStartedDirectBoot(Context context, int alarmId, long targetAt,
                                        long wakeWindowStartAt) {
        if (!SmartAlarmBootStore.supportsSmartWake(context, alarmId, targetAt)) {
            AppLog.w(context, "SmartWake direct-boot window start rejected: shadow missing or stale id="
                    + alarmId + " target=" + targetAt);
            return;
        }
        Intent intent = new Intent(context, SmartWakeMonitoringService.class).setAction(ACTION_WINDOW_START)
                .putExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, targetAt)
                .putExtra(SmartAlarmScheduler.EXTRA_WAKE_WINDOW_START_AT, wakeWindowStartAt)
                .putExtra(EXTRA_MONITORING_START_SOURCE, "SCHEDULED_ALARM")
                .putExtra(EXTRA_DIRECT_BOOT_RECOVERY, true);
        ContextCompat.startForegroundService(context, intent);
    }
    public static void updateActivity(Context context, SmartWakeDetector.UserActivity activity,
                                      long stateChangeAt, long callbackReceivedAt) {
        if (!active) return;
        context.startService(new Intent(context, SmartWakeMonitoringService.class).setAction(ACTION_ACTIVITY)
                .putExtra(EXTRA_ACTIVITY_STATE, activity.name())
                .putExtra(EXTRA_ACTIVITY_STATE_CHANGE_AT, stateChangeAt)
                .putExtra(EXTRA_ACTIVITY_CALLBACK_RECEIVED_AT, callbackReceivedAt));
    }

    @Override public void onCreate() {
        super.onCreate();
        destroyed = false;
        active = true;
        createChannel();
        startForeground(NOTIFICATION_ID, new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Smart Alarm").setContentText("מנטר חלון התעוררות").setOngoing(true).build());
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        serviceCreatedAt = System.currentTimeMillis();
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock cpuLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "Zmanio:SmartWakeCandidate");
        cpuLock.setReferenceCounted(false);
        // A busy main looper must not delay the bounded lock's normal safety release.
        candidateLockTimeoutThread = new HandlerThread("SmartWakeCandidateTimeout");
        candidateLockTimeoutThread.start();
        Handler lockTimeoutHandler = new Handler(candidateLockTimeoutThread.getLooper());
        candidateWakeLock = new SmartWakeCandidateWakeLock(
                new SmartWakeCandidateWakeLock.Backend() {
                    @Override public void acquire(long timeoutMs) { cpuLock.acquire(timeoutMs); }
                    @Override public boolean isHeld() { return cpuLock.isHeld(); }
                    @Override public void release() {
                        try { if (cpuLock.isHeld()) cpuLock.release(); }
                        catch (RuntimeException error) {
                            AppLog.e(SmartWakeMonitoringService.this, "Candidate wakelock release failed", error);
                        }
                    }
                }, new SmartWakeCandidateWakeLock.Clock() {
                    @Override public long elapsed() { return SystemClock.elapsedRealtime(); }
                    @Override public long wall() { return System.currentTimeMillis(); }
                }, new SmartWakeCandidateWakeLock.Timer() {
                    @Override public void schedule(Runnable task, long delayMs) { lockTimeoutHandler.postDelayed(task, delayMs); }
                    @Override public void cancel(Runnable task) { lockTimeoutHandler.removeCallbacks(task); }
                }, message -> AppLog.d(SmartWakeMonitoringService.this, message));
        runningService = new WeakReference<>(this);
        AppLog.d(this, "SERVICE_START service=SmartWakeMonitoringService");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        serviceStartCommandCount++;
        if (intent == null || (flags & (START_FLAG_REDELIVERY | START_FLAG_RETRY)) != 0) {
            AppLog.w(this, "SERVICE_RESTART service=SmartWakeMonitoringService flags=" + flags
                    + " startId=" + startId + " commandCount=" + serviceStartCommandCount);
        }
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            int alarmId = intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
            removeSession(alarmId, "EXPLICIT_STOP");
            AppLog.d(this, "SERVICE_STOP service=SmartWakeMonitoringService id=" + alarmId
                    + " reason=EXPLICIT_STOP");
            if (sessions.isEmpty()) stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_HARD_STOP.equals(intent.getAction())) {
            int alarmId = intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
            long targetAt = intent.getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
            MonitorSession session = sessions.get(alarmId);
            if (session != null && session.targetAt == targetAt
                    && SmartWakeRuntimePolicy.isPastHardStop(System.currentTimeMillis(), targetAt)) {
                removeSession(alarmId, "DEADLINE_PLUS_GRACE_HARD_STOP");
                AppLog.w(this, "SERVICE_STOP service=SmartWakeMonitoringService id=" + alarmId
                        + " reason=DEADLINE_PLUS_GRACE_HARD_STOP");
            }
            if (sessions.isEmpty()) stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_ACTIVITY.equals(intent.getAction())) {
            SmartWakeDetector.UserActivity activity = userActivity(intent.getStringExtra(EXTRA_ACTIVITY_STATE));
            long stateChangeAt = intent.getLongExtra(EXTRA_ACTIVITY_STATE_CHANGE_AT, 0L);
            long callbackReceivedAt = intent.getLongExtra(EXTRA_ACTIVITY_CALLBACK_RECEIVED_AT, 0L);
            for (MonitorSession session : sessions.values()) {
                session.detector.setUserActivity(activity, stateChangeAt, callbackReceivedAt);
            }
            return START_NOT_STICKY;
        }
        boolean windowStartCheckpoint = intent != null && ACTION_WINDOW_START.equals(intent.getAction());
        boolean directBootRecovery = intent != null
                && intent.getBooleanExtra(EXTRA_DIRECT_BOOT_RECOVERY, false);
        long targetAt = intent == null ? 0L : intent.getLongExtra(SmartAlarmScheduler.EXTRA_TARGET_AT, 0L);
        long wakeWindowStartAt = intent == null ? targetAt
                : intent.getLongExtra(SmartAlarmScheduler.EXTRA_WAKE_WINDOW_START_AT, targetAt);
        int alarmId = intent == null ? 1 : intent.getIntExtra(SmartAlarmScheduler.EXTRA_ALARM_ID, 1);
        long expectedMonitoringStartAt = intent == null ? 0L : intent.getLongExtra(
                SmartAlarmScheduler.EXTRA_EXPECTED_MONITORING_START_AT, 0L);
        if (expectedMonitoringStartAt <= 0L) {
            expectedMonitoringStartAt = SmartAlarmBootStore.monitoringStartAt(this, alarmId, targetAt);
        }
        String monitoringStartSource = intent == null ? "SERVICE_RECOVERY"
                : intent.getStringExtra(EXTRA_MONITORING_START_SOURCE);
        if (intent == null || (flags & (START_FLAG_REDELIVERY | START_FLAG_RETRY)) != 0) {
            monitoringStartSource = "SERVICE_RECOVERY";
        } else if (monitoringStartSource == null) monitoringStartSource = "OTHER";
        long currentTime = System.currentTimeMillis();
        boolean userUnlocked = isUserUnlocked();
        boolean directBootEligible = directBootRecovery && !userUnlocked
                && SmartAlarmBootStore.supportsSmartWake(this, alarmId, targetAt)
                && !SmartAlarmBootStore.delivered(this, alarmId, targetAt);
        if (!directBootEligible && !EntitlementAccess.isFeatureAccessGranted(this)) {
            AppLog.w(this, "SmartWake session start rejected: feature access unavailable"
                    + " direct_boot_requested=" + directBootRecovery + " unlocked=" + userUnlocked);
            if (sessions.isEmpty()) stopSelf();
            return START_NOT_STICKY;
        }
        boolean alreadyFired = directBootEligible
                ? SmartAlarmBootStore.delivered(this, alarmId, targetAt)
                : new SmartAlarmStateStore(this, alarmId).fired(targetAt);
        if (targetAt <= currentTime || SmartWakeRuntimePolicy.isPastHardStop(currentTime, targetAt)
                || alreadyFired) {
            if (sessions.isEmpty()) stopSelf();
            return START_NOT_STICKY;
        }
        MonitorSession existing = sessions.get(alarmId);
        if (existing != null && SmartWakeRuntimePolicy.isSameSession(
                existing.alarmId, existing.targetAt, alarmId, targetAt)) {
            // Recovery can redeliver the same monitor intent. Retain the one evaluator loop.
            if (windowStartCheckpoint) {
                enableActiveWindowSampling();
                evaluateSessions(System.currentTimeMillis(), true);
                scheduleNextEvaluation(evaluationIntervalMs);
            }
            else AppLog.d(this, "SmartWake monitoring continued id=" + alarmId + " target=" + targetAt
                    + " evaluationSchedule=retained");
            if (existing.directBootRecovery && userUnlocked && !directBootRecovery) {
                seedUnlockedWearState(existing.detector);
                AppLog.d(this, "SMART_WAKE_DIRECT_BOOT_MERGED id=" + alarmId
                        + " occurrence_id=" + sessionId(alarmId, targetAt)
                        + " duplicate_session=false duplicate_listeners=false");
            }
            return START_REDELIVER_INTENT;
        }
        long now = System.currentTimeMillis();
        try {
            SmartWakeDetector detector = new SmartWakeDetector(now);
            if (!directBootEligible) {
                WearStateStore wearState = new WearStateStore(this);
                SmartWakeDetector.UserActivity storedActivity = userActivity(wearState.userActivityState());
                detector.seedUserActivity(storedActivity, wearState.userActivityStateChangeAt(),
                        wearState.userActivityCallbackReceivedAt());
            } else {
                AppLog.d(this, "SMART_WAKE_DIRECT_BOOT_SESSION id=" + alarmId
                        + " occurrence_id=" + sessionId(alarmId, targetAt)
                        + " system_activity=NO_DATA credential_storage=LOCKED");
            }
            boolean firstSession = sessions.isEmpty();
            if (existing != null) {
                // Replace a recalculated occurrence without tearing down shared sensors or
                // cancelling the replacement occurrence's already scheduled hard-stop alarm.
                removeSession(alarmId, "OCCURRENCE_REPLACED", false, false);
                firstSession = false;
            }
            sessions.put(alarmId, new MonitorSession(alarmId, targetAt, wakeWindowStartAt, now,
                    expectedMonitoringStartAt, monitoringStartSource, directBootEligible, detector));
            if (firstSession) {
                resourcesReleased = false;
                registerMotion(now >= wakeWindowStartAt);
                registerHeartRate(directBootEligible);
            }
            if (firstSession) scheduleNextEvaluation(SmartWakeEvaluationCadence.Mode.NORMAL.intervalMs);
            AppLog.d(this, "SMART_WAKE_SESSION_START session_id=" + sessionId(alarmId, targetAt)
                    + " occurrence_id=" + sessionId(alarmId, targetAt)
                    + " monitoring_start=" + now + " earliest_wake=" + wakeWindowStartAt
                    + " deadline=" + targetAt + " direct_boot=" + directBootEligible
                    + " EXPECTED_MONITORING_START_AT=" + expectedMonitoringStartAt
                    + " ACTUAL_MONITORING_START_AT=" + now
                    + " MONITORING_START_DELAY_MS=" + (expectedMonitoringStartAt > 0L
                    ? now - expectedMonitoringStartAt : -1L)
                    + " MONITORING_START_SOURCE=" + monitoringStartSource);
            AppLog.d(this, "SmartWake monitoring started id=" + alarmId + " wakeWindow=" + wakeWindowStartAt + " target=" + targetAt + " accel=" + (accelerometer != null)
                    + " gyro=" + (gyroscope != null) + " steps=" + (stepDetector != null)
                    + "; liveStageSource=UNAVAILABLE HealthServices1.1_has_no_live_sleep_stage_stream"
                    + " HealthConnectSleepSession=RETROSPECTIVE_AFTER_SESSION_ONLY");
            if (windowStartCheckpoint) {
                AppLog.w(this, "SmartWake baseline checkpoint started monitor at window boundary id=" + alarmId
                        + "; baseline cannot be valid yet (monitor start was delayed)");
                evaluateSessions(now, true);
                scheduleNextEvaluation(evaluationIntervalMs);
            }
        } catch (RuntimeException error) {
            AppLog.e(this, "SmartWake session start failed id=" + alarmId
                    + "; independent final deadline retained", error);
            removeSession(alarmId, "SESSION_START_EXCEPTION");
            if (sessions.isEmpty()) stopSelf();
            return START_NOT_STICKY;
        }
        return START_REDELIVER_INTENT;
    }

    private void registerMotion(boolean activeWindow) {
        if (sensorManager == null) return;
        if (!SmartWakeRuntimePolicy.shouldRegisterMotion(
                motionRegistered, activeWindowSampling, activeWindow)) return;
        if (motionRegistered) unregisterMotion("SAMPLING_PHASE_CHANGE");
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        // Preserve the exact sampling rate and every sample used by the detector, while allowing
        // the sensor hub to batch more aggressively during baseline collection. Inside the actual
        // wake window, shorten delivery latency without changing feature calibration/sample rate.
        int maxLatencyUs = activeWindow ? 2_000_000 : 15_000_000;
        if (accelerometer != null) {
            try {
                accelerometerRegistered = sensorManager.registerListener(
                        this, accelerometer, 100_000, maxLatencyUs);
            } catch (RuntimeException error) {
                accelerometerRegistered = false;
                AppLog.e(this, "SmartWake accelerometer registration failed; continuing", error);
            }
            if (accelerometerRegistered) accelerometerRegistrationCount++;
            AppLog.d(this, "ACCEL_REGISTER success=" + accelerometerRegistered
                    + " registration_count=" + accelerometerRegistrationCount
                    + " period_us=100000 max_latency_us=" + maxLatencyUs
                    + " wake_up=" + accelerometer.isWakeUpSensor());
        }
        if (gyroscope != null) {
            try {
                gyroscopeRegistered = sensorManager.registerListener(
                        this, gyroscope, 200_000, maxLatencyUs);
            } catch (RuntimeException error) {
                gyroscopeRegistered = false;
                AppLog.e(this, "SmartWake gyroscope registration failed; continuing", error);
            }
            if (gyroscopeRegistered) gyroscopeRegistrationCount++;
            AppLog.d(this, "GYRO_REGISTER success=" + gyroscopeRegistered
                    + " registration_count=" + gyroscopeRegistrationCount
                    + " period_us=200000 max_latency_us=" + maxLatencyUs
                    + " wake_up=" + gyroscope.isWakeUpSensor());
        }
        if (stepDetector != null) {
            try {
                stepDetectorRegistered = sensorManager.registerListener(
                        this, stepDetector, 1_000_000, maxLatencyUs);
            } catch (SecurityException exception) {
                stepDetector = null;
                stepDetectorRegistered = false;
                AppLog.w(this, "SmartWake step detector unavailable: ACTIVITY_RECOGNITION not granted");
            }
            if (stepDetector != null) AppLog.d(this, "STEP_REGISTER success="
                    + stepDetectorRegistered + " period_us=1000000 max_latency_us=" + maxLatencyUs
                    + " wake_up=" + stepDetector.isWakeUpSensor());
        }
        motionRegistered = accelerometerRegistered || gyroscopeRegistered || stepDetectorRegistered;
        activeWindowSampling = activeWindow;
        AppLog.d(this, "SmartWake motion sampling phase="
                + (activeWindow ? "ACTIVE_WINDOW" : "BASELINE_LEAD_IN")
                + " accelPeriodUs=100000 gyroPeriodUs=200000 maxLatencyUs=" + maxLatencyUs);
    }

    private void unregisterMotion(String reason) {
        if (sensorManager != null) {
            if (accelerometerRegistered && accelerometer != null) {
                try {
                    sensorManager.unregisterListener(this, accelerometer);
                } catch (RuntimeException error) {
                    AppLog.e(this, "SmartWake accelerometer unregister failed", error);
                }
                AppLog.d(this, "ACCEL_UNREGISTER registration_count=" + accelerometerRegistrationCount
                        + " sample_count=" + accelerometerSampleCount + " reason=" + reason);
            }
            if (gyroscopeRegistered && gyroscope != null) {
                try {
                    sensorManager.unregisterListener(this, gyroscope);
                } catch (RuntimeException error) {
                    AppLog.e(this, "SmartWake gyroscope unregister failed", error);
                }
                AppLog.d(this, "GYRO_UNREGISTER registration_count=" + gyroscopeRegistrationCount
                        + " sample_count=" + gyroscopeSampleCount + " reason=" + reason);
            }
            if (stepDetectorRegistered && stepDetector != null) {
                try {
                    sensorManager.unregisterListener(this, stepDetector);
                } catch (RuntimeException error) {
                    AppLog.e(this, "SmartWake step detector unregister failed", error);
                }
                AppLog.d(this, "STEP_UNREGISTER sample_count=" + stepSampleCount + " reason=" + reason);
            }
        }
        accelerometerRegistered = false;
        gyroscopeRegistered = false;
        stepDetectorRegistered = false;
        motionRegistered = false;
    }

    private void enableActiveWindowSampling() {
        registerMotion(true);
    }

    private void registerHeartRate(boolean directBootRecovery) {
        if (checkSelfPermission(android.Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            AppLog.w(this, "SmartWake HR unavailable: BODY_SENSORS not granted"); return;
        }
        try {
            measureClient = HealthServices.getClient(this).getMeasureClient();
        } catch (RuntimeException error) {
            AppLog.e(this, "SmartWake Health Services unavailable"
                    + " direct_boot=" + directBootRecovery + "; trying direct HR", error);
            registerDirectHeartRateFallback();
            return;
        }
        AppLog.d(this, "SmartWake Health Services HR requested direct_boot=" + directBootRecovery);
        Futures.addCallback(measureClient.getCapabilitiesAsync(), new FutureCallback<>() {
            @Override public void onSuccess(androidx.health.services.client.data.MeasureCapabilities capabilities) {
                if (destroyed || sessions.isEmpty()) return;
                AppLog.d(SmartWakeMonitoringService.this, "SmartWake Measure capabilities=" + capabilities.getSupportedDataTypesMeasure());
                if (capabilities.getSupportedDataTypesMeasure().contains(DataType.HEART_RATE_BPM)) {
                    heartRateMeasureRegistrationRequested = true;
                    try {
                        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, heartRateCallback);
                    } catch (RuntimeException error) {
                        AppLog.e(SmartWakeMonitoringService.this,
                                "SmartWake HR callback registration failed; trying direct HR", error);
                        registerDirectHeartRateFallback();
                    }
                } else registerDirectHeartRateFallback();
            }
            @Override public void onFailure(Throwable throwable) {
                if (destroyed || sessions.isEmpty()) return;
                AppLog.e(SmartWakeMonitoringService.this, "SmartWake capability query failed", throwable);
                registerDirectHeartRateFallback();
            }
        }, MoreExecutors.directExecutor());
    }

    private void registerDirectHeartRateFallback() {
        if (sensorManager == null || destroyed || sessions.isEmpty() || directHeartRateRegistered) return;
        directHeartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if (directHeartRateSensor != null) {
            try {
                directHeartRateRegistered = sensorManager.registerListener(
                        this, directHeartRateSensor, 1_000_000);
            } catch (RuntimeException error) {
                directHeartRateRegistered = false;
                AppLog.e(this, "SmartWake direct HR fallback registration failed", error);
            }
        }
        AppLog.d(this, "SmartWake direct HR fallback available=" + (directHeartRateSensor != null)
                + " registered=" + directHeartRateRegistered
                + " wake_up=" + (directHeartRateSensor == null ? "NO_DATA"
                    : directHeartRateSensor.isWakeUpSensor()));
    }

    private final Runnable evaluateRunnable = new Runnable() {
        @Override public void run() {
            long startedAt = SystemClock.elapsedRealtime();
            long startedUptime = SystemClock.uptimeMillis();
            SmartWakeEvaluationCadence.Mode targetMode = evaluationMode;
            SmartWakeEvaluationTiming timing = SmartWakeEvaluationTiming.of(
                    nextEvaluationDueAtElapsedMs, nextEvaluationDueAtUptimeMs,
                    startedAt, startedUptime);
            List<MonitorSession> evaluatedSessions = new ArrayList<>(sessions.values());
            boolean candidateLockHeldAtStart = candidateWakeLock.isHeld();
            for (MonitorSession session : evaluatedSessions) {
                candidateWakeLock.recordEvaluation(session.wakeLockStats, startedAt, targetMode, timing);
            }
            try {
                evaluateSessions(System.currentTimeMillis(), false);
            } catch (RuntimeException error) {
                AppLog.e(SmartWakeMonitoringService.this,
                        "SmartWake detector loop failed; independent final deadline retained", error);
                for (MonitorSession session : new ArrayList<>(sessions.values())) {
                    removeSession(session.alarmId, "DETECTOR_EXCEPTION");
                }
            }
            long durationMs = SystemClock.elapsedRealtime() - startedAt;
            AppLog.d(SmartWakeMonitoringService.this, "SMART_WAKE_EVALUATION_TIMING"
                    + " EVALUATION_MODE=" + targetMode
                    + " TARGET_INTERVAL_MS=" + targetMode.intervalMs
                    + " NEXT_EVALUATION_DUE_AT_ELAPSED=" + timing.dueAtElapsedMs
                    + " EVALUATION_ACTUAL_START_AT_ELAPSED=" + timing.actualStartAtElapsedMs
                    + " EVALUATION_LATE_BY_MS=" + timing.lateByMs
                    + " EVALUATION_UPTIME_LATE_BY_MS=" + timing.uptimeLateByMs
                    + " SUSPEND_GAP_MS=" + timing.suspendGapMs
                    + " CANDIDATE_WAKELOCK_HELD_AT_START=" + candidateLockHeldAtStart
                    + " EVALUATION_DURATION_MS=" + durationMs);
            for (MonitorSession session : evaluatedSessions) {
                session.cadenceStats.record(startedAt, durationMs, targetMode, timing);
            }
            if (!SmartWakeEvaluationCadence.shouldSchedule(sessions.size())) {
                handler.removeCallbacks(this);
                stopSelf();
            } else {
                // Measured from the evaluation's start so logging/processing does not add to
                // the requested interval. The main looper serializes evaluations and sensors.
                scheduleNextEvaluation(SmartWakeEvaluationCadence.nextDelayMs(
                        evaluationIntervalMs, durationMs));
            }
        }
    };

    private void scheduleNextEvaluation(long delayMs) {
        handler.removeCallbacks(evaluateRunnable);
        if (SmartWakeEvaluationCadence.shouldSchedule(sessions.size())) {
            nextEvaluationDueAtElapsedMs = SystemClock.elapsedRealtime() + delayMs;
            nextEvaluationDueAtUptimeMs = SystemClock.uptimeMillis() + delayMs;
            handler.postDelayed(evaluateRunnable, delayMs);
        } else {
            nextEvaluationDueAtElapsedMs = Long.MIN_VALUE;
            nextEvaluationDueAtUptimeMs = Long.MIN_VALUE;
        }
    }

    private void evaluateSessions(long now, boolean windowStartCheckpoint) {
        long nextIntervalMs = SmartWakeEvaluationCadence.Mode.NORMAL.intervalMs;
        SmartWakeEvaluationCadence.Mode nextMode = SmartWakeEvaluationCadence.Mode.NORMAL;
        for (MonitorSession session : new ArrayList<>(sessions.values())) {
            if (now >= session.targetAt) {
                removeSession(session.alarmId, "FINAL_DEADLINE_REACHED");
                continue;
            }
            session.evaluationCount++;
            session.detector.setSelfStimulus(SelfStimulusTracker.snapshot());
            SmartWakeDetector.Decision decision = session.detector.evaluate(now);
            if (decision.baselineReady && session.baselineReadyAt == Long.MIN_VALUE) {
                session.baselineReadyAt = now;
                AppLog.d(this, "SMART_WAKE_BASELINE_READY occurrence_id="
                        + sessionId(session.alarmId, session.targetAt)
                        + " BASELINE_READY_AT=" + now
                        + " TIME_FROM_MONITORING_START_TO_BASELINE_READY_MS="
                        + (now - session.monitoringStartedAt));
            }
            candidateWakeLock.update(session.wakeLockStats, now, session.wakeWindowStartAt,
                    session.targetAt, !destroyed, decision.candidateActive,
                    decision.shouldWake && now >= session.wakeWindowStartAt,
                    decision.candidateActive ? now - decision.candidateAgeMs : Long.MIN_VALUE,
                    decision.candidateOriginScore, decision.candidateOriginGroups,
                    decision.candidateConfirmationStatus);
            // Shadow telemetry is intentionally one-way. Its failure must not affect alarm logic.
            try {
                session.shadow.record(now, decision, session.detector.shadowMovementBuckets(
                        session.shadow.requiredMovementStartAt(now), now));
            } catch (RuntimeException error) {
                AppLog.e(this, "SmartWake shadow telemetry failed id=" + session.alarmId, error);
            }
            SmartWakePracticalWakeQuality.Result practicalWakeQuality = null;
            if (decision.shouldWake) {
                // Strictly post-decision observation: this result is logged only and cannot
                // reach the detector, cadence selector, wake lock, or alarm scheduling path.
                try {
                    practicalWakeQuality = SmartWakePracticalWakeQuality.evaluate(decision,
                            session.shadow.window90());
                } catch (RuntimeException error) {
                    AppLog.e(this, "SmartWake practical quality shadow failed id=" + session.alarmId, error);
                }
            }
            SmartWakeEvaluationCadence.Mode mode = SmartWakeEvaluationCadence.forDecision(decision);
            if (mode.intervalMs < nextIntervalMs) {
                nextIntervalMs = mode.intervalMs;
                nextMode = mode;
            }
            if (mode == SmartWakeEvaluationCadence.Mode.CANDIDATE) session.candidateEvaluations++;
            else if (mode == SmartWakeEvaluationCadence.Mode.WATCHING) session.watchingEvaluations++;
            else session.normalEvaluations++;
            String cadenceTelemetry = " EVALUATION_MODE=" + mode
                    + " EVALUATION_CADENCE_MS=" + mode.intervalMs
                    + " evaluation_interval_ms=" + mode.intervalMs
                    + " EVALUATION_COUNT=" + session.evaluationCount
                    + " HR_NEW_SINCE_LAST_EVAL=" + decision.hrNew
                    + " MOVEMENT_NEW_SINCE_LAST_EVAL=" + decision.movementNew
                    + " STEP_NEW_SINCE_LAST_EVAL=" + decision.stepNew
                    + " fresh_evidence_changed=" + decision.freshEvidenceChanged
                    + " interesting_frames=" + decision.interestingFrameCount
                    + " candidate_active=" + decision.candidateActive
                    + " wakeability_state=" + decision.wakeabilityState
                    + decision.recencyCompact();
            String compactSummary = "id=" + session.alarmId + " timestamp=" + now
                    + " → WAKE_SCORE=" + decision.score
                    + " → groups=" + decision.evidenceGroups
                    + " → candidate=" + decision.candidateActive
                    + " → clearly_awake=" + decision.clearlyAwake
                    + " → decision=" + (decision.shouldWake ? "WAKE" : "CONTINUE")
                    + cadenceTelemetry;
            String shadowCompact = "";
            try {
                if (session.shadow.window90() != null) shadowCompact = session.shadow.compact();
            } catch (RuntimeException error) {
                AppLog.e(this, "SmartWake shadow compact telemetry failed id=" + session.alarmId, error);
            }
            AppLog.appendSmartWakeSummary(this, compactSummary + shadowCompact
                    + (practicalWakeQuality == null ? "" : practicalWakeQuality.compact()));
            if (session.lastDetailedTelemetryAt == Long.MIN_VALUE
                    || now - session.lastDetailedTelemetryAt >= 30_000L || decision.shouldWake) {
                session.lastDetailedTelemetryAt = now;
                AppLog.d(this, "SmartWake score id=" + session.alarmId + cadenceTelemetry
                        + "\n" + decision.telemetry()
                        + (practicalWakeQuality == null ? "" : "\n"
                        + practicalWakeQuality.telemetry(decision, session.shadow.window90())));
                try {
                    if (session.shadow.window90() != null) AppLog.d(this, session.shadow.telemetry());
                } catch (RuntimeException error) {
                    AppLog.e(this, "SmartWake shadow detail telemetry failed id=" + session.alarmId, error);
                }
                AppLog.d(this, sensorDeliveryTelemetry());
                long runtimeMinutes = Math.max(1L, (now - serviceCreatedAt + 59_999L) / 60_000L);
                AppLog.d(this, "SMART_WAKE_RUNTIME_COUNTERS session_id="
                        + sessionId(session.alarmId, session.targetAt)
                        + " evaluation_count=" + session.evaluationCount
                        + " normal_evaluations=" + session.normalEvaluations
                        + " watching_evaluations=" + session.watchingEvaluations
                        + " candidate_evaluations=" + session.candidateEvaluations
                        + " accel_sample_count=" + accelerometerSampleCount
                        + " accel_callbacks_per_minute=" + (accelerometerSampleCount / runtimeMinutes)
                        + " gyro_sample_count=" + gyroscopeSampleCount
                        + " gyro_callbacks_per_minute=" + (gyroscopeSampleCount / runtimeMinutes)
                        + " step_sample_count=" + stepSampleCount
                        + " service_command_count=" + serviceStartCommandCount);
            }
            if (windowStartCheckpoint) {
                if (decision.baselineReady) AppLog.d(this, "SmartWake baseline ready at window start id=" + session.alarmId);
                else AppLog.w(this, "SmartWake baseline failure at window start id=" + session.alarmId
                        + " status=" + decision.baselineStatus + " samples HR=" + decision.baselineHeartRateSamples
                        + " accel=" + decision.baselineAccelerometerSamples + " gyro=" + decision.baselineGyroscopeSamples);
            }
            if (decision.shouldWake && now >= session.wakeWindowStartAt) {
                removeSession(session.alarmId, "EARLY_WAKE_DECISION");
                SmartAlarmScheduler.scheduleDetectedFire(this, session.alarmId, session.targetAt,
                        decision.wakeReason);
            } else if (decision.shouldWake) AppLog.d(this,
                    "SmartWake candidate held until wake window id=" + session.alarmId + " at=" + session.wakeWindowStartAt);
        }
        evaluationIntervalMs = nextIntervalMs;
        evaluationMode = nextMode;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (sessions.isEmpty() || event.values.length == 0) return;
        long now = System.currentTimeMillis();
        long sampleAt = epochMillisForSensorEvent(event.timestamp, now);
        long sampleElapsedAt = event.timestamp / 1_000_000L;
        long callbackAtElapsed = SystemClock.elapsedRealtime();
        SelfStimulusContamination.Taint taint = SelfStimulusTracker.taintAt(sampleElapsedAt);
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            lastHrEventAtElapsedMs = sampleElapsedAt;
            lastHrCallbackAtElapsedMs = callbackAtElapsed;
            for (MonitorSession session : sessions.values()) session.detector.addHeartRate(event.values[0], sampleAt, taint.cardio);
            if (!hasDirectBootSession()) SmartWakeSamplingProfile.recordHeartRateDelivery(this, now);
        }
        else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && event.values.length >= 3) {
            lastAccelEventAtElapsedMs = sampleElapsedAt;
            lastAccelCallbackAtElapsedMs = callbackAtElapsed;
            accelerometerSampleCount++;
            gravityX = .8f * gravityX + .2f * event.values[0]; gravityY = .8f * gravityY + .2f * event.values[1]; gravityZ = .8f * gravityZ + .2f * event.values[2];
            double linear = Math.sqrt(Math.pow(event.values[0]-gravityX,2)+Math.pow(event.values[1]-gravityY,2)+Math.pow(event.values[2]-gravityZ,2));
            for (MonitorSession session : sessions.values()) session.detector.addAccelerometerMotion(linear, sampleAt, taint.movement);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && event.values.length >= 3) {
            lastGyroEventAtElapsedMs = sampleElapsedAt;
            lastGyroCallbackAtElapsedMs = callbackAtElapsed;
            gyroscopeSampleCount++;
            double rotation = Math.sqrt(event.values[0]*event.values[0]+event.values[1]*event.values[1]+event.values[2]*event.values[2]);
            for (MonitorSession session : sessions.values()) session.detector.addGyroscopeMotion(rotation, sampleAt, taint.movement);
        } else if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            lastStepEventAtElapsedMs = sampleElapsedAt;
            lastStepCallbackAtElapsedMs = callbackAtElapsed;
            stepSampleCount++;
            for (MonitorSession session : sessions.values()) session.detector.addStep(sampleAt, taint.step);
        }
    }

    private String sensorDeliveryTelemetry() {
        return "SMART_WAKE_SENSOR_DELIVERY"
                + " ACCEL_EVENT_AT_ELAPSED=" + lastAccelEventAtElapsedMs
                + " ACCEL_CALLBACK_AT_ELAPSED=" + lastAccelCallbackAtElapsedMs
                + " GYRO_EVENT_AT_ELAPSED=" + lastGyroEventAtElapsedMs
                + " GYRO_CALLBACK_AT_ELAPSED=" + lastGyroCallbackAtElapsedMs
                + " STEP_EVENT_AT_ELAPSED=" + lastStepEventAtElapsedMs
                + " STEP_CALLBACK_AT_ELAPSED=" + lastStepCallbackAtElapsedMs
                + " HR_EVENT_AT_ELAPSED=" + lastHrEventAtElapsedMs
                + " HR_CALLBACK_AT_ELAPSED=" + lastHrCallbackAtElapsedMs;
    }

    private static long epochMillisForSensorEvent(long eventTimestampNanos, long nowEpochMillis) {
        long ageMillis = Math.max(0L,
                (SystemClock.elapsedRealtimeNanos() - eventTimestampNanos) / 1_000_000L);
        return Math.min(nowEpochMillis, nowEpochMillis - ageMillis);
    }

    private static long epochMillisFromBootDuration(long durationFromBootMillis, long nowEpochMillis) {
        long bootEpochMillis = nowEpochMillis - SystemClock.elapsedRealtime();
        return Math.min(nowEpochMillis, bootEpochMillis + Math.max(0L, durationFromBootMillis));
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override public void onDestroy() {
        active = false;
        if (runningService.get() == this) runningService = new WeakReference<>(null);
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        for (MonitorSession session : new ArrayList<>(sessions.values())) {
            removeSession(session.alarmId, "SERVICE_DESTROY", false, false);
        }
        releaseMonitoringResources("SERVICE_DESTROY");
        if (candidateWakeLock != null) candidateWakeLock.stopAll("SERVICE_DESTROY");
        if (candidateLockTimeoutThread != null) candidateLockTimeoutThread.quitSafely();
        AppLog.d(this, "SERVICE_DESTROY service=SmartWakeMonitoringService"
                + " accel_registration_count=" + accelerometerRegistrationCount
                + " accel_sample_count=" + accelerometerSampleCount
                + " gyro_registration_count=" + gyroscopeRegistrationCount
                + " gyro_sample_count=" + gyroscopeSampleCount
                + " step_sample_count=" + stepSampleCount
                + " command_count=" + serviceStartCommandCount);
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
        final int alarmId;
        final long targetAt, wakeWindowStartAt, monitoringStartedAt, expectedMonitoringStartAt;
        final String monitoringStartSource;
        final boolean directBootRecovery;
        final SmartWakeDetector detector;
        long evaluationCount;
        long baselineReadyAt = Long.MIN_VALUE;
        long lastDetailedTelemetryAt = Long.MIN_VALUE;
        long normalEvaluations, watchingEvaluations, candidateEvaluations;
        final CadenceStats cadenceStats = new CadenceStats();
        final SmartWakeShadowTelemetry shadow = new SmartWakeShadowTelemetry();
        final SmartWakeCandidateWakeLock.Session wakeLockStats;
        MonitorSession(int alarmId, long targetAt, long wakeWindowStartAt,
                       long monitoringStartedAt, long expectedMonitoringStartAt,
                       String monitoringStartSource, boolean directBootRecovery,
                       SmartWakeDetector detector) {
            this.alarmId = alarmId;
            this.targetAt = targetAt;
            this.wakeWindowStartAt = wakeWindowStartAt;
            this.monitoringStartedAt = monitoringStartedAt;
            this.expectedMonitoringStartAt = expectedMonitoringStartAt;
            this.monitoringStartSource = monitoringStartSource;
            this.directBootRecovery = directBootRecovery;
            this.detector = detector;
            this.wakeLockStats = new SmartWakeCandidateWakeLock.Session(sessionId(alarmId, targetAt));
        }
    }

    private void removeSession(int alarmId, String reason) {
        removeSession(alarmId, reason, true, true);
    }

    private void removeSession(int alarmId, String reason, boolean cancelHardStop) {
        removeSession(alarmId, reason, cancelHardStop, true);
    }

    private void removeSession(int alarmId, String reason, boolean cancelHardStop,
                               boolean releaseWhenEmpty) {
        MonitorSession removed = sessions.remove(alarmId);
        if (removed == null) return;
        candidateWakeLock.stop(removed.wakeLockStats, reason);
        AppLog.appendSmartWakeSummary(this, "CANDIDATE_WAKELOCK_SESSION_SUMMARY session_id="
                + sessionId(removed.alarmId, removed.targetAt) + " "
                + removed.wakeLockStats.summary() + " reason=" + reason);
        if (cancelHardStop) SmartAlarmScheduler.cancelHardStop(this, alarmId);
        String shadowSummary = "";
        try {
            shadowSummary = removed.shadow.sessionSummary();
            AppLog.appendSmartWakeSummary(this, "SHADOW_SESSION_SUMMARY session_id="
                    + sessionId(removed.alarmId, removed.targetAt) + " "
                    + shadowSummary + " reason=" + reason);
        } catch (RuntimeException error) {
            AppLog.e(this, "SmartWake shadow session telemetry failed id=" + alarmId, error);
        }
        AppLog.d(this, "SMART_WAKE_SESSION_STOP session_id="
                + sessionId(removed.alarmId, removed.targetAt)
                + " occurrence_id=" + sessionId(removed.alarmId, removed.targetAt)
                + " monitoring_start=" + removed.monitoringStartedAt
                + " EXPECTED_MONITORING_START_AT=" + removed.expectedMonitoringStartAt
                + " ACTUAL_MONITORING_START_AT=" + removed.monitoringStartedAt
                + " MONITORING_START_DELAY_MS=" + (removed.expectedMonitoringStartAt > 0L
                ? removed.monitoringStartedAt - removed.expectedMonitoringStartAt : -1L)
                + " MONITORING_START_SOURCE=" + removed.monitoringStartSource
                + " earliest_wake=" + removed.wakeWindowStartAt
                + " deadline=" + removed.targetAt
                + " BASELINE_READY_AT=" + (removed.baselineReadyAt == Long.MIN_VALUE
                ? "NO_DATA" : Long.toString(removed.baselineReadyAt))
                + " TIME_FROM_MONITORING_START_TO_BASELINE_READY_MS="
                + (removed.baselineReadyAt == Long.MIN_VALUE ? "NO_DATA"
                : Long.toString(removed.baselineReadyAt - removed.monitoringStartedAt))
                + " evaluation_count=" + removed.evaluationCount
                + " normal_evaluations=" + removed.normalEvaluations
                + " watching_evaluations=" + removed.watchingEvaluations
                + " candidate_evaluations=" + removed.candidateEvaluations
                + " NORMAL_EVALUATION_COUNT=" + removed.normalEvaluations
                + " WATCHING_EVALUATION_COUNT=" + removed.watchingEvaluations
                + " CANDIDATE_EVALUATION_COUNT=" + removed.candidateEvaluations
                + " " + removed.cadenceStats.summary()
                + " " + removed.wakeLockStats.summary()
                + (shadowSummary.isEmpty() ? "" : " " + shadowSummary)
                + " reason=" + reason);
        if (releaseWhenEmpty && SmartWakeRuntimePolicy.shouldReleaseResources(sessions.size())) {
            releaseMonitoringResources(reason);
        }
    }

    private void releaseMonitoringResources(String reason) {
        if (candidateWakeLock != null) candidateWakeLock.stopAll(reason);
        if (resourcesReleased) return;
        resourcesReleased = true;
        handler.removeCallbacks(evaluateRunnable);
        unregisterMotion(reason);
        if (sensorManager != null && directHeartRateRegistered && directHeartRateSensor != null) {
            try {
                sensorManager.unregisterListener(this, directHeartRateSensor);
            } catch (RuntimeException error) {
                AppLog.e(this, "SmartWake direct HR unregister failed", error);
            }
        }
        directHeartRateRegistered = false;
        directHeartRateSensor = null;
        if (measureClient != null && heartRateMeasureRegistrationRequested) {
            try {
                measureClient.unregisterMeasureCallbackAsync(
                        DataType.HEART_RATE_BPM, heartRateCallback);
            } catch (RuntimeException error) {
                AppLog.e(this, "SmartWake Health Services unregister failed", error);
            }
        }
        heartRateMeasureRegistrationRequested = false;
        AppLog.d(this, "SMART_WAKE_RESOURCES_RELEASED reason=" + reason
                + " accel_registration_count=" + accelerometerRegistrationCount
                + " accel_sample_count=" + accelerometerSampleCount
                + " gyro_registration_count=" + gyroscopeRegistrationCount
                + " gyro_sample_count=" + gyroscopeSampleCount
                + " step_sample_count=" + stepSampleCount);
    }

    private static String sessionId(int alarmId, long targetAt) {
        return alarmId + ":" + targetAt;
    }

    /** Session-only timing, deliberately independent from the detector's evidence accounting. */
    private static final class CadenceStats {
        private long lastStartedAt = Long.MIN_VALUE;
        private long minActualIntervalMs = Long.MAX_VALUE;
        private long maxActualIntervalMs;
        private long maxEvaluationDurationMs;
        private long overrunCount;
        private final IntervalStats normal = new IntervalStats();
        private final IntervalStats watching = new IntervalStats();
        private final IntervalStats candidate = new IntervalStats();
        private final SmartWakeEvaluationTiming.LatenessStats normalLateness =
                new SmartWakeEvaluationTiming.LatenessStats();
        private final SmartWakeEvaluationTiming.LatenessStats watchingLateness =
                new SmartWakeEvaluationTiming.LatenessStats();
        private final SmartWakeEvaluationTiming.LatenessStats candidateLateness =
                new SmartWakeEvaluationTiming.LatenessStats();
        private long totalSuspendGapMs;
        private long maxSuspendGapMs;

        void record(long startedAt, long durationMs, SmartWakeEvaluationCadence.Mode targetMode,
                    SmartWakeEvaluationTiming timing) {
            maxEvaluationDurationMs = Math.max(maxEvaluationDurationMs, durationMs);
            if (durationMs > targetMode.intervalMs) overrunCount++;
            latenessStats(targetMode).add(timing.lateByMs);
            totalSuspendGapMs += timing.suspendGapMs;
            maxSuspendGapMs = Math.max(maxSuspendGapMs, timing.suspendGapMs);
            if (lastStartedAt != Long.MIN_VALUE) {
                long actualIntervalMs = Math.max(0L, startedAt - lastStartedAt);
                minActualIntervalMs = Math.min(minActualIntervalMs, actualIntervalMs);
                maxActualIntervalMs = Math.max(maxActualIntervalMs, actualIntervalMs);
                intervalStats(targetMode).add(actualIntervalMs);
            }
            lastStartedAt = startedAt;
        }

        String summary() {
            return "NORMAL_AVG_INTERVAL_MS=" + normal.average()
                    + " WATCHING_AVG_INTERVAL_MS=" + watching.average()
                    + " CANDIDATE_AVG_INTERVAL_MS=" + candidate.average()
                    + " MIN_ACTUAL_INTERVAL_MS=" + (minActualIntervalMs == Long.MAX_VALUE ? -1L : minActualIntervalMs)
                    + " MAX_ACTUAL_INTERVAL_MS=" + maxActualIntervalMs
                    + " MAX_EVALUATION_DURATION_MS=" + maxEvaluationDurationMs
                    + " EVALUATION_OVERRUN_COUNT=" + overrunCount
                    + " NORMAL_AVG_LATENESS_MS=" + normalLateness.average()
                    + " NORMAL_MAX_LATENESS_MS=" + normalLateness.maxMs
                    + " WATCHING_AVG_LATENESS_MS=" + watchingLateness.average()
                    + " WATCHING_MAX_LATENESS_MS=" + watchingLateness.maxMs
                    + " CANDIDATE_AVG_LATENESS_MS=" + candidateLateness.average()
                    + " CANDIDATE_MAX_LATENESS_MS=" + candidateLateness.maxMs
                    + " TOTAL_SUSPEND_GAP_MS=" + totalSuspendGapMs
                    + " MAX_SUSPEND_GAP_MS=" + maxSuspendGapMs;
        }

        private IntervalStats intervalStats(SmartWakeEvaluationCadence.Mode mode) {
            if (mode == SmartWakeEvaluationCadence.Mode.CANDIDATE) return candidate;
            if (mode == SmartWakeEvaluationCadence.Mode.WATCHING) return watching;
            return normal;
        }

        private SmartWakeEvaluationTiming.LatenessStats latenessStats(
                SmartWakeEvaluationCadence.Mode mode) {
            if (mode == SmartWakeEvaluationCadence.Mode.CANDIDATE) return candidateLateness;
            if (mode == SmartWakeEvaluationCadence.Mode.WATCHING) return watchingLateness;
            return normalLateness;
        }
    }

    private static final class IntervalStats {
        long count, totalMs;
        void add(long intervalMs) { count++; totalMs += intervalMs; }
        long average() { return count == 0 ? -1L : totalMs / count; }
    }

    private boolean isUserUnlocked() {
        UserManager manager = getSystemService(UserManager.class);
        return manager == null || manager.isUserUnlocked();
    }

    private boolean hasDirectBootSession() {
        for (MonitorSession session : sessions.values()) {
            if (session.directBootRecovery) return true;
        }
        return false;
    }

    private void seedUnlockedWearState(SmartWakeDetector detector) {
        WearStateStore wearState = new WearStateStore(this);
        detector.seedUserActivity(userActivity(wearState.userActivityState()),
                wearState.userActivityStateChangeAt(), wearState.userActivityCallbackReceivedAt());
    }
}
