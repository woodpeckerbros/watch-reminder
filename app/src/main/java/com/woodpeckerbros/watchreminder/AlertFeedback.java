package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.media.RingtoneManager;
import com.woodpeckerbros.watchreminder.smartwake.SmartAlarmStore;

public class AlertFeedback {
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;

    private AlertFeedback(Context context) {
        this.context = context.getApplicationContext();
    }

    public static AlertFeedback start(Context context, ReminderSettings settings) {
        AlertFeedback feedback = new AlertFeedback(context);
        feedback.startInternal(settings);
        return feedback;
    }

    public static AlertFeedback startSmartAlarm(Context context, SmartAlarmStore settings) {
        AlertFeedback feedback = new AlertFeedback(context);
        feedback.startConfigured(
                settings.alertDurationSeconds() * 1000,
                settings.vibrationEnabled(),
                settings.vibrationStyle(),
                settings.vibrationStrength(),
                settings.soundEnabled(),
                settings.soundVolumePercent(),
                settings.soundUri());
        return feedback;
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);
        stopSound();
        stopVibration(context);
    }

    private void startInternal(ReminderSettings settings) {
        startConfigured(settings.alertDurationMs(), settings.vibrationEnabled(), settings.vibrationStyle(), 3,
                settings.alertSoundEnabled(), settings.alertVolumePercent(), settings.alertSoundUri());
    }

    private void startConfigured(int durationMs, boolean vibrationEnabled, String vibrationStyle, int vibrationStrength,
                                 boolean soundEnabled, int volumePercent, String soundUri) {
        if (vibrationEnabled) startVibration(vibrationStyle, vibrationStrength, durationMs);
        if (soundEnabled && volumePercent > 0) startSound(soundUri, volumePercent);
        handler.postDelayed(this::stop, durationMs);
    }

    private void startSound(String savedUri, int volumePercent) {
        try {
            Uri uri = soundUri(savedUri);
            if (uri == null) {
                return;
            }
            player = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            }
            float volume = volumePercent / 100f;
            player.setVolume(volume, volume);
            player.setLooping(true);
            player.setDataSource(context, uri);
            player.prepare();
            player.start();
        } catch (Exception exception) {
            AppLog.e(context, "alert sound failed", exception);
            stopSound();
        }
    }

    private Uri soundUri(String saved) {
        if (saved != null && !saved.trim().isEmpty()) {
            return Uri.parse(saved);
        }
        Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        return alarm == null ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) : alarm;
    }

    private void stopSound() {
        if (player == null) {
            return;
        }
        try {
            if (player.isPlaying()) {
                player.stop();
            }
        } catch (Exception ignored) {
        }
        try {
            player.release();
        } catch (Exception ignored) {
        }
        player = null;
    }

    private void startVibration(String style, int strength, int durationMs) {
        stopVibration(context);
        if (ReminderSettings.VIBRATION_OFF.equals(style)) {
            return;
        }
        long[] pattern = ReminderSettings.vibrationPattern(style, durationMs);
        int amplitude = strength <= 1 ? 80 : strength == 2 ? 160 : 255;
        int[] amplitudes = new int[pattern.length];
        for (int index = 0; index < amplitudes.length; index++) amplitudes[index] = index % 2 == 1 ? amplitude : 0;
        VibrationEffect effect = VibrationEffect.createWaveform(pattern, amplitudes, -1);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (manager != null) {
                    manager.getDefaultVibrator().vibrate(effect);
                }
            } catch (Exception ignored) {
            }
            return;
        }
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            vibrator.vibrate(effect);
        }
    }

    public static void stopVibration(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (manager != null) {
                    manager.cancel();
                    Vibrator vibrator = manager.getDefaultVibrator();
                    if (vibrator != null) {
                        vibrator.cancel();
                    }
                }
            } catch (Exception ignored) {
            }
            return;
        }
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            vibrator.cancel();
        }
    }
}
