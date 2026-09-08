package com.woodpeckerbros.watchreminder.reminder;

import com.woodpeckerbros.watchreminder.*;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Persistent second path for restoring AlarmManager entries after a watch reboot. */
public class ReminderRecoveryJobService extends JobService {
    private static final int JOB_ID = 0x5a4d10;
    private static final int POST_UPDATE_JOB_ID = JOB_ID + 1;
    private static final long INTERVAL_MS = 15 * 60_000L;
    private static final long POST_UPDATE_DELAY_MS = 30_000L;
    private static final long RECENT_RECOVERY_WINDOW_MS = 60_000L;
    private static final String RECOVERY_PREFS = "reminder_recovery_state";
    private static final String KEY_LAST_COMPLETED_AT = "last_completed_at";
    private static final String KEY_POST_UPDATE_AT = "post_update_at";

    public static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) {
            AppLog.w(context, "recovery job unavailable");
            return;
        }
        JobInfo job = new JobInfo.Builder(
                JOB_ID,
                new ComponentName(context, ReminderRecoveryJobService.class))
                .setPersisted(true)
                .setPeriodic(INTERVAL_MS)
                .build();
        int result = scheduler.schedule(job);
        AppLog.d(context, "recovery job scheduled result=" + result);
    }

    /**
     * Repairs schedules after an APK update without competing with the first visible frame.
     * A normal periodic job remains as the persistent fallback.
     */
    public static void schedulePostUpdateRecovery(Context context) {
        context.getApplicationContext().getSharedPreferences(RECOVERY_PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_POST_UPDATE_AT, System.currentTimeMillis()).apply();
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) {
            AppLog.w(context, "post-update recovery job unavailable");
            return;
        }
        JobInfo job = new JobInfo.Builder(
                POST_UPDATE_JOB_ID,
                new ComponentName(context, ReminderRecoveryJobService.class))
                .setMinimumLatency(POST_UPDATE_DELAY_MS)
                .build();
        int result = scheduler.schedule(job);
        AppLog.d(context, "post-update recovery scheduled result=" + result);
    }

    public static void markRecoveryCompleted(Context context) {
        context.getApplicationContext().getSharedPreferences(RECOVERY_PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_COMPLETED_AT, System.currentTimeMillis()).apply();
    }

    private static boolean wasRecoveredRecently(Context context) {
        long completedAt = context.getSharedPreferences(RECOVERY_PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_COMPLETED_AT, 0L);
        return completedAt > 0L && System.currentTimeMillis() - completedAt < RECENT_RECOVERY_WINDOW_MS;
    }

    private static boolean postUpdateRecoveryIsComplete(Context context) {
        long postUpdateAt = context.getSharedPreferences(RECOVERY_PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_POST_UPDATE_AT, 0L);
        long completedAt = context.getSharedPreferences(RECOVERY_PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_COMPLETED_AT, 0L);
        return postUpdateAt > 0L && completedAt >= postUpdateAt;
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        AppLog.d(this, "recovery job started");
        new Thread(() -> {
            try {
                // A periodic JobService has no foreground-service start exemption. It recovers
                // AlarmManager entries only; the monitoring FGS starts from visible UI or the
                // original boot receiver path.
                boolean postUpdate = params.getJobId() == POST_UPDATE_JOB_ID;
                if (postUpdate ? !postUpdateRecoveryIsComplete(this) : !wasRecoveredRecently(this)) {
                    BootReceiver.recover(this, false);
                } else {
                    AppLog.d(this, "recovery job skipped; recent recovery already completed");
                }
                if (postUpdate) {
                    schedule(this);
                }
            } catch (RuntimeException error) {
                AppLog.e(this, "recovery job failed", error);
            } finally {
                jobFinished(params, false);
            }
        }, "reminder-recovery").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
