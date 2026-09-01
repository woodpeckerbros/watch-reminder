package com.woodpeckerbros.watchreminder;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Persistent second path for restoring AlarmManager entries after a watch reboot. */
public class ReminderRecoveryJobService extends JobService {
    private static final int JOB_ID = 0x5a4d10;
    private static final long INTERVAL_MS = 15 * 60_000L;

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

    @Override
    public boolean onStartJob(JobParameters params) {
        AppLog.d(this, "recovery job started");
        new Thread(() -> {
            try {
                BootReceiver.recover(this);
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
