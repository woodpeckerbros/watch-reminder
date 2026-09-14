package com.woodpeckerbros.watchreminder.reminder;

import android.app.NotificationChannel;

/**
 * Makes a full-screen transport notification visibly alerting to Wear SystemUI.
 * The actual sound and repeating vibration still start only from the alert Activity.
 */
public final class AlertAttention {
    public static final long[] VIBRATION = {0L, 1L};

    private AlertAttention() {
    }

    public static void configure(NotificationChannel channel) {
        channel.setVibrationPattern(VIBRATION);
        channel.enableVibration(true);
        channel.setSound(null, null);
    }
}
