package com.woodpeckerbros.watchreminder;

import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Recalculates every schedule whose time can depend on the zmanim settings. */
public final class ZmanimRescheduler {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private ZmanimRescheduler() {
    }

    public static void schedule(Context context) {
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> rescheduleNow(appContext));
    }

    static void rescheduleNow(Context context) {
        AppLog.d(context, "zmanim reschedule begin");
        new ReminderStore(context).rescheduleAll();
        ReminderDueChecker.dispatchAfterZmanimChange(context);
        JewishDayScheduler.schedule(context);
        TekufaScheduler.schedule(context);
        MoonBlessingScheduler.schedule(context);
        DafYomiScheduler.schedule(context);
        OmerScheduler.schedule(context);
        ReminderScheduler.scheduleWatchdog(context);
        ComplicationRefresh.request(context);
        AppLog.d(context, "zmanim reschedule end");
    }
}
