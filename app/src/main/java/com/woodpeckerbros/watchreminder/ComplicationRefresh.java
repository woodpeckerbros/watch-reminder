package com.woodpeckerbros.watchreminder;

import android.content.ComponentName;
import android.content.Context;

import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester;

public class ComplicationRefresh {
    private ComplicationRefresh() {
    }

    public static void request(Context context) {
        try {
            ComplicationDataSourceUpdateRequester.create(
                    context,
                    new ComponentName(context, NextReminderComplicationService.class)
            ).requestUpdateAll();
        } catch (Exception ignored) {
        }
    }
}
