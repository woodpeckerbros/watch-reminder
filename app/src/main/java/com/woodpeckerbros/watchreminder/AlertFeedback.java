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

    public void stop() {
        handler.removeCallbacksAndMessages(null);
        stopSound();
        stopVibration(context);
    }

    private void startInternal(ReminderSettings settings) {
        int durationMs = settings.alertDurationMs();
        if (settings.vibrationEnabled()) {
            startVibration(settings);
        }
        if (settings.alertSoundEnabled() && settings.alertVolumePercent() > 0) {
            startSound(settings);
        }
        handler.postDelayed(this::stop, durationMs);
    }

    private void startSound(ReminderSettings settings) {
        try {
            Uri uri = soundUri(settings);
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
            float volume = settings.alertVolumePercent() / 100f;
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

    private Uri soundUri(ReminderSettings settings) {
        String saved = settings.alertSoundUri();
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

    private void startVibration(ReminderSettings settings) {
        stopVibration(context);
        if (ReminderSettings.VIBRATION_OFF.equals(settings.vibrationStyle())) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (manager != null) {
                    manager.getDefaultVibrator().vibrate(VibrationEffect.createWaveform(settings.vibrationPattern(), -1));
                }
            } catch (Exception ignored) {
            }
            return;
        }
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            vibrator.vibrate(VibrationEffect.createWaveform(settings.vibrationPattern(), -1));
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
