package com.woodpeckerbros.watchreminder.reminder;

import com.woodpeckerbros.watchreminder.*;

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
import com.woodpeckerbros.watchreminder.smartalarm.SmartAlarmStore;

import java.util.concurrent.atomic.AtomicLong;

public class AlertFeedback {
    private static final Object VIBRATION_LOCK = new Object();
    private static final Object SOUND_LOCK = new Object();
    private static final AtomicLong NEXT_VIBRATION_OWNER = new AtomicLong();
    /** A hardware vibration must never rely on a later cancel() call to end. */
    private static final int MAX_VIBRATION_CHUNK_MS = 2_000;
    private static long activeVibrationOwner;
    private static MediaPlayer activeSoundPlayer;
    private static long activeSoundOwner;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private long soundOwner;
    private long soundDeadlineAt;
    private long vibrationOwner;
    private long vibrationDeadlineAt;
    private String vibrationStyle;
    private int vibrationStrength;

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

    public static AlertFeedback preview(Context context, boolean vibrationEnabled, String vibrationStyle,
                                        int vibrationStrength, boolean soundEnabled, int volumePercent,
                                        String soundUri) {
        AlertFeedback feedback = new AlertFeedback(context);
        feedback.startConfigured(4_000, vibrationEnabled, vibrationStyle, vibrationStrength,
                soundEnabled, volumePercent, soundUri);
        return feedback;
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);
        stopSound();
        stopOwnedVibration();
    }

    private void startInternal(ReminderSettings settings) {
        startConfigured(settings.alertDurationMs(), settings.vibrationEnabled(), settings.vibrationStyle(),
                settings.vibrationStrength(),
                settings.alertSoundEnabled(), settings.alertVolumePercent(), settings.alertSoundUri());
    }

    private void startConfigured(int durationMs, boolean vibrationEnabled, String vibrationStyle, int vibrationStrength,
                                 boolean soundEnabled, int volumePercent, String soundUri) {
        if (vibrationEnabled) startVibration(vibrationStyle, vibrationStrength, durationMs);
        if (soundEnabled && volumePercent > 0) startSound(soundUri, volumePercent, durationMs);
        handler.postDelayed(this::stop, durationMs);
    }

    private void startSound(String savedUri, int volumePercent, int durationMs) {
        final long owner = NEXT_VIBRATION_OWNER.incrementAndGet();
        try {
            Uri uri = soundUri(savedUri);
            if (uri == null) {
                return;
            }
            MediaPlayer nextPlayer = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                nextPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            }
            float volume = volumePercent / 100f;
            nextPlayer.setVolume(volume, volume);
            // Do not give MediaPlayer an infinite loop. If the process dies or an action races,
            // the current file still ends naturally instead of continuing forever.
            nextPlayer.setLooping(false);
            nextPlayer.setDataSource(context, uri);
            nextPlayer.setOnCompletionListener(ignored -> replaySoundIfStillActive(owner));
            nextPlayer.prepare();
            synchronized (SOUND_LOCK) {
                stopActiveSoundLocked();
                player = nextPlayer;
                soundOwner = owner;
                soundDeadlineAt = android.os.SystemClock.uptimeMillis() + Math.max(1, durationMs);
                activeSoundPlayer = nextPlayer;
                activeSoundOwner = owner;
            }
            nextPlayer.start();
            AppLog.d(context, "alert sound started owner=" + owner);
        } catch (Exception exception) {
            AppLog.e(context, "alert sound failed", exception);
            stopSound();
        }
    }

    private void replaySoundIfStillActive(long owner) {
        synchronized (SOUND_LOCK) {
            if (owner != soundOwner || owner != activeSoundOwner || player == null
                    || android.os.SystemClock.uptimeMillis() >= soundDeadlineAt) {
                return;
            }
            try {
                player.seekTo(0);
                player.start();
            } catch (Exception error) {
                AppLog.e(context, "alert sound replay failed", error);
                stopActiveSoundLocked();
                player = null;
                soundOwner = 0;
            }
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
        synchronized (SOUND_LOCK) {
            if (soundOwner != 0 && soundOwner == activeSoundOwner) {
                long stoppedOwner = soundOwner;
                stopActiveSoundLocked();
                AppLog.d(context, "alert sound stopped owner=" + stoppedOwner);
            }
            player = null;
            soundOwner = 0;
            soundDeadlineAt = 0;
        }
    }

    private static void stopActiveSoundLocked() {
        if (activeSoundPlayer != null) {
            try { activeSoundPlayer.stop(); } catch (Exception ignored) {}
            try { activeSoundPlayer.release(); } catch (Exception ignored) {}
        }
        activeSoundPlayer = null;
        activeSoundOwner = 0;
    }

    private void startVibration(String style, int strength, int durationMs) {
        if (ReminderSettings.VIBRATION_OFF.equals(style)) {
            return;
        }
        long owner;
        synchronized (VIBRATION_LOCK) {
            cancelVibration(context);
            owner = NEXT_VIBRATION_OWNER.incrementAndGet();
            vibrationOwner = owner;
            activeVibrationOwner = owner;
            vibrationDeadlineAt = android.os.SystemClock.uptimeMillis() + Math.max(1, durationMs);
            vibrationStyle = style;
            vibrationStrength = strength;
        }
        playVibrationChunk(owner);
    }

    /**
     * Wear OS receives only a finite waveform. The next chunk is scheduled by the app while the
     * alert is still active; if the process or cancellation path fails, hardware stops by itself.
     */
    private void playVibrationChunk(long owner) {
        final long now = android.os.SystemClock.uptimeMillis();
        final long remaining;
        final String style;
        final int strength;
        synchronized (VIBRATION_LOCK) {
            if (vibrationOwner != owner || activeVibrationOwner != owner || now >= vibrationDeadlineAt) {
                return;
            }
            remaining = Math.min(MAX_VIBRATION_CHUNK_MS, vibrationDeadlineAt - now);
            style = vibrationStyle;
            strength = vibrationStrength;
        }
        long[] pattern = ReminderSettings.vibrationPattern(style, (int) remaining);
        int normalizedStrength = Math.max(1, Math.min(10, strength));
        int amplitude = Math.round(normalizedStrength * 255f / 10f);
        int[] amplitudes = new int[pattern.length];
        for (int index = 0; index < amplitudes.length; index++) amplitudes[index] = index % 2 == 1 ? amplitude : 0;
        AudioAttributes alarmAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (manager != null) {
                    Vibrator vibrator = manager.getDefaultVibrator();
                    if (vibrator != null && vibrator.hasVibrator()) {
                        VibrationEffect effect = vibrator.hasAmplitudeControl()
                                ? VibrationEffect.createWaveform(pattern, amplitudes, -1)
                                : VibrationEffect.createWaveform(pattern, -1);
                        vibrator.vibrate(effect, alarmAttributes);
                        AppLog.d(context, "alert vibration chunk owner=" + owner + " durationMs=" + remaining);
                    } else {
                        AppLog.w(context, "alert vibration unavailable: no default vibrator");
                    }
                }
            } catch (Exception error) {
                AppLog.e(context, "alert vibration failed", error);
            }
            scheduleNextVibrationChunk(owner, remaining);
            return;
        }
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            VibrationEffect effect = vibrator.hasAmplitudeControl()
                ? VibrationEffect.createWaveform(pattern, amplitudes, -1)
                : VibrationEffect.createWaveform(pattern, -1);
        vibrator.vibrate(effect, alarmAttributes);
            AppLog.d(context, "alert vibration chunk owner=" + owner + " durationMs=" + remaining);
        } else {
            AppLog.w(context, "alert vibration unavailable: no vibrator");
        }
        scheduleNextVibrationChunk(owner, remaining);
    }

    private void scheduleNextVibrationChunk(long owner, long chunkDurationMs) {
        if (chunkDurationMs >= MAX_VIBRATION_CHUNK_MS) {
            handler.postDelayed(() -> playVibrationChunk(owner), chunkDurationMs);
        }
    }

    public static void stopVibration(Context context) {
        synchronized (VIBRATION_LOCK) {
            activeVibrationOwner = 0;
            cancelVibration(context);
        }
    }

    private void stopOwnedVibration() {
        synchronized (VIBRATION_LOCK) {
            if (vibrationOwner == 0 || activeVibrationOwner != vibrationOwner) {
                return;
            }
            long stoppedOwner = vibrationOwner;
            activeVibrationOwner = 0;
            vibrationOwner = 0;
            vibrationDeadlineAt = 0;
            cancelVibration(context);
            AppLog.d(context, "alert vibration stopped owner=" + stoppedOwner);
        }
    }

    private static void cancelVibration(Context context) {
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
