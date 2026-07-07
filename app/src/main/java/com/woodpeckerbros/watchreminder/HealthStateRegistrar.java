package com.woodpeckerbros.watchreminder;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.health.services.client.HealthServices;
import androidx.health.services.client.data.PassiveListenerConfig;

public class HealthStateRegistrar {
    private HealthStateRegistrar() {
    }

    public static void register(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            PassiveListenerConfig config = PassiveListenerConfig.builder()
                    .setShouldUserActivityInfoBeRequested(true)
                    .build();
            HealthServices.getClient(context)
                    .getPassiveMonitoringClient()
                    .setPassiveListenerServiceAsync(ReminderHealthPassiveService.class, config);
        } catch (Exception ignored) {
        }
    }
}
