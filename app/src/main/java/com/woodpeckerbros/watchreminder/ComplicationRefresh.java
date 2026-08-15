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

    private ComplicationRefresh() {
    }

    public static synchronized void request(Context context) {
        if (pending) {
            return;
        }
        pending = true;
        Context applicationContext = context.getApplicationContext();
        HANDLER.postDelayed(() -> {
            synchronized (ComplicationRefresh.class) {
                pending = false;
            }
            requestNow(applicationContext);
        }, DEBOUNCE_MS);
    }

    private static void requestNow(Context context) {
        try {
            ComplicationDataSourceUpdateRequester.create(
                    context,
                    new ComponentName(context, NextReminderComplicationService.class)
            ).requestUpdateAll();
            ComplicationDataSourceUpdateRequester.create(
                    context,
                    new ComponentName(context, IntermittentFastingComplicationService.class)
            ).requestUpdateAll();
        } catch (Exception ignored) {
        }
    }
}
