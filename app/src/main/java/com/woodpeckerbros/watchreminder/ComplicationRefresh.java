package com.woodpeckerbros.watchreminder;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester;

public class ComplicationRefresh {
    private static final long DEBOUNCE_MS = 15_000L;
    private static final int NEXT_REMINDER = 1;
    private static final int FASTING = 1 << 1;
    private static final int STATIC_AND_DATE = 1 << 2;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static boolean pending;
    private static int pendingTargets;

    private ComplicationRefresh() {
    }

    public static synchronized void request(Context context) {
        request(context, NEXT_REMINDER);
    }

    public static synchronized void requestFasting(Context context) {
        request(context, FASTING);
    }

    public static synchronized void requestNextAndFasting(Context context) {
        request(context, NEXT_REMINDER | FASTING);
    }

    public static synchronized void requestAll(Context context) {
        request(context, NEXT_REMINDER | FASTING | STATIC_AND_DATE);
    }

    private static synchronized void request(Context context, int targets) {
        pendingTargets |= targets;
        if (pending) {
            return;
        }
        pending = true;
        Context applicationContext = context.getApplicationContext();
        HANDLER.postDelayed(() -> {
            int targetsToRefresh;
            synchronized (ComplicationRefresh.class) {
                pending = false;
                targetsToRefresh = pendingTargets;
                pendingTargets = 0;
            }
            requestNow(applicationContext, targetsToRefresh);
        }, DEBOUNCE_MS);
    }

    private static void requestNow(Context context, int targets) {
        try {
            if ((targets & NEXT_REMINDER) != 0) {
                update(context, NextReminderComplicationService.class);
            }
            if ((targets & FASTING) != 0) {
                update(context, IntermittentFastingComplicationService.class);
            }
            if ((targets & STATIC_AND_DATE) != 0) {
                update(context, ZmanimComplicationService.class);
                update(context, HebrewDateComplicationService.class);
                update(context, BlessingReminderComplicationService.class);
            }
        } catch (Exception ignored) {
        }
    }

    private static void update(Context context, Class<?> serviceClass) {
        ComplicationDataSourceUpdateRequester.create(
                context,
                new ComponentName(context, serviceClass)
        ).requestUpdateAll();
    }
}
