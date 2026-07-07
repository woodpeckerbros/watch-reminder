package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

public class WearStateGate {
    private static final int DEFER_SNOOZE_MINUTES = 5;
    private static final long OFF_BODY_PROBE_TIMEOUT_MS = 1500L;

    public interface Result {
        void onReady(boolean defer);
    }

    private WearStateGate() {
    }

    public static boolean shouldDeferKnown(Context context) {
        return new WearStateStore(context).shouldDeferAlerts();
    }

    public static void evaluate(Context context, Result result) {
        WearStateStore store = new WearStateStore(context);
        if (store.asleep()) {
            AppLog.w(context, "WearStateGate defer: asleep");
            result.onReady(true);
            return;
        }
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true);
        if (sensorManager == null || sensor == null) {
            AppLog.d(context, "WearStateGate no off-body sensor, cached offBody=" + store.offBody());
            result.onReady(store.offBody());
            return;
        }
        Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] finished = {false};
        SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (finished[0]) {
                    return;
                }
                finished[0] = true;
                sensorManager.unregisterListener(this);
                boolean onBody = event.values != null && event.values.length > 0 && event.values[0] == 1.0f;
                store.setOffBody(!onBody);
                AppLog.d(context, "WearStateGate sensor onBody=" + onBody);
                result.onReady(!onBody);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };
        boolean registered = sensorManager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL,
                handler
        );
        if (!registered) {
            AppLog.w(context, "WearStateGate sensor register failed");
            result.onReady(store.offBody());
            return;
        }
        handler.postDelayed(() -> {
            if (finished[0]) {
                return;
            }
            finished[0] = true;
            sensorManager.unregisterListener(listener);
            AppLog.w(context, "WearStateGate sensor timeout cached offBody=" + store.offBody());
            result.onReady(store.offBody());
        }, OFF_BODY_PROBE_TIMEOUT_MS);
    }

    public static void defer(Context context, String reminderId, String reminderName) {
        defer(context, reminderId, reminderName, System.currentTimeMillis());
    }

    public static void defer(Context context, String reminderId, String reminderName, long originalScheduledAt) {
        AppLog.w(context, "WearStateGate defer id=" + reminderId + " name=" + reminderName);
        DeferredWearStateService.start(context);
        ReminderScheduler.scheduleWatchdog(context);
        ComplicationRefresh.request(context);
    }
}
