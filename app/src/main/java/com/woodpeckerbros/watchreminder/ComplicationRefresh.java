package com.woodpeckerbros.watchreminder;

import com.woodpeckerbros.watchreminder.reminder.*;

import com.woodpeckerbros.watchreminder.zmanim.*;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester;

public class ComplicationRefresh {
    private static final long DEBOUNCE_MS = 15_000L;
    private static final long ACTIVATION_RETRY_MS = 1_500L;
    private static final long CONFIGURATION_REFRESH_MS = 2_000L;
    private static final String ONEPLUS_UPDATE_RECEIVER_PACKAGE = "com.google.wear.services";
    private static final String ACTION_REQUEST_UPDATE =
            "android.support.wearable.complications.ACTION_REQUEST_UPDATE";
    private static final String ACTION_REQUEST_UPDATE_ALL =
            "android.support.wearable.complications.ACTION_REQUEST_UPDATE_ALL";
    private static final String EXTRA_PROVIDER_COMPONENT =
            "android.support.wearable.complications.EXTRA_PROVIDER_COMPONENT";
    private static final String EXTRA_COMPLICATION_IDS =
            "android.support.wearable.complications.EXTRA_COMPLICATION_IDS";
    private static final String EXTRA_PENDING_INTENT =
            "android.support.wearable.complications.EXTRA_PENDING_INTENT";
    private static final int NEXT_REMINDER = 1;
    private static final int FASTING = 1 << 1;
    private static final int STATIC_AND_DATE = 1 << 2;
    private static final int WATER = 1 << 3;
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

    public static synchronized void requestWater(Context context) {
        request(context, WATER);
    }

    public static synchronized void requestAll(Context context) {
        request(context, NEXT_REMINDER | FASTING | STATIC_AND_DATE | WATER);
    }

    /**
     * Some watch-face editors persist the selected provider before their complication manager is
     * ready to perform the usual first data request. Request the newly activated instance
     * explicitly, then repeat once after that short persistence race has passed.
     */
    public static void requestActivated(Context context, Class<?> serviceClass, int complicationId) {
        Context applicationContext = context.getApplicationContext();
        AppLog.d(applicationContext, "Complication activated provider="
                + serviceClass.getSimpleName() + " id=" + complicationId);
        update(applicationContext, serviceClass, complicationId);
        HANDLER.postDelayed(
                () -> update(applicationContext, serviceClass, complicationId),
                ACTIVATION_RETRY_MS
        );
    }

    /**
     * A provider configuration result is committed by Wear OS only after the activity finishes.
     * Refresh afterwards so the request can see the newly assigned provider instance.
     */
    public static void requestAfterConfiguration(Context context) {
        Context applicationContext = context.getApplicationContext();
        HANDLER.postDelayed(
                () -> requestNow(applicationContext,
                        NEXT_REMINDER | FASTING | STATIC_AND_DATE | WATER),
                CONFIGURATION_REFRESH_MS
        );
    }

    public static void logDataRequest(Context context, Class<?> serviceClass,
                                      int complicationId, Object type) {
        AppLog.d(context, "Complication data requested provider=" + serviceClass.getSimpleName()
                + " id=" + complicationId + " type=" + type);
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
            if ((targets & WATER) != 0) {
                update(context, WaterReminderComplicationService.class);
            }
        } catch (Exception ignored) {
        }
    }

    private static void update(Context context, Class<?> serviceClass) {
        ComponentName component = new ComponentName(context, serviceClass);
        try {
            ComplicationDataSourceUpdateRequester.create(context, component).requestUpdateAll();
        } catch (RuntimeException error) {
            AppLog.e(context, "Complication standard update-all failed provider="
                    + component.flattenToShortString(), error);
        }
        sendOnePlusCompatibilityRequest(context, component, null);
    }

    private static void update(Context context, Class<?> serviceClass, int complicationId) {
        ComponentName component = new ComponentName(context, serviceClass);
        try {
            ComplicationDataSourceUpdateRequester.create(context, component)
                    .requestUpdate(complicationId);
        } catch (RuntimeException error) {
            AppLog.e(context, "Complication standard targeted update failed provider="
                    + component.flattenToShortString() + " id=" + complicationId, error);
        }
        sendOnePlusCompatibilityRequest(context, component, new int[]{complicationId});
    }

    /**
     * AndroidX 1.3 sends the pre-Android-16 update broadcast only to
     * {@code com.google.android.wearable.app}. OnePlus Watch 3 hosts the complication requester in
     * {@code com.google.wear.services}, so the standard request never reaches its manager. Send
     * the same authenticated protocol to that package as a compatibility fallback. Other watches
     * simply have no matching receiver and ignore it.
     */
    private static void sendOnePlusCompatibilityRequest(Context context, ComponentName component,
                                                        int[] complicationIds) {
        if (Build.VERSION.SDK_INT >= 36) return;
        Intent request = new Intent(complicationIds == null
                ? ACTION_REQUEST_UPDATE_ALL : ACTION_REQUEST_UPDATE)
                .setPackage(ONEPLUS_UPDATE_RECEIVER_PACKAGE)
                .putExtra(EXTRA_PROVIDER_COMPONENT, component)
                .putExtra(EXTRA_PENDING_INTENT, PendingIntent.getActivity(
                        context, 0, new Intent(""), PendingIntent.FLAG_IMMUTABLE));
        if (complicationIds != null) request.putExtra(EXTRA_COMPLICATION_IDS, complicationIds);
        try {
            context.sendBroadcast(request);
            AppLog.d(context, "Complication OnePlus compatibility request provider="
                    + component.flattenToShortString()
                    + (complicationIds == null ? " all" : " id=" + complicationIds[0]));
        } catch (RuntimeException error) {
            AppLog.e(context, "Complication OnePlus compatibility request failed provider="
                    + component.flattenToShortString(), error);
        }
    }
}
