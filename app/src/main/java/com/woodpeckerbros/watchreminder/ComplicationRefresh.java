package com.woodpeckerbros.watchreminder;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester;

public class ComplicationRefresh {
    private static final long DEBOUNCE_MS = 15_000L;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static boolean pending;
    private static boolean includeAll;

    private ComplicationRefresh() {
    }

    public static synchronized void request(Context context) {
        request(context, false);
    }

    public static synchronized void requestAll(Context context) {
        request(context, true);
    }

    private static synchronized void request(Context context, boolean all) {
        includeAll |= all;
        if (pending) {
            return;
        }
        pending = true;
        Context applicationContext = context.getApplicationContext();
        HANDLER.postDelayed(() -> {
            boolean refreshAll;
            synchronized (ComplicationRefresh.class) {
                pending = false;
                refreshAll = includeAll;
                includeAll = false;
            }
            requestNow(applicationContext, refreshAll);
        }, DEBOUNCE_MS);
    }

    private static void requestNow(Context context, boolean all) {
        try {
            ComplicationDataSourceUpdateRequester.create(
                    context,
                    new ComponentName(context, NextReminderComplicationService.class)
            ).requestUpdateAll();
            ComplicationDataSourceUpdateRequester.create(
                    context,
                    new ComponentName(context, IntermittentFastingComplicationService.class)
            ).requestUpdateAll();
            if (!all) {
                return;
            }
            ComplicationDataSourceUpdateRequester.create(
                    context,
                    new ComponentName(context, ZmanimComplicationService.class)
            ).requestUpdateAll();
            ComplicationDataSourceUpdateRequester.create(
                    context,
                    new ComponentName(context, HebrewDateComplicationService.class)
            ).requestUpdateAll();
            ComplicationDataSourceUpdateRequester.create(
                    context,
                    new ComponentName(context, BlessingReminderComplicationService.class)
            ).requestUpdateAll();
        } catch (Exception ignored) {
        }
    }
}
